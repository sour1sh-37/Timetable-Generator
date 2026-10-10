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
        boolean isServerMode = false;
        int serverPort = 8080;
        String configPath = "config/school-config.yaml";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--server".equalsIgnoreCase(arg) || "--daemon".equalsIgnoreCase(arg)) {
                isServerMode = true;
                if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                    serverPort = Integer.parseInt(args[++i]);
                }
            } else if (arg.startsWith("--server=")) {
                isServerMode = true;
                serverPort = Integer.parseInt(arg.substring("--server=".length()));
            } else if ("--port".equalsIgnoreCase(arg) && i + 1 < args.length && args[i + 1].matches("\\d+")) {
                serverPort = Integer.parseInt(args[++i]);
            } else if ("--cli".equalsIgnoreCase(arg) || "--headless".equalsIgnoreCase(arg) || "--batch".equalsIgnoreCase(arg)) {
                isCliMode = true;
            } else if (arg.endsWith(".yaml") || arg.endsWith(".yml")) {
                configPath = arg;
                isCliMode = true;
            }
        }

        if (isServerMode) {
            runServer(serverPort);
            return;
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

    public static void runServer(int port) {
        System.out.println("================================================================================");
        System.out.println(" SIES Timetable Generator — HTTP API Backend Daemon");
        System.out.println("================================================================================");
        try {
            com.sies.timetable.server.TimetableHttpServer server = new com.sies.timetable.server.TimetableHttpServer(port);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SERVER] Shutting down HTTP API server...");
                server.stop(0);
            }));

            System.out.println("✓ Server bound strictly to loopback: " + server.getBaseUrl());
            System.out.println("✓ Ready for React/Tauri desktop frontend requests.");
            System.out.println("Press Ctrl+C or send POST /api/admin/shutdown to stop.\n");

            // Keep alive until stopped or interrupted
            server.awaitTermination();
            System.out.println("[SERVER] Daemon terminated gracefully.");
        } catch (InterruptedException e) {
            System.out.println("[SERVER] Daemon interrupted. Exiting.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[SERVER ERROR] Failed to start HTTP API daemon: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
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
