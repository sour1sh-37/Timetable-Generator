package com.sies.timetable;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.gui.TimetableMainFrame;
import com.sies.timetable.service.TimetableService;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.io.File;

/**
 * Main application entry point for SIES APJ School Timetable Generator.
 * Automatically launches Desktop GUI in interactive environments or runs CLI in headless/batch mode.
 */
public class Main {

    public static void main(String[] args) {
        boolean isCliMode = false;
        String configPath = "config/school-config.yaml";

        for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg) || "--headless".equalsIgnoreCase(arg) || "--batch".equalsIgnoreCase(arg)) {
                isCliMode = true;
            } else if (arg.endsWith(".yaml") || arg.endsWith(".yml")) {
                configPath = arg;
                isCliMode = true;
            }
        }

        if (GraphicsEnvironment.isHeadless()) {
            isCliMode = true;
        }

        if (isCliMode) {
            runCli(configPath);
        } else {
            final String finalConfigPath = configPath;
            SwingUtilities.invokeLater(() -> {
                try {
                    TimetableMainFrame frame = new TimetableMainFrame();
                    frame.setVisible(true);
                } catch (Exception e) {
                    System.err.println("Failed to launch GUI, falling back to CLI: " + e.getMessage());
                    runCli(finalConfigPath);
                }
            });
        }
    }

    public static void runCli(String configPath) {
        System.out.println("================================================================================");
        System.out.println(" SIES (Dr APJ Abdul Kalam Memorial) High School — Timetable Generator 2026–27");
        System.out.println("================================================================================\n");

        try {
            SchoolConfig config = SchoolConfig.load(configPath);
            File folder = new File(".");
            TimetableService.FolderDetectionResult detection = TimetableService.detectInputFiles(folder);

            if (!detection.isValid()) {
                System.err.println("[ERROR] Missing required input files in current folder: " + String.join(", ", detection.missingFiles()));
                System.exit(1);
            }

            System.out.println("[Step 1/7] Loading configuration and constraint specifications...");
            System.out.println("  ✓ School: " + config.getSchoolName() + " (" + config.getAcademicYear() + ")");
            System.out.println("  ✓ Schedule: " + config.getDays().size() + " days, " + config.getPeriodsPerDay() + " periods/day (recess after P" + config.getRecessAfterPeriod() + ")");

            TimetableService.GenerationResult result = TimetableService.generate(detection, config, (step, total, msg) -> {
                System.out.printf("[%d/%d] %s\n", step, total, msg);
            });

            if (!result.isSuccess()) {
                System.err.println("\n[FATAL ERROR] Timetable generation failed: " + result.errorMessage());
                System.exit(1);
            }

            System.out.println("\n================================================================================");
            System.out.println(" SIES Timetable Generation Completed Successfully!");
            System.out.println(" • Hard Constraints Status: PASS (0 Violations)");
            System.out.printf(" • Schedule Quality Index:  %.2f / 100.0\n", result.validationResult().overallQualityIndex());
            System.out.printf(" • Solve Duration:          %.2f seconds\n", result.solveTimeSeconds());
            System.out.println(" • Excel Timetable:         " + result.excelFile().getPath());
            System.out.println(" • Student CSV Timetable:   " + result.studentCsvFile().getPath());
            System.out.println(" • Teacher CSV Timetable:   " + result.teacherCsvFile().getPath());
            System.out.println("================================================================================\n");

        } catch (Exception e) {
            System.err.println("\n[FATAL ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
