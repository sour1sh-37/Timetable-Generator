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
    private int weeklyRequiredPeriodsPerClass = 48;
    private int recessAfterPeriod = 4;
    private final Map<Day, Integer> dailyRecessOverrides = new HashMap<>();

    // 3. Subjects
    private final List<Subject> subjects = new ArrayList<>();

    // 4. Professors & Allocations
    private final List<ProfessorEntry> professors = new ArrayList<>();

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

        for (Day d : Day.values()) {
            dailyPeriods.put(d, 8);
            dailyRecessOverrides.put(d, 4);
        }
        weeklyRequiredPeriodsPerClass = 48;
        recessAfterPeriod = 4;
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

    public int getDailyPeriod(Day day) {
        return dailyPeriods.getOrDefault(day, 8);
    }

    public void setDailyPeriod(Day day, int periods) {
        dailyPeriods.put(day, Math.max(0, periods));
    }

    public void setDailyPeriods(Day day, int periods) {
        setDailyPeriod(day, periods);
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

    public int getRecessAfterPeriod(Day day) {
        return dailyRecessOverrides.getOrDefault(day, recessAfterPeriod);
    }

    public void setDailyRecessOverride(Day day, int p) {
        dailyRecessOverrides.put(day, p);
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
        cfg.setDailyPeriods(new HashMap<>(dailyPeriods));
        cfg.setWeeklyRequiredPeriodsPerClass(weeklyRequiredPeriodsPerClass);
        cfg.setRecessAfterPeriod(recessAfterPeriod);
        for (Map.Entry<Day, Integer> e : dailyRecessOverrides.entrySet()) {
            cfg.setDailyRecessAfterPeriod(e.getKey(), e.getValue());
        }

        for (Subject s : subjects) {
            if (s.isRequiresLab()) {
                cfg.getLabResources().put(s.getNormalizedName(), "COMPUTER_LAB");
            }
        }
        return cfg;
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
            for (Day d : Day.values()) {
                this.dailyPeriods.put(d, 8);
                this.dailyRecessOverrides.put(d, 4);
            }
            this.weeklyRequiredPeriodsPerClass = 48;
            this.recessAfterPeriod = 4;

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
