package com.sies.timetable.exporter;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.AllocationReconciler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Exports production teacher timetables in clean CSV grid format,
 * dynamically adapting to configured daily periods and recess.
 */
public class TeacherTimetableCsvExporter {

    public static void export(
            Schedule schedule,
            AllocationReconciler.ReconciliationResult recResult,
            SchoolConfig config,
            String filePath
    ) throws Exception {
        Files.createDirectories(Paths.get(filePath).getParent());

        int maxPeriods = config.getMaxDailyPeriods();
        int defaultRecess = config.getRecessAfterPeriod(Day.MONDAY);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            StringBuilder header = new StringBuilder("TeacherCode,TeacherName,TotalWorkload,Day");
            for (int p = 1; p <= defaultRecess; p++) {
                Period period = config.getPeriods().get(p);
                String time = period != null ? " (" + period.getStartTime() + "-" + period.getEndTime() + ")" : "";
                header.append(",Period ").append(p).append(time);
            }
            header.append(",RECESS");
            for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
                Period period = config.getPeriods().get(p);
                String time = period != null ? " (" + period.getStartTime() + "-" + period.getEndTime() + ")" : "";
                header.append(",Period ").append(p).append(time);
            }
            header.append("\n");
            bw.write(header.toString());

            for (Teacher teacher : recResult.teachers()) {
                for (Day day : config.getDays()) {
                    int dailyPeriods = config.getDailyPeriods(day);
                    StringBuilder sb = new StringBuilder();
                    sb.append(teacher.getCode()).append(",");
                    sb.append(String.format("\"%s\",", teacher.getFullName()));
                    sb.append(teacher.getTotalWorkload()).append(",");
                    sb.append(day.getDisplayName());

                    for (int p = 1; p <= defaultRecess; p++) {
                        sb.append(",");
                        if (p <= dailyPeriods) {
                            Period period = config.getPeriods().get(p);
                            ScheduleAssignment sa = schedule.getTeacherAssignment(teacher, new TimeSlot(day, period));
                            if (sa != null) {
                                sb.append(String.format("\"%s (%s)\"", sa.schoolClass().getCanonicalName(), sa.subject().getNormalizedName()));
                            } else {
                                sb.append("\"FREE\"");
                            }
                        } else {
                            sb.append("\"FREE\"");
                        }
                    }

                    if (config.isRecessEnabled(day)) {
                        sb.append(",\"RECESS\"");
                    } else {
                        sb.append(",\"---\"");
                    }

                    for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
                        sb.append(",");
                        if (p <= dailyPeriods) {
                            Period period = config.getPeriods().get(p);
                            ScheduleAssignment sa = schedule.getTeacherAssignment(teacher, new TimeSlot(day, period));
                            if (sa != null) {
                                sb.append(String.format("\"%s (%s)\"", sa.schoolClass().getCanonicalName(), sa.subject().getNormalizedName()));
                            } else {
                                sb.append("\"FREE\"");
                            }
                        } else {
                            sb.append("\"FREE\"");
                        }
                    }

                    sb.append("\n");
                    bw.write(sb.toString());
                }
            }
        }
    }
}
