package com.sies.timetable.service;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.exporter.*;
import com.sies.timetable.model.*;
import com.sies.timetable.parser.*;
import com.sies.timetable.reconciliation.*;
import com.sies.timetable.solver.CpSatTimetableSolver;
import com.sies.timetable.validator.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Service Layer orchestrating timetable generation, file detection,
 * manual UI input processing, and dynamic grid transformations.
 */
public class TimetableService {

    public interface ProgressListener {
        void onProgress(int stepIndex, int totalSteps, String message);
    }

    public record FolderDetectionResult(
            boolean isValid,
            File folder,
            File standardwiseFile,
            File teacherwiseFile,
            File classTeachersFile,
            File constraintsFile,
            String statusMessage,
            List<String> missingFiles
    ) {}

    public record GenerationResult(
            boolean isSuccess,
            String errorMessage,
            Schedule schedule,
            AllocationReconciler.ReconciliationResult reconciliationResult,
            ScheduleValidator.ScheduleValidationResult validationResult,
            SchoolConfig config,
            File outputDir,
            File excelFile,
            File studentCsvFile,
            File teacherCsvFile,
            File pdfDir,
            double solveTimeSeconds
    ) {
        public GenerationResult(boolean isSuccess, String errorMessage, Schedule schedule,
                                AllocationReconciler.ReconciliationResult reconciliationResult,
                                ScheduleValidator.ScheduleValidationResult validationResult,
                                SchoolConfig config, File outputDir, File excelFile,
                                File studentCsvFile, File teacherCsvFile, double solveTimeSeconds) {
            this(isSuccess, errorMessage, schedule, reconciliationResult, validationResult, config,
                    outputDir, excelFile, studentCsvFile, teacherCsvFile,
                    outputDir != null ? new File(outputDir, "pdf") : null, solveTimeSeconds);
        }
    }

    public record TimetableCell(
            String mainText,
            String subText,
            String fullText,
            boolean isRecess,
            boolean isFree,
            SubjectCategory category
    ) {}

    public record TimetableGrid(
            String title,
            String subtitle,
            String[] headers,
            String[] rowLabels,
            TimetableCell[][] cells
    ) {}

    public static FolderDetectionResult detectInputFiles(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return new FolderDetectionResult(false, folder, null, null, null, null, "Selected path is not a valid directory.", List.of("Valid directory required"));
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return new FolderDetectionResult(false, folder, null, null, null, null, "Cannot read directory contents.", List.of("Read permission required"));
        }

        File stdFile = null;
        File tchFile = null;
        File ctFile = null;
        File cstFile = null;

        // Check sample-data subdirectory first if present
        File sampleDir = new File(folder, "sample-data");
        if (sampleDir.exists() && sampleDir.isDirectory()) {
            File[] sampleFiles = sampleDir.listFiles();
            if (sampleFiles != null) {
                for (File f : sampleFiles) {
                    String name = f.getName().toLowerCase();
                    if (f.isFile()) {
                        if (name.contains("standardwise") && (name.endsWith(".xlsx") || name.endsWith(".xls"))) {
                            stdFile = f;
                        } else if (name.contains("teacherwise") && (name.endsWith(".xlsx") || name.endsWith(".xls"))) {
                            tchFile = f;
                        } else if (name.contains("classteacher") && (name.endsWith(".xlsx") || name.endsWith(".xls"))) {
                            ctFile = f;
                        } else if (name.equals("constraints.csv") || (name.contains("constraint") && name.endsWith(".csv"))) {
                            cstFile = f;
                        }
                    }
                }
            }
        }

        // Fall back to scanning files directly in folder
        if (stdFile == null || tchFile == null || ctFile == null) {
            for (File f : files) {
                String name = f.getName().toLowerCase();
                if (f.isFile()) {
                    if (stdFile == null && name.contains("standardwise") && (name.endsWith(".xlsx") || name.endsWith(".xls"))) {
                        stdFile = f;
                    } else if (tchFile == null && name.contains("teacherwise") && (name.endsWith(".xlsx") || name.endsWith(".xls"))) {
                        tchFile = f;
                    } else if (ctFile == null && name.contains("classteacher") && (name.endsWith(".xlsx") || name.endsWith(".xls"))) {
                        ctFile = f;
                    } else if (cstFile == null && (name.equals("constraints.csv") || (name.contains("constraint") && name.endsWith(".csv")))) {
                        cstFile = f;
                    }
                }
            }
        }

        if (cstFile == null) {
            File rootCst = new File(folder, "constraints.csv");
            if (rootCst.exists()) cstFile = rootCst;
            else {
                File sampleCst = new File(folder, "sample-data/sample-constraints.csv");
                if (sampleCst.exists()) cstFile = sampleCst;
            }
        }

        List<String> missing = new ArrayList<>();
        if (stdFile == null) missing.add("Standardwise Allotment XLSX");
        if (tchFile == null) missing.add("Teacherwise Allotment XLSX");
        if (ctFile == null) missing.add("Classteachers List XLSX");
        if (cstFile == null) missing.add("constraints.csv");

        if (missing.isEmpty()) {
            return new FolderDetectionResult(
                    true, folder, stdFile, tchFile, ctFile, cstFile,
                    "✓ All required input files found (" + stdFile.getName() + ", " + tchFile.getName() + ", " + ctFile.getName() + ")",
                    Collections.emptyList()
            );
        } else {
            return new FolderDetectionResult(
                    false, folder, stdFile, tchFile, ctFile, cstFile,
                    "⚠ Missing required files: " + String.join(", ", missing),
                    missing
            );
        }
    }

    public static GenerationResult generate(FolderDetectionResult detection, ProgressListener listener) {
        return generate(detection, null, listener);
    }

    public static GenerationResult generate(FolderDetectionResult detection, SchoolConfig configOverride, ProgressListener listener) {
        if (!detection.isValid()) {
            return new GenerationResult(false, "Input folder is missing required files: " + String.join(", ", detection.missingFiles()),
                    null, null, null, null, null, null, null, null, null, 0.0);
        }

        try {
            // Step 1: Load Configuration
            if (listener != null) listener.onProgress(1, 7, "Loading configuration and constraint specifications...");
            SchoolConfig config = configOverride != null ? configOverride : SchoolConfig.load("config/school-config.yaml");
            config.setStandardwiseFile(detection.standardwiseFile().getAbsolutePath());
            config.setTeacherwiseFile(detection.teacherwiseFile().getAbsolutePath());
            config.setClassTeachersFile(detection.classTeachersFile().getAbsolutePath());
            config.setConstraintsFile(detection.constraintsFile().getAbsolutePath());

            List<ConstraintSpec> constraintSpecs = ConstraintsCsvParser.parse(config.getConstraintsFile());

            // Step 2: Parse Inputs
            if (listener != null) listener.onProgress(2, 7, "Parsing spreadsheet allocations and teacher blocks...");
            List<StandardwiseParser.StandardwiseRecord> stdRecords = StandardwiseParser.parse(config.getStandardwiseFile());
            List<TeacherwiseParser.ParsedTeacherBlock> teacherBlocks = TeacherwiseParser.parse(config.getTeacherwiseFile());
            List<ClassTeacherListParser.ParsedClassTeacherRow> ctRows = ClassTeacherListParser.parse(config.getClassTeachersFile());

            // Step 3: Granular Reconciliation
            if (listener != null) listener.onProgress(3, 7, "Reconciling atomic allocations and teacher workloads...");
            AllocationReconciler.ReconciliationResult recResult = AllocationReconciler.reconcile(stdRecords, teacherBlocks, ctRows, config);

            // Step 4: Intermediate Export & Pre-Solve Validation
            if (listener != null) listener.onProgress(4, 7, "Validating scheduling feasibility axioms...");
            Files.createDirectories(Paths.get(config.getOutputDir()));
            IntermediateDataExporter.exportAll(recResult, config.getOutputDir());
            List<InputValidator.ValidationCheckResult> inputChecks = InputValidator.validate(recResult, config);
            InputValidator.exportReport(inputChecks, config.getOutputDir() + "/input_validation_report.csv");

            long failedAxioms = inputChecks.stream().filter(c -> c.status().equals("FAIL")).count();
            if (failedAxioms > 0) {
                return new GenerationResult(false, "Pre-solve input validation failed. Check input_validation_report.csv.",
                        null, recResult, null, config, new File(config.getOutputDir()), null, null, null, null, 0.0);
            }

            // Step 5: Solve CP-SAT Model
            if (listener != null) listener.onProgress(5, 7, "Solving CP-SAT constraint optimization model (Google OR-Tools)...");
            CpSatTimetableSolver.SolverResult solverResult = CpSatTimetableSolver.solve(recResult, config);

            if (solverResult.schedule() == null) {
                return new GenerationResult(false, "CP-SAT solver failed to find a valid schedule.",
                        null, recResult, null, config, new File(config.getOutputDir()), null, null, null, null, solverResult.solveTimeSeconds());
            }

            // Step 6: Independent Post-Solve Validation
            if (listener != null) listener.onProgress(6, 7, "Running independent post-solve verification...");
            ScheduleValidator.ScheduleValidationResult valResult = ScheduleValidator.validate(
                    solverResult.schedule(), recResult, config, constraintSpecs
            );
            ScheduleValidator.exportReports(valResult, config.getOutputDir());

            // Step 7: Export Production Outputs
            if (listener != null) listener.onProgress(7, 7, "Generating Excel workbook, CSVs, and per-class PDF timetables...");
            File studentCsv = new File(config.getOutputDir() + "/student_timetable.csv");
            File teacherCsv = new File(config.getOutputDir() + "/teacher_timetable.csv");
            File excelFile = new File(config.getOutputDir() + "/timetable.xlsx");
            File pdfDir = new File(config.getOutputDir() + "/pdf");

            StudentTimetableCsvExporter.export(solverResult.schedule(), recResult, config, studentCsv.getAbsolutePath());
            TeacherTimetableCsvExporter.export(solverResult.schedule(), recResult, config, teacherCsv.getAbsolutePath());
            ExcelTimetableExporter.export(solverResult.schedule(), recResult, valResult, config, excelFile.getAbsolutePath());
            PdfTimetableExporter.exportAllClasses(solverResult.schedule(), recResult, config, pdfDir);

            if (listener != null) listener.onProgress(7, 7, "Timetable generation complete with 0 hard constraint violations!");

            return new GenerationResult(
                    true,
                    null,
                    solverResult.schedule(),
                    recResult,
                    valResult,
                    config,
                    new File(config.getOutputDir()),
                    excelFile,
                    studentCsv,
                    teacherCsv,
                    pdfDir,
                    solverResult.solveTimeSeconds()
            );

        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new GenerationResult(false, "Timetable generation failed: " + errorMsg,
                    null, null, null, null, null, null, null, null, null, 0.0);
        }
    }

    /**
     * Primary entry point for GUI-driven manual input workflow.
     */
    public static GenerationResult generate(ManualTimetableModel manualModel, ProgressListener listener) {
        try {
            // Step 1: Pre-solve validation on manual model
            if (listener != null) listener.onProgress(1, 7, "Validating manual allocations and timetable structure...");
            List<String> modelErrors = manualModel.validateAll();
            if (!modelErrors.isEmpty()) {
                return new GenerationResult(false, "Validation failed:\n • " + String.join("\n • ", modelErrors),
                        null, null, null, null, null, null, null, null, null, 0.0);
            }

            // Step 2: Build SchoolConfig
            if (listener != null) listener.onProgress(2, 7, "Configuring dynamic academic parameters and periods...");
            SchoolConfig config = manualModel.getStructureConfig();

            // Step 3: Reconcile / Convert to domain model
            if (listener != null) listener.onProgress(3, 7, "Reconciling atomic teaching allocations and teacher workloads...");
            AllocationReconciler.ReconciliationResult recResult = manualModel.toReconciliationResult();

            // Step 4: Pre-solve Axiom Validation
            if (listener != null) listener.onProgress(4, 7, "Checking scheduling feasibility axioms...");
            Files.createDirectories(Paths.get(config.getOutputDir()));
            IntermediateDataExporter.exportAll(recResult, config.getOutputDir());
            List<InputValidator.ValidationCheckResult> inputChecks = InputValidator.validate(recResult, config);
            InputValidator.exportReport(inputChecks, config.getOutputDir() + "/input_validation_report.csv");

            long failedAxioms = inputChecks.stream().filter(c -> c.status().equals("FAIL")).count();
            if (failedAxioms > 0) {
                return new GenerationResult(false, "Pre-solve feasibility check failed. Check input_validation_report.csv.",
                        null, recResult, null, config, new File(config.getOutputDir()), null, null, null, null, 0.0);
            }

            // Step 5: Solve CP-SAT Model
            if (listener != null) listener.onProgress(5, 7, "Solving CP-SAT constraint optimization model (Google OR-Tools)...");
            CpSatTimetableSolver.SolverResult solverResult = CpSatTimetableSolver.solve(recResult, config);

            if (solverResult.schedule() == null) {
                return new GenerationResult(false, "CP-SAT solver failed to find a feasible schedule within the time limit.",
                        null, recResult, null, config, new File(config.getOutputDir()), null, null, null, null, solverResult.solveTimeSeconds());
            }

            // Step 6: Post-Solve Validation
            if (listener != null) listener.onProgress(6, 7, "Verifying operational constraints...");
            ScheduleValidator.ScheduleValidationResult valResult = ScheduleValidator.validate(
                    solverResult.schedule(), recResult, config, Collections.emptyList()
            );
            ScheduleValidator.exportReports(valResult, config.getOutputDir());

            // Step 7: Export Production Outputs (Excel, CSVs, and PDFs)
            if (listener != null) listener.onProgress(7, 7, "Exporting Excel, CSVs, and per-class PDF timetables...");
            File studentCsv = new File(config.getOutputDir() + "/student_timetable.csv");
            File teacherCsv = new File(config.getOutputDir() + "/teacher_timetable.csv");
            File excelFile = new File(config.getOutputDir() + "/timetable.xlsx");
            File pdfDir = new File(config.getOutputDir() + "/pdf");

            StudentTimetableCsvExporter.export(solverResult.schedule(), recResult, config, studentCsv.getAbsolutePath());
            TeacherTimetableCsvExporter.export(solverResult.schedule(), recResult, config, teacherCsv.getAbsolutePath());
            ExcelTimetableExporter.export(solverResult.schedule(), recResult, valResult, config, excelFile.getAbsolutePath());
            PdfTimetableExporter.exportAllClasses(solverResult.schedule(), recResult, config, pdfDir);

            if (listener != null) listener.onProgress(7, 7, "Timetable generation and PDF export completed successfully!");

            return new GenerationResult(
                    true,
                    null,
                    solverResult.schedule(),
                    recResult,
                    valResult,
                    config,
                    new File(config.getOutputDir()),
                    excelFile,
                    studentCsv,
                    teacherCsv,
                    pdfDir,
                    solverResult.solveTimeSeconds()
            );

        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new GenerationResult(false, "Timetable generation failed: " + errorMsg,
                    null, null, null, null, null, null, null, null, null, 0.0);
        }
    }

    public static void exportAll(Schedule schedule, AllocationReconciler.ReconciliationResult recResult,
                                 ScheduleValidator.ScheduleValidationResult valResult, SchoolConfig config) throws Exception {
        Path outputDirPath = Paths.get(config.getOutputDir());
        Files.createDirectories(outputDirPath);

        Path studentCsvPath = outputDirPath.resolve("student_timetable.csv");
        Path studentCsvTmp = outputDirPath.resolve("student_timetable.csv.tmp");
        StudentTimetableCsvExporter.export(schedule, recResult, config, studentCsvTmp.toAbsolutePath().toString());
        Files.move(studentCsvTmp, studentCsvPath, StandardCopyOption.REPLACE_EXISTING);

        Path teacherCsvPath = outputDirPath.resolve("teacher_timetable.csv");
        Path teacherCsvTmp = outputDirPath.resolve("teacher_timetable.csv.tmp");
        TeacherTimetableCsvExporter.export(schedule, recResult, config, teacherCsvTmp.toAbsolutePath().toString());
        Files.move(teacherCsvTmp, teacherCsvPath, StandardCopyOption.REPLACE_EXISTING);

        Path excelPath = outputDirPath.resolve("timetable.xlsx");
        Path excelTmp = outputDirPath.resolve("timetable.xlsx.tmp");
        ExcelTimetableExporter.export(schedule, recResult, valResult, config, excelTmp.toAbsolutePath().toString());
        Files.move(excelTmp, excelPath, StandardCopyOption.REPLACE_EXISTING);

        File pdfDir = new File(config.getOutputDir() + "/pdf");
        PdfTimetableExporter.exportAllClasses(schedule, recResult, config, pdfDir);
    }

    public static TimetableGrid getClassGrid(Schedule schedule, SchoolClass sc, AllocationReconciler.ReconciliationResult recResult, SchoolConfig config) {
        ClassTeacherMapping ctm = recResult.classTeacherMappings().get(sc);
        String ctName = (ctm != null && ctm.getClassTeacher() != null) ?
                ctm.getClassTeacher().getFullName() + " (" + ctm.getClassTeacher().getCode() + ")" : "Not Assigned";

        String title = "CLASS: " + sc.getDisplayName();
        String subtitle = "Class Teacher: " + ctName + "   |   Room: Standard " + sc.getStandard() +
                "   |   Total Periods: " + config.getWeeklyRequiredPeriodsPerClass();

        int maxPeriods = config.getMaxDailyPeriods();
        int defaultRecess = config.getRecessAfterPeriod(Day.MONDAY);
        int totalCols = 1 + maxPeriods + 1;

        String[] headers = new String[totalCols];
        headers[0] = "Day";
        for (int p = 1; p <= defaultRecess; p++) {
            Period period = config.getPeriods().get(p);
            String time = period != null ? "\n" + period.getStartTime() + " - " + period.getEndTime() : "";
            headers[p] = "Period " + p + time;
        }
        String recHeaderTime = config.getRecessTiming(Day.MONDAY);
        headers[defaultRecess + 1] = "RECESS" + (recHeaderTime.isBlank() ? "\n09:30 - 09:50" : "\n" + recHeaderTime);
        for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
            Period period = config.getPeriods().get(p);
            String time = period != null ? "\n" + period.getStartTime() + " - " + period.getEndTime() : "";
            headers[p + 1] = "Period " + p + time;
        }

        List<Day> days = config.getDays();
        String[] rowLabels = days.stream().map(Day::getDisplayName).toArray(String[]::new);
        TimetableCell[][] cells = new TimetableCell[days.size()][totalCols - 1];

        for (int d = 0; d < days.size(); d++) {
            Day day = days.get(d);
            int dailyPeriods = config.getDailyPeriods(day);
            int col = 0;

            for (int p = 1; p <= defaultRecess; p++) {
                if (p <= dailyPeriods) {
                    Period period = config.getPeriods().get(p);
                    ScheduleAssignment sa = schedule.getAssignment(sc, day, period);
                    if (sa != null) {
                        cells[d][col++] = new TimetableCell(
                                sa.subject().getNormalizedName(),
                                sa.teacher().getCode(),
                                sa.subject().getNormalizedName() + " (" + sa.teacher().getCode() + ")",
                                false, false, sa.subject().getCategory()
                        );
                    } else {
                        cells[d][col++] = new TimetableCell("---", "", "---", false, true, SubjectCategory.OTHER);
                    }
                } else {
                    cells[d][col++] = new TimetableCell("---", "", "---", false, true, SubjectCategory.OTHER);
                }
            }

            if (config.isRecessEnabled(day)) {
                cells[d][col++] = new TimetableCell("RECESS", "BREAK", "RECESS", true, false, SubjectCategory.OTHER);
            } else {
                cells[d][col++] = new TimetableCell("---", "NO BREAK", "NO BREAK", false, true, SubjectCategory.OTHER);
            }

            for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
                if (p <= dailyPeriods) {
                    Period period = config.getPeriods().get(p);
                    ScheduleAssignment sa = schedule.getAssignment(sc, day, period);
                    if (sa != null) {
                        cells[d][col++] = new TimetableCell(
                                sa.subject().getNormalizedName(),
                                sa.teacher().getCode(),
                                sa.subject().getNormalizedName() + " (" + sa.teacher().getCode() + ")",
                                false, false, sa.subject().getCategory()
                        );
                    } else {
                        cells[d][col++] = new TimetableCell("---", "", "---", false, true, SubjectCategory.OTHER);
                    }
                } else {
                    cells[d][col++] = new TimetableCell("---", "", "---", false, true, SubjectCategory.OTHER);
                }
            }
        }

        return new TimetableGrid(title, subtitle, headers, rowLabels, cells);
    }

    public static TimetableGrid getTeacherGrid(Schedule schedule, Teacher teacher, SchoolConfig config) {
        String title = "TEACHER: " + teacher.getFullName() + " (" + teacher.getCode() + ")";
        String subtitle = "Total Workload: " + teacher.getTotalWorkload() + " Periods / Week   |   Status: Active Faculty";

        int maxPeriods = config.getMaxDailyPeriods();
        int defaultRecess = config.getRecessAfterPeriod(Day.MONDAY);
        int totalCols = 1 + maxPeriods + 1;

        String[] headers = new String[totalCols];
        headers[0] = "Day";
        for (int p = 1; p <= defaultRecess; p++) {
            Period period = config.getPeriods().get(p);
            String time = period != null ? "\n" + period.getStartTime() + " - " + period.getEndTime() : "";
            headers[p] = "Period " + p + time;
        }
        String recTeacherHeader = config.getRecessTiming(Day.MONDAY);
        headers[defaultRecess + 1] = "RECESS" + (recTeacherHeader.isBlank() ? "\n09:30 - 09:50" : "\n" + recTeacherHeader);
        for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
            Period period = config.getPeriods().get(p);
            String time = period != null ? "\n" + period.getStartTime() + " - " + period.getEndTime() : "";
            headers[p + 1] = "Period " + p + time;
        }

        List<Day> days = config.getDays();
        String[] rowLabels = days.stream().map(Day::getDisplayName).toArray(String[]::new);
        TimetableCell[][] cells = new TimetableCell[days.size()][totalCols - 1];

        for (int d = 0; d < days.size(); d++) {
            Day day = days.get(d);
            int dailyPeriods = config.getDailyPeriods(day);
            int col = 0;

            for (int p = 1; p <= defaultRecess; p++) {
                if (p <= dailyPeriods) {
                    Period period = config.getPeriods().get(p);
                    ScheduleAssignment sa = schedule.getTeacherAssignment(teacher, new TimeSlot(day, period));
                    if (sa != null) {
                        cells[d][col++] = new TimetableCell(
                                sa.schoolClass().getCanonicalName(),
                                sa.subject().getNormalizedName(),
                                sa.schoolClass().getCanonicalName() + " (" + sa.subject().getNormalizedName() + ")",
                                false, false, sa.subject().getCategory()
                        );
                    } else {
                        cells[d][col++] = new TimetableCell("FREE", "", "FREE", false, true, SubjectCategory.OTHER);
                    }
                } else {
                    cells[d][col++] = new TimetableCell("FREE", "", "FREE", false, true, SubjectCategory.OTHER);
                }
            }

            if (config.isRecessEnabled(day)) {
                cells[d][col++] = new TimetableCell("RECESS", "BREAK", "RECESS", true, false, SubjectCategory.OTHER);
            } else {
                cells[d][col++] = new TimetableCell("---", "NO BREAK", "NO BREAK", false, true, SubjectCategory.OTHER);
            }

            for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
                if (p <= dailyPeriods) {
                    Period period = config.getPeriods().get(p);
                    ScheduleAssignment sa = schedule.getTeacherAssignment(teacher, new TimeSlot(day, period));
                    if (sa != null) {
                        cells[d][col++] = new TimetableCell(
                                sa.schoolClass().getCanonicalName(),
                                sa.subject().getNormalizedName(),
                                sa.schoolClass().getCanonicalName() + " (" + sa.subject().getNormalizedName() + ")",
                                false, false, sa.subject().getCategory()
                        );
                    } else {
                        cells[d][col++] = new TimetableCell("FREE", "", "FREE", false, true, SubjectCategory.OTHER);
                    }
                } else {
                    cells[d][col++] = new TimetableCell("FREE", "", "FREE", false, true, SubjectCategory.OTHER);
                }
            }
        }

        return new TimetableGrid(title, subtitle, headers, rowLabels, cells);
    }
}
