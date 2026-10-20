package com.sies.timetable.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.AllocationReconciler;
import com.sies.timetable.service.TimetableService;
import com.sies.timetable.solver.CpSatTimetableSolver;
import com.sies.timetable.validator.ScheduleValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight HTTP API Server binding strictly to loopback (127.0.0.1).
 * Serves frontend clients (Vite / Tauri) with zero bloat using Java 21 Virtual Threads.
 * Features asynchronous CP-SAT solver execution, progress streaming, grid inspection, and export downloads.
 */
public class TimetableHttpServer {

    private static final Logger LOGGER = Logger.getLogger(TimetableHttpServer.class.getName());
    private static final String HOST = "127.0.0.1";

    public record SolverJobStatus(
            String status, // IDLE, RUNNING, COMPLETED, FAILED, CANCELLED
            String jobId,
            int step,
            int totalSteps,
            int percent,
            String message,
            double elapsedSeconds,
            boolean isSuccess,
            String error
    ) {}

    private final int requestedPort;
    private HttpServer server;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    // Asynchronous Solver & Studio State
    private final AtomicReference<SolverJobStatus> currentJob = new AtomicReference<>(
            new SolverJobStatus("IDLE", null, 0, 7, 0, "Ready to generate", 0.0, false, null)
    );
    private volatile TimetableService.GenerationResult latestResult = null;
    private volatile boolean scheduleStale = false;
    private volatile Thread runningSolverThread = null;
    private final Object scheduleLock = new Object();
    private final ManualTimetableModel activeModel = new ManualTimetableModel();
    private File activeConfigFile = new File("config/school-config.yaml");

    public TimetableHttpServer(int port) {
        this.requestedPort = port;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.activeModel.loadDefaultSchoolPreset();
    }

    public void setConfigFile(File file) {
        this.activeConfigFile = file;
    }

    public File getConfigFile() {
        return this.activeConfigFile;
    }

    public ManualTimetableModel getActiveModel() {
        return activeModel;
    }

    public void setLatestResultForTesting(TimetableService.GenerationResult result) {
        this.latestResult = result;
        this.scheduleStale = false;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            LOGGER.warning("Server is already running on port " + getPort());
            return;
        }

        server = HttpServer.create(new InetSocketAddress(HOST, requestedPort), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        registerRoutes();

        server.start();
        LOGGER.info("Timetable HTTP API Server started at http://" + HOST + ":" + getPort());
    }

    public synchronized void stop(int delaySeconds) {
        if (server != null) {
            LOGGER.info("Stopping Timetable HTTP API Server...");
            server.stop(delaySeconds);
            server = null;
            scheduler.shutdown();
            stopLatch.countDown();
        }
    }

    public void awaitTermination() throws InterruptedException {
        stopLatch.await();
    }

    public int getPort() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        return requestedPort;
    }

    public String getBaseUrl() {
        return "http://" + HOST + ":" + getPort();
    }

    public void setLatestResult(TimetableService.GenerationResult result) {
        this.latestResult = result;
        this.scheduleStale = false;
    }

    public TimetableService.GenerationResult getLatestResult() {
        return this.latestResult;
    }

    private void registerRoutes() {
        // Phase 1 Core API Endpoints
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/config", new ConfigHandler());
        server.createContext("/api/workspace/detect", new WorkspaceDetectHandler());
        server.createContext("/api/model/preset", new ModelPresetHandler());
        server.createContext("/api/model/validate", new ModelValidateHandler());
        server.createContext("/api/admin/shutdown", new ShutdownHandler());

        // Phase 3 Solver & Timetable Studio Endpoints
        server.createContext("/api/solve/start", new SolveStartHandler());
        server.createContext("/api/solve/status", new SolveStatusHandler());
        server.createContext("/api/solve/cancel", new SolveCancelHandler());
        server.createContext("/api/schedule/summary", new ScheduleSummaryHandler());
        server.createContext("/api/schedule/class", new ScheduleClassHandler());
        server.createContext("/api/schedule/teacher", new ScheduleTeacherHandler());
        server.createContext("/api/schedule/validation", new ScheduleValidationHandler());
        server.createContext("/api/export/download", new ExportDownloadHandler());
        server.createContext("/api/export/open-folder", new ExportOpenFolderHandler());

        // School Setup & Inputs Workspace Endpoints (Batch 1)
        server.createContext("/api/setup/model", new SetupModelHandler());
        server.createContext("/api/setup/reset", new SetupResetHandler());
        server.createContext("/api/setup/validate", new SetupValidateHandler());
        server.createContext("/api/constraints", new ConstraintsHandler());

        // Manual Timetable Editing Endpoints (Batch 2)
        server.createContext("/api/schedule/edit/validate", new ScheduleEditValidateHandler());
        server.createContext("/api/schedule/edit/apply", new ScheduleEditApplyHandler());
    }

    // =========================================================================
    // Core Request Handlers (Phase 1)
    // =========================================================================

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            try {
                Runtime runtime = Runtime.getRuntime();
                Map<String, Object> jvmStats = new LinkedHashMap<>();
                jvmStats.put("version", System.getProperty("java.version"));
                jvmStats.put("vendor", System.getProperty("java.vendor"));
                jvmStats.put("pid", ProcessHandle.current().pid());
                jvmStats.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
                jvmStats.put("availableProcessors", runtime.availableProcessors());
                jvmStats.put("totalMemoryBytes", runtime.totalMemory());
                jvmStats.put("freeMemoryBytes", runtime.freeMemory());
                jvmStats.put("maxMemoryBytes", runtime.maxMemory());

                Map<String, Object> solverInfo = new LinkedHashMap<>();
                solverInfo.put("engine", "Google OR-Tools CP-SAT");
                solverInfo.put("version", "9.8.3296");
                solverInfo.put("status", "AVAILABLE");
                solverInfo.put("authoritative", true);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "UP");
                response.put("service", "SIES APJ School Timetable Generator Engine");
                response.put("apiVersion", "1.0.0");
                response.put("port", getPort());
                response.put("timestamp", System.currentTimeMillis());
                response.put("jvm", jvmStats);
                response.put("solver", solverInfo);

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/health", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                String path = "config/school-config.yaml";
                if (query != null && query.startsWith("path=")) {
                    path = query.substring(5).trim();
                }

                File configFile = new File(path);
                SchoolConfig config;
                boolean loadedFromFile = false;
                if (configFile.exists() && configFile.isFile()) {
                    config = SchoolConfig.load(configFile.getAbsolutePath());
                    loadedFromFile = true;
                } else {
                    config = SchoolConfig.createDefault();
                }

                // Synchronize active parameters if querying default path
                if ("config/school-config.yaml".equalsIgnoreCase(path)) {
                    SchoolConfig activeCfg = activeModel.getStructureConfig();
                    config.setPeriodsPerDay(activeCfg.getPeriodsPerDay());
                    config.setDailyPeriods(activeCfg.getDailyPeriods());
                    config.setRecessAfterPeriod(activeCfg.getRecessAfterPeriod());
                    config.setWeeklyRequiredPeriodsPerClass(activeCfg.getWeeklyRequiredPeriodsPerClass());
                }

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "SUCCESS");
                response.put("configPath", path);
                response.put("loadedFromFile", loadedFromFile);
                response.put("schoolName", config.getSchoolName());
                response.put("academicYear", config.getAcademicYear());
                response.put("days", config.getDays());
                response.put("periodsPerDay", config.getPeriodsPerDay());
                response.put("recessAfterPeriod", config.getRecessAfterPeriod());
                response.put("weeklyRequiredPeriodsPerClass", config.getWeeklyRequiredPeriodsPerClass());
                response.put("dailyPeriods", config.getDailyPeriods());
                response.put("coreAcademicSubjects", config.getCoreAcademicSubjects());
                response.put("activitySubjects", config.getActivitySubjects());
                response.put("specialCurricularSubjects", config.getSpecialCurricularSubjects());
                response.put("labResources", config.getLabResources());
                response.put("outputDir", config.getOutputDir());

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/config", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private class WorkspaceDetectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) && !"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET, POST");
                return;
            }

            try {
                String targetPath = ".";
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body = readBody(exchange);
                    if (!body.isBlank()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> req = objectMapper.readValue(body, Map.class);
                        if (req.containsKey("path") && req.get("path") != null) {
                            targetPath = req.get("path").toString().trim();
                        }
                    }
                }

                File folder = new File(targetPath);
                TimetableService.FolderDetectionResult result = TimetableService.detectInputFiles(folder);

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("isValid", result.isValid());
                details.put("folder", result.folder() != null ? result.folder().getAbsolutePath() : null);
                details.put("standardwiseFile", result.standardwiseFile() != null ? result.standardwiseFile().getAbsolutePath() : null);
                details.put("teacherwiseFile", result.teacherwiseFile() != null ? result.teacherwiseFile().getAbsolutePath() : null);
                details.put("classTeachersFile", result.classTeachersFile() != null ? result.classTeachersFile().getAbsolutePath() : null);
                details.put("constraintsFile", result.constraintsFile() != null ? result.constraintsFile().getAbsolutePath() : null);
                details.put("statusMessage", result.statusMessage());
                details.put("missingFiles", result.missingFiles());

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "SUCCESS");
                response.put("detection", details);

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/workspace/detect", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private class ModelPresetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            try {
                ManualTimetableModel model = new ManualTimetableModel();
                boolean loaded = model.loadDefaultSchoolPreset();

                Map<String, Object> preset = new LinkedHashMap<>();
                preset.put("loaded", loaded);
                preset.put("name", "SIES APJ School Standard 2026-27");
                preset.put("startStandard", model.getStartStandard());
                preset.put("endStandard", model.getEndStandard());

                List<Integer> standardsList = new ArrayList<>(model.getStandardDivisionsCount().keySet());
                preset.put("standards", standardsList);

                Map<Integer, Integer> divisions = new LinkedHashMap<>();
                for (int s : standardsList) {
                    divisions.put(s, model.getDivisionsCount(s));
                }
                preset.put("divisions", divisions);
                preset.put("totalClasses", model.getGeneratedClasses().size());
                preset.put("totalSubjects", model.getSubjects().size());
                preset.put("totalProfessors", model.getProfessors().size());
                preset.put("weeklyRequiredPeriodsPerClass", model.getWeeklyRequiredPeriodsPerClass());
                preset.put("recessAfterPeriod", model.getRecessAfterPeriod());

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "SUCCESS");
                response.put("preset", preset);

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/model/preset", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private class ModelValidateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            try {
                String targetPath = ".";
                String body = readBody(exchange);
                if (!body.isBlank()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body, Map.class);
                    if (req.containsKey("folder") && req.get("folder") != null) {
                        targetPath = req.get("folder").toString().trim();
                    }
                }

                File folder = new File(targetPath);
                TimetableService.FolderDetectionResult detection = TimetableService.detectInputFiles(folder);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "SUCCESS");
                response.put("isValid", detection.isValid());
                response.put("statusMessage", detection.statusMessage());
                response.put("missingFiles", detection.missingFiles());

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/model/validate", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private class ShutdownHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
                Map<String, Object> forbidden = Map.of(
                        "status", "FORBIDDEN",
                        "error", "Shutdown endpoint is restricted strictly to loopback callers."
                );
                sendJsonResponse(exchange, 403, forbidden);
                return;
            }

            Map<String, Object> response = Map.of(
                    "status", "SHUTTING_DOWN",
                    "message", "Timetable HTTP API Server is terminating gracefully."
            );
            sendJsonResponse(exchange, 200, response);

            scheduler.schedule(() -> {
                LOGGER.info("Shutdown initiated by administrative API request.");
                stop(0);
            }, 300, TimeUnit.MILLISECONDS);
        }
    }

    // =========================================================================
    // Phase 3 Solver & Studio Request Handlers
    // =========================================================================

    private class SolveStartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            SolverJobStatus activeJob = currentJob.get();
            if ("RUNNING".equalsIgnoreCase(activeJob.status())) {
                sendJsonResponse(exchange, 409, Map.of(
                        "status", "BUSY",
                        "message", "A timetable solver job is already actively running."
                ));
                return;
            }

            String mode = "preset";
            String folderPath = ".";
            String body = readBody(exchange);
            if (!body.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body, Map.class);
                    if (req.containsKey("mode") && req.get("mode") != null) {
                        mode = req.get("mode").toString().trim();
                    }
                    if (req.containsKey("folder") && req.get("folder") != null) {
                        folderPath = req.get("folder").toString().trim();
                    }
                } catch (Exception ignored) {}
            }

            final String finalMode = mode;
            final String finalFolderPath = folderPath;
            final String jobId = "job-" + System.currentTimeMillis();
            final long startTime = System.currentTimeMillis();

            currentJob.set(new SolverJobStatus(
                    "RUNNING", jobId, 1, 7, 14,
                    "Loading configuration and constraint specifications...",
                    0.0, false, null
            ));

            runningSolverThread = Thread.ofVirtual().name("solver-" + jobId).start(() -> {
                try {
                    TimetableService.ProgressListener listener = (step, total, msg) -> {
                        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                        int pct = (step * 100) / total;
                        currentJob.set(new SolverJobStatus("RUNNING", jobId, step, total, pct, msg, elapsed, false, null));
                    };

                    TimetableService.GenerationResult result;
                    if ("workspace".equalsIgnoreCase(finalMode)) {
                        File folder = new File(finalFolderPath);
                        TimetableService.FolderDetectionResult detection = TimetableService.detectInputFiles(folder);
                        if (!detection.isValid()) {
                            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                            currentJob.set(new SolverJobStatus(
                                    "FAILED", jobId, 1, 7, 0,
                                    "Missing required files: " + String.join(", ", detection.missingFiles()),
                                    elapsed, false, "Input files missing"
                            ));
                            return;
                        }
                        result = TimetableService.generate(detection, listener);
                    } else {
                        // Solve authoritative active configured model (preserves manual setup edits)
                        result = TimetableService.generate(activeModel, listener);
                    }

                    double finalElapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                    if (result.isSuccess()) {
                        latestResult = result;
                        scheduleStale = false;
                        currentJob.set(new SolverJobStatus(
                                "COMPLETED", jobId, 7, 7, 100,
                                "Timetable generation complete with 0 hard constraint violations!",
                                finalElapsed, true, null
                        ));
                    } else {
                        currentJob.set(new SolverJobStatus(
                                "FAILED", jobId, 7, 7, 100,
                                result.errorMessage(), finalElapsed, false, result.errorMessage()
                        ));
                    }
                } catch (Exception e) {
                    double finalElapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                    currentJob.set(new SolverJobStatus(
                            "FAILED", jobId, 7, 7, 100,
                            "Solver failed: " + e.getMessage(), finalElapsed, false, e.getMessage()
                    ));
                } finally {
                    runningSolverThread = null;
                }
            });

            sendJsonResponse(exchange, 202, Map.of(
                    "status", "ACCEPTED",
                    "jobId", jobId,
                    "message", "Timetable generation started asynchronously."
            ));
        }
    }

    private class SolveStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            sendJsonResponse(exchange, 200, currentJob.get());
        }
    }

    private class SolveCancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            if (runningSolverThread != null) {
                runningSolverThread.interrupt();
                CpSatTimetableSolver.cancelActiveSolve();
                SolverJobStatus job = currentJob.get();
                currentJob.set(new SolverJobStatus(
                        "CANCELLED", job.jobId(), job.step(), 7, job.percent(),
                        "Solver interrupted by user cancellation.", job.elapsedSeconds(), false, "User cancelled"
                ));
                sendJsonResponse(exchange, 200, Map.of("status", "CANCELLED", "message", "Solver cancellation requested."));
            } else {
                sendJsonResponse(exchange, 200, Map.of("status", "IDLE", "message", "No active solver job running."));
            }
        }
    }

    private class ScheduleSummaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            try {
                if (latestResult == null || !latestResult.isSuccess()) {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("status", "SUCCESS");
                    resp.put("available", false);
                    resp.put("isStale", scheduleStale);
                    resp.put("message", "No generated timetable is currently available in memory. Trigger generation first.");
                    sendJsonResponse(exchange, 200, resp);
                    return;
                }

                List<String> classes = (latestResult.reconciliationResult() != null && latestResult.reconciliationResult().classes() != null)
                        ? latestResult.reconciliationResult().classes().stream().map(SchoolClass::getCanonicalName).toList()
                        : List.of();

                List<Map<String, Object>> teachers = (latestResult.reconciliationResult() != null && latestResult.reconciliationResult().teachers() != null)
                        ? latestResult.reconciliationResult().teachers().stream().map(t -> {
                            Map<String, Object> map = new LinkedHashMap<>();
                            map.put("code", t.getCode());
                            map.put("name", t.getFullName());
                            map.put("totalWorkload", t.getTotalWorkload());
                            map.put("isFreeTeacher", t.isFreeTeacher());
                            return map;
                        }).toList()
                        : List.of();

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "SUCCESS");
                response.put("available", true);
                response.put("isStale", scheduleStale);
                response.put("solveTimeSeconds", latestResult.solveTimeSeconds());
                response.put("qualityIndex", latestResult.validationResult() != null ? latestResult.validationResult().overallQualityIndex() : 100.0);
                response.put("allHardConstraintsPassed", latestResult.validationResult() != null && latestResult.validationResult().allHardConstraintsPassed());
                response.put("totalHardViolations", latestResult.validationResult() != null ? latestResult.validationResult().totalHardViolations() : 0);
                response.put("totalSoftPenalty", latestResult.validationResult() != null ? latestResult.validationResult().totalSoftPenalty() : 0);
                response.put("totalClasses", classes.size());
                response.put("classes", classes);
                response.put("totalTeachers", teachers.size());
                response.put("teachers", teachers);
                Map<String, Object> outputFiles = new LinkedHashMap<>();
                outputFiles.put("excelPath", latestResult.excelFile() != null ? latestResult.excelFile().getPath() : null);
                outputFiles.put("studentCsvPath", latestResult.studentCsvFile() != null ? latestResult.studentCsvFile().getPath() : null);
                outputFiles.put("teacherCsvPath", latestResult.teacherCsvFile() != null ? latestResult.teacherCsvFile().getPath() : null);
                outputFiles.put("pdfDir", latestResult.pdfDir() != null ? latestResult.pdfDir().getPath() : null);
                response.put("outputFiles", outputFiles);

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/schedule/summary", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private class ScheduleClassHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            if (latestResult == null || !latestResult.isSuccess()) {
                sendJsonResponse(exchange, 404, Map.of(
                        "status", "NOT_FOUND",
                        "error", "No generated timetable is available. Please run generation first."
                ));
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String classId = "5-A";
            if (query != null && query.contains("id=")) {
                for (String part : query.split("&")) {
                    if (part.startsWith("id=")) {
                        classId = part.substring(3).trim();
                        break;
                    }
                }
            }

            SchoolClass targetClass = null;
            for (SchoolClass c : latestResult.reconciliationResult().classes()) {
                if (c.getCanonicalName().equalsIgnoreCase(classId)
                        || c.getDisplayName().equalsIgnoreCase(classId)
                        || c.getStandardDivisionName().equalsIgnoreCase(classId)) {
                    targetClass = c;
                    break;
                }
            }

            if (targetClass == null && !latestResult.reconciliationResult().classes().isEmpty()) {
                targetClass = latestResult.reconciliationResult().classes().get(0);
            }

            if (targetClass == null) {
                sendJsonResponse(exchange, 404, Map.of("status", "NOT_FOUND", "error", "Class " + classId + " not found."));
                return;
            }

            TimetableService.TimetableGrid grid = TimetableService.getClassGrid(
                    latestResult.schedule(), targetClass, latestResult.reconciliationResult(), latestResult.config()
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "SUCCESS");
            response.put("classId", targetClass.getCanonicalName());
            response.put("grid", grid);

            sendJsonResponse(exchange, 200, response);
        }
    }

    private class ScheduleTeacherHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            if (latestResult == null || !latestResult.isSuccess()) {
                sendJsonResponse(exchange, 404, Map.of(
                        "status", "NOT_FOUND",
                        "error", "No generated timetable is available. Please run generation first."
                ));
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String teacherCode = null;
            if (query != null && query.contains("code=")) {
                for (String part : query.split("&")) {
                    if (part.startsWith("code=")) {
                        teacherCode = part.substring(5).trim();
                        break;
                    }
                }
            }

            Teacher targetTeacher = null;
            if (teacherCode != null && !teacherCode.isBlank()) {
                for (Teacher t : latestResult.reconciliationResult().teachers()) {
                    if (t.getCode().equalsIgnoreCase(teacherCode) || t.getFullName().equalsIgnoreCase(teacherCode)) {
                        targetTeacher = t;
                        break;
                    }
                }
            }

            if (targetTeacher == null && !latestResult.reconciliationResult().teachers().isEmpty()) {
                targetTeacher = latestResult.reconciliationResult().teachers().get(0);
            }

            if (targetTeacher == null) {
                sendJsonResponse(exchange, 404, Map.of("status", "NOT_FOUND", "error", "Teacher " + teacherCode + " not found."));
                return;
            }

            TimetableService.TimetableGrid grid = TimetableService.getTeacherGrid(
                    latestResult.schedule(), targetTeacher, latestResult.config()
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "SUCCESS");
            response.put("teacherCode", targetTeacher.getCode());
            response.put("grid", grid);

            sendJsonResponse(exchange, 200, response);
        }
    }

    private class ScheduleValidationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            if (latestResult == null || latestResult.validationResult() == null) {
                sendJsonResponse(exchange, 404, Map.of(
                        "status", "NOT_FOUND",
                        "error", "No validation diagnostics available. Generate a timetable first."
                ));
                return;
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "SUCCESS");
            response.put("validation", latestResult.validationResult());

            sendJsonResponse(exchange, 200, response);
        }
    }

    private class ExportDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            if (latestResult == null || !latestResult.isSuccess()) {
                sendJsonResponse(exchange, 404, Map.of("status", "NOT_FOUND", "error", "No generated schedule output available."));
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String type = "excel";
            String classId = "5-A";
            if (query != null) {
                for (String part : query.split("&")) {
                    if (part.startsWith("type=")) type = part.substring(5).trim();
                    if (part.startsWith("class=")) classId = part.substring(6).trim();
                }
            }

            File targetFile = null;
            String contentType = "application/octet-stream";

            switch (type.toLowerCase()) {
                case "excel" -> {
                    targetFile = latestResult.excelFile();
                    contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                }
                case "student_csv" -> {
                    targetFile = latestResult.studentCsvFile();
                    contentType = "text/csv; charset=UTF-8";
                }
                case "teacher_csv" -> {
                    targetFile = latestResult.teacherCsvFile();
                    contentType = "text/csv; charset=UTF-8";
                }
                case "pdf" -> {
                    if (latestResult.pdfDir() != null && latestResult.pdfDir().exists()) {
                        File classPdf = new File(latestResult.pdfDir(), classId + "_Timetable.pdf");
                        if (!classPdf.exists()) {
                            classPdf = new File(latestResult.pdfDir(), "Class_" + classId + "_Timetable.pdf");
                        }
                        if (classPdf.exists()) {
                            targetFile = classPdf;
                        } else {
                            File[] pdfs = latestResult.pdfDir().listFiles((d, name) -> name.endsWith(".pdf"));
                            if (pdfs != null && pdfs.length > 0) targetFile = pdfs[0];
                        }
                    }
                    contentType = "application/pdf";
                }
            }

            if (targetFile == null || !targetFile.exists() || !targetFile.isFile()) {
                sendJsonResponse(exchange, 404, Map.of(
                        "status", "NOT_FOUND",
                        "error", "Requested export file does not exist on disk: " + (targetFile != null ? targetFile.getName() : type)
                ));
                return;
            }

            setCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + targetFile.getName() + "\"");

            byte[] bytes = Files.readAllBytes(targetFile.toPath());
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class ExportOpenFolderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            File outputDir = latestResult != null ? latestResult.outputDir() : new File("output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            boolean opened = false;
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(outputDir);
                    opened = true;
                }
            } catch (Exception ignored) {}

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "SUCCESS");
            response.put("opened", opened);
            response.put("path", outputDir.getAbsolutePath());

            sendJsonResponse(exchange, 200, response);
        }
    }

    // =========================================================================
    // School Setup & Inputs Workspace Request Handlers (Batch 1)
    // =========================================================================

    private Map<String, Object> buildModelDto(ManualTimetableModel model) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("startStandard", model.getStartStandard());
        dto.put("endStandard", model.getEndStandard());
        dto.put("standardDivisions", model.getStandardDivisionsCount());

        List<String> classes = model.getGeneratedClasses().stream()
                .map(SchoolClass::getStandardDivisionName)
                .toList();
        dto.put("classes", classes);

        List<Map<String, Object>> subjectsList = new ArrayList<>();
        for (Subject s : model.getSubjects()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("code", s.getNormalizedName());
            sm.put("name", s.getName());
            sm.put("category", s.getCategory() != null ? s.getCategory().name() : "CORE_ACADEMIC");
            sm.put("isLab", s.isRequiresLab());
            subjectsList.add(sm);
        }
        dto.put("subjects", subjectsList);

        List<Map<String, Object>> profsList = new ArrayList<>();
        for (ManualTimetableModel.ProfessorEntry p : model.getProfessors()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("code", p.getCode());
            pm.put("name", p.getName());
            pm.put("freeTeacher", p.isFreeTeacher());
            pm.put("classTeacherOf", p.getClassTeacherOf() != null ? p.getClassTeacherOf().getStandardDivisionName() : null);
            pm.put("totalWorkload", p.calculateTotalWorkload());

            List<Map<String, Object>> allocs = new ArrayList<>();
            for (ManualTimetableModel.AllocationEntry a : p.getAllocations()) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("subjectCode", a.getSubject().getNormalizedName());
                am.put("subjectName", a.getSubject().getName());
                am.put("weeklyLectures", a.getWeeklyLectures());
                List<String> targetCls = a.getTargetClasses().stream()
                        .map(SchoolClass::getStandardDivisionName)
                        .toList();
                am.put("classes", targetCls);
                allocs.add(am);
            }
            pm.put("allocations", allocs);
            profsList.add(pm);
        }
        dto.put("professors", profsList);

        Map<String, Object> bell = new LinkedHashMap<>();
        List<String> days = model.getDays().stream().map(Enum::name).toList();
        bell.put("days", days);
        bell.put("periodsPerDay", model.getPeriodsPerDay());
        bell.put("weeklyRequiredPeriodsPerClass", model.getWeeklyRequiredPeriodsPerClass());
        bell.put("recessAfterPeriod", model.getRecessAfterPeriod());
        Map<String, Integer> dailyPeriods = new LinkedHashMap<>();
        Map<String, Integer> recessOverrides = new LinkedHashMap<>();
        Map<String, Boolean> dailyRecessEnabled = new LinkedHashMap<>();
        Map<String, Integer> dailyRecessDurations = new LinkedHashMap<>();
        for (Day d : model.getDays()) {
            dailyPeriods.put(d.name(), model.getDailyPeriod(d));
            recessOverrides.put(d.name(), model.getDailyRecessPosition(d));
            dailyRecessEnabled.put(d.name(), model.isRecessEnabled(d));
            dailyRecessDurations.put(d.name(), model.getDailyRecessDuration(d));
        }
        bell.put("dailyPeriods", dailyPeriods);
        bell.put("recessOverrides", recessOverrides);
        bell.put("dailyRecessEnabled", dailyRecessEnabled);
        bell.put("dailyRecessAfterPeriod", recessOverrides);
        bell.put("dailyRecessDurations", dailyRecessDurations);
        bell.put("saturdayEnabled", days.contains("SATURDAY"));
        bell.put("schoolStartTime", model.getSchoolStartTime());
        bell.put("saturdayStartTime", model.getSaturdayStartTime());
        bell.put("defaultPeriodDuration", model.getDefaultPeriodDuration());
        bell.put("passingTime", model.getPassingTime());
        bell.put("recessDuration", model.getRecessDuration());
        bell.put("shortBreakEnabled", model.isShortBreakEnabled());
        bell.put("shortBreakAfterPeriod", model.getShortBreakAfterPeriod());
        bell.put("shortBreakDuration", model.getShortBreakDuration());
        bell.put("periodDurationOverrides", model.getPeriodDurationOverrides());
        bell.put("periodTimeOverrides", model.getPeriodTimeOverrides());

        SchoolConfig tmpCfg = model.buildSchoolConfig();
        Map<String, Object> computedTimings = new LinkedHashMap<>();
        computedTimings.put("weekday", tmpCfg.computePeriodTimings(Day.MONDAY));
        computedTimings.put("saturday", tmpCfg.computePeriodTimings(Day.SATURDAY));
        computedTimings.put("recessWeekday", tmpCfg.getRecessTiming(Day.MONDAY));
        computedTimings.put("recessSaturday", tmpCfg.getRecessTiming(Day.SATURDAY));
        bell.put("computedTimings", computedTimings);

        dto.put("bellSchedule", bell);
        dto.put("constraints", buildConstraintsDto(model));

        return dto;
    }

    private Map<String, Object> buildConstraintsDto(ManualTimetableModel model) {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> hc = new LinkedHashMap<>();
        addHardConstraintInfo(hc, "H1", "Class Teacher First Period Allocation",
                "Prioritizes assigning class teachers to their designated class during the first morning instructional period (Period 1).",
                false, model.isHardConstraintEnabled("H1"), null);

        Map<String, Object> h2Params = new LinkedHashMap<>();
        h2Params.put("maxContinuousLectures", model.getH2MaxContinuousLectures());
        addHardConstraintInfo(hc, "H2", "Teacher Continuous Lecture Limit",
                "Restricts consecutive teaching periods before a recess break to prevent teacher fatigue.",
                false, model.isHardConstraintEnabled("H2"), h2Params);

        addHardConstraintInfo(hc, "H3", "Class Slot Capacity & Integrity",
                "Non-negotiable schedule integrity rule: exactly one subject and teacher must occupy every instructional class slot.",
                true, true, null);

        addHardConstraintInfo(hc, "H4", "No Teacher Double-Booking",
                "Non-negotiable physical impossibility constraint: no teacher may be assigned to multiple classrooms simultaneously.",
                true, true, null);

        Map<String, Object> h5Params = new LinkedHashMap<>();
        h5Params.put("maxConcurrentLabClasses", model.getH5MaxConcurrentLabClasses());
        addHardConstraintInfo(hc, "H5", "Computer Lab Exclusivity",
                "Resource bottleneck rule: caps simultaneous computer sessions to the physical capacity of the school lab.",
                false, model.isHardConstraintEnabled("H5"), h5Params);

        Map<String, Object> h6Params = new LinkedHashMap<>();
        h6Params.put("prohibitedPeriod", model.getH6ProhibitedPeriod());
        addHardConstraintInfo(hc, "H6", "Physical Education Period Prohibition",
                "Protective scheduling rule: prevents strenuous outdoor physical education during the specified school period.",
                false, model.isHardConstraintEnabled("H6"), h6Params);

        addHardConstraintInfo(hc, "H7", "Physical Education Post-Recess Prohibition",
                "Health & safety rule: strictly prohibits physical education immediately after the recess/lunch break.",
                false, model.isHardConstraintEnabled("H7"), null);

        Map<String, Object> h8Params = new LinkedHashMap<>();
        h8Params.put("maxActivityPeriodsPerDay", model.getH8MaxActivityPeriodsPerDay());
        addHardConstraintInfo(hc, "H8", "Maximum Daily Activity Periods",
                "Curriculum balance rule: limits total non-academic activity periods (P.E., Computer, Art, Craft, Music, Library) scheduled per class per day.",
                false, model.isHardConstraintEnabled("H8"), h8Params);

        addHardConstraintInfo(hc, "H9", "Exact Curriculum Allotment Fulfillment",
                "Non-negotiable syllabus requirement: every approved teaching allocation must be scheduled exactly to its approved weekly period count.",
                true, true, null);

        addHardConstraintInfo(hc, "H10", "P.E. & Computer Recess Partition",
                "Fatigue management rule: partitions physical education and computer lab sessions across recess on days when both occur.",
                false, model.isHardConstraintEnabled("H10"), null);

        root.put("hardConstraints", hc);

        Map<String, Object> sc = new LinkedHashMap<>();
        addSoftConstraintInfo(sc, "S1", "Even Weekly Subject Distribution",
                "Evenly spaces subject lectures across days of the week rather than bunching them consecutively.",
                model.isSoftConstraintEnabled("S1"), model.getSoftConstraintWeight("S1", 100), 100);

        addSoftConstraintInfo(sc, "S2", "Non-Consecutive Days for 2-Period Subjects",
                "Separates 2-period subjects by at least one intervening day for optimal pedagogical retention.",
                model.isSoftConstraintEnabled("S2"), model.getSoftConstraintWeight("S2", 80), 80);

        addSoftConstraintInfo(sc, "S3", "Core Subjects Morning Placement",
                "Prefers placing core academic subjects (Maths, Science, English) in morning high-attention periods.",
                model.isSoftConstraintEnabled("S3"), model.getSoftConstraintWeight("S3", 50), 50);

        addSoftConstraintInfo(sc, "S4", "Teacher Daily Workload Balance",
                "Minimizes variance in daily teaching periods per teacher across the working week.",
                model.isSoftConstraintEnabled("S4"), model.getSoftConstraintWeight("S4", 40), 40);

        addSoftConstraintInfo(sc, "S5", "Preferred Teacher Time Window",
                "Minimizes idle waiting gaps and promotes compact instructional blocks in teacher timetables.",
                model.isSoftConstraintEnabled("S5"), model.getSoftConstraintWeight("S5", 30), 30);

        addSoftConstraintInfo(sc, "S6", "Activity Subject Afternoon Placement",
                "Prefers scheduling co-curricular arts, music, and craft activities during afternoon sessions.",
                model.isSoftConstraintEnabled("S6"), model.getSoftConstraintWeight("S6", 20), 20);

        root.put("softConstraints", sc);
        return root;
    }

    private void addHardConstraintInfo(Map<String, Object> target, String id, String name, String desc,
                                       boolean mandatory, boolean enabled, Map<String, Object> extraParams) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", id);
        info.put("name", name);
        info.put("description", desc);
        info.put("mandatory", mandatory);
        info.put("enabled", mandatory || enabled);
        if (extraParams != null) {
            info.putAll(extraParams);
        }
        target.put(id, info);
    }

    private void addSoftConstraintInfo(Map<String, Object> target, String id, String name, String desc,
                                       boolean enabled, int weight, int defaultWeight) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", id);
        info.put("name", name);
        info.put("description", desc);
        info.put("enabled", enabled);
        info.put("weight", weight);
        info.put("defaultWeight", defaultWeight);
        target.put(id, info);
    }

    private void applyConstraintsMap(Map<?, ?> cMap, ManualTimetableModel model) {
        if (cMap == null) return;

        // Hard constraints
        Object hcObj = cMap.get("hardConstraints");
        if (hcObj == null) hcObj = cMap.get("hard_constraints");
        if (hcObj instanceof Map<?, ?> hcMap) {
            for (Map.Entry<?, ?> e : hcMap.entrySet()) {
                String id = e.getKey().toString().trim().toUpperCase();
                if (e.getValue() instanceof Map<?, ?> em) {
                    if (em.containsKey("enabled")) {
                        boolean en = Boolean.parseBoolean(em.get("enabled").toString());
                        model.setHardConstraintEnabled(id, en);
                    }
                    if ("H2".equalsIgnoreCase(id) && em.containsKey("maxContinuousLectures")) {
                        model.setH2MaxContinuousLectures(((Number) em.get("maxContinuousLectures")).intValue());
                    } else if ("H2".equalsIgnoreCase(id) && em.containsKey("max_continuous_lectures")) {
                        model.setH2MaxContinuousLectures(((Number) em.get("max_continuous_lectures")).intValue());
                    }
                    if ("H5".equalsIgnoreCase(id) && em.containsKey("maxConcurrentLabClasses")) {
                        model.setH5MaxConcurrentLabClasses(((Number) em.get("maxConcurrentLabClasses")).intValue());
                    } else if ("H5".equalsIgnoreCase(id) && em.containsKey("max_concurrent_lab_classes")) {
                        model.setH5MaxConcurrentLabClasses(((Number) em.get("max_concurrent_lab_classes")).intValue());
                    }
                    if ("H6".equalsIgnoreCase(id) && em.containsKey("prohibitedPeriod")) {
                        model.setH6ProhibitedPeriod(((Number) em.get("prohibitedPeriod")).intValue());
                    } else if ("H6".equalsIgnoreCase(id) && em.containsKey("prohibited_period")) {
                        model.setH6ProhibitedPeriod(((Number) em.get("prohibited_period")).intValue());
                    }
                    if ("H8".equalsIgnoreCase(id) && em.containsKey("maxActivityPeriodsPerDay")) {
                        model.setH8MaxActivityPeriodsPerDay(((Number) em.get("maxActivityPeriodsPerDay")).intValue());
                    } else if ("H8".equalsIgnoreCase(id) && em.containsKey("max_activity_periods_per_day")) {
                        model.setH8MaxActivityPeriodsPerDay(((Number) em.get("max_activity_periods_per_day")).intValue());
                    }
                } else if (e.getValue() instanceof Boolean b) {
                    model.setHardConstraintEnabled(id, b);
                }
            }
        }

        // Soft constraints
        Object scObj = cMap.get("softConstraints");
        if (scObj == null) scObj = cMap.get("soft_constraints");
        if (scObj instanceof Map<?, ?> scMap) {
            for (Map.Entry<?, ?> e : scMap.entrySet()) {
                String id = e.getKey().toString().trim().toUpperCase();
                if (e.getValue() instanceof Map<?, ?> em) {
                    if (em.containsKey("enabled")) {
                        model.setSoftConstraintEnabled(id, Boolean.parseBoolean(em.get("enabled").toString()));
                    }
                    if (em.containsKey("weight")) {
                        model.setSoftConstraintWeight(id, ((Number) em.get("weight")).intValue());
                    }
                } else if (e.getValue() instanceof Boolean b) {
                    model.setSoftConstraintEnabled(id, b);
                }
            }
        }

        // Backward-compatible weights
        if (cMap.containsKey("weights") && cMap.get("weights") instanceof Map<?, ?> wMap) {
            for (Map.Entry<?, ?> e : wMap.entrySet()) {
                try {
                    String id = e.getKey().toString().trim().toUpperCase();
                    int w = ((Number) e.getValue()).intValue();
                    model.setSoftConstraintWeight(id, w);
                } catch (Exception ignored) {}
            }
        }
    }

    private class ConstraintsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            try {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "SUCCESS");
                    response.put("constraints", buildConstraintsDto(activeModel));
                    sendJsonResponse(exchange, 200, response);
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body = readBody(exchange);
                    if (!body.isBlank()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = objectMapper.readValue(body, Map.class);
                        applyConstraintsMap(payload, activeModel);
                        if (activeConfigFile != null) {
                            activeModel.persistScheduleToConfigFile(activeConfigFile);
                        }
                        scheduleStale = true;
                    }
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "SUCCESS");
                    response.put("message", "Constraints configuration updated successfully.");
                    response.put("constraints", buildConstraintsDto(activeModel));
                    sendJsonResponse(exchange, 200, response);
                } else {
                    sendMethodNotAllowed(exchange, "GET, POST");
                }
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "Bad request in /api/constraints: " + e.getMessage());
                sendJsonResponse(exchange, 400, Map.of(
                        "status", "ERROR",
                        "error", e.getMessage()
                ));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/constraints", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private Map<String, Object> buildValidationDto(ManualTimetableModel.ValidationReport rep) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("isValid", rep.isValid());
        v.put("errors", rep.errors());
        v.put("warnings", rep.warnings());
        v.put("notices", rep.notices());
        v.put("totalAvailablePeriods", rep.totalAvailablePeriods());
        v.put("weeklyRequiredPeriods", rep.weeklyRequiredPeriods());
        v.put("totalClasses", rep.totalClasses());
        v.put("totalProfessors", rep.totalProfessors());
        v.put("totalSubjects", rep.totalSubjects());
        return v;
    }

    private class SetupModelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            String method = exchange.getRequestMethod().toUpperCase();

            try {
                if ("GET".equals(method)) {
                    ManualTimetableModel.ValidationReport rep = activeModel.validate();
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "SUCCESS");
                    response.put("model", buildModelDto(activeModel));
                    response.put("validation", buildValidationDto(rep));
                    sendJsonResponse(exchange, 200, response);
                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    if (body.isBlank()) {
                        sendBadRequest(exchange, "Empty request body");
                        return;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                    // 1. Academic Structure
                    if (payload.containsKey("startStandard") && payload.containsKey("endStandard")) {
                        int start = ((Number) payload.get("startStandard")).intValue();
                        int end = ((Number) payload.get("endStandard")).intValue();
                        activeModel.setStandardRange(start, end);
                    }

                    if (payload.containsKey("standardDivisions") && payload.get("standardDivisions") instanceof Map<?, ?> divMap) {
                        for (Map.Entry<?, ?> entry : divMap.entrySet()) {
                            int std = Integer.parseInt(entry.getKey().toString());
                            int count = ((Number) entry.getValue()).intValue();
                            activeModel.setDivisionsCount(std, count);
                        }
                    }

                    // 2. Bell Schedule
                    if (payload.containsKey("bellSchedule") && payload.get("bellSchedule") instanceof Map<?, ?> bellMap) {
                        if (bellMap.containsKey("days") && bellMap.get("days") instanceof List<?> dayList) {
                            List<Day> newDays = new ArrayList<>();
                            for (Object dObj : dayList) {
                                try {
                                    newDays.add(Day.valueOf(dObj.toString().trim().toUpperCase()));
                                } catch (Exception ignored) {}
                            }
                            if (!newDays.isEmpty()) {
                                activeModel.setDays(newDays);
                            }
                        }

                        int previousPpd = activeModel.getPeriodsPerDay();
                        boolean hasPpd = bellMap.containsKey("periodsPerDay");
                        int ppd = hasPpd ? ((Number) bellMap.get("periodsPerDay")).intValue() : previousPpd;
                        if (hasPpd) {
                            if (ppd < 1 || ppd > 12) {
                                throw new IllegalArgumentException("Teaching periods per day must be between 1 and 12 (received " + ppd + ").");
                            }
                            activeModel.setPeriodsPerDay(ppd);
                        }

                        if (bellMap.containsKey("dailyPeriods") && bellMap.get("dailyPeriods") instanceof Map<?, ?> dpMap) {
                            boolean isUniformDp = true;
                            Integer uniformVal = null;
                            for (Object v : dpMap.values()) {
                                if (v instanceof Number n) {
                                    int val = n.intValue();
                                    if (uniformVal == null) {
                                        uniformVal = val;
                                    } else if (uniformVal != val) {
                                        isUniformDp = false;
                                        break;
                                    }
                                }
                            }

                            if (!hasPpd || !isUniformDp || (uniformVal != null && uniformVal == ppd)) {
                                for (Map.Entry<?, ?> entry : dpMap.entrySet()) {
                                    try {
                                        Day d = Day.valueOf(entry.getKey().toString().trim().toUpperCase());
                                        int count = ((Number) entry.getValue()).intValue();
                                        if (count < 0 || count > 12) {
                                            throw new IllegalArgumentException("Daily periods for " + d + " must be between 0 and 12 (received " + count + ").");
                                        }
                                        activeModel.setDailyPeriod(d, count);
                                    } catch (IllegalArgumentException e) {
                                        throw e;
                                    } catch (Exception ignored) {}
                                }
                            }
                        }

                        if (bellMap.containsKey("weeklyRequiredPeriodsPerClass")) {
                            activeModel.setWeeklyRequiredPeriodsPerClass(((Number) bellMap.get("weeklyRequiredPeriodsPerClass")).intValue());
                        }

                        if (bellMap.containsKey("recessAfterPeriod")) {
                            int rec = ((Number) bellMap.get("recessAfterPeriod")).intValue();
                            int minPpd = activeModel.getDays().stream()
                                    .mapToInt(activeModel::getDailyPeriod)
                                    .filter(p -> p > 0)
                                    .min().orElse(activeModel.getPeriodsPerDay());
                            if (rec < 1 || rec > minPpd) {
                                throw new IllegalArgumentException(String.format(
                                        "Recess placement (%d) must be between period 1 and %d.",
                                        rec, minPpd
                                ));
                            }
                            activeModel.setRecessAfterPeriod(rec);
                        }

                        if (bellMap.containsKey("recessOverrides") && bellMap.get("recessOverrides") instanceof Map<?, ?> recMap) {
                            for (Map.Entry<?, ?> entry : recMap.entrySet()) {
                                try {
                                    Day d = Day.valueOf(entry.getKey().toString().trim().toUpperCase());
                                    int p = ((Number) entry.getValue()).intValue();
                                    if (p > 0) {
                                        activeModel.setDailyRecessOverride(d, p);
                                    }
                                } catch (Exception ignored) {}
                            }
                        }

                        if (bellMap.containsKey("dailyRecessAfterPeriod") && bellMap.get("dailyRecessAfterPeriod") instanceof Map<?, ?> drapMap) {
                            for (Map.Entry<?, ?> entry : drapMap.entrySet()) {
                                try {
                                    Day d = Day.valueOf(entry.getKey().toString().trim().toUpperCase());
                                    int p = ((Number) entry.getValue()).intValue();
                                    if (p > 0) {
                                        activeModel.setDailyRecessOverride(d, p);
                                    }
                                } catch (Exception ignored) {}
                            }
                        }

                        if (bellMap.containsKey("dailyRecessEnabled") && bellMap.get("dailyRecessEnabled") instanceof Map<?, ?> dreMap) {
                            for (Map.Entry<?, ?> entry : dreMap.entrySet()) {
                                try {
                                    Day d = Day.valueOf(entry.getKey().toString().trim().toUpperCase());
                                    activeModel.setDailyRecessEnabled(d, Boolean.parseBoolean(entry.getValue().toString()));
                                } catch (Exception ignored) {}
                            }
                        }

                        if (bellMap.containsKey("dailyRecessDurations") && bellMap.get("dailyRecessDurations") instanceof Map<?, ?> drdMap) {
                            for (Map.Entry<?, ?> entry : drdMap.entrySet()) {
                                try {
                                    Day d = Day.valueOf(entry.getKey().toString().trim().toUpperCase());
                                    activeModel.setDailyRecessDuration(d, ((Number) entry.getValue()).intValue());
                                } catch (Exception ignored) {}
                            }
                        }

                        if (bellMap.containsKey("schoolStartTime") && bellMap.get("schoolStartTime") != null) {
                            String st = bellMap.get("schoolStartTime").toString().trim();
                            if (!st.matches("^\\d{1,2}:\\d{2}$")) {
                                throw new IllegalArgumentException("School start time must be in HH:mm format (received " + st + ").");
                            }
                            activeModel.setSchoolStartTime(st);
                        }

                        if (bellMap.containsKey("saturdayStartTime") && bellMap.get("saturdayStartTime") != null) {
                            String sst = bellMap.get("saturdayStartTime").toString().trim();
                            if (!sst.matches("^\\d{1,2}:\\d{2}$")) {
                                throw new IllegalArgumentException("Saturday start time must be in HH:mm format (received " + sst + ").");
                            }
                            activeModel.setSaturdayStartTime(sst);
                        }

                        if (bellMap.containsKey("defaultPeriodDuration")) {
                            int dpd = ((Number) bellMap.get("defaultPeriodDuration")).intValue();
                            if (dpd < 15 || dpd > 120) {
                                throw new IllegalArgumentException("Default period duration must be between 15 and 120 minutes (received " + dpd + ").");
                            }
                            activeModel.setDefaultPeriodDuration(dpd);
                        }

                        if (bellMap.containsKey("passingTime")) {
                            activeModel.setPassingTime(((Number) bellMap.get("passingTime")).intValue());
                        }

                        if (bellMap.containsKey("recessDuration")) {
                            int rd = ((Number) bellMap.get("recessDuration")).intValue();
                            if (rd < 5 || rd > 90) {
                                throw new IllegalArgumentException("Recess duration must be between 5 and 90 minutes (received " + rd + ").");
                            }
                            activeModel.setRecessDuration(rd);
                        }

                        if (bellMap.containsKey("shortBreakEnabled")) {
                            activeModel.setShortBreakEnabled(Boolean.parseBoolean(bellMap.get("shortBreakEnabled").toString()));
                        }

                        if (bellMap.containsKey("shortBreakAfterPeriod")) {
                            activeModel.setShortBreakAfterPeriod(((Number) bellMap.get("shortBreakAfterPeriod")).intValue());
                        }

                        if (bellMap.containsKey("shortBreakDuration")) {
                            activeModel.setShortBreakDuration(((Number) bellMap.get("shortBreakDuration")).intValue());
                        }

                        if (bellMap.containsKey("periodDurationOverrides") && bellMap.get("periodDurationOverrides") instanceof Map<?, ?> pdoMap) {
                            for (Map.Entry<?, ?> entry : pdoMap.entrySet()) {
                                try {
                                    int p = Integer.parseInt(entry.getKey().toString());
                                    int dur = ((Number) entry.getValue()).intValue();
                                    activeModel.setPeriodDurationOverride(p, dur);
                                } catch (Exception ignored) {}
                            }
                        }

                        if (bellMap.containsKey("periodTimeOverrides") && bellMap.get("periodTimeOverrides") instanceof Map<?, ?> ptoMap) {
                            for (Map.Entry<?, ?> entry : ptoMap.entrySet()) {
                                try {
                                    int p = Integer.parseInt(entry.getKey().toString());
                                    activeModel.setPeriodTimeOverride(p, entry.getValue() != null ? entry.getValue().toString() : "");
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    // 3. Subjects
                    if (payload.containsKey("subjects") && payload.get("subjects") instanceof List<?> subList) {
                        activeModel.clearSubjects();
                        for (Object sObj : subList) {
                            if (sObj instanceof Map<?, ?> sm) {
                                String code = sm.get("code") != null ? sm.get("code").toString() : "";
                                String name = sm.get("name") != null ? sm.get("name").toString() : code;
                                String catStr = sm.get("category") != null ? sm.get("category").toString() : "CORE_ACADEMIC";
                                SubjectCategory cat;
                                try {
                                    cat = SubjectCategory.valueOf(catStr.toUpperCase());
                                } catch (Exception e) {
                                    cat = SubjectCategory.CORE_ACADEMIC;
                                }
                                boolean isLab = Boolean.TRUE.equals(sm.get("isLab"));
                                activeModel.addSubject(new Subject(name, code, cat, isLab));
                            }
                        }
                    }

                    // 4. Faculty & Allocations
                    if (payload.containsKey("professors") && payload.get("professors") instanceof List<?> profList) {
                        activeModel.clearProfessors();
                        for (Object pObj : profList) {
                            if (pObj instanceof Map<?, ?> pm) {
                                String code = pm.get("code") != null ? pm.get("code").toString() : "";
                                String name = pm.get("name") != null ? pm.get("name").toString() : code;
                                boolean free = Boolean.TRUE.equals(pm.get("freeTeacher"));
                                String ctOf = pm.get("classTeacherOf") != null ? pm.get("classTeacherOf").toString() : null;

                                ManualTimetableModel.ProfessorEntry prof = new ManualTimetableModel.ProfessorEntry(name, code, free);
                                if (ctOf != null && !ctOf.isBlank()) {
                                    SchoolClass sc = activeModel.findClass(ctOf);
                                    if (sc != null) prof.setClassTeacherOf(sc);
                                }

                                if (pm.containsKey("allocations") && pm.get("allocations") instanceof List<?> aList) {
                                    for (Object aObj : aList) {
                                        if (aObj instanceof Map<?, ?> am) {
                                            String sCode = am.get("subjectCode") != null ? am.get("subjectCode").toString() : "";
                                            Subject subj = activeModel.findSubject(sCode);
                                            if (subj == null) {
                                                subj = new Subject(sCode, sCode, SubjectCategory.CORE_ACADEMIC, false);
                                                activeModel.addSubject(subj);
                                            }
                                            int weekly = am.get("weeklyLectures") != null ? ((Number) am.get("weeklyLectures")).intValue() : 1;
                                            List<SchoolClass> targetClasses = new ArrayList<>();
                                            if (am.get("classes") instanceof List<?> cList) {
                                                for (Object cObj : cList) {
                                                    SchoolClass sc = activeModel.findClass(cObj.toString());
                                                    if (sc != null) targetClasses.add(sc);
                                                }
                                            }
                                            prof.addAllocation(new ManualTimetableModel.AllocationEntry(subj, targetClasses, weekly));
                                        }
                                    }
                                }
                                activeModel.addProfessor(prof);
                            }
                        }
                    }

                    // 5. Constraints
                    if (payload.containsKey("constraints") && payload.get("constraints") instanceof Map<?, ?> cMap) {
                        applyConstraintsMap(cMap, activeModel);
                    }

                    if (activeConfigFile != null) {
                        activeModel.persistScheduleToConfigFile(activeConfigFile);
                    }
                    scheduleStale = true;
                    ManualTimetableModel.ValidationReport rep = activeModel.validate();
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "SUCCESS");
                    response.put("message", "Model configuration updated and validated successfully.");
                    response.put("model", buildModelDto(activeModel));
                    response.put("validation", buildValidationDto(rep));
                    sendJsonResponse(exchange, 200, response);
                } else {
                    sendMethodNotAllowed(exchange, "GET, POST");
                }
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "Bad request in /api/setup/model: " + e.getMessage());
                sendJsonResponse(exchange, 400, Map.of(
                        "status", "ERROR",
                        "error", e.getMessage()
                ));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/setup/model", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private class SetupResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            try {
                activeModel.loadDefaultSchoolPreset();
                if (activeConfigFile != null) {
                    activeModel.persistScheduleToConfigFile(activeConfigFile);
                }
                scheduleStale = true;
                ManualTimetableModel.ValidationReport rep = activeModel.validate();

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "SUCCESS");
                response.put("message", "Reset active model to default SIES APJ School preset.");
                response.put("model", buildModelDto(activeModel));
                response.put("validation", buildValidationDto(rep));

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/setup/reset", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private class SetupValidateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET, POST");
                return;
            }

            try {
                ManualTimetableModel.ValidationReport rep = activeModel.validate();

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "SUCCESS");
                response.put("validation", buildValidationDto(rep));

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/setup/validate", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    // =========================================================================
    // Manual Timetable Editing Request Handlers (Batch 2)
    // =========================================================================

    private record CandidateScheduleResult(
            Schedule candidateSchedule,
            ScheduleValidator.ScheduleValidationResult validationResult
    ) {}

    private CandidateScheduleResult computeEditedSchedule(Map<String, Object> req) throws Exception {
        if (latestResult == null || !latestResult.isSuccess()) {
            throw new IllegalStateException("No active generated timetable available to edit. Please run generation first.");
        }

        String classId = (String) req.get("classId");
        if (classId == null || classId.isBlank()) {
            throw new IllegalArgumentException("classId is required.");
        }

        SchoolClass targetClass = null;
        for (SchoolClass sc : latestResult.reconciliationResult().classes()) {
            if (sc.getCanonicalName().equalsIgnoreCase(classId) ||
                sc.getDisplayName().equalsIgnoreCase(classId) ||
                sc.getStandardDivisionName().equalsIgnoreCase(classId)) {
                targetClass = sc;
                break;
            }
        }
        if (targetClass == null) {
            throw new IllegalArgumentException("Class " + classId + " not found in active schedule.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> src = (Map<String, Object>) req.get("source");
        @SuppressWarnings("unchecked")
        Map<String, Object> tgt = (Map<String, Object>) req.get("target");

        if (src == null || tgt == null) {
            throw new IllegalArgumentException("source and target slot specifications are required.");
        }

        Day d1 = Day.valueOf(src.get("day").toString().trim().toUpperCase());
        int p1Num = ((Number) src.get("period")).intValue();
        Period p1 = latestResult.config().getPeriods().get(p1Num);
        if (p1 == null) {
            throw new IllegalArgumentException("Invalid period number: " + p1Num);
        }

        Day d2 = Day.valueOf(tgt.get("day").toString().trim().toUpperCase());
        int p2Num = ((Number) tgt.get("period")).intValue();
        Period p2 = latestResult.config().getPeriods().get(p2Num);
        if (p2 == null) {
            throw new IllegalArgumentException("Invalid period number: " + p2Num);
        }

        ScheduleAssignment sa1 = latestResult.schedule().getAssignment(targetClass, d1, p1);
        ScheduleAssignment sa2 = latestResult.schedule().getAssignment(targetClass, d2, p2);

        if (sa1 == null && sa2 == null) {
            throw new IllegalArgumentException("Both selected time slots are empty.");
        }

        List<ScheduleAssignment> newAssignments = new ArrayList<>();
        for (ScheduleAssignment a : latestResult.schedule().getAssignments()) {
            if (a.equals(sa1) || a.equals(sa2)) {
                continue;
            }
            newAssignments.add(a);
        }

        if (sa1 != null) {
            TimeSlot targetSlot = new TimeSlot(d2, p2);
            newAssignments.add(new ScheduleAssignment(sa1.schoolClass(), targetSlot, sa1.teacher(), sa1.subject(), sa1.allocation()));
        }

        if (sa2 != null) {
            TimeSlot targetSlot = new TimeSlot(d1, p1);
            newAssignments.add(new ScheduleAssignment(sa2.schoolClass(), targetSlot, sa2.teacher(), sa2.subject(), sa2.allocation()));
        }

        Schedule candidate = new Schedule(newAssignments);
        ScheduleValidator.ScheduleValidationResult valResult = ScheduleValidator.validate(
                candidate, latestResult.reconciliationResult(), latestResult.config(), Collections.emptyList()
        );

        return new CandidateScheduleResult(candidate, valResult);
    }

    private class ScheduleEditValidateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            try {
                String body = readBody(exchange);
                @SuppressWarnings("unchecked")
                Map<String, Object> req = objectMapper.readValue(body, Map.class);

                CandidateScheduleResult res = computeEditedSchedule(req);
                boolean isValid = res.validationResult().allHardConstraintsPassed();

                List<String> errors = new ArrayList<>();
                List<String> warnings = new ArrayList<>();
                for (ScheduleValidator.ViolationDetail v : res.validationResult().violations()) {
                    if ("HARD".equalsIgnoreCase(v.severity())) {
                        errors.add(v.getFormattedSummary());
                    } else {
                        warnings.add(v.getFormattedSummary());
                    }
                }

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("status", "SUCCESS");
                resp.put("isValid", isValid);
                resp.put("hardViolations", res.validationResult().totalHardViolations());
                resp.put("errors", errors);
                resp.put("warnings", warnings);
                resp.put("qualityScore", res.validationResult().overallQualityIndex());

                sendJsonResponse(exchange, 200, resp);
            } catch (IllegalArgumentException | IllegalStateException e) {
                sendBadRequest(exchange, e.getMessage());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/schedule/edit/validate", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    private class ScheduleEditApplyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            try {
                String body = readBody(exchange);
                @SuppressWarnings("unchecked")
                Map<String, Object> req = objectMapper.readValue(body, Map.class);

                CandidateScheduleResult res;
                synchronized (scheduleLock) {
                    res = computeEditedSchedule(req);
                    if (!res.validationResult().allHardConstraintsPassed()) {
                        List<String> errors = res.validationResult().getHardViolations().stream()
                                .map(ScheduleValidator.ViolationDetail::getFormattedSummary)
                                .toList();

                        sendJsonResponse(exchange, 400, Map.of(
                                "status", "REJECTED",
                                "error", "Manual edit violates hard constraints and cannot be saved.",
                                "violations", errors
                        ));
                        return;
                    }

                    // Update latestResult with candidate schedule
                    TimetableService.GenerationResult updatedResult = new TimetableService.GenerationResult(
                            true, null, res.candidateSchedule(), latestResult.reconciliationResult(),
                            res.validationResult(), latestResult.config(), latestResult.outputDir(),
                            latestResult.excelFile(), latestResult.studentCsvFile(), latestResult.teacherCsvFile(),
                            latestResult.pdfDir(), latestResult.solveTimeSeconds()
                    );
                    latestResult = updatedResult;

                    // Synchronize disk exports
                    TimetableService.exportAll(
                            res.candidateSchedule(), latestResult.reconciliationResult(),
                            res.validationResult(), latestResult.config()
                    );
                }

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("status", "SUCCESS");
                resp.put("message", "Manual timetable edit accepted and synchronized across all views and export files.");
                resp.put("qualityScore", res.validationResult().overallQualityIndex());

                sendJsonResponse(exchange, 200, resp);
            } catch (IllegalArgumentException | IllegalStateException e) {
                sendBadRequest(exchange, e.getMessage());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in /api/schedule/edit/apply", e);
                sendInternalError(exchange, e.getMessage());
            }
        }
    }

    // =========================================================================
    // HTTP Helpers
    // =========================================================================

    private boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        setCorsHeaders(exchange);
        byte[] bytes = objectMapper.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange, String allowedMethods) throws IOException {
        setCorsHeaders(exchange);
        Map<String, Object> err = Map.of(
                "status", "METHOD_NOT_ALLOWED",
                "error", "HTTP method " + exchange.getRequestMethod() + " is not permitted. Allowed: " + allowedMethods
        );
        sendJsonResponse(exchange, 405, err);
    }

    private void sendBadRequest(HttpExchange exchange, String message) throws IOException {
        setCorsHeaders(exchange);
        Map<String, Object> err = Map.of(
                "status", "BAD_REQUEST",
                "error", message != null ? message : "Bad request"
        );
        sendJsonResponse(exchange, 400, err);
    }

    private void sendInternalError(HttpExchange exchange, String message) throws IOException {
        setCorsHeaders(exchange);
        Map<String, Object> err = Map.of(
                "status", "INTERNAL_SERVER_ERROR",
                "error", message != null ? message : "Unknown error occurred"
        );
        sendJsonResponse(exchange, 500, err);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
