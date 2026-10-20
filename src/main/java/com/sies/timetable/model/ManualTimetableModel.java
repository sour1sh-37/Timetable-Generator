package com.sies.timetable.model;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.parser.*;
import com.sies.timetable.reconciliation.AllocationReconciler;
import com.sies.timetable.reconciliation.ReconciliationReport;

import java.io.File;
import java.util.*;

/**
 * Domain model representing user-entered manual configuration in the GUI.
 * Supports full CRUD (Add/Edit/Delete) on standards, classes, subjects, professors,
 * allocations, and timetable parameters.
 */
public class ManualTimetableModel {

    // 1. Academic Structure
    private int startStandard = 5;
    private int endStandard = 10;
    private final Map<Integer, Integer> standardDivisionsCount = new TreeMap<>();
    private final List<SchoolClass> generatedClasses = new ArrayList<>();

    // 2. Timetable Structure
    private final List<Day> days = new ArrayList<>(Arrays.asList(Day.values()));
    private final Map<Day, Integer> dailyPeriods = new HashMap<>();
    private int periodsPerDay = 8;
    private int weeklyRequiredPeriodsPerClass = 48;
    private int recessAfterPeriod = 4;
    private final Map<Day, Integer> dailyRecessOverrides = new HashMap<>();
    private final Map<Day, Boolean> dailyRecessEnabled = new HashMap<>();
    private final Map<Day, Integer> dailyRecessDurations = new HashMap<>();
    private String schoolStartTime = "07:10";
    private String saturdayStartTime = "07:30";
    private int defaultPeriodDuration = 35;
    private int passingTime = 0;
    private int recessDuration = 20;
    private boolean shortBreakEnabled = false;
    private int shortBreakAfterPeriod = 2;
    private int shortBreakDuration = 10;
    private final Map<Integer, Integer> periodDurationOverrides = new HashMap<>();
    private final Map<Integer, String> periodTimeOverrides = new HashMap<>();

    // 3. Subjects
    private final List<Subject> subjects = new ArrayList<>();

    // 4. Professors & Allocations
    private final List<ProfessorEntry> professors = new ArrayList<>();

    // 5. Constraints Configuration
    private final Map<String, Boolean> hardConstraintsEnabled = new LinkedHashMap<>();
    private final Map<String, Boolean> softConstraintsEnabled = new LinkedHashMap<>();
    private final Map<String, Integer> softConstraintWeights = new LinkedHashMap<>();
    private int h2MaxContinuousLectures = 2;
    private int h5MaxConcurrentLabClasses = 1;
    private int h6ProhibitedPeriod = 1;
    private int h8MaxActivityPeriodsPerDay = 2;

    public ManualTimetableModel() {
        initDefaultStructure();
    }

    private void initDefaultStructure() {
        startStandard = 5;
        endStandard = 10;
        standardDivisionsCount.clear();
        standardDivisionsCount.put(5, 5); // 5-A, 5-B, 5-C, 5-D, 5-E
        standardDivisionsCount.put(6, 4); // 6-A, 6-B, 6-C, 6-D
        standardDivisionsCount.put(7, 4); // 7-A, 7-B, 7-C, 7-D
        standardDivisionsCount.put(8, 5); // 8-A, 8-B, 8-C, 8-D, 8-E
        standardDivisionsCount.put(9, 4); // 9-A, 9-B, 9-C, 9-D
        standardDivisionsCount.put(10, 4); // 10-A, 10-B, 10-C, 10-D

        recomputeGeneratedClasses();

        this.periodsPerDay = 8;
        this.schoolStartTime = "07:10";
        this.saturdayStartTime = "07:30";
        this.defaultPeriodDuration = 35;
        this.passingTime = 0;
        this.recessDuration = 20;
        this.shortBreakEnabled = false;
        this.shortBreakAfterPeriod = 2;
        this.shortBreakDuration = 10;
        this.periodDurationOverrides.clear();
        this.periodDurationOverrides.put(1, 40);
        this.periodTimeOverrides.clear();

        for (Day d : Day.values()) {
            dailyPeriods.put(d, 8);
            dailyRecessOverrides.put(d, 4);
            dailyRecessEnabled.put(d, true);
            dailyRecessDurations.put(d, 20);
        }
        weeklyRequiredPeriodsPerClass = 48;
        recessAfterPeriod = 4;

        hardConstraintsEnabled.clear();
        for (int i = 1; i <= 10; i++) hardConstraintsEnabled.put("H" + i, true);
        softConstraintsEnabled.clear();
        for (int i = 1; i <= 6; i++) softConstraintsEnabled.put("S" + i, true);
        softConstraintWeights.clear();
        softConstraintWeights.put("S1", 100);
        softConstraintWeights.put("S2", 80);
        softConstraintWeights.put("S3", 50);
        softConstraintWeights.put("S4", 40);
        softConstraintWeights.put("S5", 30);
        softConstraintWeights.put("S6", 20);
        h2MaxContinuousLectures = 2;
        h5MaxConcurrentLabClasses = 1;
        h6ProhibitedPeriod = 1;
        h8MaxActivityPeriodsPerDay = 2;
    }

    // =========================================================================
    // Academic Structure Methods (CRUD)
    // =========================================================================

    public int getStartStandard() { return startStandard; }
    public int getEndStandard() { return endStandard; }

    public void setStandardRange(int from, int to) {
        if (from > to || from < 1) {
            throw new IllegalArgumentException("Invalid standard range: " + from + " to " + to);
        }
        this.startStandard = from;
        this.endStandard = to;

        // Remove standards outside range
        standardDivisionsCount.keySet().removeIf(std -> std < from || std > to);

        // Add defaults for new standards
        for (int s = from; s <= to; s++) {
            standardDivisionsCount.putIfAbsent(s, 2);
        }

        recomputeGeneratedClasses();
    }

    public void setStandardsRange(int from, int to) {
        setStandardRange(from, to);
    }

    public void setDivisionsCount(int standard, int count) {
        if (count < 1) count = 1;
        standardDivisionsCount.put(standard, count);
        recomputeGeneratedClasses();
    }

    public void setDivisionCount(int standard, int count) {
        setDivisionsCount(standard, count);
    }

    public int getDivisionsCount(int standard) {
        return standardDivisionsCount.getOrDefault(standard, 2);
    }

    public Map<Integer, Integer> getStandardDivisionsCount() {
        return Collections.unmodifiableMap(standardDivisionsCount);
    }

    public void recomputeGeneratedClasses() {
        generatedClasses.clear();
        for (int s = startStandard; s <= endStandard; s++) {
            int count = standardDivisionsCount.getOrDefault(s, 2);
            for (int i = 0; i < count; i++) {
                String div = String.valueOf((char) ('A' + i));
                generatedClasses.add(new SchoolClass(s, div));
            }
        }
    }

    public List<SchoolClass> getGeneratedClasses() {
        return Collections.unmodifiableList(generatedClasses);
    }

    public SchoolClass findClass(String canonicalOrDisplayName) {
        if (canonicalOrDisplayName == null) return null;
        for (SchoolClass sc : generatedClasses) {
            if (sc.getCanonicalName().equalsIgnoreCase(canonicalOrDisplayName) ||
                    sc.getDisplayName().equalsIgnoreCase(canonicalOrDisplayName) ||
                    sc.getStandardDivisionName().equalsIgnoreCase(canonicalOrDisplayName)) {
                return sc;
            }
        }
        return null;
    }

    public SchoolClass findClassByName(String canonicalOrDisplayName) {
        return findClass(canonicalOrDisplayName);
    }

    // =========================================================================
    // Timetable Structure Methods (CRUD)
    // =========================================================================

    public List<Day> getDays() { return Collections.unmodifiableList(days); }

    public void setDays(List<Day> newDays) {
        if (newDays != null && !newDays.isEmpty()) {
            this.days.clear();
            this.days.addAll(newDays);
        }
    }

    public int getPeriodsPerDay() {
        return periodsPerDay > 0 ? periodsPerDay : getDailyPeriod(Day.MONDAY);
    }

    public void setPeriodsPerDay(int periods) {
        if (periods < 1 || periods > 12) {
            throw new IllegalArgumentException("Teaching periods per day must be between 1 and 12 (received " + periods + ")");
        }
        this.periodsPerDay = periods;
        for (Day d : Day.values()) {
            dailyPeriods.put(d, periods);
        }
        if (this.recessAfterPeriod >= periods) {
            this.recessAfterPeriod = Math.max(1, periods - 1);
        }
    }

    public int getDailyPeriod(Day day) {
        return dailyPeriods.getOrDefault(day, periodsPerDay > 0 ? periodsPerDay : 8);
    }

    public void setDailyPeriod(Day day, int periods) {
        int safePeriods = Math.max(0, periods);
        dailyPeriods.put(day, safePeriods);
        this.periodsPerDay = dailyPeriods.values().stream().mapToInt(Integer::intValue).max().orElse(8);
        if (this.recessAfterPeriod >= safePeriods && safePeriods > 1) {
            this.recessAfterPeriod = safePeriods - 1;
        }
        int curOverride = dailyRecessOverrides.getOrDefault(day, recessAfterPeriod);
        if (safePeriods > 1 && curOverride >= safePeriods) {
            dailyRecessOverrides.put(day, safePeriods - 1);
        } else if (safePeriods <= 1) {
            dailyRecessEnabled.put(day, false);
        }
    }

    public void setDailyPeriods(Day day, int periods) {
        setDailyPeriod(day, periods);
    }

    public void setDailyPeriodsMap(Map<Day, Integer> map) {
        if (map != null) {
            map.forEach(this::setDailyPeriod);
        }
    }

    public Map<Day, Integer> getDailyPeriods() {
        return Collections.unmodifiableMap(dailyPeriods);
    }

    public int getTotalAvailablePeriods() {
        int sum = 0;
        for (Day d : days) {
            sum += dailyPeriods.getOrDefault(d, 8);
        }
        return sum;
    }

    public int getWeeklyRequiredPeriodsPerClass() {
        return weeklyRequiredPeriodsPerClass;
    }

    public void setWeeklyRequiredPeriodsPerClass(int req) {
        this.weeklyRequiredPeriodsPerClass = Math.max(0, req);
    }

    public int getRecessAfterPeriod() {
        return recessAfterPeriod;
    }

    public void setRecessAfterPeriod(int p) {
        this.recessAfterPeriod = Math.max(1, p);
    }

    public int getDailyRecessPosition(Day day) {
        int dp = dailyPeriods.getOrDefault(day, periodsPerDay);
        int rec = dailyRecessOverrides.getOrDefault(day, recessAfterPeriod);
        if (rec < 1 || (dp > 1 && rec >= dp)) {
            return Math.max(1, Math.min(recessAfterPeriod, dp > 1 ? dp - 1 : 1));
        }
        return rec;
    }

    public int getRecessAfterPeriod(Day day) {
        if (!isRecessEnabled(day)) {
            return 0; // 0 explicitly signifies NO RECESS for downstream solver & exporters
        }
        int rec = dailyRecessOverrides.getOrDefault(day, recessAfterPeriod);
        return (rec > 0) ? rec : getDailyRecessPosition(day);
    }

    public void setDailyRecessOverride(Day day, int p) {
        if (p > 0) {
            dailyRecessOverrides.put(day, p);
        }
    }

    public boolean isRecessEnabled(Day day) {
        if (dailyRecessEnabled.containsKey(day)) {
            return Boolean.TRUE.equals(dailyRecessEnabled.get(day));
        }
        int dp = dailyPeriods.getOrDefault(day, periodsPerDay);
        if (day == Day.SATURDAY && dp <= 4) {
            return false;
        }
        return dp > 1;
    }

    public void setDailyRecessEnabled(Day day, boolean enabled) {
        dailyRecessEnabled.put(day, enabled);
    }

    public Map<Day, Boolean> getDailyRecessEnabled() {
        return Collections.unmodifiableMap(dailyRecessEnabled);
    }

    public void setDailyRecessEnabledMap(Map<Day, Boolean> map) {
        this.dailyRecessEnabled.clear();
        if (map != null) this.dailyRecessEnabled.putAll(map);
    }

    public Map<Day, Integer> getDailyRecessOverrides() {
        return Collections.unmodifiableMap(dailyRecessOverrides);
    }

    public void setDailyRecessOverridesMap(Map<Day, Integer> map) {
        this.dailyRecessOverrides.clear();
        if (map != null) this.dailyRecessOverrides.putAll(map);
    }

    public int getDailyRecessDuration(Day day) {
        return dailyRecessDurations.getOrDefault(day, recessDuration > 0 ? recessDuration : 20);
    }

    public void setDailyRecessDuration(Day day, int duration) {
        dailyRecessDurations.put(day, Math.max(1, duration));
    }

    public Map<Day, Integer> getDailyRecessDurations() {
        return Collections.unmodifiableMap(dailyRecessDurations);
    }

    public void setDailyRecessDurationsMap(Map<Day, Integer> map) {
        this.dailyRecessDurations.clear();
        if (map != null) this.dailyRecessDurations.putAll(map);
    }

    public String getSchoolStartTime() { return schoolStartTime != null ? schoolStartTime : "07:10"; }
    public void setSchoolStartTime(String schoolStartTime) { this.schoolStartTime = schoolStartTime; }

    public String getSaturdayStartTime() { return saturdayStartTime != null ? saturdayStartTime : "07:30"; }
    public void setSaturdayStartTime(String saturdayStartTime) { this.saturdayStartTime = saturdayStartTime; }

    public int getDefaultPeriodDuration() { return defaultPeriodDuration > 0 ? defaultPeriodDuration : 35; }
    public void setDefaultPeriodDuration(int defaultPeriodDuration) { this.defaultPeriodDuration = Math.max(1, defaultPeriodDuration); }

    public int getPassingTime() { return passingTime; }
    public void setPassingTime(int passingTime) { this.passingTime = Math.max(0, passingTime); }

    public int getRecessDuration() { return recessDuration > 0 ? recessDuration : 20; }
    public void setRecessDuration(int recessDuration) {
        this.recessDuration = Math.max(1, recessDuration);
        for (Day d : getDays()) {
            dailyRecessDurations.put(d, this.recessDuration);
        }
    }

    public boolean isShortBreakEnabled() { return shortBreakEnabled; }
    public void setShortBreakEnabled(boolean shortBreakEnabled) { this.shortBreakEnabled = shortBreakEnabled; }

    public int getShortBreakAfterPeriod() { return shortBreakAfterPeriod; }
    public void setShortBreakAfterPeriod(int shortBreakAfterPeriod) { this.shortBreakAfterPeriod = Math.max(1, shortBreakAfterPeriod); }

    public int getShortBreakDuration() { return shortBreakDuration > 0 ? shortBreakDuration : 10; }
    public void setShortBreakDuration(int shortBreakDuration) { this.shortBreakDuration = Math.max(1, shortBreakDuration); }

    public Map<Integer, Integer> getPeriodDurationOverrides() {
        return Collections.unmodifiableMap(periodDurationOverrides);
    }

    public void setPeriodDurationOverride(int period, int duration) {
        periodDurationOverrides.put(period, Math.max(1, duration));
    }

    public void setPeriodDurationOverridesMap(Map<Integer, Integer> map) {
        this.periodDurationOverrides.clear();
        if (map != null) this.periodDurationOverrides.putAll(map);
    }

    public Map<Integer, String> getPeriodTimeOverrides() {
        return Collections.unmodifiableMap(periodTimeOverrides);
    }

    public void setPeriodTimeOverride(int period, String timing) {
        if (timing != null && !timing.isBlank()) {
            periodTimeOverrides.put(period, timing.trim());
        } else {
            periodTimeOverrides.remove(period);
        }
    }

    public void setPeriodTimeOverridesMap(Map<Integer, String> map) {
        this.periodTimeOverrides.clear();
        if (map != null) this.periodTimeOverrides.putAll(map);
    }

    // =========================================================================
    // Subjects Methods (CRUD)
    // =========================================================================

    public List<Subject> getSubjects() {
        return Collections.unmodifiableList(subjects);
    }

    public void addSubject(Subject s) {
        if (s != null && !subjects.contains(s)) {
            subjects.add(s);
        }
    }

    public void updateSubject(int index, Subject s) {
        if (index >= 0 && index < subjects.size()) {
            subjects.set(index, s);
        }
    }

    public void removeSubject(int index) {
        if (index >= 0 && index < subjects.size()) {
            Subject removed = subjects.remove(index);
            // Remove allocations using this subject
            for (ProfessorEntry p : professors) {
                p.getAllocations().removeIf(a -> a.getSubject().equals(removed));
            }
        }
    }

    public void clearSubjects() {
        subjects.clear();
    }

    public Subject findSubject(String normalizedName) {
        if (normalizedName == null) return null;
        for (Subject s : subjects) {
            if (s.getNormalizedName().equalsIgnoreCase(normalizedName.trim())) {
                return s;
            }
        }
        return null;
    }

    public void updateSubject(String currentName, Subject newSubject) {
        Subject s = findSubject(currentName);
        if (s != null) {
            int idx = subjects.indexOf(s);
            if (idx >= 0) {
                subjects.set(idx, newSubject);
                for (ProfessorEntry p : professors) {
                    for (AllocationEntry a : p.getAllocations()) {
                        if (a.getSubject().equals(s)) {
                            a.setSubject(newSubject);
                        }
                    }
                }
            }
        }
    }

    public void removeSubject(String name) {
        Subject s = findSubject(name);
        if (s != null) {
            int idx = subjects.indexOf(s);
            if (idx >= 0) removeSubject(idx);
        }
    }

    // =========================================================================
    // Professors & Allocations Methods (CRUD)
    // =========================================================================

    public List<ProfessorEntry> getProfessors() {
        return Collections.unmodifiableList(professors);
    }

    public void addProfessor(ProfessorEntry p) {
        if (p != null) {
            professors.add(p);
        }
    }

    public void updateProfessor(int index, ProfessorEntry p) {
        if (index >= 0 && index < professors.size()) {
            professors.set(index, p);
        }
    }

    public void removeProfessor(int index) {
        if (index >= 0 && index < professors.size()) {
            professors.remove(index);
        }
    }

    public ProfessorEntry findProfessorByCode(String code) {
        if (code == null) return null;
        for (ProfessorEntry p : professors) {
            if (p.getCode().equalsIgnoreCase(code.trim())) {
                return p;
            }
        }
        return null;
    }

    public ProfessorEntry findProfessor(String code) {
        return findProfessorByCode(code);
    }

    public void removeProfessor(String code) {
        ProfessorEntry p = findProfessorByCode(code);
        if (p != null) {
            professors.remove(p);
        }
    }

    public void clearProfessors() {
        professors.clear();
    }

    // =========================================================================
    // Real-Time Mathematical Validation
    // =========================================================================

    public record ValidationReport(
            boolean isValid,
            List<String> errors,
            List<String> warnings,
            List<String> notices,
            int totalAvailablePeriods,
            int weeklyRequiredPeriods,
            int totalClasses,
            int totalProfessors,
            int totalSubjects
    ) {}

    public ValidationReport validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notices = new ArrayList<>();

        int totalAvailable = getTotalAvailablePeriods();
        int weeklyRequired = getWeeklyRequiredPeriodsPerClass();

        // 1. Academic Structure Checks
        if (generatedClasses.isEmpty()) {
            errors.add("No classes defined. Please configure at least one standard and division.");
        }

        // 1b. Bell Schedule and Recess Integrity
        for (Day d : days) {
            int dp = dailyPeriods.getOrDefault(d, 0);
            if (dp < 1 || dp > 12) {
                errors.add(String.format("Invalid teaching periods count for %s: %d periods (must be between 1 and 12).",
                        d.getDisplayName(), dp));
            }
            if (isRecessEnabled(d)) {
                int rec = dailyRecessOverrides.getOrDefault(d, recessAfterPeriod);
                if (dp <= 1) {
                    errors.add(String.format("Invalid recess placement for %s: Day only has %d period, so recess cannot be scheduled. Please disable recess for %s.",
                            d.getDisplayName(), dp, d.getDisplayName()));
                } else if (rec < 1 || rec >= dp) {
                    errors.add(String.format("Invalid recess placement for %s: After period %d (daily periods: %d; recess must be between 1 and %d).",
                            d.getDisplayName(), rec, dp, dp - 1));
                }
            }
        }

        // 2. Capacity Check: Available >= Required
        if (totalAvailable < weeklyRequired) {
            errors.add(String.format("INVALID TIMETABLE CAPACITY: Required %d periods/week per class, but only %d periods configured (%s). Deficit: %d periods.",
                    weeklyRequired, totalAvailable, formatDailySummary(), (weeklyRequired - totalAvailable)));
        } else if (totalAvailable > weeklyRequired) {
            notices.add(String.format("Available capacity (%d periods/week) exceeds requirement (%d periods/week). %d periods per class will be free/spare slots.",
                    totalAvailable, weeklyRequired, (totalAvailable - weeklyRequired)));
        } else {
            notices.add(String.format("Exact capacity match: %d available periods == %d required periods per class.",
                    totalAvailable, weeklyRequired));
        }

        // 3. Class Subject Requirements Check
        Map<SchoolClass, Integer> classAssignedPeriods = new HashMap<>();
        for (SchoolClass sc : generatedClasses) {
            classAssignedPeriods.put(sc, 0);
        }

        for (ProfessorEntry p : professors) {
            for (AllocationEntry a : p.getAllocations()) {
                for (SchoolClass sc : a.getTargetClasses()) {
                    if (classAssignedPeriods.containsKey(sc)) {
                        classAssignedPeriods.put(sc, classAssignedPeriods.get(sc) + a.getWeeklyLectures());
                    }
                }
            }
        }

        int classesWithPeriodMismatch = 0;
        for (SchoolClass sc : generatedClasses) {
            int assigned = classAssignedPeriods.getOrDefault(sc, 0);
            if (assigned != weeklyRequired) {
                classesWithPeriodMismatch++;
                errors.add(String.format("Class %s: Total subject periods assigned (%d) does not equal required (%d).",
                        sc.getStandardDivisionName(), assigned, weeklyRequired));
            }
        }

        // 4. Professor Workload Checks
        for (ProfessorEntry p : professors) {
            int workload = p.calculateTotalWorkload();
            if (workload > totalAvailable) {
                errors.add(String.format("Professor %s (%s): Total workload (%d periods) exceeds maximum weekly capacity (%d periods).",
                        p.getName(), p.getCode(), workload, totalAvailable));
            } else if (workload >= 33) {
                warnings.add(String.format("Professor %s (%s): Heavy teaching load (%d periods/week, ~%.1f per day).",
                        p.getName(), p.getCode(), workload, (double) workload / Math.max(1, days.size())));
            }
        }

        // 5. Duplicate Class Teachers Check
        Map<SchoolClass, String> assignedClassTeachers = new HashMap<>();
        for (ProfessorEntry p : professors) {
            if (p.getClassTeacherOf() != null) {
                SchoolClass sc = p.getClassTeacherOf();
                if (assignedClassTeachers.containsKey(sc)) {
                    errors.add(String.format("Multiple class teachers assigned to %s: %s and %s.",
                            sc.getStandardDivisionName(), assignedClassTeachers.get(sc), p.getName()));
                } else {
                    assignedClassTeachers.put(sc, p.getName());
                }
            }
        }

        boolean isValid = errors.isEmpty();
        return new ValidationReport(
                isValid,
                errors,
                warnings,
                notices,
                totalAvailable,
                weeklyRequired,
                generatedClasses.size(),
                professors.size(),
                subjects.size()
        );
    }

    private String formatDailySummary() {
        StringBuilder sb = new StringBuilder();
        for (Day d : days) {
            if (!sb.isEmpty()) sb.append(" + ");
            sb.append(dailyPeriods.getOrDefault(d, 8));
        }
        return sb.toString();
    }

    // =========================================================================
    // Conversion to ReconciliationResult and SchoolConfig
    // =========================================================================

    public List<String> validateAll() {
        return validate().errors();
    }

    public SchoolConfig getStructureConfig() {
        return buildSchoolConfig();
    }

    public AllocationReconciler.ReconciliationResult toReconciliationResult() {
        return toReconciliationResult(buildSchoolConfig());
    }

    public AllocationReconciler.ReconciliationResult toReconciliationResult(SchoolConfig config) {
        Map<String, SchoolClass> classMap = new TreeMap<>();
        for (SchoolClass sc : generatedClasses) {
            classMap.put(sc.getCanonicalName(), sc);
            classMap.put(sc.getStandardDivisionName(), sc);
        }

        Map<String, Subject> subjectMap = new TreeMap<>();
        for (Subject s : subjects) {
            subjectMap.put(s.getNormalizedName(), s);
        }

        Map<String, Teacher> teacherMap = new TreeMap<>();
        List<Teacher> teacherList = new ArrayList<>();
        for (ProfessorEntry p : professors) {
            int workload = p.calculateTotalWorkload();
            Teacher t = new Teacher(p.getCode(), p.getName(), workload, p.getAllocations().size(), p.isFreeTeacher());
            teacherMap.put(t.getCode(), t);
            teacherList.add(t);
        }

        List<TeachingAllocation> allocations = new ArrayList<>();
        int allocId = 0;
        for (ProfessorEntry p : professors) {
            Teacher t = teacherMap.get(p.getCode());
            for (AllocationEntry a : p.getAllocations()) {
                Subject s = a.getSubject();
                for (SchoolClass sc : a.getTargetClasses()) {
                    allocations.add(new TeachingAllocation(
                            allocId++,
                            sc,
                            t,
                            s,
                            a.getWeeklyLectures(),
                            1
                    ));
                }
            }
        }

        Map<SchoolClass, ClassTeacherMapping> ctMap = new HashMap<>();
        for (ProfessorEntry p : professors) {
            if (p.getClassTeacherOf() != null) {
                Teacher t = teacherMap.get(p.getCode());
                ctMap.put(p.getClassTeacherOf(), new ClassTeacherMapping(p.getClassTeacherOf(), t, Collections.emptyList()));
            }
        }

        ReconciliationReport report = new ReconciliationReport();
        return new AllocationReconciler.ReconciliationResult(
                new ArrayList<>(generatedClasses),
                classMap,
                teacherList,
                teacherMap,
                new ArrayList<>(subjects),
                subjectMap,
                allocations,
                ctMap,
                report
        );
    }

    public SchoolConfig buildSchoolConfig() {
        SchoolConfig cfg = SchoolConfig.createDefault();
        cfg.setDays(new ArrayList<>(days));
        cfg.setPeriodsPerDay(getPeriodsPerDay());
        cfg.setDailyPeriods(new HashMap<>(dailyPeriods));
        cfg.setWeeklyRequiredPeriodsPerClass(weeklyRequiredPeriodsPerClass);
        cfg.setRecessAfterPeriod(recessAfterPeriod);
        for (Day d : days) {
            cfg.setDailyRecessAfterPeriod(d, getDailyRecessPosition(d));
            cfg.setDailyRecessEnabled(d, isRecessEnabled(d));
            cfg.setDailyRecessDuration(d, getDailyRecessDuration(d));
        }
        cfg.setSchoolStartTime(schoolStartTime);
        cfg.setSaturdayStartTime(saturdayStartTime);
        cfg.setDefaultPeriodDurationMinutes(defaultPeriodDuration);
        cfg.setPassingTimeMinutes(passingTime);
        cfg.setRecessDurationMinutes(recessDuration);
        cfg.setShortBreakEnabled(shortBreakEnabled);
        cfg.setShortBreakAfterPeriod(shortBreakAfterPeriod);
        cfg.setShortBreakDurationMinutes(shortBreakDuration);
        cfg.setPeriodDurationOverrides(new HashMap<>(periodDurationOverrides));
        cfg.setPeriodTimeOverrides(new HashMap<>(periodTimeOverrides));

        for (Map.Entry<String, Boolean> e : hardConstraintsEnabled.entrySet()) {
            try {
                cfg.setHardConstraintEnabled(e.getKey(), e.getValue());
            } catch (Exception ignored) {}
        }
        for (Map.Entry<String, Boolean> e : softConstraintsEnabled.entrySet()) {
            cfg.setSoftConstraintEnabled(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : softConstraintWeights.entrySet()) {
            try {
                cfg.setSoftConstraintWeight(e.getKey(), e.getValue());
            } catch (Exception ignored) {}
        }
        cfg.setH2MaxContinuousLectures(h2MaxContinuousLectures);
        cfg.setH5MaxConcurrentLabClasses(h5MaxConcurrentLabClasses);
        cfg.setH6ProhibitedPeriod(h6ProhibitedPeriod);
        cfg.setH8MaxActivityPeriodsPerDay(h8MaxActivityPeriodsPerDay);

        for (Subject s : subjects) {
            if (s.isRequiresLab()) {
                cfg.getLabResources().put(s.getNormalizedName(), "COMPUTER_LAB");
            }
        }
        return cfg;
    }

    @SuppressWarnings("unchecked")
    public boolean persistScheduleToConfigFile(File cfgFile) {
        if (cfgFile == null) return false;
        try {
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Map<String, Object> data = null;
            if (cfgFile.exists()) {
                try (java.io.InputStream in = new java.io.FileInputStream(cfgFile)) {
                    data = yaml.load(in);
                } catch (Exception ignored) {}
            }
            if (data == null) {
                data = new LinkedHashMap<>();
            }

            Map<String, Object> scheduleMap = (Map<String, Object>) data.computeIfAbsent("schedule", k -> new LinkedHashMap<>());
            scheduleMap.put("days", days.stream().map(Enum::name).toList());
            scheduleMap.put("periods_per_day", getPeriodsPerDay());
            scheduleMap.put("recess_after_period", recessAfterPeriod);
            scheduleMap.put("weekly_required_periods_per_class", weeklyRequiredPeriodsPerClass);

            Map<String, Integer> dpMap = new LinkedHashMap<>();
            for (Day d : days) {
                dpMap.put(d.name(), getDailyPeriod(d));
            }
            scheduleMap.put("daily_periods", dpMap);

            Map<String, Boolean> dreMap = new LinkedHashMap<>();
            for (Day d : days) {
                dreMap.put(d.name(), isRecessEnabled(d));
            }
            scheduleMap.put("daily_recess_enabled", dreMap);

            Map<String, Integer> drapMap = new LinkedHashMap<>();
            for (Day d : days) {
                drapMap.put(d.name(), getDailyRecessPosition(d));
            }
            scheduleMap.put("daily_recess_after_period", drapMap);

            Map<String, Integer> drdMap = new LinkedHashMap<>();
            for (Day d : days) {
                drdMap.put(d.name(), getDailyRecessDuration(d));
            }
            scheduleMap.put("daily_recess_durations", drdMap);

            Map<String, Object> tcMap = new LinkedHashMap<>();
            tcMap.put("start_time", schoolStartTime);
            tcMap.put("saturday_start_time", saturdayStartTime);
            tcMap.put("default_period_duration", defaultPeriodDuration);
            tcMap.put("passing_time", passingTime);
            tcMap.put("recess_duration", recessDuration);
            tcMap.put("short_break_enabled", shortBreakEnabled);
            tcMap.put("short_break_after_period", shortBreakAfterPeriod);
            tcMap.put("short_break_duration", shortBreakDuration);

            Map<Integer, Integer> pdoMap = new LinkedHashMap<>(periodDurationOverrides);
            tcMap.put("period_duration_overrides", pdoMap);
            Map<Integer, String> ptoMap = new LinkedHashMap<>(periodTimeOverrides);
            tcMap.put("period_time_overrides", ptoMap);
            scheduleMap.put("timing_config", tcMap);

            // Compute dynamic period timings for Monday
            SchoolConfig tmpCfg = buildSchoolConfig();
            Map<Object, String> periodTimings = new LinkedHashMap<>(tmpCfg.computePeriodTimings(Day.MONDAY));
            String recTime = tmpCfg.getRecessTiming(Day.MONDAY);
            if (!recTime.isBlank()) {
                periodTimings.put("recess", recTime);
            }
            scheduleMap.put("period_timings", periodTimings);

            // Persist structured constraints
            data.put("constraints", tmpCfg.toConstraintsMap());

            File parent = cfgFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (java.io.Writer writer = new java.io.FileWriter(cfgFile)) {
                yaml.dump(data, writer);
            }
            return true;
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(ManualTimetableModel.class.getName())
                    .log(java.util.logging.Level.WARNING, "Failed to persist schedule to " + cfgFile, e);
            return false;
        }
    }

    // =========================================================================
    // Preset Loader: 2026-27 School Data
    // =========================================================================

    public boolean loadDefaultSchoolPreset() {
        try {
            File cfgFile = new File("config/school-config.yaml");
            SchoolConfig config = cfgFile.exists() ? SchoolConfig.load(cfgFile.getAbsolutePath()) : SchoolConfig.createDefault();

            File stdFile = new File(config.getStandardwiseFile() != null ? config.getStandardwiseFile() : "sample-data/sample-standardwise-allotment.xlsx");
            if (!stdFile.exists()) stdFile = new File("sample-data/sample-standardwise-allotment.xlsx");
            if (!stdFile.exists()) stdFile = new File("Standardwise allotment  26-27 (4).xlsx");

            File tchFile = new File(config.getTeacherwiseFile() != null ? config.getTeacherwiseFile() : "sample-data/sample-teacherwise-allotment.xlsx");
            if (!tchFile.exists()) tchFile = new File("sample-data/sample-teacherwise-allotment.xlsx");
            if (!tchFile.exists()) tchFile = new File("TEACHERWISE ALLOTMENT 26-27 (4).xlsx");

            File ctFile = new File(config.getClassTeachersFile() != null ? config.getClassTeachersFile() : "sample-data/sample-classteacher-list.xlsx");
            if (!ctFile.exists()) ctFile = new File("sample-data/sample-classteacher-list.xlsx");
            if (!ctFile.exists()) ctFile = new File("Classteachers and free teachers list 2026-27 (1).xlsx");

            if (!stdFile.exists() || !tchFile.exists() || !ctFile.exists()) {
                return false;
            }

            List<StandardwiseParser.StandardwiseRecord> stdRecords = StandardwiseParser.parse(stdFile.getAbsolutePath());
            List<TeacherwiseParser.ParsedTeacherBlock> teacherBlocks = TeacherwiseParser.parse(tchFile.getAbsolutePath());
            List<ClassTeacherListParser.ParsedClassTeacherRow> ctRows = ClassTeacherListParser.parse(ctFile.getAbsolutePath());

            AllocationReconciler.ReconciliationResult rec = AllocationReconciler.reconcile(stdRecords, teacherBlocks, ctRows, config);

            // 1. Academic structure
            this.startStandard = 5;
            this.endStandard = 10;
            this.standardDivisionsCount.clear();
            this.standardDivisionsCount.put(5, 5);
            this.standardDivisionsCount.put(6, 4);
            this.standardDivisionsCount.put(7, 4);
            this.standardDivisionsCount.put(8, 5);
            this.standardDivisionsCount.put(9, 4);
            this.standardDivisionsCount.put(10, 4);
            recomputeGeneratedClasses();

            // 2. Schedule
            int ppd = config.getPeriodsPerDay() > 0 ? config.getPeriodsPerDay() : 8;
            this.periodsPerDay = ppd;
            this.recessAfterPeriod = config.getRecessAfterPeriod() > 0 ? config.getRecessAfterPeriod() : 4;
            if (config.getDays() != null && !config.getDays().isEmpty()) {
                this.days.clear();
                this.days.addAll(config.getDays());
            }
            for (Day d : Day.values()) {
                int dp = config.getDailyPeriods(d);
                this.dailyPeriods.put(d, dp > 0 ? dp : ppd);
                int recPos = config.getRawRecessAfterPeriod(d);
                this.dailyRecessOverrides.put(d, recPos > 0 ? recPos : Math.max(1, Math.min(recessAfterPeriod, dp > 1 ? dp - 1 : 1)));
                this.dailyRecessEnabled.put(d, config.isRecessEnabled(d));
                this.dailyRecessDurations.put(d, config.getRecessDuration(d));
            }
            this.schoolStartTime = config.getSchoolStartTime();
            this.saturdayStartTime = config.getSaturdayStartTime();
            this.defaultPeriodDuration = config.getDefaultPeriodDurationMinutes();
            this.passingTime = config.getPassingTimeMinutes();
            this.recessDuration = config.getRecessDurationMinutes();
            this.shortBreakEnabled = config.isShortBreakEnabled();
            this.shortBreakAfterPeriod = config.getShortBreakAfterPeriod();
            this.shortBreakDuration = config.getShortBreakDurationMinutes();
            this.periodDurationOverrides.clear();
            this.periodDurationOverrides.putAll(config.getPeriodDurationOverrides());
            this.periodTimeOverrides.clear();
            this.periodTimeOverrides.putAll(config.getPeriodTimeOverrides());

            this.weeklyRequiredPeriodsPerClass = config.getWeeklyRequiredPeriodsPerClass() > 0 ?
                    config.getWeeklyRequiredPeriodsPerClass() : (ppd * this.days.size());

            // Constraints configuration
            this.hardConstraintsEnabled.clear();
            this.hardConstraintsEnabled.putAll(config.getHardConstraintsEnabled());
            this.softConstraintsEnabled.clear();
            this.softConstraintsEnabled.putAll(config.getSoftConstraintsEnabled());
            this.softConstraintWeights.clear();
            this.softConstraintWeights.putAll(config.getSoftConstraintWeights());
            this.h2MaxContinuousLectures = config.getH2MaxContinuousLectures();
            this.h5MaxConcurrentLabClasses = config.getH5MaxConcurrentLabClasses();
            this.h6ProhibitedPeriod = config.getH6ProhibitedPeriod();
            this.h8MaxActivityPeriodsPerDay = config.getH8MaxActivityPeriodsPerDay();

            // 3. Subjects
            this.subjects.clear();
            this.subjects.addAll(rec.subjects());

            // 4. Class Teacher Map
            Map<Teacher, SchoolClass> teacherCtMap = new HashMap<>();
            for (Map.Entry<SchoolClass, ClassTeacherMapping> e : rec.classTeacherMappings().entrySet()) {
                if (e.getValue() != null && e.getValue().getClassTeacher() != null) {
                    teacherCtMap.put(e.getValue().getClassTeacher(), e.getKey());
                }
            }

            // 5. Group allocations by teacher and subject
            this.professors.clear();
            for (Teacher t : rec.teachers()) {
                ProfessorEntry prof = new ProfessorEntry(t.getFullName(), t.getCode(), t.isFreeTeacher());
                if (teacherCtMap.containsKey(t)) {
                    prof.setClassTeacherOf(teacherCtMap.get(t));
                }

                // Group allocations for this teacher by subject & period count
                Map<String, List<SchoolClass>> subjClassMap = new LinkedHashMap<>();
                Map<String, Integer> subjPeriodsMap = new LinkedHashMap<>();

                for (TeachingAllocation a : rec.allocations()) {
                    if (a.getTeacher().equals(t)) {
                        String key = a.getSubject().getNormalizedName() + "###" + a.getPeriodCount();
                        subjClassMap.computeIfAbsent(key, k -> new ArrayList<>()).add(a.getSchoolClass());
                        subjPeriodsMap.put(key, a.getPeriodCount());
                    }
                }

                for (Map.Entry<String, List<SchoolClass>> entry : subjClassMap.entrySet()) {
                    String[] parts = entry.getKey().split("###");
                    String sName = parts[0];
                    int pCount = Integer.parseInt(parts[1]);
                    Subject subj = findSubject(sName);
                    if (subj == null) {
                        subj = new Subject(sName, sName, config.categorizeSubject(sName), config.isLabRequired(sName));
                        this.subjects.add(subj);
                    }
                    prof.addAllocation(new AllocationEntry(subj, entry.getValue(), pCount));
                }

                this.professors.add(prof);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // Constraints Configuration Getters & Setters
    // =========================================================================

    public Map<String, Boolean> getHardConstraintsEnabled() {
        Map<String, Boolean> res = new LinkedHashMap<>();
        for (int i = 1; i <= 10; i++) {
            String id = "H" + i;
            res.put(id, isHardConstraintEnabled(id));
        }
        return res;
    }

    public boolean isHardConstraintEnabled(String id) {
        if (SchoolConfig.isHardConstraintMandatory(id)) return true;
        return hardConstraintsEnabled.getOrDefault(id.toUpperCase().trim(), true);
    }

    public void setHardConstraintEnabled(String id, boolean enabled) {
        String u = id.trim().toUpperCase();
        if (!enabled && SchoolConfig.isHardConstraintMandatory(u)) {
            throw new IllegalArgumentException("Constraint " + u + " is mandatory for timetable integrity and cannot be disabled.");
        }
        hardConstraintsEnabled.put(u, enabled);
    }

    public Map<String, Boolean> getSoftConstraintsEnabled() {
        Map<String, Boolean> res = new LinkedHashMap<>();
        for (int i = 1; i <= 6; i++) {
            String id = "S" + i;
            res.put(id, isSoftConstraintEnabled(id));
        }
        return res;
    }

    public boolean isSoftConstraintEnabled(String id) {
        return softConstraintsEnabled.getOrDefault(id.toUpperCase().trim(), true);
    }

    public void setSoftConstraintEnabled(String id, boolean enabled) {
        softConstraintsEnabled.put(id.toUpperCase().trim(), enabled);
    }

    public Map<String, Integer> getSoftConstraintWeights() {
        return new LinkedHashMap<>(softConstraintWeights);
    }

    public int getSoftConstraintWeight(String id, int defaultVal) {
        return softConstraintWeights.getOrDefault(id.toUpperCase().trim(), defaultVal);
    }

    public void setSoftConstraintWeight(String id, int weight) {
        if (weight < 0 || weight > 500) {
            throw new IllegalArgumentException("Soft constraint weight must be between 0 and 500.");
        }
        softConstraintWeights.put(id.toUpperCase().trim(), weight);
    }

    public int getH2MaxContinuousLectures() {
        return h2MaxContinuousLectures > 0 ? h2MaxContinuousLectures : 2;
    }

    public void setH2MaxContinuousLectures(int val) {
        if (val < 1 || val > 8) {
            throw new IllegalArgumentException("H2 max continuous lectures must be between 1 and 8 periods.");
        }
        this.h2MaxContinuousLectures = val;
    }

    public int getH5MaxConcurrentLabClasses() {
        return h5MaxConcurrentLabClasses > 0 ? h5MaxConcurrentLabClasses : 1;
    }

    public void setH5MaxConcurrentLabClasses(int val) {
        if (val < 1 || val > 10) {
            throw new IllegalArgumentException("H5 max concurrent lab classes must be between 1 and 10.");
        }
        this.h5MaxConcurrentLabClasses = val;
    }

    public int getH6ProhibitedPeriod() {
        return h6ProhibitedPeriod > 0 ? h6ProhibitedPeriod : 1;
    }

    public void setH6ProhibitedPeriod(int val) {
        if (val < 1 || val > 12) {
            throw new IllegalArgumentException("H6 prohibited period must be between 1 and 12.");
        }
        this.h6ProhibitedPeriod = val;
    }

    public int getH8MaxActivityPeriodsPerDay() {
        return h8MaxActivityPeriodsPerDay > 0 ? h8MaxActivityPeriodsPerDay : 2;
    }

    public void setH8MaxActivityPeriodsPerDay(int val) {
        if (val < 1 || val > 8) {
            throw new IllegalArgumentException("H8 max activity periods per day must be between 1 and 8.");
        }
        this.h8MaxActivityPeriodsPerDay = val;
    }

    public void clearAll() {
        initDefaultStructure();
        subjects.clear();
        professors.clear();
    }

    // =========================================================================
    // Inner Data Classes
    // =========================================================================

    public static class ProfessorEntry {
        private String name;
        private String code;
        private boolean freeTeacher;
        private SchoolClass classTeacherOf;
        private final List<AllocationEntry> allocations = new ArrayList<>();

        public ProfessorEntry(String name, String code) {
            this(name, code, false);
        }

        public ProfessorEntry(String name, String code, boolean freeTeacher) {
            this.name = name != null ? name.trim() : "";
            this.code = code != null ? code.trim().toUpperCase() : "";
            this.freeTeacher = freeTeacher;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code.trim().toUpperCase(); }

        public boolean isFreeTeacher() { return freeTeacher; }
        public void setFreeTeacher(boolean freeTeacher) { this.freeTeacher = freeTeacher; }

        public SchoolClass getClassTeacherOf() { return classTeacherOf; }
        public void setClassTeacherOf(SchoolClass classTeacherOf) { this.classTeacherOf = classTeacherOf; }

        public List<AllocationEntry> getAllocations() { return allocations; }

        public void addAllocation(AllocationEntry a) {
            if (a != null) allocations.add(a);
        }

        public void removeAllocation(int index) {
            if (index >= 0 && index < allocations.size()) {
                allocations.remove(index);
            }
        }

        public int calculateTotalWorkload() {
            int sum = 0;
            for (AllocationEntry a : allocations) {
                sum += (a.getTargetClasses().size() * a.getWeeklyLectures());
            }
            return sum;
        }

        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }

    public static class AllocationEntry {
        private Subject subject;
        private final List<SchoolClass> targetClasses = new ArrayList<>();
        private int weeklyLectures;

        public AllocationEntry(Subject subject, List<SchoolClass> targetClasses, int weeklyLectures) {
            this.subject = Objects.requireNonNull(subject);
            if (targetClasses != null) this.targetClasses.addAll(targetClasses);
            this.weeklyLectures = weeklyLectures;
        }

        public Subject getSubject() { return subject; }
        public void setSubject(Subject subject) { this.subject = subject; }

        public List<SchoolClass> getTargetClasses() { return targetClasses; }
        public void setTargetClasses(List<SchoolClass> classes) {
            this.targetClasses.clear();
            if (classes != null) this.targetClasses.addAll(classes);
        }

        public int getWeeklyLectures() { return weeklyLectures; }
        public void setWeeklyLectures(int weeklyLectures) { this.weeklyLectures = weeklyLectures; }

        public int getTotalWorkload() {
            return targetClasses.size() * weeklyLectures;
        }
    }
}
