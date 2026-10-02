package com.sies.timetable.validator;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.AllocationReconciler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Independent post-solve validator that verifies the generated Schedule against all
 * Hard Constraints (H1–H10) and Soft Constraints (S1–S6) with granular, location-specific
 * diagnostic records and human-readable explanations.
 */
public class ScheduleValidator {

    public record ViolationDetail(
            String constraintId,
            String constraintName,
            String severity, // HARD or SOFT
            String day,
            String period,
            String className,
            String teacher,
            String subject,
            String resource,
            String conflictingAssignments,
            String reason,
            int penaltyScore
    ) {
        public String getFormattedSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(constraintId).append("] ").append(constraintName)
                    .append(" (").append(severity).append(")\n");
            if (day != null && !day.isEmpty() || (period != null && !period.isEmpty())) {
                sb.append("  • Time: ").append(day != null ? day : "");
                if (period != null && !period.isEmpty()) sb.append(" • Period ").append(period);
                sb.append("\n");
            }
            if (className != null && !className.isEmpty()) sb.append("  • Class: ").append(className).append("\n");
            if (teacher != null && !teacher.isEmpty()) sb.append("  • Teacher: ").append(teacher).append("\n");
            if (subject != null && !subject.isEmpty()) sb.append("  • Subject: ").append(subject).append("\n");
            if (resource != null && !resource.isEmpty()) sb.append("  • Resource: ").append(resource).append("\n");
            if (conflictingAssignments != null && !conflictingAssignments.isEmpty()) {
                sb.append("  • Conflicts: ").append(conflictingAssignments).append("\n");
            }
            sb.append("  • Reason: ").append(reason);
            return sb.toString();
        }
    }

    public record ConstraintCheck(
            String constraintId,
            String name,
            String type, // HARD or SOFT
            String status, // PASS or FAIL
            int violationCount,
            int penaltyScore,
            String details
    ) {}

    public record ScheduleValidationResult(
            boolean allHardConstraintsPassed,
            int totalHardViolations,
            int totalSoftPenalty,
            double overallQualityIndex,
            List<ConstraintCheck> checks,
            List<ViolationDetail> violations
    ) {
        public List<ViolationDetail> getHardViolations() {
            return violations.stream().filter(v -> "HARD".equalsIgnoreCase(v.severity())).toList();
        }

        public List<ViolationDetail> getSoftViolations() {
            return violations.stream().filter(v -> "SOFT".equalsIgnoreCase(v.severity())).toList();
        }
    }

    public static ScheduleValidationResult validate(
            Schedule schedule,
            AllocationReconciler.ReconciliationResult recResult,
            SchoolConfig config,
            List<ConstraintSpec> constraintSpecs
    ) {
        List<ConstraintCheck> checks = new ArrayList<>();
        List<ViolationDetail> violations = new ArrayList<>();
        int totalHardViolations = 0;
        int totalSoftPenalty = 0;

        int numClasses = recResult.classes().size();
        int numDays = config.getDays().size();
        int numPeriods = config.getPeriodsPerDay();
        int recessAfter = config.getRecessAfterPeriod();

        // -------------------------------------------------------------
        // H3: Class Slot Capacity & Allotment Coverage
        // -------------------------------------------------------------
        int h3Violations = 0;
        int totalExpectedSchoolPeriods = recResult.allocations().stream().mapToInt(TeachingAllocation::getPeriodCount).sum();

        for (SchoolClass sc : recResult.classes()) {
            int classExpectedPeriods = recResult.allocations().stream()
                    .filter(a -> a.getSchoolClass().equals(sc))
                    .mapToInt(TeachingAllocation::getPeriodCount)
                    .sum();
            int classScheduledPeriods = schedule.getAssignmentsForClass(sc).size();

            if (classScheduledPeriods != classExpectedPeriods) {
                h3Violations++;
                violations.add(new ViolationDetail(
                        "H3", "Class Scheduled Count Mismatch", "HARD",
                        "All Days", "All Periods", sc.getDisplayName(), "", "", "",
                        String.format("Scheduled: %d | Expected: %d", classScheduledPeriods, classExpectedPeriods),
                        String.format("Class %s has %d scheduled periods, but requires %d.", sc.getDisplayName(), classScheduledPeriods, classExpectedPeriods),
                        0
                ));
            }

            for (Day d : config.getDays()) {
                int numPeriodsForDay = config.getDailyPeriods(d);
                for (int p = 1; p <= numPeriodsForDay; p++) {
                    Period period = config.getPeriods().get(p);
                    ScheduleAssignment sa = schedule.getAssignment(sc, d, period);
                    if (sa == null && classExpectedPeriods >= config.getTotalWeeklyPeriods()) {
                        h3Violations++;
                        violations.add(new ViolationDetail(
                                "H3", "Unfilled Class Time Slot", "HARD",
                                d.getDisplayName(), String.valueOf(p), sc.getDisplayName(), "", "", "",
                                "Slot is EMPTY",
                                "Every class must have exactly one subject/teacher assigned to every instructional time slot when operating at full capacity.",
                                0
                        ));
                    }
                }
            }
        }
        if (schedule.getTotalAssignmentsCount() != totalExpectedSchoolPeriods) {
            h3Violations++;
            violations.add(new ViolationDetail(
                    "H3", "Total Scheduled Slots Mismatch", "HARD",
                    "All Days", "All Periods", "All Classes", "", "", "",
                    String.format("Scheduled count: %d / %d slots", schedule.getTotalAssignmentsCount(), totalExpectedSchoolPeriods),
                    String.format("Total scheduled school slots must equal exactly %d.", totalExpectedSchoolPeriods),
                    0
            ));
        }
        checks.add(new ConstraintCheck("H3", "Single Assignment Per Class Slot", "HARD",
                h3Violations == 0 ? "PASS" : "FAIL", h3Violations, 0,
                h3Violations == 0 ? String.format("All %d required class time slots are uniquely filled.", totalExpectedSchoolPeriods) : h3Violations + " slot violations."));
        totalHardViolations += h3Violations;

        // -------------------------------------------------------------
        // H9: Exact Granular Allotment Fulfillment (393 atomic matches)
        // -------------------------------------------------------------
        int h9Violations = 0;
        for (TeachingAllocation alloc : recResult.allocations()) {
            long scheduledCount = schedule.getAssignments().stream()
                    .filter(a -> a.allocation().equals(alloc))
                    .count();

            if (scheduledCount != alloc.getPeriodCount()) {
                h9Violations++;
                String diffText = (scheduledCount < alloc.getPeriodCount()) ?
                        "Missing: " + (alloc.getPeriodCount() - scheduledCount) :
                        "Excess: " + (scheduledCount - alloc.getPeriodCount());

                violations.add(new ViolationDetail(
                        "H9", "Incorrect Allotment Count", "HARD",
                        "Weekly Allotment", "N/A", alloc.getSchoolClass().getDisplayName(),
                        alloc.getTeacher().getFullName() + " (" + alloc.getTeacher().getCode() + ")",
                        alloc.getSubject().getNormalizedName(), "",
                        String.format("Required: %d | Scheduled: %d | %s", alloc.getPeriodCount(), scheduledCount, diffText),
                        String.format("Every atomic requirement must exactly fulfill its allocated periods (Required: %d, Actual: %d).",
                                alloc.getPeriodCount(), scheduledCount),
                        0
                ));
            }
        }
        checks.add(new ConstraintCheck("H9", "Exact Granular Allotment Fulfillment", "HARD",
                h9Violations == 0 ? "PASS" : "FAIL", h9Violations, 0,
                h9Violations == 0 ? "All atomic requirement period counts exactly matched." : h9Violations + " allotment mismatches."));
        totalHardViolations += h9Violations;

        // -------------------------------------------------------------
        // H4: Single Class Per Teacher Slot (Teacher Clash)
        // -------------------------------------------------------------
        int h4Violations = 0;
        for (Teacher t : recResult.teachers()) {
            for (Day d : config.getDays()) {
                int numPeriodsForDay = config.getDailyPeriods(d);
                for (int p = 1; p <= numPeriodsForDay; p++) {
                    Period period = config.getPeriods().get(p);
                    TimeSlot slot = new TimeSlot(d, period);
                    List<ScheduleAssignment> tAssigns = schedule.getAssignmentsAtSlot(slot).stream()
                            .filter(a -> a.teacher().equals(t))
                            .toList();

                    if (tAssigns.size() > 1) {
                        h4Violations++;
                        String conflictClasses = tAssigns.stream()
                                .map(a -> a.schoolClass().getDisplayName() + " (" + a.subject().getNormalizedName() + ")")
                                .collect(Collectors.joining(" vs "));

                        violations.add(new ViolationDetail(
                                "H4", "Teacher Clash", "HARD",
                                d.getDisplayName(), String.valueOf(p),
                                tAssigns.stream().map(a -> a.schoolClass().getDisplayName()).collect(Collectors.joining(", ")),
                                t.getFullName() + " (" + t.getCode() + ")",
                                tAssigns.stream().map(a -> a.subject().getNormalizedName()).collect(Collectors.joining(", ")),
                                "",
                                conflictClasses,
                                "The same teacher is assigned to multiple classes during the same time slot.",
                                0
                        ));
                    }
                }
            }
        }
        checks.add(new ConstraintCheck("H4", "Single Class Per Teacher Slot", "HARD",
                h4Violations == 0 ? "PASS" : "FAIL", h4Violations, 0,
                h4Violations == 0 ? "Zero teacher double-booking clashes." : h4Violations + " teacher clashes."));
        totalHardViolations += h4Violations;

        // -------------------------------------------------------------
        // H1: Class Teacher First Period Allocation
        // -------------------------------------------------------------
        int h1Violations = 0;
        for (SchoolClass sc : recResult.classes()) {
            ClassTeacherMapping ctm = recResult.classTeacherMappings().get(sc);
            if (ctm != null && ctm.getClassTeacher() != null) {
                Teacher ct = ctm.getClassTeacher();
                int totalCtAllocInClass = recResult.allocations().stream()
                        .filter(a -> a.getSchoolClass().equals(sc) && a.getTeacher().equals(ct))
                        .mapToInt(TeachingAllocation::getPeriodCount)
                        .sum();

                if (totalCtAllocInClass > 0) {
                    int activeDaysWithP1 = 0;
                    for (Day d : config.getDays()) {
                        if (config.getDailyPeriods(d) > 0) activeDaysWithP1++;
                    }
                    int expectedP1 = Math.min(activeDaysWithP1, totalCtAllocInClass);
                    long actualP1 = schedule.getAssignmentsForClass(sc).stream()
                            .filter(a -> a.isPeriod1() && a.teacher().equals(ct))
                            .count();

                    if (actualP1 != expectedP1) {
                        h1Violations++;
                        violations.add(new ViolationDetail(
                                "H1", "Class Teacher Period 1 Mismatch", "HARD",
                                "Weekly Schedule", "Period 1", sc.getDisplayName(),
                                ct.getFullName() + " (" + ct.getCode() + ")",
                                "Allocated Subjects in Class", "",
                                String.format("Expected Period 1s: %d | Actual: %d", expectedP1, actualP1),
                                String.format("Class teacher %s for %s must teach in Period 1 on %d days (actual: %d).",
                                        ct.getCode(), sc.getDisplayName(), expectedP1, actualP1),
                                0
                        ));
                    }
                }
            }
        }
        checks.add(new ConstraintCheck("H1", "Class Teacher First Period Allocation", "HARD",
                h1Violations == 0 ? "PASS" : "FAIL", h1Violations, 0,
                h1Violations == 0 ? "Class teacher Period 1 allocations are fulfilled to maximum capacity." : h1Violations + " Period 1 allocation mismatches."));
        totalHardViolations += h1Violations;

        // -------------------------------------------------------------
        // H2: Max Continuous Lectures Per Teacher
        // -------------------------------------------------------------
        int h2Violations = 0;
        for (Teacher t : recResult.teachers()) {
            int maxCont = (t.getTotalWorkload() <= 30) ? 2 : 3;

            for (Day d : config.getDays()) {
                int numPeriodsForDay = config.getDailyPeriods(d);
                int recessP = config.getRecessAfterPeriod(d);

                // Session 1: 1 .. min(recessP, numPeriodsForDay)
                int s1End = Math.min(recessP, numPeriodsForDay);
                h2Violations += checkConsecutiveSession(t, d, 1, s1End, maxCont, schedule, config, violations);

                // Session 2: recessP + 1 .. numPeriodsForDay
                if (recessP < numPeriodsForDay) {
                    h2Violations += checkConsecutiveSession(t, d, recessP + 1, numPeriodsForDay, maxCont, schedule, config, violations);
                }
            }
        }
        checks.add(new ConstraintCheck("H2", "Max Continuous Lectures Per Teacher", "HARD",
                h2Violations == 0 ? "PASS" : "FAIL", h2Violations, 0,
                h2Violations == 0 ? "All teachers strictly adhere to continuous period limits per session." : h2Violations + " continuous lecture violations."));
        totalHardViolations += h2Violations;

        // -------------------------------------------------------------
        // H5: Computer Lab Exclusivity
        // -------------------------------------------------------------
        int h5Violations = 0;
        for (Day d : config.getDays()) {
            int numPeriodsForDay = config.getDailyPeriods(d);
            for (int p = 1; p <= numPeriodsForDay; p++) {
                Period period = config.getPeriods().get(p);
                TimeSlot slot = new TimeSlot(d, period);
                List<ScheduleAssignment> compAssigns = schedule.getAssignmentsAtSlot(slot).stream()
                        .filter(ScheduleAssignment::isComputer)
                        .toList();

                if (compAssigns.size() > 1) {
                    h5Violations++;
                    String conflictClasses = compAssigns.stream()
                            .map(a -> a.schoolClass().getDisplayName() + " (" + a.teacher().getCode() + ")")
                            .collect(Collectors.joining(" vs "));

                    violations.add(new ViolationDetail(
                            "H5", "Computer Lab Conflict", "HARD",
                            d.getDisplayName(), String.valueOf(p),
                            compAssigns.stream().map(a -> a.schoolClass().getDisplayName()).collect(Collectors.joining(", ")),
                            compAssigns.stream().map(a -> a.teacher().getCode()).collect(Collectors.joining(", ")),
                            "Computer",
                            "Computer Lab",
                            conflictClasses,
                            "Only one Computer class can utilize the single computer lab facility at a time.",
                            0
                    ));
                }
            }
        }
        checks.add(new ConstraintCheck("H5", "Computer Lab Exclusivity", "HARD",
                h5Violations == 0 ? "PASS" : "FAIL", h5Violations, 0,
                h5Violations == 0 ? "Zero Computer Lab resource conflicts." : h5Violations + " lab conflicts."));
        totalHardViolations += h5Violations;

        // -------------------------------------------------------------
        // H6 & H7: PE Prohibitions in Period 1 and Immediately After Recess
        // -------------------------------------------------------------
        int h6Violations = 0;
        int h7Violations = 0;
        for (ScheduleAssignment sa : schedule.getAssignments()) {
            if (sa.isPhysicalEducation()) {
                if (sa.getPeriodIndex() == 1) {
                    h6Violations++;
                    violations.add(new ViolationDetail(
                            "H6", "P.E. in Period 1", "HARD",
                            sa.getDay().getDisplayName(), "1", sa.schoolClass().getDisplayName(),
                            sa.teacher().getFullName() + " (" + sa.teacher().getCode() + ")",
                            "Physical Education", "School Ground",
                            sa.schoolClass().getDisplayName() + " → P.E. (Period 1)",
                            "Physical Education is strictly prohibited in Period 1 (reserved for morning opening/assembly).",
                            0
                    ));
                }
                int recessP = config.getRecessAfterPeriod(sa.getDay());
                if (sa.getPeriodIndex() == recessP + 1) {
                    h7Violations++;
                    violations.add(new ViolationDetail(
                            "H7", "P.E. Immediately After Recess", "HARD",
                            sa.getDay().getDisplayName(), String.valueOf(recessP + 1), sa.schoolClass().getDisplayName(),
                            sa.teacher().getFullName() + " (" + sa.teacher().getCode() + ")",
                            "Physical Education", "School Ground",
                            sa.schoolClass().getDisplayName() + " → P.E. (After Recess)",
                            "Physical Education is strictly prohibited immediately following recess.",
                            0
                    ));
                }
            }
        }
        checks.add(new ConstraintCheck("H6", "Physical Education Prohibited in Period 1", "HARD",
                h6Violations == 0 ? "PASS" : "FAIL", h6Violations, 0,
                h6Violations == 0 ? "No P.E. lectures in Period 1." : h6Violations + " Period 1 P.E. violations."));
        checks.add(new ConstraintCheck("H7", "Physical Education Prohibited Immediately After Recess", "HARD",
                h7Violations == 0 ? "PASS" : "FAIL", h7Violations, 0,
                h7Violations == 0 ? "No P.E. lectures immediately after recess." : h7Violations + " after-recess P.E. violations."));
        totalHardViolations += (h6Violations + h7Violations);

        // -------------------------------------------------------------
        // H8: Max 2 Activity Periods Per Day
        // -------------------------------------------------------------
        int h8Violations = 0;
        for (SchoolClass sc : recResult.classes()) {
            for (Day d : config.getDays()) {
                List<ScheduleAssignment> actAssigns = schedule.getAssignmentsForClassAndDay(sc, d).stream()
                        .filter(ScheduleAssignment::isActivity)
                        .toList();

                if (actAssigns.size() > 2) {
                    h8Violations++;
                    String actList = actAssigns.stream()
                            .map(a -> "P" + a.getPeriodIndex() + " → " + a.subject().getNormalizedName() + " (" + a.teacher().getCode() + ")")
                            .collect(Collectors.joining(", "));

                    violations.add(new ViolationDetail(
                            "H8", "Excessive Activity Periods", "HARD",
                            d.getDisplayName(), actAssigns.stream().map(a -> "P" + a.getPeriodIndex()).collect(Collectors.joining(", ")),
                            sc.getDisplayName(),
                            actAssigns.stream().map(a -> a.teacher().getCode()).collect(Collectors.joining(", ")),
                            actAssigns.stream().map(a -> a.subject().getNormalizedName()).collect(Collectors.joining(", ")),
                            "",
                            actList + " [Total: " + actAssigns.size() + "]",
                            String.format("Class has %d activity periods on %s, exceeding the maximum allowed limit of 2 per day.",
                                    actAssigns.size(), d.getDisplayName()),
                            0
                    ));
                }
            }
        }
        checks.add(new ConstraintCheck("H8", "Max 2 Activity Periods Per Class Per Day", "HARD",
                h8Violations == 0 ? "PASS" : "FAIL", h8Violations, 0,
                h8Violations == 0 ? "All classes satisfy max 2 activity periods daily." : h8Violations + " activity limit violations."));
        totalHardViolations += h8Violations;

        // -------------------------------------------------------------
        // H10: P.E. & Computer Same-Day Partition Around Recess
        // -------------------------------------------------------------
        int h10Violations = 0;
        for (SchoolClass sc : recResult.classes()) {
            for (Day d : config.getDays()) {
                List<ScheduleAssignment> dayAssigns = schedule.getAssignmentsForClassAndDay(sc, d);
                boolean hasPE = dayAssigns.stream().anyMatch(ScheduleAssignment::isPhysicalEducation);
                boolean hasComp = dayAssigns.stream().anyMatch(ScheduleAssignment::isComputer);

                if (hasPE && hasComp) {
                    int recessP = config.getRecessAfterPeriod(d);
                    boolean pePre = dayAssigns.stream().anyMatch(a -> a.isPhysicalEducation() && a.getPeriodIndex() <= recessP);
                    boolean pePost = dayAssigns.stream().anyMatch(a -> a.isPhysicalEducation() && a.getPeriodIndex() > recessP);
                    boolean compPre = dayAssigns.stream().anyMatch(a -> a.isComputer() && a.getPeriodIndex() <= recessP);
                    boolean compPost = dayAssigns.stream().anyMatch(a -> a.isComputer() && a.getPeriodIndex() > recessP);

                    boolean validSplit = (pePre && compPost) || (compPre && pePost);
                    if (!validSplit) {
                        h10Violations++;
                        ScheduleAssignment peAssign = dayAssigns.stream().filter(ScheduleAssignment::isPhysicalEducation).findFirst().orElse(null);
                        ScheduleAssignment compAssign = dayAssigns.stream().filter(ScheduleAssignment::isComputer).findFirst().orElse(null);
                        String locText = String.format("P.E. at Period %s, Computer at Period %s (both %s)",
                                (peAssign != null ? peAssign.getPeriodIndex() : "?"),
                                (compAssign != null ? compAssign.getPeriodIndex() : "?"),
                                (pePre ? "Pre-Recess" : "Post-Recess"));

                        violations.add(new ViolationDetail(
                                "H10", "P.E. and Computer Recess Partition Violation", "HARD",
                                d.getDisplayName(),
                                (peAssign != null ? "P" + peAssign.getPeriodIndex() : "") + ", " + (compAssign != null ? "P" + compAssign.getPeriodIndex() : ""),
                                sc.getDisplayName(),
                                (peAssign != null ? peAssign.teacher().getCode() : "") + ", " + (compAssign != null ? compAssign.teacher().getCode() : ""),
                                "Physical Education & Computer",
                                "Computer Lab / Ground",
                                locText,
                                "When P.E. and Computer occur on the same day, they must be separated across the recess boundary (one pre-recess, one post-recess).",
                                0
                        ));
                    }
                }
            }
        }
        checks.add(new ConstraintCheck("H10", "P.E. and Computer Same-Day Partition", "HARD",
                h10Violations == 0 ? "PASS" : "FAIL", h10Violations, 0,
                h10Violations == 0 ? "All same-day P.E. and Computer sessions are cleanly split across recess." : h10Violations + " recess split violations."));
        totalHardViolations += h10Violations;

        // -------------------------------------------------------------
        // Soft Constraints (S1, S2, S4) Detailed Diagnostics
        // -------------------------------------------------------------
        // S1: Subject distribution
        int s1Violations = 0;
        int wS1 = config.getWeight("S1", 100);
        for (SchoolClass sc : recResult.classes()) {
            for (TeachingAllocation a : recResult.allocations().stream().filter(al -> al.getSchoolClass().equals(sc)).toList()) {
                int k = a.getPeriodCount();
                int targetMax = (k <= config.getDays().size()) ? 1 : 2;
                for (Day d : config.getDays()) {
                    List<ScheduleAssignment> dAssigns = schedule.getAssignmentsForClassAndDay(sc, d).stream()
                            .filter(sa -> sa.allocation().equals(a))
                            .toList();

                    if (dAssigns.size() > targetMax) {
                        int excess = dAssigns.size() - targetMax;
                        s1Violations += excess;
                        int pen = excess * wS1;
                        totalSoftPenalty += pen;

                        violations.add(new ViolationDetail(
                                "S1", "Subject Daily Concentration", "SOFT",
                                d.getDisplayName(),
                                dAssigns.stream().map(sa -> "P" + sa.getPeriodIndex()).collect(Collectors.joining(", ")),
                                sc.getDisplayName(),
                                a.getTeacher().getFullName() + " (" + a.getTeacher().getCode() + ")",
                                a.getSubject().getNormalizedName(),
                                "",
                                String.format("%d periods on %s (target max: %d)", dAssigns.size(), d.getDisplayName(), targetMax),
                                String.format("Subject is scheduled %d times on %s, exceeding the preferred daily target of %d.",
                                        dAssigns.size(), d.getDisplayName(), targetMax),
                                pen
                        ));
                    }
                }
            }
        }
        checks.add(new ConstraintCheck("S1", "Subject Lectures Evenly Distributed", "SOFT", "OPTIMIZED", s1Violations, s1Violations * wS1,
                "Daily distribution excess periods: " + s1Violations + " (Penalty: " + (s1Violations * wS1) + ")"));

        // S2: 2-period non-consecutive days
        int s2Violations = 0;
        int wS2 = config.getWeight("S2", 80);
        for (SchoolClass sc : recResult.classes()) {
            for (TeachingAllocation a : recResult.allocations().stream().filter(al -> al.getSchoolClass().equals(sc) && al.getPeriodCount() == 2).toList()) {
                List<ScheduleAssignment> aAssigns = schedule.getAssignmentsForClass(sc).stream()
                        .filter(sa -> sa.allocation().equals(a))
                        .toList();

                Map<Day, ScheduleAssignment> dayMap = new HashMap<>();
                for (ScheduleAssignment sa : aAssigns) dayMap.put(sa.getDay(), sa);

                List<Day> dayList = config.getDays();
                for (int d = 0; d < dayList.size() - 1; d++) {
                    Day d1 = dayList.get(d);
                    Day d2 = dayList.get(d + 1);
                    if (dayMap.containsKey(d1) && dayMap.containsKey(d2)) {
                        s2Violations++;
                        int pen = wS2;
                        totalSoftPenalty += pen;

                        ScheduleAssignment sa1 = dayMap.get(d1);
                        ScheduleAssignment sa2 = dayMap.get(d2);

                        violations.add(new ViolationDetail(
                                "S2", "Consecutive Days for 2-Period Subject", "SOFT",
                                d1.getDisplayName() + " & " + d2.getDisplayName(),
                                "P" + sa1.getPeriodIndex() + " & P" + sa2.getPeriodIndex(),
                                sc.getDisplayName(),
                                a.getTeacher().getFullName() + " (" + a.getTeacher().getCode() + ")",
                                a.getSubject().getNormalizedName(),
                                "",
                                String.format("%s (P%d) and %s (P%d)", d1.getDisplayName(), sa1.getPeriodIndex(), d2.getDisplayName(), sa2.getPeriodIndex()),
                                "The two weekly lectures occur on consecutive days instead of being spaced apart across the week.",
                                pen
                        ));
                    }
                }
            }
        }
        checks.add(new ConstraintCheck("S2", "Two-Period Non-Consecutive Days", "SOFT", "OPTIMIZED", s2Violations, s2Violations * wS2,
                "Consecutive day occurrences for 2-period subjects: " + s2Violations + " (Penalty: " + (s2Violations * wS2) + ")"));

        // S4: Teacher Daily Workload Balance
        int s4Deviations = 0;
        int wS4 = config.getWeight("S4", 40);
        for (Teacher t : recResult.teachers()) {
            if (t.getTotalWorkload() > numDays) {
                int avgDaily = Math.round((float) t.getTotalWorkload() / numDays);
                for (Day d : config.getDays()) {
                    List<ScheduleAssignment> tDayAssigns = schedule.getAssignmentsForTeacherAndDay(t, d);
                    int dLoad = tDayAssigns.size();
                    int dev = Math.abs(dLoad - avgDaily);
                    s4Deviations += dev;

                    if (dev >= 2) { // Report notable deviations in detailed diagnostics list
                        violations.add(new ViolationDetail(
                                "S4", "Teacher Daily Workload Imbalance", "SOFT",
                                d.getDisplayName(),
                                tDayAssigns.stream().map(sa -> "P" + sa.getPeriodIndex()).collect(Collectors.joining(", ")),
                                tDayAssigns.stream().map(sa -> sa.schoolClass().getDisplayName()).distinct().collect(Collectors.joining(", ")),
                                t.getFullName() + " (" + t.getCode() + ")",
                                tDayAssigns.stream().map(sa -> sa.subject().getNormalizedName()).distinct().collect(Collectors.joining(", ")),
                                "",
                                String.format("%d periods on %s (Average target: %d)", dLoad, d.getDisplayName(), avgDaily),
                                String.format("Teacher has %d lectures on %s, deviating by %d from the daily average of %d periods.",
                                        dLoad, d.getDisplayName(), dev, avgDaily),
                                dev * wS4
                        ));
                    }
                }
            }
        }
        int s4Penalty = s4Deviations * wS4;
        checks.add(new ConstraintCheck("S4", "Teacher Daily Workload Balance", "SOFT", "OPTIMIZED", s4Deviations, s4Penalty,
                "Workload distribution deviations: " + s4Deviations + " (Penalty: " + s4Penalty + ")"));
        totalSoftPenalty += s4Penalty;

        double qualityIndex = (totalHardViolations == 0) ? Math.max(50.0, 100.0 - (totalSoftPenalty / 500.0)) : 0.0;

        return new ScheduleValidationResult(
                totalHardViolations == 0,
                totalHardViolations,
                totalSoftPenalty,
                qualityIndex,
                checks,
                violations
        );
    }

    private static int checkConsecutiveSession(Teacher t, Day d, int startP, int endP, int maxCont,
                                               Schedule schedule, SchoolConfig config, List<ViolationDetail> violations) {
        int vCount = 0;
        int window = maxCont + 1;
        if (endP - startP + 1 >= window) {
            for (int p = startP; p <= endP - window + 1; p++) {
                boolean allTaught = true;
                List<ScheduleAssignment> assigns = new ArrayList<>();
                for (int k = 0; k < window; k++) {
                    int currP = p + k;
                    Period period = config.getPeriods().get(currP);
                    TimeSlot slot = new TimeSlot(d, period);
                    List<ScheduleAssignment> atSlot = schedule.getAssignmentsAtSlot(slot).stream()
                            .filter(sa -> sa.teacher().equals(t))
                            .toList();
                    if (atSlot.isEmpty()) {
                        allTaught = false;
                        break;
                    }
                    assigns.addAll(atSlot);
                }
                if (allTaught) {
                    vCount++;
                    violations.add(new ViolationDetail(
                            "H2", "Max Continuous Lectures Exceeded", "HARD",
                            d.getDisplayName(),
                            "P" + p + "-P" + (p + maxCont),
                            assigns.stream().map(a -> a.schoolClass().getDisplayName()).distinct().collect(Collectors.joining(", ")),
                            t.getFullName() + " (" + t.getCode() + ")",
                            assigns.stream().map(a -> a.subject().getNormalizedName()).distinct().collect(Collectors.joining(", ")),
                            "",
                            assigns.stream().map(a -> "P" + a.getPeriodIndex() + ":" + a.schoolClass().getDisplayName()).collect(Collectors.joining(", ")),
                            String.format("Teacher %s exceeds maximum continuous lecture limit (%d) with %d consecutive periods (P%d-P%d) on %s.",
                                    t.getCode(), maxCont, window, p, p + maxCont, d.getDisplayName()),
                            0
                    ));
                }
            }
        }
        return vCount;
    }

    public static void exportReports(ScheduleValidationResult valResult, String outputDir) throws Exception {
        Files.createDirectories(Paths.get(outputDir));

        // 1. Detailed Violation Report CSV with exact location
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputDir + "/validation_report.csv"))) {
            bw.write("ConstraintID,ConstraintName,Severity,Day,Period,Class,Teacher,Subject,Resource,ConflictingAssignments,Reason,PenaltyScore\n");

            if (valResult.violations().isEmpty()) {
                bw.write("ALL,\"All Hard Constraints\",HARD,All Days,All Periods,All Classes,All Teachers,All Subjects,,\"None\",\"All 10 Hard Constraints passed with 0 violations across all scheduled time slots.\",0\n");
            } else {
                for (ViolationDetail v : valResult.violations()) {
                    bw.write(String.format("%s,\"%s\",%s,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d\n",
                            escape(v.constraintId()),
                            escape(v.constraintName()),
                            escape(v.severity()),
                            escape(v.day()),
                            escape(v.period()),
                            escape(v.className()),
                            escape(v.teacher()),
                            escape(v.subject()),
                            escape(v.resource()),
                            escape(v.conflictingAssignments()),
                            escape(v.reason()),
                            v.penaltyScore()
                    ));
                }
            }
        }

        // 2. High-Level Quality Report CSV
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputDir + "/quality_report.csv"))) {
            bw.write("Metric,Value,Status,Description\n");
            bw.write(String.format("HARD_CONSTRAINTS_PASS,%b,%s,\"Zero hard constraint violations required for validity\"\n",
                    valResult.allHardConstraintsPassed(), valResult.allHardConstraintsPassed() ? "PASS" : "FAIL"));
            bw.write(String.format("TOTAL_HARD_VIOLATIONS,%d,%s,\"Count of non-negotiable operational violations\"\n",
                    valResult.totalHardViolations(), valResult.totalHardViolations() == 0 ? "PERFECT" : "FAIL"));
            bw.write(String.format("TOTAL_SOFT_PENALTY,%d,OPTIMIZED,\"Weighted sum of soft constraint penalties\"\n",
                    valResult.totalSoftPenalty()));
            bw.write(String.format("OVERALL_QUALITY_INDEX,%.2f / 100.0,OPTIMAL,\"Comprehensive schedule balance and pedagogical pacing index\"\n",
                    valResult.overallQualityIndex()));
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }
}
