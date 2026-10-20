package com.sies.timetable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sies.timetable.server.TimetableHttpServer;
import com.sies.timetable.service.TimetableService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test verifying that the TimetableHttpServer runs cleanly on loopback,
 * responds accurately across all endpoints, handles CORS preflight, asynchronous solver contracts,
 * schedule grid inspection, and export downloads.
 */
public class HttpServerIntegrationTest {

    private static TimetableHttpServer server;
    private static HttpClient client;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    public static void setUp() throws Exception {
        // Ephemeral port 0 to prevent port collisions
        server = new TimetableHttpServer(0);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testServerStartsOnEphemeralPort() {
        assertTrue(server.getPort() > 0, "Server should bind to a valid dynamic port");
        assertTrue(server.getBaseUrl().startsWith("http://127.0.0.1:"), "Server must bind strictly to 127.0.0.1");
    }

    @Test
    public void testHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));

        JsonNode json = mapper.readTree(response.body());
        assertEquals("UP", json.get("status").asText());
        assertEquals("Google OR-Tools CP-SAT", json.get("solver").get("engine").asText());
        assertTrue(json.get("jvm").get("pid").asLong() > 0);
    }

    @Test
    public void testConfigEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/config"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonNode json = mapper.readTree(response.body());
        assertEquals("SUCCESS", json.get("status").asText());
        assertNotNull(json.get("schoolName").asText());
        assertTrue(json.get("periodsPerDay").asInt() > 0);
    }

    @Test
    public void testWorkspaceDetectEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/workspace/detect"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"path\": \".\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonNode json = mapper.readTree(response.body());
        assertEquals("SUCCESS", json.get("status").asText());
        assertTrue(json.has("detection"));
        assertTrue(json.get("detection").has("isValid"));
    }

    @Test
    public void testModelPresetEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/model/preset"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonNode json = mapper.readTree(response.body());
        assertEquals("SUCCESS", json.get("status").asText());
        assertTrue(json.has("preset"));
        assertTrue(json.get("preset").has("standards"));
    }

    @Test
    public void testCorsOptionsPreflight() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/health"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertEquals(204, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    public void testSolveStatusWhenIdle() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/solve/status"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonNode json = mapper.readTree(response.body());
        assertTrue(json.has("status"));
        assertEquals("IDLE", json.get("status").asText());
    }

    @Test
    public void testSolveCancelWhenIdle() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/solve/cancel"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonNode json = mapper.readTree(response.body());
        assertEquals("IDLE", json.get("status").asText());
    }

    @Test
    public void testScheduleSummaryWhenNotGenerated() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/schedule/summary"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonNode json = mapper.readTree(response.body());
        assertEquals("SUCCESS", json.get("status").asText());
        assertFalse(json.get("available").asBoolean());
    }

    @Test
    public void testScheduleClassWhenNotGenerated() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/schedule/class?id=5-A"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    @Test
    public void testExportDownloadWhenNotGenerated() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/export/download?type=excel"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    @Test
    public void testSetupModelEndpoints() throws Exception {
        // Test GET /api/setup/model
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/setup/model"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResp.statusCode());

        JsonNode json = mapper.readTree(getResp.body());
        assertEquals("SUCCESS", json.get("status").asText());
        assertTrue(json.has("model"));
        assertTrue(json.has("validation"));
        assertEquals(26, json.get("model").get("classes").size());
        assertTrue(json.get("model").get("professors").size() > 0);
        assertTrue(json.get("validation").get("isValid").asBoolean());

        // Test POST /api/setup/validate
        HttpRequest valReq = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/setup/validate"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> valResp = client.send(valReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, valResp.statusCode());
        JsonNode valJson = mapper.readTree(valResp.body());
        assertEquals("SUCCESS", valJson.get("status").asText());
        assertTrue(valJson.get("validation").get("isValid").asBoolean());
    }

    @Test
    public void testScheduleEditWhenNotGenerated() throws Exception {
        String payload = """
                {
                  "classId": "5-A",
                  "source": { "day": "MONDAY", "period": 1 },
                  "target": { "day": "MONDAY", "period": 2 },
                  "operation": "SWAP"
                }
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/schedule/edit/validate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, resp.statusCode());
    }

    @Test
    public void testScheduleEditAndUndoLifecycle() throws Exception {
        com.sies.timetable.model.ManualTimetableModel model = new com.sies.timetable.model.ManualTimetableModel();
        model.setStandardsRange(5, 5);
        model.setDivisionCount(5, 1); // 1 class: 5-A
        for (com.sies.timetable.model.Day d : com.sies.timetable.model.Day.values()) model.setDailyPeriods(d, 8);
        model.setWeeklyRequiredPeriodsPerClass(48);

        com.sies.timetable.model.Subject math = new com.sies.timetable.model.Subject("MATHS", com.sies.timetable.model.SubjectCategory.CORE_ACADEMIC, false);
        com.sies.timetable.model.Subject eng = new com.sies.timetable.model.Subject("ENGLISH", com.sies.timetable.model.SubjectCategory.CORE_ACADEMIC, false);
        model.addSubject(math);
        model.addSubject(eng);

        com.sies.timetable.model.SchoolClass sc = model.getGeneratedClasses().get(0);
        com.sies.timetable.model.ManualTimetableModel.ProfessorEntry p1 = new com.sies.timetable.model.ManualTimetableModel.ProfessorEntry("Teacher 1", "T01", false);
        p1.setClassTeacherOf(sc);
        p1.addAllocation(new com.sies.timetable.model.ManualTimetableModel.AllocationEntry(math, List.of(sc), 24));
        model.addProfessor(p1);

        com.sies.timetable.model.ManualTimetableModel.ProfessorEntry p2 = new com.sies.timetable.model.ManualTimetableModel.ProfessorEntry("Teacher 2", "T02", false);
        p2.addAllocation(new com.sies.timetable.model.ManualTimetableModel.AllocationEntry(eng, List.of(sc), 24));
        model.addProfessor(p2);

        com.sies.timetable.config.SchoolConfig cfg = model.getStructureConfig();
        cfg.setOutputDir("target/test-output-edit");
        com.sies.timetable.reconciliation.AllocationReconciler.ReconciliationResult recResult = model.toReconciliationResult(cfg);

        // Construct initial valid assignments
        List<com.sies.timetable.model.ScheduleAssignment> assignments = new ArrayList<>();
        com.sies.timetable.model.TeachingAllocation aMath = recResult.allocations().stream().filter(a -> a.getSubject().equals(math)).findFirst().orElseThrow();
        com.sies.timetable.model.TeachingAllocation aEng = recResult.allocations().stream().filter(a -> a.getSubject().equals(eng)).findFirst().orElseThrow();

        for (com.sies.timetable.model.Day d : cfg.getDays()) {
            for (int p = 1; p <= 8; p++) {
                com.sies.timetable.model.Period period = cfg.getPeriods().get(p);
                com.sies.timetable.model.TimeSlot slot = new com.sies.timetable.model.TimeSlot(d, period);
                com.sies.timetable.model.TeachingAllocation chosen = (p % 2 == 1) ? aMath : aEng;
                assignments.add(new com.sies.timetable.model.ScheduleAssignment(sc, slot, chosen.getTeacher(), chosen.getSubject(), chosen));
            }
        }
        com.sies.timetable.model.Schedule initialSchedule = new com.sies.timetable.model.Schedule(assignments);
        com.sies.timetable.validator.ScheduleValidator.ScheduleValidationResult valResult =
                com.sies.timetable.validator.ScheduleValidator.validate(initialSchedule, recResult, cfg, Collections.emptyList());

        java.io.File outDir = new java.io.File(cfg.getOutputDir());
        outDir.mkdirs();
        java.io.File excel = new java.io.File(outDir, "timetable.xlsx");
        java.io.File scsv = new java.io.File(outDir, "student_timetable.csv");
        java.io.File tcsv = new java.io.File(outDir, "teacher_timetable.csv");
        java.io.File pdfDir = new java.io.File(outDir, "pdf");

        try {
            com.sies.timetable.service.TimetableService.exportAll(initialSchedule, recResult, valResult, cfg);

            com.sies.timetable.service.TimetableService.GenerationResult genResult = new com.sies.timetable.service.TimetableService.GenerationResult(
                    true, null, initialSchedule, recResult, valResult, cfg, outDir, excel, scsv, tcsv, pdfDir, 1.0
            );
            server.setLatestResultForTesting(genResult);

            // 1. Validate swap between MONDAY P3 (MATHS) and MONDAY P4 (ENGLISH)
            String swapPayload = """
                    {
                      "classId": "5-A",
                      "source": { "day": "MONDAY", "period": 3 },
                      "target": { "day": "MONDAY", "period": 4 },
                      "operation": "SWAP"
                    }
                    """;

            HttpRequest valReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/schedule/edit/validate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(swapPayload))
                    .build();

            HttpResponse<String> valResp = client.send(valReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, valResp.statusCode());
            JsonNode valJson = mapper.readTree(valResp.body());
            assertEquals("SUCCESS", valJson.get("status").asText());
            assertTrue(valJson.get("isValid").asBoolean());

            // 2. Apply swap
            HttpRequest applyReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/schedule/edit/apply"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(swapPayload))
                    .build();

            HttpResponse<String> applyResp = client.send(applyReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, applyResp.statusCode());
            JsonNode applyJson = mapper.readTree(applyResp.body());
            assertEquals("SUCCESS", applyJson.get("status").asText());

            // Verify disk exports exist and were refreshed
            assertTrue(excel.exists());
            assertTrue(scsv.exists());

            // 3. Simulate Undo (reversing target and source)
            String undoPayload = """
                    {
                      "classId": "5-A",
                      "source": { "day": "MONDAY", "period": 4 },
                      "target": { "day": "MONDAY", "period": 3 },
                      "operation": "SWAP"
                    }
                    """;

            HttpRequest undoReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/schedule/edit/apply"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(undoPayload))
                    .build();

            HttpResponse<String> undoResp = client.send(undoReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, undoResp.statusCode());
            JsonNode undoJson = mapper.readTree(undoResp.body());
            assertEquals("SUCCESS", undoJson.get("status").asText());
        } finally {
            server.setLatestResultForTesting(null);
        }
    }

    @Test
    public void testScheduleStaleTracking() throws Exception {
        TimetableService.GenerationResult dummy = new TimetableService.GenerationResult(
                true, null, null, null, null, null, null, null, null, null, null, 1.0
        );
        server.setLatestResultForTesting(dummy);

        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/schedule/summary"))
                .GET().build();
        HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp1.statusCode());
        JsonNode json1 = mapper.readTree(resp1.body());
        assertFalse(json1.get("isStale").asBoolean());

        String updatePayload = """
                {
                  "schoolName": "SIES High School & Junior College"
                }
                """;
        HttpRequest updateReq = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/setup/model"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(updatePayload))
                .build();
        HttpResponse<String> updateResp = client.send(updateReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateResp.statusCode());

        HttpResponse<String> resp2 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp2.statusCode());
        JsonNode json2 = mapper.readTree(resp2.body());
        assertTrue(json2.get("isStale").asBoolean());

        server.setLatestResultForTesting(null);
    }
}
