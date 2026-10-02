package com.sies.timetable.exporter;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.AllocationReconciler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Exports production student/class timetables in clean CSV grid format,
 * dynamically adapting to configured daily periods and recess.
 */
public class StudentTimetableCsvExporter {

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
            StringBuilder header = new StringBuilder("Standard,Division,Class,Day");
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

            for (SchoolClass sc : recResult.classes()) {
                for (Day day : config.getDays()) {
                    int dailyPeriods = config.getDailyPeriods(day);
                    StringBuilder sb = new StringBuilder();
                    sb.append(sc.getStandard()).append(",");
                    sb.append(sc.getDivision()).append(",");
                    sb.append(sc.getDisplayName()).append(",");
                    sb.append(day.getDisplayName());

                    for (int p = 1; p <= defaultRecess; p++) {
                        sb.append(",");
                        if (p <= dailyPeriods) {
                            Period period = config.getPeriods().get(p);
                            ScheduleAssignment sa = schedule.getAssignment(sc, day, period);
                            if (sa != null) {
                                sb.append(String.format("\"%s (%s)\"", sa.subject().getNormalizedName(), sa.teacher().getCode()));
                            } else {
                                sb.append("\"---\"");
                            }
                        } else {
                            sb.append("\"---\"");
                        }
                    }

                    sb.append(",\"RECESS\"");

                    for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
                        sb.append(",");
                        if (p <= dailyPeriods) {
                            Period period = config.getPeriods().get(p);
                            ScheduleAssignment sa = schedule.getAssignment(sc, day, period);
                            if (sa != null) {
                                sb.append(String.format("\"%s (%s)\"", sa.subject().getNormalizedName(), sa.teacher().getCode()));
                            } else {
                                sb.append("\"---\"");
                            }
                        } else {
                            sb.append("\"---\"");
                        }
                    }

                    sb.append("\n");
                    bw.write(sb.toString());
                }
            }
        }
    }
}
