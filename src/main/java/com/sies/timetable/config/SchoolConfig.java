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
    private Map<Day, Boolean> dailyRecessEnabled = new HashMap<>();
    private Map<Day, Integer> dailyRecessDurations = new HashMap<>();
    private String schoolStartTime = "07:10";
    private String saturdayStartTime = "07:30";
    private int defaultPeriodDurationMinutes = 35;
    private int passingTimeMinutes = 0;
    private int recessDurationMinutes = 20;
    private boolean shortBreakEnabled = false;
    private int shortBreakAfterPeriod = 2;
    private int shortBreakDurationMinutes = 10;
    private Map<Integer, Integer> periodDurationOverrides = new HashMap<>();
    private Map<Integer, String> periodTimeOverrides = new HashMap<>();
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
    private Map<String, Boolean> hardConstraintsEnabled = new LinkedHashMap<>();
    private Map<String, Boolean> softConstraintsEnabled = new LinkedHashMap<>();
    private int h2MaxContinuousLectures = 2;
    private int h5MaxConcurrentLabClasses = 1;
    private int h6ProhibitedPeriod = 1;
    private int h8MaxActivityPeriodsPerDay = 2;
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

            if (scheduleMap.containsKey("daily_periods") && scheduleMap.get("daily_periods") instanceof Map<?, ?> dpMap) {
                for (Map.Entry<?, ?> e : dpMap.entrySet()) {
                    try {
                        Day d = Day.valueOf(e.getKey().toString().trim().toUpperCase());
                        int p = ((Number) e.getValue()).intValue();
                        cfg.dailyPeriods.put(d, p);
                    } catch (Exception ignored) {}
                }
            }

            if (scheduleMap.containsKey("weekly_required_periods_per_class")) {
                cfg.weeklyRequiredPeriodsPerClass = ((Number) scheduleMap.get("weekly_required_periods_per_class")).intValue();
            } else {
                cfg.weeklyRequiredPeriodsPerClass = cfg.getTotalWeeklyPeriods();
            }

            if (scheduleMap.containsKey("daily_recess_enabled") && scheduleMap.get("daily_recess_enabled") instanceof Map<?, ?> dreMap) {
                for (Map.Entry<?, ?> e : dreMap.entrySet()) {
                    try {
                        Day d = Day.valueOf(e.getKey().toString().trim().toUpperCase());
                        cfg.dailyRecessEnabled.put(d, Boolean.parseBoolean(e.getValue().toString()));
                    } catch (Exception ignored) {}
                }
            }

            if (scheduleMap.containsKey("daily_recess_after_period") && scheduleMap.get("daily_recess_after_period") instanceof Map<?, ?> drapMap) {
                for (Map.Entry<?, ?> e : drapMap.entrySet()) {
                    try {
                        Day d = Day.valueOf(e.getKey().toString().trim().toUpperCase());
                        cfg.dailyRecessAfterPeriod.put(d, ((Number) e.getValue()).intValue());
                    } catch (Exception ignored) {}
                }
            }

            if (scheduleMap.containsKey("daily_recess_durations") && scheduleMap.get("daily_recess_durations") instanceof Map<?, ?> drdMap) {
                for (Map.Entry<?, ?> e : drdMap.entrySet()) {
                    try {
                        Day d = Day.valueOf(e.getKey().toString().trim().toUpperCase());
                        cfg.dailyRecessDurations.put(d, ((Number) e.getValue()).intValue());
                    } catch (Exception ignored) {}
                }
            }

            if (scheduleMap.containsKey("timing_config") && scheduleMap.get("timing_config") instanceof Map<?, ?> tcMap) {
                if (tcMap.containsKey("start_time")) cfg.schoolStartTime = tcMap.get("start_time").toString();
                if (tcMap.containsKey("saturday_start_time")) cfg.saturdayStartTime = tcMap.get("saturday_start_time").toString();
                if (tcMap.containsKey("default_period_duration")) cfg.defaultPeriodDurationMinutes = ((Number) tcMap.get("default_period_duration")).intValue();
                if (tcMap.containsKey("passing_time")) cfg.passingTimeMinutes = ((Number) tcMap.get("passing_time")).intValue();
                if (tcMap.containsKey("recess_duration")) cfg.recessDurationMinutes = ((Number) tcMap.get("recess_duration")).intValue();
                if (tcMap.containsKey("short_break_enabled")) cfg.shortBreakEnabled = Boolean.parseBoolean(tcMap.get("short_break_enabled").toString());
                if (tcMap.containsKey("short_break_after_period")) cfg.shortBreakAfterPeriod = ((Number) tcMap.get("short_break_after_period")).intValue();
                if (tcMap.containsKey("short_break_duration")) cfg.shortBreakDurationMinutes = ((Number) tcMap.get("short_break_duration")).intValue();

                if (tcMap.containsKey("period_duration_overrides") && tcMap.get("period_duration_overrides") instanceof Map<?, ?> pdoMap) {
                    for (Map.Entry<?, ?> e : pdoMap.entrySet()) {
                        try {
                            cfg.periodDurationOverrides.put(Integer.parseInt(e.getKey().toString()), ((Number) e.getValue()).intValue());
                        } catch (Exception ignored) {}
                    }
                }
                if (tcMap.containsKey("period_time_overrides") && tcMap.get("period_time_overrides") instanceof Map<?, ?> ptoMap) {
                    for (Map.Entry<?, ?> e : ptoMap.entrySet()) {
                        try {
                            cfg.periodTimeOverrides.put(Integer.parseInt(e.getKey().toString()), e.getValue().toString());
                        } catch (Exception ignored) {}
                    }
                }
            }

            cfg.periodsPerDay = cfg.getMaxDailyPeriods();
            Map<Object, String> timings = (Map<Object, String>) scheduleMap.get("period_timings");
            cfg.periodTimingsMap = timings;
            cfg.periods = new TreeMap<>();
            Map<Integer, String> computedTimings = cfg.computePeriodTimings(Day.MONDAY);
            for (int p = 1; p <= cfg.periodsPerDay; p++) {
                String timing = (timings != null && timings.containsKey(p)) ? timings.get(p) : computedTimings.getOrDefault(p, "Period " + p);
                cfg.periods.put(p, new Period(p, timing, cfg.getRecessAfterPeriod(Day.MONDAY)));
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

        // Constraints & Optimization Objectives
        Map<String, Object> constraintsMap = (Map<String, Object>) data.get("constraints");
        cfg.softConstraintWeights = new LinkedHashMap<>();
        cfg.hardConstraintsEnabled = new LinkedHashMap<>();
        cfg.softConstraintsEnabled = new LinkedHashMap<>();

        // Institutional Defaults
        for (int i = 1; i <= 10; i++) cfg.hardConstraintsEnabled.put("H" + i, true);
        for (int i = 1; i <= 6; i++) cfg.softConstraintsEnabled.put("S" + i, true);
        cfg.softConstraintWeights.put("S1", 100);
        cfg.softConstraintWeights.put("S2", 80);
        cfg.softConstraintWeights.put("S3", 50);
        cfg.softConstraintWeights.put("S4", 40);
        cfg.softConstraintWeights.put("S5", 30);
        cfg.softConstraintWeights.put("S6", 20);

        if (constraintsMap != null) {
            // Backward-compatible weights
            if (constraintsMap.get("weights") instanceof Map<?, ?> weightsMap) {
                for (Map.Entry<?, ?> e : weightsMap.entrySet()) {
                    try {
                        cfg.softConstraintWeights.put(e.getKey().toString().trim().toUpperCase(), ((Number) e.getValue()).intValue());
                    } catch (Exception ignored) {}
                }
            }

            // Hard constraints configuration
            if (constraintsMap.get("hard_constraints") instanceof Map<?, ?> hcMap) {
                for (Map.Entry<?, ?> e : hcMap.entrySet()) {
                    String id = e.getKey().toString().trim().toUpperCase();
                    if (e.getValue() instanceof Map<?, ?> entryMap) {
                        if (entryMap.containsKey("enabled")) {
                            boolean en = Boolean.parseBoolean(entryMap.get("enabled").toString());
                            if (!en && isHardConstraintMandatory(id)) {
                                en = true; // mandatory rules stay true
                            }
                            cfg.hardConstraintsEnabled.put(id, en);
                        }
                        if ("H2".equalsIgnoreCase(id) && entryMap.containsKey("max_continuous_lectures")) {
                            cfg.setH2MaxContinuousLectures(((Number) entryMap.get("max_continuous_lectures")).intValue());
                        }
                        if ("H5".equalsIgnoreCase(id) && entryMap.containsKey("max_concurrent_lab_classes")) {
                            cfg.setH5MaxConcurrentLabClasses(((Number) entryMap.get("max_concurrent_lab_classes")).intValue());
                        }
                        if ("H6".equalsIgnoreCase(id) && entryMap.containsKey("prohibited_period")) {
                            cfg.setH6ProhibitedPeriod(((Number) entryMap.get("prohibited_period")).intValue());
                        }
                        if ("H8".equalsIgnoreCase(id) && entryMap.containsKey("max_activity_periods_per_day")) {
                            cfg.setH8MaxActivityPeriodsPerDay(((Number) entryMap.get("max_activity_periods_per_day")).intValue());
                        }
                    } else if (e.getValue() instanceof Boolean b) {
                        if (!b && isHardConstraintMandatory(id)) b = true;
                        cfg.hardConstraintsEnabled.put(id, b);
                    }
                }
            }

            // Soft constraints configuration
            if (constraintsMap.get("soft_constraints") instanceof Map<?, ?> scMap) {
                for (Map.Entry<?, ?> e : scMap.entrySet()) {
                    String id = e.getKey().toString().trim().toUpperCase();
                    if (e.getValue() instanceof Map<?, ?> entryMap) {
                        if (entryMap.containsKey("enabled")) {
                            cfg.softConstraintsEnabled.put(id, Boolean.parseBoolean(entryMap.get("enabled").toString()));
                        }
                        if (entryMap.containsKey("weight")) {
                            cfg.softConstraintWeights.put(id, ((Number) entryMap.get("weight")).intValue());
                        }
                    } else if (e.getValue() instanceof Boolean b) {
                        cfg.softConstraintsEnabled.put(id, b);
                    }
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

    public int getPeriodsPerDay() {
        return periodsPerDay > 0 ? periodsPerDay : getMaxDailyPeriods();
    }

    public void setPeriodsPerDay(int periodsPerDay) {
        this.periodsPerDay = periodsPerDay;
        if (this.dailyPeriods == null) {
            this.dailyPeriods = new HashMap<>();
        }
        for (Day d : getDays()) {
            this.dailyPeriods.put(d, periodsPerDay);
        }
        ensurePeriodsCapacity(periodsPerDay);
    }

    public int getRecessAfterPeriod() { return recessAfterPeriod; }
    public void setRecessAfterPeriod(int recessAfterPeriod) { this.recessAfterPeriod = recessAfterPeriod; }

    public boolean isRecessEnabled(Day day) {
        if (dailyRecessEnabled != null && dailyRecessEnabled.containsKey(day)) {
            Boolean enabled = dailyRecessEnabled.get(day);
            if (Boolean.FALSE.equals(enabled)) return false;
            if (Boolean.TRUE.equals(enabled)) {
                int rec = getRawRecessAfterPeriod(day);
                return rec > 0 && rec < getDailyPeriods(day);
            }
        }
        int dp = getDailyPeriods(day);
        if (day == Day.SATURDAY && dp <= 4) {
            return false;
        }
        int rec = getRawRecessAfterPeriod(day);
        return rec > 0 && rec < dp;
    }

    public void setDailyRecessEnabled(Day day, boolean enabled) {
        if (this.dailyRecessEnabled == null) this.dailyRecessEnabled = new HashMap<>();
        this.dailyRecessEnabled.put(day, enabled);
    }

    public Map<Day, Boolean> getDailyRecessEnabled() {
        if (this.dailyRecessEnabled == null) this.dailyRecessEnabled = new HashMap<>();
        return this.dailyRecessEnabled;
    }

    public void setDailyRecessEnabled(Map<Day, Boolean> map) {
        this.dailyRecessEnabled = new HashMap<>(map);
    }

    public int getRawRecessAfterPeriod(Day day) {
        if (dailyRecessAfterPeriod != null && dailyRecessAfterPeriod.containsKey(day)) {
            int p = dailyRecessAfterPeriod.get(day);
            if (p > 0) return p;
        }
        int dp = getDailyPeriods(day);
        int def = recessAfterPeriod > 0 ? recessAfterPeriod : 4;
        return (dp > 1 && def >= dp) ? Math.max(1, dp - 1) : def;
    }

    public int getRecessAfterPeriod(Day day) {
        if (!isRecessEnabled(day)) {
            return 0;
        }
        return getRawRecessAfterPeriod(day);
    }

    public void setDailyRecessAfterPeriod(Day day, int period) {
        if (this.dailyRecessAfterPeriod == null) this.dailyRecessAfterPeriod = new HashMap<>();
        if (period > 0) {
            this.dailyRecessAfterPeriod.put(day, period);
        }
    }

    public Map<Day, Integer> getDailyRecessAfterPeriod() {
        if (this.dailyRecessAfterPeriod == null) this.dailyRecessAfterPeriod = new HashMap<>();
        return this.dailyRecessAfterPeriod;
    }

    public int getRecessDuration(Day day) {
        if (dailyRecessDurations != null && dailyRecessDurations.containsKey(day)) {
            return dailyRecessDurations.get(day);
        }
        return recessDurationMinutes > 0 ? recessDurationMinutes : 20;
    }

    public void setDailyRecessDuration(Day day, int duration) {
        if (this.dailyRecessDurations == null) this.dailyRecessDurations = new HashMap<>();
        this.dailyRecessDurations.put(day, duration);
    }

    public Map<Day, Integer> getDailyRecessDurations() {
        if (this.dailyRecessDurations == null) this.dailyRecessDurations = new HashMap<>();
        return this.dailyRecessDurations;
    }

    public void setDailyRecessDurations(Map<Day, Integer> map) {
        this.dailyRecessDurations = new HashMap<>(map);
    }

    public String getSchoolStartTime() { return schoolStartTime != null ? schoolStartTime : "07:10"; }
    public void setSchoolStartTime(String schoolStartTime) { this.schoolStartTime = schoolStartTime; }

    public String getSaturdayStartTime() { return saturdayStartTime != null ? saturdayStartTime : "07:30"; }
    public void setSaturdayStartTime(String saturdayStartTime) { this.saturdayStartTime = saturdayStartTime; }

    public int getDefaultPeriodDurationMinutes() { return defaultPeriodDurationMinutes > 0 ? defaultPeriodDurationMinutes : 35; }
    public void setDefaultPeriodDurationMinutes(int defaultPeriodDurationMinutes) { this.defaultPeriodDurationMinutes = defaultPeriodDurationMinutes; }

    public int getPassingTimeMinutes() { return passingTimeMinutes; }
    public void setPassingTimeMinutes(int passingTimeMinutes) { this.passingTimeMinutes = passingTimeMinutes; }

    public int getRecessDurationMinutes() { return recessDurationMinutes > 0 ? recessDurationMinutes : 20; }
    public void setRecessDurationMinutes(int recessDurationMinutes) {
        this.recessDurationMinutes = recessDurationMinutes;
        if (this.dailyRecessDurations == null) {
            this.dailyRecessDurations = new HashMap<>();
        }
        for (Day d : getDays()) {
            this.dailyRecessDurations.put(d, recessDurationMinutes);
        }
    }

    public boolean isShortBreakEnabled() { return shortBreakEnabled; }
    public void setShortBreakEnabled(boolean shortBreakEnabled) { this.shortBreakEnabled = shortBreakEnabled; }

    public int getShortBreakAfterPeriod() { return shortBreakAfterPeriod; }
    public void setShortBreakAfterPeriod(int shortBreakAfterPeriod) { this.shortBreakAfterPeriod = shortBreakAfterPeriod; }

    public int getShortBreakDurationMinutes() { return shortBreakDurationMinutes > 0 ? shortBreakDurationMinutes : 10; }
    public void setShortBreakDurationMinutes(int shortBreakDurationMinutes) { this.shortBreakDurationMinutes = shortBreakDurationMinutes; }

    public Map<Integer, Integer> getPeriodDurationOverrides() {
        if (this.periodDurationOverrides == null) this.periodDurationOverrides = new HashMap<>();
        return this.periodDurationOverrides;
    }
    public void setPeriodDurationOverrides(Map<Integer, Integer> periodDurationOverrides) {
        this.periodDurationOverrides = new HashMap<>(periodDurationOverrides);
    }

    public Map<Integer, String> getPeriodTimeOverrides() {
        if (this.periodTimeOverrides == null) this.periodTimeOverrides = new HashMap<>();
        return this.periodTimeOverrides;
    }
    public void setPeriodTimeOverrides(Map<Integer, String> periodTimeOverrides) {
        this.periodTimeOverrides = new HashMap<>(periodTimeOverrides);
    }

    public int getPeriodDuration(int period) {
        if (periodDurationOverrides != null && periodDurationOverrides.containsKey(period)) {
            return periodDurationOverrides.get(period);
        }
        if (period == 1 && defaultPeriodDurationMinutes == 35) {
            return 40; // Default opening morning lecture has 40 minutes at SIES
        }
        return defaultPeriodDurationMinutes > 0 ? defaultPeriodDurationMinutes : 35;
    }

    public Map<Integer, String> computePeriodTimings(Day day) {
        Map<Integer, String> result = new TreeMap<>();
        int periods = getDailyPeriods(day);
        boolean isSat = (day == Day.SATURDAY);
        String startStr = (isSat && saturdayStartTime != null && !saturdayStartTime.isBlank()) ? saturdayStartTime : schoolStartTime;
        if (startStr == null || !startStr.contains(":")) startStr = "07:10";

        int currentMin = parseTimeToMinutes(startStr);
        boolean recessOn = isRecessEnabled(day);
        int recPeriod = getRecessAfterPeriod(day);
        int recDur = getRecessDuration(day);

        for (int p = 1; p <= periods; p++) {
            if (periodTimeOverrides != null && periodTimeOverrides.containsKey(p)) {
                String override = periodTimeOverrides.get(p);
                result.put(p, override);
                if (override != null && override.contains("-")) {
                    currentMin = parseTimeToMinutes(override.split("-")[1].trim());
                }
            } else {
                int duration = getPeriodDuration(p);
                String pStart = formatMinutesToTime(currentMin);
                currentMin += duration;
                String pEnd = formatMinutesToTime(currentMin);
                result.put(p, pStart + " - " + pEnd);
            }

            // Short break check
            if (shortBreakEnabled && p == shortBreakAfterPeriod && p < periods) {
                currentMin += shortBreakDurationMinutes;
            }

            // Recess check
            if (recessOn && p == recPeriod && p < periods) {
                currentMin += recDur;
            } else if (passingTimeMinutes > 0 && p < periods) {
                currentMin += passingTimeMinutes;
            }
        }
        return result;
    }

    public String getRecessTiming(Day day) {
        if (!isRecessEnabled(day)) return "";
        int periods = getDailyPeriods(day);
        int recPeriod = getRecessAfterPeriod(day);
        if (recPeriod <= 0 || recPeriod >= periods) return "";

        boolean isSat = (day == Day.SATURDAY);
        String startStr = (isSat && saturdayStartTime != null && !saturdayStartTime.isBlank()) ? saturdayStartTime : schoolStartTime;
        if (startStr == null || !startStr.contains(":")) startStr = "07:10";

        int currentMin = parseTimeToMinutes(startStr);
        for (int p = 1; p <= recPeriod; p++) {
            if (periodTimeOverrides != null && periodTimeOverrides.containsKey(p)) {
                String override = periodTimeOverrides.get(p);
                if (override != null && override.contains("-")) {
                    currentMin = parseTimeToMinutes(override.split("-")[1].trim());
                }
            } else {
                currentMin += getPeriodDuration(p);
            }
            if (shortBreakEnabled && p == shortBreakAfterPeriod && p < recPeriod) {
                currentMin += shortBreakDurationMinutes;
            }
            if (passingTimeMinutes > 0 && p < recPeriod) {
                currentMin += passingTimeMinutes;
            }
        }

        String recStart = formatMinutesToTime(currentMin);
        int recDur = getRecessDuration(day);
        String recEnd = formatMinutesToTime(currentMin + recDur);
        return recStart + " - " + recEnd;
    }

    public static int parseTimeToMinutes(String hhmm) {
        try {
            String[] parts = hhmm.trim().split(":");
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            return 7 * 60 + 10;
        }
    }

    public static String formatMinutesToTime(int totalMinutes) {
        int h = (totalMinutes / 60) % 24;
        int m = totalMinutes % 60;
        return String.format("%02d:%02d", h, m);
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
        this.periodsPerDay = getMaxDailyPeriods();
        ensurePeriodsCapacity(this.periodsPerDay);
    }

    public void setDailyPeriod(Day day, int count) {
        if (this.dailyPeriods == null) this.dailyPeriods = new HashMap<>();
        this.dailyPeriods.put(day, count);
        this.periodsPerDay = getMaxDailyPeriods();
        ensurePeriodsCapacity(this.periodsPerDay);
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
        int effectiveMax = Math.max(1, maxPeriods);
        Map<Integer, String> computed = computePeriodTimings(Day.MONDAY);
        for (int p = 1; p <= effectiveMax; p++) {
            if (!periods.containsKey(p)) {
                String timing = (periodTimingsMap != null && periodTimingsMap.containsKey(p)) ?
                        periodTimingsMap.get(p) : computed.getOrDefault(p, "Period " + p);
                periods.put(p, new Period(p, timing, getRecessAfterPeriod(Day.MONDAY)));
            }
        }
        periods.keySet().removeIf(p -> p > effectiveMax);
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

    // =========================================================================
    // Constraint Configuration Helpers
    // =========================================================================

    public static boolean isHardConstraintMandatory(String id) {
        if (id == null) return false;
        String u = id.trim().toUpperCase();
        return "H3".equals(u) || "H4".equals(u) || "H9".equals(u);
    }

    public boolean isHardConstraintEnabled(String id) {
        if (isHardConstraintMandatory(id)) return true;
        if (hardConstraintsEnabled == null) return true;
        return hardConstraintsEnabled.getOrDefault(id.toUpperCase().trim(), true);
    }

    public void setHardConstraintEnabled(String id, boolean enabled) {
        String u = id.trim().toUpperCase();
        if (!enabled && isHardConstraintMandatory(u)) {
            throw new IllegalArgumentException("Constraint " + u + " is mandatory for timetable integrity and cannot be disabled.");
        }
        if (this.hardConstraintsEnabled == null) this.hardConstraintsEnabled = new LinkedHashMap<>();
        this.hardConstraintsEnabled.put(u, enabled);
    }

    public Map<String, Boolean> getHardConstraintsEnabled() {
        Map<String, Boolean> res = new LinkedHashMap<>();
        for (int i = 1; i <= 10; i++) {
            String id = "H" + i;
            res.put(id, isHardConstraintEnabled(id));
        }
        return res;
    }

    public boolean isSoftConstraintEnabled(String id) {
        if (softConstraintsEnabled == null) return true;
        return softConstraintsEnabled.getOrDefault(id.toUpperCase().trim(), true);
    }

    public void setSoftConstraintEnabled(String id, boolean enabled) {
        if (this.softConstraintsEnabled == null) this.softConstraintsEnabled = new LinkedHashMap<>();
        this.softConstraintsEnabled.put(id.toUpperCase().trim(), enabled);
    }

    public Map<String, Boolean> getSoftConstraintsEnabled() {
        Map<String, Boolean> res = new LinkedHashMap<>();
        for (int i = 1; i <= 6; i++) {
            String id = "S" + i;
            res.put(id, isSoftConstraintEnabled(id));
        }
        return res;
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

    public int getSoftConstraintWeight(String id) {
        if (softConstraintWeights == null) return 50;
        return softConstraintWeights.getOrDefault(id.toUpperCase().trim(), 50);
    }

    public void setSoftConstraintWeight(String id, int weight) {
        if (weight < 0 || weight > 500) {
            throw new IllegalArgumentException("Soft constraint weight must be between 0 and 500.");
        }
        if (this.softConstraintWeights == null) this.softConstraintWeights = new LinkedHashMap<>();
        this.softConstraintWeights.put(id.toUpperCase().trim(), weight);
    }

    public Map<String, Object> toConstraintsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Integer> wMap = new LinkedHashMap<>(getSoftConstraintWeights());
        map.put("weights", wMap);

        Map<String, Object> hcMap = new LinkedHashMap<>();
        for (int i = 1; i <= 10; i++) {
            String id = "H" + i;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("enabled", isHardConstraintEnabled(id));
            if ("H2".equals(id)) entry.put("max_continuous_lectures", getH2MaxContinuousLectures());
            if ("H5".equals(id)) entry.put("max_concurrent_lab_classes", getH5MaxConcurrentLabClasses());
            if ("H6".equals(id)) entry.put("prohibited_period", getH6ProhibitedPeriod());
            if ("H8".equals(id)) entry.put("max_activity_periods_per_day", getH8MaxActivityPeriodsPerDay());
            hcMap.put(id, entry);
        }
        map.put("hard_constraints", hcMap);

        Map<String, Object> scMap = new LinkedHashMap<>();
        for (int i = 1; i <= 6; i++) {
            String id = "S" + i;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("enabled", isSoftConstraintEnabled(id));
            entry.put("weight", getWeight(id, 50));
            scMap.put(id, entry);
        }
        map.put("soft_constraints", scMap);
        return map;
    }

    public static SchoolConfig createDefault() {
        SchoolConfig cfg = new SchoolConfig();
        cfg.schoolName = "SIES (Dr APJ Abdul Kalam Memorial) High School";
        cfg.academicYear = "2026-27";
        cfg.days = new ArrayList<>(Arrays.asList(Day.values()));
        cfg.periodsPerDay = 8;
        cfg.recessAfterPeriod = 4;
        cfg.dailyPeriods = new HashMap<>();
        cfg.dailyRecessAfterPeriod = new HashMap<>();
        cfg.dailyRecessEnabled = new HashMap<>();
        cfg.dailyRecessDurations = new HashMap<>();
        cfg.schoolStartTime = "07:10";
        cfg.saturdayStartTime = "07:30";
        cfg.defaultPeriodDurationMinutes = 35;
        cfg.passingTimeMinutes = 0;
        cfg.recessDurationMinutes = 20;
        cfg.periodDurationOverrides = new HashMap<>();
        cfg.periodDurationOverrides.put(1, 40);
        cfg.periodTimeOverrides = new HashMap<>();

        for (Day d : cfg.days) {
            cfg.dailyPeriods.put(d, 8);
            cfg.dailyRecessEnabled.put(d, true);
            cfg.dailyRecessAfterPeriod.put(d, 4);
            cfg.dailyRecessDurations.put(d, 20);
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

        cfg.softConstraintWeights = new LinkedHashMap<>();
        cfg.softConstraintWeights.put("S1", 100);
        cfg.softConstraintWeights.put("S2", 80);
        cfg.softConstraintWeights.put("S3", 50);
        cfg.softConstraintWeights.put("S4", 40);
        cfg.softConstraintWeights.put("S5", 30);
        cfg.softConstraintWeights.put("S6", 20);

        cfg.hardConstraintsEnabled = new LinkedHashMap<>();
        for (int i = 1; i <= 10; i++) cfg.hardConstraintsEnabled.put("H" + i, true);

        cfg.softConstraintsEnabled = new LinkedHashMap<>();
        for (int i = 1; i <= 6; i++) cfg.softConstraintsEnabled.put("S" + i, true);

        cfg.h2MaxContinuousLectures = 2;
        cfg.h5MaxConcurrentLabClasses = 1;
        cfg.h6ProhibitedPeriod = 1;
        cfg.h8MaxActivityPeriodsPerDay = 2;

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
