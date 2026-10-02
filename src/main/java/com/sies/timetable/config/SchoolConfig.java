package com.sies.timetable.config;

import com.sies.timetable.model.*;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Loads and encapsulates school configuration from YAML.
 */
public class SchoolConfig {
    private String schoolName;
    private String academicYear;
    private List<Day> days;
    private int periodsPerDay;
    private int recessAfterPeriod;
    private Map<Day, Integer> dailyPeriods;
    private Map<Day, Integer> dailyRecessAfterPeriod;
    private int weeklyRequiredPeriodsPerClass = 48;
    private Map<Integer, Period> periods;
    private Map<Object, String> periodTimingsMap;
    private Set<String> coreAcademicSubjects;
    private Set<String> activitySubjects;
    private Set<String> specialCurricularSubjects;
    private Map<String, String> labResources;
    private int timeLimitSeconds;
    private int numSearchWorkers;
    private int randomSeed;
    private boolean logSearchProgress;
    private Map<String, Integer> softConstraintWeights;
    private String standardwiseFile;
    private String teacherwiseFile;
    private String classTeachersFile;
    private String constraintsFile;
    private String outputDir;

    @SuppressWarnings("unchecked")
    public static SchoolConfig load(String configPath) throws Exception {
        SchoolConfig cfg = new SchoolConfig();
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(configPath)) {
            data = yaml.load(in);
        }

        // School
        Map<String, Object> schoolMap = (Map<String, Object>) data.get("school");
        if (schoolMap != null) {
            cfg.schoolName = (String) schoolMap.getOrDefault("name", "SIES High School");
            cfg.academicYear = (String) schoolMap.getOrDefault("academic_year", "2026-27");
        }

        // Schedule
        Map<String, Object> scheduleMap = (Map<String, Object>) data.get("schedule");
        if (scheduleMap != null) {
            List<String> dayStrs = (List<String>) scheduleMap.get("days");
            cfg.days = new ArrayList<>();
            if (dayStrs != null) {
                for (String ds : dayStrs) {
                    cfg.days.add(Day.valueOf(ds.trim().toUpperCase()));
                }
            } else {
                cfg.days = Arrays.asList(Day.values());
            }

            cfg.periodsPerDay = (int) scheduleMap.getOrDefault("periods_per_day", 8);
            cfg.recessAfterPeriod = (int) scheduleMap.getOrDefault("recess_after_period", 4);
            cfg.dailyPeriods = new HashMap<>();
            cfg.dailyRecessAfterPeriod = new HashMap<>();
            for (Day d : cfg.days) {
                cfg.dailyPeriods.put(d, cfg.periodsPerDay);
                cfg.dailyRecessAfterPeriod.put(d, cfg.recessAfterPeriod);
            }
            cfg.weeklyRequiredPeriodsPerClass = cfg.getTotalWeeklyPeriods();

            Map<Object, String> timings = (Map<Object, String>) scheduleMap.get("period_timings");
            cfg.periodTimingsMap = timings;
            cfg.periods = new TreeMap<>();
            for (int p = 1; p <= cfg.periodsPerDay; p++) {
                String timing = (timings != null && timings.containsKey(p)) ? timings.get(p) : "Period " + p;
                cfg.periods.put(p, new Period(p, timing, cfg.recessAfterPeriod));
            }
        }

        // Subjects taxonomy
        Map<String, Object> subjMap = (Map<String, Object>) data.get("subjects");
        cfg.coreAcademicSubjects = new HashSet<>();
        cfg.activitySubjects = new HashSet<>();
        cfg.specialCurricularSubjects = new HashSet<>();
        cfg.labResources = new HashMap<>();

        if (subjMap != null) {
            List<String> coreList = (List<String>) subjMap.get("core_academic");
            if (coreList != null) for (String s : coreList) cfg.coreAcademicSubjects.add(s.toUpperCase().trim());

            List<String> actList = (List<String>) subjMap.get("activity");
            if (actList != null) for (String s : actList) cfg.activitySubjects.add(s.toUpperCase().trim());

            List<String> specList = (List<String>) subjMap.get("special_curricular");
            if (specList != null) for (String s : specList) cfg.specialCurricularSubjects.add(s.toUpperCase().trim());

            Map<String, String> labs = (Map<String, String>) subjMap.get("lab_resources");
            if (labs != null) {
                for (Map.Entry<String, String> e : labs.entrySet()) {
                    cfg.labResources.put(e.getKey().toUpperCase().trim(), e.getValue());
                }
            }
        }

        // Solver
        Map<String, Object> solverMap = (Map<String, Object>) data.get("solver");
        if (solverMap != null) {
            cfg.timeLimitSeconds = (int) solverMap.getOrDefault("time_limit_seconds", 60);
            cfg.numSearchWorkers = (int) solverMap.getOrDefault("num_search_workers", 8);
            cfg.randomSeed = (int) solverMap.getOrDefault("random_seed", 42);
            cfg.logSearchProgress = (boolean) solverMap.getOrDefault("log_search_progress", false);
        }

        // Constraints weights
        Map<String, Object> constraintsMap = (Map<String, Object>) data.get("constraints");
        cfg.softConstraintWeights = new HashMap<>();
        if (constraintsMap != null) {
            Map<String, Object> weightsMap = (Map<String, Object>) constraintsMap.get("weights");
            if (weightsMap != null) {
                for (Map.Entry<String, Object> e : weightsMap.entrySet()) {
                    cfg.softConstraintWeights.put(e.getKey(), ((Number) e.getValue()).intValue());
                }
            }
        }

        // Paths
        Map<String, Object> pathsMap = (Map<String, Object>) data.get("paths");
        if (pathsMap != null) {
            cfg.standardwiseFile = (String) pathsMap.getOrDefault("standardwise_file", "Standardwise allotment  26-27 (4).xlsx");
            cfg.teacherwiseFile = (String) pathsMap.getOrDefault("teacherwise_file", "TEACHERWISE ALLOTMENT 26-27 (4).xlsx");
            cfg.classTeachersFile = (String) pathsMap.getOrDefault("class_teachers_file", "Classteachers and free teachers list 2026-27 (1).xlsx");
            cfg.constraintsFile = (String) pathsMap.getOrDefault("constraints_file", "constraints.csv");
            cfg.outputDir = (String) pathsMap.getOrDefault("output_dir", "output");
        }

        return cfg;
    }

    public SubjectCategory categorizeSubject(String subjectName) {
        String s = subjectName.toUpperCase().trim();
        if (coreAcademicSubjects.contains(s)) return SubjectCategory.CORE_ACADEMIC;
        if (activitySubjects.contains(s)) return SubjectCategory.ACTIVITY;
        if (specialCurricularSubjects.contains(s)) return SubjectCategory.SPECIAL_CURRICULAR;
        if (labResources.containsKey(s)) return SubjectCategory.LAB;
        return SubjectCategory.CORE_ACADEMIC;
    }

    public boolean isLabRequired(String subjectName) {
        return labResources.containsKey(subjectName.toUpperCase().trim());
    }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public List<Day> getDays() { return days != null ? days : Arrays.asList(Day.values()); }
    public void setDays(List<Day> days) { this.days = new ArrayList<>(days); }

    public int getPeriodsPerDay() { return periodsPerDay; }
    public void setPeriodsPerDay(int periodsPerDay) { this.periodsPerDay = periodsPerDay; }

    public int getRecessAfterPeriod() { return recessAfterPeriod; }
    public void setRecessAfterPeriod(int recessAfterPeriod) { this.recessAfterPeriod = recessAfterPeriod; }

    public int getRecessAfterPeriod(Day day) {
        if (dailyRecessAfterPeriod != null && dailyRecessAfterPeriod.containsKey(day)) {
            return dailyRecessAfterPeriod.get(day);
        }
        return recessAfterPeriod;
    }

    public void setDailyRecessAfterPeriod(Day day, int period) {
        if (this.dailyRecessAfterPeriod == null) this.dailyRecessAfterPeriod = new HashMap<>();
        this.dailyRecessAfterPeriod.put(day, period);
    }

    public Map<Day, Integer> getDailyPeriods() {
        if (dailyPeriods == null) {
            dailyPeriods = new HashMap<>();
            for (Day d : getDays()) {
                dailyPeriods.put(d, periodsPerDay > 0 ? periodsPerDay : 8);
            }
        }
        return dailyPeriods;
    }

    public int getDailyPeriods(Day day) {
        if (dailyPeriods != null && dailyPeriods.containsKey(day)) {
            return dailyPeriods.get(day);
        }
        return periodsPerDay > 0 ? periodsPerDay : 8;
    }

    public void setDailyPeriods(Map<Day, Integer> dailyPeriods) {
        this.dailyPeriods = new HashMap<>(dailyPeriods);
    }

    public void setDailyPeriod(Day day, int count) {
        if (this.dailyPeriods == null) this.dailyPeriods = new HashMap<>();
        this.dailyPeriods.put(day, count);
    }

    public int getTotalWeeklyPeriods() {
        int sum = 0;
        for (Day d : getDays()) {
            sum += getDailyPeriods(d);
        }
        return sum;
    }

    public int getMaxDailyPeriods() {
        int max = 0;
        for (Day d : getDays()) {
            max = Math.max(max, getDailyPeriods(d));
        }
        return max > 0 ? max : (periodsPerDay > 0 ? periodsPerDay : 8);
    }

    public int getWeeklyRequiredPeriodsPerClass() {
        return weeklyRequiredPeriodsPerClass;
    }

    public void setWeeklyRequiredPeriodsPerClass(int weeklyRequiredPeriodsPerClass) {
        this.weeklyRequiredPeriodsPerClass = weeklyRequiredPeriodsPerClass;
    }

    public Map<Integer, Period> getPeriods() {
        ensurePeriodsCapacity(getMaxDailyPeriods());
        return periods;
    }

    public void ensurePeriodsCapacity(int maxPeriods) {
        if (periods == null) periods = new TreeMap<>();
        for (int p = 1; p <= maxPeriods; p++) {
            if (!periods.containsKey(p)) {
                String timing = (periodTimingsMap != null && periodTimingsMap.containsKey(p)) ?
                        periodTimingsMap.get(p) : "Period " + p;
                periods.put(p, new Period(p, timing, recessAfterPeriod));
            }
        }
    }

    public int getTimeLimitSeconds() { return timeLimitSeconds > 0 ? timeLimitSeconds : 60; }
    public int getNumSearchWorkers() { return numSearchWorkers > 0 ? numSearchWorkers : 8; }
    public int getRandomSeed() { return randomSeed; }
    public boolean isLogSearchProgress() { return logSearchProgress; }
    public Map<String, Integer> getSoftConstraintWeights() { return softConstraintWeights; }
    public String getStandardwiseFile() { return standardwiseFile; }
    public String getTeacherwiseFile() { return teacherwiseFile; }
    public String getClassTeachersFile() { return classTeachersFile; }
    public String getConstraintsFile() { return constraintsFile; }
    public String getOutputDir() { return outputDir != null ? outputDir : "output"; }

    public int getWeight(String constraintId, int defaultVal) {
        if (softConstraintWeights == null) return defaultVal;
        return softConstraintWeights.getOrDefault(constraintId, defaultVal);
    }

    public void setTimeLimitSeconds(int timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }
    public void setRandomSeed(int randomSeed) { this.randomSeed = randomSeed; }
    public void setStandardwiseFile(String standardwiseFile) { this.standardwiseFile = standardwiseFile; }
    public void setTeacherwiseFile(String teacherwiseFile) { this.teacherwiseFile = teacherwiseFile; }
    public void setClassTeachersFile(String classTeachersFile) { this.classTeachersFile = classTeachersFile; }
    public void setConstraintsFile(String constraintsFile) { this.constraintsFile = constraintsFile; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    public Set<String> getCoreAcademicSubjects() { return coreAcademicSubjects; }
    public Set<String> getActivitySubjects() { return activitySubjects; }
    public Set<String> getSpecialCurricularSubjects() { return specialCurricularSubjects; }
    public Map<String, String> getLabResources() { return labResources; }

    public static SchoolConfig createDefault() {
        SchoolConfig cfg = new SchoolConfig();
        cfg.schoolName = "SIES (Dr APJ Abdul Kalam Memorial) High School";
        cfg.academicYear = "2026-27";
        cfg.days = new ArrayList<>(Arrays.asList(Day.values()));
        cfg.periodsPerDay = 8;
        cfg.recessAfterPeriod = 4;
        cfg.dailyPeriods = new HashMap<>();
        cfg.dailyRecessAfterPeriod = new HashMap<>();
        for (Day d : cfg.days) {
            cfg.dailyPeriods.put(d, 8);
            cfg.dailyRecessAfterPeriod.put(d, 4);
        }
        cfg.weeklyRequiredPeriodsPerClass = 48;
        cfg.timeLimitSeconds = 60;
        cfg.numSearchWorkers = 8;
        cfg.randomSeed = 42;
        cfg.outputDir = "output";

        cfg.coreAcademicSubjects = new HashSet<>(Arrays.asList(
                "ENGLISH", "MARATHI", "HINDI", "SANSKRIT", "MATHS", "ALGEBRA",
                "GEOMETRY", "SCIENCE", "SCIENCE I", "SCIENCE II", "EVS I", "EVS II",
                "HISTORY", "GEOGRAPHY"
        ));
        cfg.activitySubjects = new HashSet<>(Arrays.asList(
                "P.E", "P.E.", "COMPUTER", "ART", "CRAFT", "MUSIC", "LIBRARY",
                "SCOUT & GUIDE", "SELF DEFENCE", "CCA"
        ));
        cfg.specialCurricularSubjects = new HashSet<>(Arrays.asList(
                "VALUE EDUCATION", "WATER SECURITY"
        ));
        cfg.labResources = new HashMap<>();
        cfg.labResources.put("COMPUTER", "COMPUTER_LAB");

        cfg.softConstraintWeights = new HashMap<>();
        cfg.softConstraintWeights.put("S1", 100);
        cfg.softConstraintWeights.put("S2", 80);
        cfg.softConstraintWeights.put("S3", 50);
        cfg.softConstraintWeights.put("S4", 40);
        cfg.softConstraintWeights.put("S5", 30);
        cfg.softConstraintWeights.put("S6", 20);

        cfg.periodTimingsMap = new HashMap<>();
        cfg.periodTimingsMap.put(1, "07:10 - 07:50");
        cfg.periodTimingsMap.put(2, "07:50 - 08:25");
        cfg.periodTimingsMap.put(3, "08:25 - 09:00");
        cfg.periodTimingsMap.put(4, "09:00 - 09:30");
        cfg.periodTimingsMap.put(5, "09:50 - 10:25");
        cfg.periodTimingsMap.put(6, "10:25 - 11:00");
        cfg.periodTimingsMap.put(7, "11:00 - 11:35");
        cfg.periodTimingsMap.put(8, "11:35 - 12:10");

        cfg.ensurePeriodsCapacity(8);
        return cfg;
    }
}
