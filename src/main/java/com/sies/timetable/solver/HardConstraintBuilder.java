package com.sies.timetable.solver;

import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.AllocationReconciler;

import java.util.*;

/**
 * Builds and enforces Hard Constraints (H1 through H10) on the CP-SAT model.
 */
public class HardConstraintBuilder {

    public static void applyHardConstraints(
            CpModel model,
            IntVar[][][][] x, // [classIdx][dayIdx][periodIdx][allocIdx]
            AllocationReconciler.ReconciliationResult recResult,
            SchoolConfig config
    ) {
        int numClasses = recResult.classes().size();
        int numDays = config.getDays().size();

        // Map allocations per class
        Map<Integer, List<TeachingAllocation>> classAllocMap = new HashMap<>();
        for (TeachingAllocation a : recResult.allocations()) {
            int cIdx = recResult.classes().indexOf(a.getSchoolClass());
            classAllocMap.computeIfAbsent(cIdx, k -> new ArrayList<>()).add(a);
        }

        // -------------------------------------------------------------
        // H3: Class Slot Capacity & Spare Period Handling
        // -------------------------------------------------------------
        for (int c = 0; c < numClasses; c++) {
            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
            int totalRequiredForClass = allocs.stream().mapToInt(TeachingAllocation::getPeriodCount).sum();
            int totalAvailableForClass = config.getTotalWeeklyPeriods();

            for (int d = 0; d < numDays; d++) {
                int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                for (int p = 0; p < numPeriodsForDay; p++) {
                    LinearExprBuilder slotSum = LinearExpr.newBuilder();
                    for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                        slotSum.add(x[c][d][p][aIdx]);
                    }
                    if (totalRequiredForClass >= totalAvailableForClass) {
                        model.addEquality(slotSum.build(), 1);
                    } else {
                        model.addLessOrEqual(slotSum.build(), 1);
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // H9: Exact Granular Allotment Fulfillment (Each atomic req = K)
        // -------------------------------------------------------------
        for (int c = 0; c < numClasses; c++) {
            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
            for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                TeachingAllocation a = allocs.get(aIdx);
                LinearExprBuilder allocSum = LinearExpr.newBuilder();
                for (int d = 0; d < numDays; d++) {
                    int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                    for (int p = 0; p < numPeriodsForDay; p++) {
                        allocSum.add(x[c][d][p][aIdx]);
                    }
                }
                model.addEquality(allocSum.build(), a.getPeriodCount());
            }
        }

        // -------------------------------------------------------------
        // H4 & H2: Single Class Per Teacher Slot + Continuous Lecture Limits
        // -------------------------------------------------------------
        int numTeachers = recResult.teachers().size();
        IntVar[][][] isTeaching = new IntVar[numTeachers][numDays][];

        for (int tIdx = 0; tIdx < numTeachers; tIdx++) {
            Teacher teacher = recResult.teachers().get(tIdx);
            for (int d = 0; d < numDays; d++) {
                int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                isTeaching[tIdx][d] = new IntVar[numPeriodsForDay];
                for (int p = 0; p < numPeriodsForDay; p++) {
                    isTeaching[tIdx][d][p] = model.newBoolVar("w_" + teacher.getCode() + "_d" + d + "_p" + p);
                    LinearExprBuilder teacherSum = LinearExpr.newBuilder();
                    for (int c = 0; c < numClasses; c++) {
                        List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
                        for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                            if (allocs.get(aIdx).getTeacher().equals(teacher)) {
                                teacherSum.add(x[c][d][p][aIdx]);
                            }
                        }
                    }
                    // Enforce H4: teacher is in at most 1 class, and link to boolean isTeaching variable
                    model.addEquality(isTeaching[tIdx][d][p], teacherSum.build());
                }
            }
        }

        // -------------------------------------------------------------
        // H2: Maximum Continuous Lectures Per Teacher
        // Enforce within each continuous session (partitioned by recess)
        // -------------------------------------------------------------
        for (int tIdx = 0; tIdx < numTeachers; tIdx++) {
            Teacher teacher = recResult.teachers().get(tIdx);
            int maxCont = (teacher.getTotalWorkload() <= 30) ? 2 : 3;

            for (int d = 0; d < numDays; d++) {
                int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                int recessP = config.getRecessAfterPeriod(config.getDays().get(d));

                // Session 1: 0 .. min(recessP, numPeriodsForDay) - 1
                int s1End = Math.min(recessP, numPeriodsForDay);
                applyConsecutiveLimits(model, isTeaching[tIdx][d], 0, s1End, maxCont);

                // Session 2: recessP .. numPeriodsForDay - 1
                if (recessP < numPeriodsForDay) {
                    applyConsecutiveLimits(model, isTeaching[tIdx][d], recessP, numPeriodsForDay, maxCont);
                }
            }
        }

        // -------------------------------------------------------------
        // H1: Class Teacher First Period Allocation
        // -------------------------------------------------------------
        for (int c = 0; c < numClasses; c++) {
            SchoolClass sc = recResult.classes().get(c);
            ClassTeacherMapping ctm = recResult.classTeacherMappings().get(sc);
            if (ctm != null && ctm.getClassTeacher() != null) {
                Teacher ct = ctm.getClassTeacher();
                List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());

                List<Integer> ctAllocIndices = new ArrayList<>();
                int totalCtPeriodsInClass = 0;
                for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                    if (allocs.get(aIdx).getTeacher().equals(ct)) {
                        ctAllocIndices.add(aIdx);
                        totalCtPeriodsInClass += allocs.get(aIdx).getPeriodCount();
                    }
                }

                if (!ctAllocIndices.isEmpty()) {
                    int activeDaysWithP1 = 0;
                    LinearExprBuilder p1Sum = LinearExpr.newBuilder();
                    for (int d = 0; d < numDays; d++) {
                        int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                        if (numPeriodsForDay > 0) {
                            activeDaysWithP1++;
                            LinearExprBuilder dayP1Sum = LinearExpr.newBuilder();
                            for (int aIdx : ctAllocIndices) {
                                p1Sum.add(x[c][d][0][aIdx]);
                                dayP1Sum.add(x[c][d][0][aIdx]);
                            }
                            // At most 1 session of class teacher in Period 1 on any day d
                            model.addLessOrEqual(dayP1Sum.build(), 1);
                        }
                    }
                    int targetPeriod1Slots = Math.min(activeDaysWithP1, totalCtPeriodsInClass);
                    // Enforce class teacher fills min(activeDays, allocated periods) Period 1 slots
                    model.addEquality(p1Sum.build(), targetPeriod1Slots);
                }
            }
        }

        // -------------------------------------------------------------
        // H5: Computer Lab Exclusivity (No Simultaneous Computer Classes)
        // -------------------------------------------------------------
        for (int d = 0; d < numDays; d++) {
            int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
            for (int p = 0; p < numPeriodsForDay; p++) {
                LinearExprBuilder labSum = LinearExpr.newBuilder();
                for (int c = 0; c < numClasses; c++) {
                    List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
                    for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                        if (allocs.get(aIdx).isComputer()) {
                            labSum.add(x[c][d][p][aIdx]);
                        }
                    }
                }
                model.addLessOrEqual(labSum.build(), 1);
            }
        }

        // -------------------------------------------------------------
        // H6: Physical Education Prohibited in Period 1 (Index 0)
        // -------------------------------------------------------------
        for (int c = 0; c < numClasses; c++) {
            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
            for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                if (allocs.get(aIdx).isPhysicalEducation()) {
                    for (int d = 0; d < numDays; d++) {
                        if (config.getDailyPeriods(config.getDays().get(d)) > 0) {
                            model.addEquality(x[c][d][0][aIdx], 0);
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // H7: Physical Education Prohibited Immediately After Recess
        // -------------------------------------------------------------
        for (int c = 0; c < numClasses; c++) {
            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
            for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                if (allocs.get(aIdx).isPhysicalEducation()) {
                    for (int d = 0; d < numDays; d++) {
                        int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                        int recessP = config.getRecessAfterPeriod(config.getDays().get(d));
                        if (recessP < numPeriodsForDay) {
                            model.addEquality(x[c][d][recessP][aIdx], 0);
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // H8: Maximum Two Activity Periods Per Class Per Day
        // -------------------------------------------------------------
        for (int c = 0; c < numClasses; c++) {
            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
            for (int d = 0; d < numDays; d++) {
                int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                LinearExprBuilder actSum = LinearExpr.newBuilder();
                for (int p = 0; p < numPeriodsForDay; p++) {
                    for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                        if (allocs.get(aIdx).isActivity()) {
                            actSum.add(x[c][d][p][aIdx]);
                        }
                    }
                }
                model.addLessOrEqual(actSum.build(), 2);
            }
        }

        // -------------------------------------------------------------
        // H10: P.E. & Computer Same-Day Partition Around Recess
        // -------------------------------------------------------------
        for (int c = 0; c < numClasses; c++) {
            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
            List<Integer> peIndices = new ArrayList<>();
            List<Integer> compIndices = new ArrayList<>();

            for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                if (allocs.get(aIdx).isPhysicalEducation()) peIndices.add(aIdx);
                if (allocs.get(aIdx).isComputer()) compIndices.add(aIdx);
            }

            if (!peIndices.isEmpty() && !compIndices.isEmpty()) {
                for (int d = 0; d < numDays; d++) {
                    int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                    int recessP = config.getRecessAfterPeriod(config.getDays().get(d));

                    if (recessP < numPeriodsForDay) {
                        LinearExprBuilder preSum = LinearExpr.newBuilder();
                        LinearExprBuilder postSum = LinearExpr.newBuilder();

                        for (int p = 0; p < recessP; p++) {
                            for (int aIdx : peIndices) preSum.add(x[c][d][p][aIdx]);
                            for (int aIdx : compIndices) preSum.add(x[c][d][p][aIdx]);
                        }

                        for (int p = recessP; p < numPeriodsForDay; p++) {
                            for (int aIdx : peIndices) postSum.add(x[c][d][p][aIdx]);
                            for (int aIdx : compIndices) postSum.add(x[c][d][p][aIdx]);
                        }

                        model.addLessOrEqual(preSum.build(), 1);
                        model.addLessOrEqual(postSum.build(), 1);
                    }
                }
            }
        }
    }

    private static void applyConsecutiveLimits(CpModel model, IntVar[] dayTeaching, int startIdx, int endIdx, int maxCont) {
        int length = endIdx - startIdx;
        int window = maxCont + 1;
        if (length >= window) {
            for (int s = startIdx; s <= endIdx - window; s++) {
                LinearExprBuilder windowSum = LinearExpr.newBuilder();
                for (int k = 0; k < window; k++) {
                    windowSum.add(dayTeaching[s + k]);
                }
                model.addLessOrEqual(windowSum.build(), maxCont);
            }
        }
    }
}
