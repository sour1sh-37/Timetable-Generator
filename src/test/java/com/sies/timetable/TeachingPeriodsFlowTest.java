package com.sies.timetable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.exporter.ExcelTimetableExporter;
import com.sies.timetable.exporter.PdfTimetableExporter;
import com.sies.timetable.exporter.StudentTimetableCsvExporter;
import com.sies.timetable.model.*;
import com.sies.timetable.model.ManualTimetableModel.AllocationEntry;
import com.sies.timetable.model.ManualTimetableModel.ProfessorEntry;
import com.sies.timetable.reconciliation.AllocationReconciler;
import com.sies.timetable.server.TimetableHttpServer;
import com.sies.timetable.service.TimetableService;
import com.sies.timetable.solver.CpSatTimetableSolver;
import com.sies.timetable.validator.ScheduleValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TeachingPeriodsFlowTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testChangeTeachingPeriodsPerDayTo7() {
        ManualTimetableModel model = new ManualTimetableModel();
        assertEquals(8, model.getPeriodsPerDay());
        assertEquals(8, model.getDailyPeriod(Day.MONDAY));
        assertEquals(48, model.getTotalAvailablePeriods());

        // Change to 7 periods per day
        model.setPeriodsPerDay(7);
        assertEquals(7, model.getPeriodsPerDay());
        for (Day d : model.getDays()) {
            assertEquals(7, model.getDailyPeriod(d), "Day " + d + " must have 7 periods");
        }
        assertEquals(42, model.getTotalAvailablePeriods());

        // Verify SchoolConfig projection
        SchoolConfig cfg = model.getStructureConfig();
        assertEquals(7, cfg.getPeriodsPerDay());
        assertEquals(7, cfg.getMaxDailyPeriods());
        assertEquals(7, cfg.getPeriods().size(), "Config periods map must have exactly 7 entries");
        assertNotNull(cfg.getPeriods().get(7));
        assertNull(cfg.getPeriods().get(8), "Period 8 must be pruned when configured for 7 periods");
    }

    @Test
    public void testPersistenceToConfigFile(@TempDir Path tempDir) throws Exception {
        ManualTimetableModel model = new ManualTimetableModel();
        model.setPeriodsPerDay(7);
        model.setRecessAfterPeriod(3);

        File cfgFile = tempDir.resolve("test-school-config.yaml").toFile();
        boolean saved = model.persistScheduleToConfigFile(cfgFile);
        assertTrue(saved, "persistScheduleToConfigFile must succeed");
        assertTrue(cfgFile.exists());

        // Load back with SchoolConfig
        SchoolConfig reloaded = SchoolConfig.load(cfgFile.getAbsolutePath());
        assertEquals(7, reloaded.getPeriodsPerDay());
        assertEquals(3, reloaded.getRecessAfterPeriod());
        for (Day d : reloaded.getDays()) {
            assertEquals(7, reloaded.getDailyPeriods(d));
        }
        assertEquals(7, reloaded.getPeriods().size());
    }

    @Test
    public void testInvalidPeriodsPerDayRejection() {
        ManualTimetableModel model = new ManualTimetableModel();

        assertThrows(IllegalArgumentException.class, () -> model.setPeriodsPerDay(0),
                "Should reject 0 periods per day");
        assertThrows(IllegalArgumentException.class, () -> model.setPeriodsPerDay(-1),
                "Should reject negative periods per day");
        assertThrows(IllegalArgumentException.class, () -> model.setPeriodsPerDay(13),
                "Should reject > 12 periods per day");
    }

    @Test
    public void testRecessPlacementValidation() {
        ManualTimetableModel model = new ManualTimetableModel();
        model.setPeriodsPerDay(5);
        // Valid recess: after period 1..5
        model.setRecessAfterPeriod(4);
        ManualTimetableModel.ValidationReport rep1 = model.validate();
        assertFalse(rep1.errors().stream().anyMatch(e -> e.contains("Invalid recess placement")),
                "Recess after period 4 on a 5-period day should be valid");

        // Force invalid recess after period 6 (exceeds total daily periods of 5)
        model.setDailyRecessOverride(Day.MONDAY, 6);
        ManualTimetableModel.ValidationReport rep2 = model.validate();
        assertTrue(rep2.errors().stream().anyMatch(e -> e.contains("Invalid recess placement for Monday")),
                "Should flag recess placed beyond the final period of the day");
    }

    @Test
    public void testCapacityValidationDeficitWhenReduced() {
        ManualTimetableModel model = new ManualTimetableModel();
        // Initially 48 available, 48 required
        model.setWeeklyRequiredPeriodsPerClass(48);
        model.setPeriodsPerDay(7); // 42 available

        ManualTimetableModel.ValidationReport rep = model.validate();
        assertFalse(rep.isValid(), "Capacity must be invalid when Available (42) < Required (48)");
        assertTrue(rep.errors().stream().anyMatch(e -> e.contains("Deficit: 6 periods")),
                "Should report exact deficit of 6 periods");
    }

    @Test
    public void testEndToEndSolverGenerationWith7PeriodsPerDay(@TempDir Path tempDir) throws Exception {
        ManualTimetableModel model = new ManualTimetableModel();
        model.setStandardsRange(5, 6);
        model.setDivisionCount(5, 2);
        model.setDivisionCount(6, 2);
        assertEquals(4, model.getGeneratedClasses().size()); // 5-A, 5-B, 6-A, 6-B

        // Configure 7 periods per day for all 6 days -> 42 periods available
        model.setPeriodsPerDay(7);
        model.setRecessAfterPeriod(3); // Recess after P3 (P1-P3, Recess, P4-P7)
        int weeklyReq = 42;
        model.setWeeklyRequiredPeriodsPerClass(weeklyReq);

        Subject math = new Subject("MATHS", SubjectCategory.CORE_ACADEMIC, false);
        Subject sci = new Subject("SCIENCE", SubjectCategory.CORE_ACADEMIC, false);
        Subject eng = new Subject("ENGLISH", SubjectCategory.CORE_ACADEMIC, false);
        Subject sst = new Subject("SST", SubjectCategory.CORE_ACADEMIC, false);
        Subject comp = new Subject("COMP", SubjectCategory.LAB, true);
        Subject pe = new Subject("P.E", SubjectCategory.ACTIVITY, false);
        Subject art = new Subject("ART", SubjectCategory.ACTIVITY, false);

        model.addSubject(math);
        model.addSubject(sci);
        model.addSubject(eng);
        model.addSubject(sst);
        model.addSubject(comp);
        model.addSubject(pe);
        model.addSubject(art);

        List<SchoolClass> allClasses = model.getGeneratedClasses();

        // Allocations: 7+7+7+7+6+4+4 = 42 periods per class!
        ProfessorEntry pMath = new ProfessorEntry("Prof Mathematics", "T_MATH", false);
        pMath.addAllocation(new AllocationEntry(math, allClasses, 7));
        pMath.setClassTeacherOf(allClasses.get(0));

        ProfessorEntry pSci = new ProfessorEntry("Prof Science", "T_SCI", false);
        pSci.addAllocation(new AllocationEntry(sci, allClasses, 7));
        pSci.setClassTeacherOf(allClasses.get(1));

        ProfessorEntry pEng = new ProfessorEntry("Prof English", "T_ENG", false);
        pEng.addAllocation(new AllocationEntry(eng, allClasses, 7));
        pEng.setClassTeacherOf(allClasses.get(2));

        ProfessorEntry pSst = new ProfessorEntry("Prof Social Studies", "T_SST", false);
        pSst.addAllocation(new AllocationEntry(sst, allClasses, 7));
        pSst.setClassTeacherOf(allClasses.get(3));

        ProfessorEntry pComp = new ProfessorEntry("Prof Computer", "T_COMP", false);
        pComp.addAllocation(new AllocationEntry(comp, allClasses, 6));

        ProfessorEntry pPe = new ProfessorEntry("Coach PE", "T_PE", false);
        pPe.addAllocation(new AllocationEntry(pe, allClasses, 4));

        ProfessorEntry pArt = new ProfessorEntry("Prof Art", "T_ART", false);
        pArt.addAllocation(new AllocationEntry(art, allClasses, 4));

        model.addProfessor(pMath);
        model.addProfessor(pSci);
        model.addProfessor(pEng);
        model.addProfessor(pSst);
        model.addProfessor(pComp);
        model.addProfessor(pPe);
        model.addProfessor(pArt);

        ManualTimetableModel.ValidationReport rep = model.validate();
        assertTrue(rep.isValid(), "Pre-solve validation must pass for 42-period configuration. Errors: " + rep.errors());

        SchoolConfig config = model.getStructureConfig();
        config.setOutputDir(tempDir.toString());
        config.setTimeLimitSeconds(15);

        AllocationReconciler.ReconciliationResult recResult = model.toReconciliationResult(config);

        // Run CP-SAT solver
        CpSatTimetableSolver.SolverResult solveRes = CpSatTimetableSolver.solve(recResult, config);
        assertNotNull(solveRes);
        assertTrue(solveRes.status() == com.google.ortools.sat.CpSolverStatus.OPTIMAL ||
                   solveRes.status() == com.google.ortools.sat.CpSolverStatus.FEASIBLE,
                "Solver must find a solution with 7 periods per day");

        Schedule schedule = solveRes.schedule();
        assertNotNull(schedule);
        assertEquals(4 * 42, schedule.getAssignments().size(), "Must schedule exactly 4 * 42 = 168 assignments");

        // Verify period bounds: Every assignment must be in Period 1..7, NEVER in Period 8+
        for (ScheduleAssignment sa : schedule.getAssignments()) {
            int p = sa.getPeriodIndex();
            assertTrue(p >= 1 && p <= 7, "Assignment period " + p + " must be between 1 and 7");
        }

        // Validate axioms H1-H10
        ScheduleValidator.ScheduleValidationResult valResult = ScheduleValidator.validate(schedule, recResult, config, Collections.emptyList());
        assertEquals(0, valResult.totalHardViolations(), "Generated 7-period schedule must satisfy all H1-H10 hard constraints");

        // Verify exporters run without error on 7-period schedule
        File excel = tempDir.resolve("timetable_7p.xlsx").toFile();
        File scsv = tempDir.resolve("student_timetable_7p.csv").toFile();
        ExcelTimetableExporter.export(schedule, recResult, valResult, config, excel.getAbsolutePath());
        assertTrue(excel.exists());
        StudentTimetableCsvExporter.export(schedule, recResult, config, scsv.getAbsolutePath());
        assertTrue(scsv.exists());

        File pdfDir = tempDir.resolve("pdf").toFile();
        pdfDir.mkdirs();
        PdfTimetableExporter.exportAllClasses(schedule, recResult, config, pdfDir);
        assertTrue(new File(pdfDir, "5-A_Timetable.pdf").exists());
    }

    @Test
    public void testHttpServerSetupModelEndpointWith7Periods(@TempDir Path tempDir) throws Exception {
        TimetableHttpServer server = new TimetableHttpServer(0);
        File tempCfg = tempDir.resolve("test-server-config.yaml").toFile();
        server.setConfigFile(tempCfg);
        server.start();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        try {
            // 1. Post updated model with periodsPerDay = 7 and stale dailyPeriods containing 8
            // (Verifies server prioritizes updated periodsPerDay and does not let stale dailyPeriods overwrite it)
            String payload = """
                    {
                      "bellSchedule": {
                        "periodsPerDay": 7,
                        "dailyPeriods": {
                          "MONDAY": 8,
                          "TUESDAY": 8,
                          "WEDNESDAY": 8,
                          "THURSDAY": 8,
                          "FRIDAY": 8,
                          "SATURDAY": 8
                        },
                        "recessAfterPeriod": 3,
                        "weeklyRequiredPeriodsPerClass": 42
                      }
                    }
                    """;

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/setup/model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, postResp.statusCode(), "POST /api/setup/model must succeed");

            JsonNode postJson = mapper.readTree(postResp.body());
            assertEquals("SUCCESS", postJson.get("status").asText());
            assertEquals(7, postJson.get("model").get("bellSchedule").get("periodsPerDay").asInt(),
                    "Response model bellSchedule must reflect 7 periods per day");

            // 2. Query GET /api/setup/model and confirm state remains 7
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/setup/model"))
                    .GET()
                    .build();

            HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResp.statusCode());
            JsonNode getJson = mapper.readTree(getResp.body());
            assertEquals(7, getJson.get("model").get("bellSchedule").get("periodsPerDay").asInt(),
                    "GET /api/setup/model must return 7 periods per day");

            // 3. Query GET /api/config and confirm periodsPerDay = 7
            HttpRequest cfgReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/config"))
                    .GET()
                    .build();

            HttpResponse<String> cfgResp = client.send(cfgReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, cfgResp.statusCode());
            JsonNode cfgJson = mapper.readTree(cfgResp.body());
            assertEquals(7, cfgJson.get("periodsPerDay").asInt(),
                    "GET /api/config must reflect 7 periods per day");

            // 4. Test invalid value rejection: POST periodsPerDay = 0
            String invalidPayload = """
                    {
                      "bellSchedule": {
                        "periodsPerDay": 0
                      }
                    }
                    """;
            HttpRequest invReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/setup/model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(invalidPayload))
                    .build();

            HttpResponse<String> invResp = client.send(invReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invResp.statusCode(), "Server must reject invalid period count with HTTP 400");
            JsonNode invJson = mapper.readTree(invResp.body());
            assertEquals("ERROR", invJson.get("status").asText());
            assertTrue(invJson.get("error").asText().contains("between 1 and 12"));

        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testIndividualDailyPeriodsMonFri8Sat4() {
        ManualTimetableModel model = new ManualTimetableModel();
        // Configure Mon-Fri: 8, Sat: 4
        for (Day d : Day.values()) {
            model.setDailyPeriod(d, d == Day.SATURDAY ? 4 : 8);
        }
        model.setWeeklyRequiredPeriodsPerClass(44);

        assertEquals(44, model.getTotalAvailablePeriods());
        assertEquals(4, model.getDailyPeriod(Day.SATURDAY));
        assertEquals(8, model.getDailyPeriod(Day.MONDAY));
        assertEquals(8, model.getPeriodsPerDay());

        ManualTimetableModel.ValidationReport rep = model.validate();
        assertFalse(rep.errors().stream().anyMatch(e -> e.contains("INVALID TIMETABLE CAPACITY")),
                "Capacity check must pass for 44 available and 44 required");
        assertFalse(rep.errors().stream().anyMatch(e -> e.contains("Invalid recess placement")),
                "Recess placement must be valid");

        SchoolConfig cfg = model.getStructureConfig();
        assertEquals(44, cfg.getTotalWeeklyPeriods());
        assertEquals(8, cfg.getMaxDailyPeriods());
        assertEquals(4, cfg.getDailyPeriods(Day.SATURDAY));
        assertEquals(8, cfg.getDailyPeriods(Day.MONDAY));
    }

    @Test
    public void testPersistenceAndReloadWithIndividualDailyPeriods(@TempDir Path tempDir) throws Exception {
        ManualTimetableModel model = new ManualTimetableModel();
        for (Day d : Day.values()) {
            model.setDailyPeriod(d, d == Day.SATURDAY ? 4 : 8);
        }
        model.setWeeklyRequiredPeriodsPerClass(44);
        model.setRecessAfterPeriod(4);

        File cfgFile = tempDir.resolve("test-individual-periods.yaml").toFile();
        boolean saved = model.persistScheduleToConfigFile(cfgFile);
        assertTrue(saved, "Persisting schedule with individual daily periods must succeed");

        SchoolConfig reloaded = SchoolConfig.load(cfgFile.getAbsolutePath());
        assertEquals(4, reloaded.getDailyPeriods(Day.SATURDAY), "Saturday must have 4 periods after reload");
        assertEquals(8, reloaded.getDailyPeriods(Day.MONDAY), "Monday must have 8 periods after reload");
        assertEquals(44, reloaded.getWeeklyRequiredPeriodsPerClass(), "Weekly required must be 44 after reload");
        assertEquals(44, reloaded.getTotalWeeklyPeriods(), "Total available must be 44 after reload");
        assertEquals(4, reloaded.getRecessAfterPeriod(), "Recess position must be 4 after reload");
    }

    @Test
    public void testHttpServerSetupModelWithIndividualDailyPeriods(@TempDir Path tempDir) throws Exception {
        TimetableHttpServer server = new TimetableHttpServer(0);
        File tempCfg = tempDir.resolve("test-server-daily-periods.yaml").toFile();
        server.setConfigFile(tempCfg);
        server.start();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        try {
            String payload = """
                    {
                      "bellSchedule": {
                        "days": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"],
                        "dailyPeriods": {
                          "MONDAY": 8,
                          "TUESDAY": 8,
                          "WEDNESDAY": 8,
                          "THURSDAY": 8,
                          "FRIDAY": 8,
                          "SATURDAY": 4
                        },
                        "weeklyRequiredPeriodsPerClass": 44,
                        "recessAfterPeriod": 4
                      }
                    }
                    """;

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/setup/model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, postResp.statusCode());

            JsonNode postJson = mapper.readTree(postResp.body());
            assertEquals("SUCCESS", postJson.get("status").asText());
            JsonNode bell = postJson.get("model").get("bellSchedule");
            assertEquals(4, bell.get("dailyPeriods").get("SATURDAY").asInt(), "Saturday must reflect 4 periods");
            assertEquals(8, bell.get("dailyPeriods").get("MONDAY").asInt(), "Monday must reflect 8 periods");
            assertEquals(44, bell.get("weeklyRequiredPeriodsPerClass").asInt(), "Weekly required must be 44");

            // Query GET /api/setup/model
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/setup/model"))
                    .GET()
                    .build();

            HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResp.statusCode());
            JsonNode getJson = mapper.readTree(getResp.body());
            assertEquals(4, getJson.get("model").get("bellSchedule").get("dailyPeriods").get("SATURDAY").asInt());

            // Query GET /api/config
            HttpRequest cfgReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/config"))
                    .GET()
                    .build();

            HttpResponse<String> cfgResp = client.send(cfgReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, cfgResp.statusCode());
            JsonNode cfgJson = mapper.readTree(cfgResp.body());
            assertEquals(44, cfgJson.get("weeklyRequiredPeriodsPerClass").asInt());
            assertEquals(4, cfgJson.get("dailyPeriods").get("SATURDAY").asInt());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testSmartTimingAutoCalculation() {
        SchoolConfig config = SchoolConfig.createDefault();
        config.setSchoolStartTime("07:30");
        config.setDefaultPeriodDurationMinutes(40);
        config.setPassingTimeMinutes(5);
        config.setRecessDurationMinutes(30);
        config.setRecessAfterPeriod(4);

        Map<Integer, String> timings = config.computePeriodTimings(Day.MONDAY);
        assertNotNull(timings);
        assertEquals("07:30 - 08:10", timings.get(1));

        // P2 starts after 5 min passing time: 08:15 to 08:55
        assertEquals("08:15 - 08:55", timings.get(2));

        // Recess timing: after P4 (09:45 - 10:25) -> Recess is 10:25 - 10:55
        String recess = config.getRecessTiming(Day.MONDAY);
        assertEquals("10:25 - 10:55", recess);

        // P5 starts after recess (10:55) -> 10:55 to 11:35
        assertEquals("10:55 - 11:35", timings.get(5));
    }

    @Test
    public void testSaturdayScheduleWithoutRecess() {
        SchoolConfig config = SchoolConfig.createDefault();
        config.setSchoolStartTime("07:30");
        config.setSaturdayStartTime("08:00");
        config.setDailyPeriod(Day.SATURDAY, 4);
        config.setDailyRecessEnabled(Day.SATURDAY, false);

        assertTrue(config.isRecessEnabled(Day.MONDAY), "Monday must have recess enabled");
        assertFalse(config.isRecessEnabled(Day.SATURDAY), "Saturday recess must be disabled");

        String satRecess = config.getRecessTiming(Day.SATURDAY);
        assertEquals("", satRecess, "Saturday should return empty recess timing when disabled");

        Map<Integer, String> satTimings = config.computePeriodTimings(Day.SATURDAY);
        assertEquals(4, satTimings.size(), "Saturday must compute exactly 4 periods");
        assertTrue(satTimings.get(1).startsWith("08:00"), "Saturday must start at 08:00");
    }

    @Test
    public void testSmartTimingPersistenceYaml(@TempDir Path tempDir) throws Exception {
        ManualTimetableModel model = new ManualTimetableModel();
        model.setSchoolStartTime("07:45");
        model.setSaturdayStartTime("08:15");
        model.setDefaultPeriodDuration(45);
        model.setPassingTime(3);
        model.setRecessDuration(25);
        model.setDailyRecessEnabled(Day.SATURDAY, false);
        model.setDailyRecessDuration(Day.FRIDAY, 35);
        model.setPeriodDurationOverride(1, 50);
        model.setPeriodTimeOverride(8, "13:30 - 14:15");

        File cfgFile = tempDir.resolve("smart-timing-config.yaml").toFile();
        boolean saved = model.persistScheduleToConfigFile(cfgFile);
        assertTrue(saved, "Configuration saving must succeed");

        SchoolConfig loaded = SchoolConfig.load(cfgFile.getAbsolutePath());
        assertEquals("07:45", loaded.getSchoolStartTime());
        assertEquals("08:15", loaded.getSaturdayStartTime());
        assertEquals(45, loaded.getDefaultPeriodDurationMinutes());
        assertEquals(3, loaded.getPassingTimeMinutes());
        assertEquals(25, loaded.getRecessDurationMinutes());
        assertFalse(loaded.isRecessEnabled(Day.SATURDAY));
        assertEquals(35, loaded.getDailyRecessDurations().get(Day.FRIDAY));
        assertEquals(50, loaded.getPeriodDurationOverrides().get(1));
        assertEquals("13:30 - 14:15", loaded.getPeriodTimeOverrides().get(8));
    }

    @Test
    public void testSetupModelApiWithSmartTimingAndSaturdayNoRecess(@TempDir Path tempDir) throws Exception {
        TimetableHttpServer server = new TimetableHttpServer(0);
        File tempCfg = tempDir.resolve("test-server-smart-config.yaml").toFile();
        server.setConfigFile(tempCfg);
        server.start();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        try {
            String payload = """
                    {
                      "bellSchedule": {
                        "schoolStartTime": "07:30",
                        "saturdayStartTime": "08:00",
                        "defaultPeriodDurationMinutes": 40,
                        "passingTimeMinutes": 5,
                        "recessDurationMinutes": 30,
                        "dailyRecessEnabled": {
                          "MONDAY": true,
                          "TUESDAY": true,
                          "WEDNESDAY": true,
                          "THURSDAY": true,
                          "FRIDAY": true,
                          "SATURDAY": false
                        },
                        "dailyPeriods": {
                          "MONDAY": 8,
                          "TUESDAY": 8,
                          "WEDNESDAY": 8,
                          "THURSDAY": 8,
                          "FRIDAY": 8,
                          "SATURDAY": 4
                        },
                        "weeklyRequiredPeriodsPerClass": 44,
                        "recessAfterPeriod": 4
                      }
                    }
                    """;

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/setup/model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, postResp.statusCode());

            JsonNode respJson = mapper.readTree(postResp.body());
            assertEquals("SUCCESS", respJson.get("status").asText());
            JsonNode bell = respJson.get("model").get("bellSchedule");
            assertEquals("07:30", bell.get("schoolStartTime").asText());
            assertEquals("08:00", bell.get("saturdayStartTime").asText());
            assertFalse(bell.get("dailyRecessEnabled").get("SATURDAY").asBoolean());
            assertTrue(bell.has("computedTimings"));
            assertTrue(bell.get("computedTimings").has("weekday"));
            assertTrue(bell.get("computedTimings").has("saturday"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testSaturdayThreePeriodsWithoutRecessPassesValidation() {
        ManualTimetableModel model = new ManualTimetableModel();
        model.loadDefaultSchoolPreset();
        model.setDailyPeriod(Day.SATURDAY, 3);
        model.setDailyRecessEnabled(Day.SATURDAY, false);
        model.setWeeklyRequiredPeriodsPerClass(model.getTotalAvailablePeriods());

        ManualTimetableModel.ValidationReport rep = model.validate();
        List<String> recessErrors = rep.errors().stream()
                .filter(e -> e.toLowerCase().contains("recess") && e.toLowerCase().contains("saturday"))
                .toList();
        assertTrue(recessErrors.isEmpty(), "Saturday with 3 periods and no recess must have 0 recess errors, but got: " + recessErrors);
        assertFalse(rep.errors().stream().anyMatch(e -> e.contains("Invalid recess placement")),
                "Must not report any invalid recess placement when recess is disabled for Saturday");

        SchoolConfig cfg = model.buildSchoolConfig();
        assertFalse(cfg.isRecessEnabled(Day.SATURDAY), "SchoolConfig must report Saturday recess disabled");
        assertEquals(0, cfg.getRecessAfterPeriod(Day.SATURDAY), "SchoolConfig must report 0 active recess period when disabled");
        assertTrue(model.getDailyRecessPosition(Day.SATURDAY) > 0, "Daily recess position fallback must be a valid positive period");
        assertTrue(model.getDailyRecessPosition(Day.SATURDAY) < 3, "Daily recess position fallback must be < daily periods (1 or 2)");
    }

    @Test
    public void testSaturdayThreePeriodsWithValidRecessPassesValidation() {
        ManualTimetableModel model = new ManualTimetableModel();
        model.loadDefaultSchoolPreset();
        model.setDailyPeriod(Day.SATURDAY, 3);
        model.setDailyRecessEnabled(Day.SATURDAY, true);
        model.setWeeklyRequiredPeriodsPerClass(model.getTotalAvailablePeriods());

        // Test recess after period 1 (1 <= 1 < 3)
        model.setDailyRecessOverride(Day.SATURDAY, 1);
        ManualTimetableModel.ValidationReport rep1 = model.validate();
        List<String> recessErrors1 = rep1.errors().stream()
                .filter(e -> e.toLowerCase().contains("recess") && e.toLowerCase().contains("saturday"))
                .toList();
        assertTrue(recessErrors1.isEmpty(), "Saturday with 3 periods and recess after period 1 must have 0 recess errors");

        // Test recess after period 2 (1 <= 2 < 3)
        model.setDailyRecessOverride(Day.SATURDAY, 2);
        ManualTimetableModel.ValidationReport rep2 = model.validate();
        List<String> recessErrors2 = rep2.errors().stream()
                .filter(e -> e.toLowerCase().contains("recess") && e.toLowerCase().contains("saturday"))
                .toList();
        assertTrue(recessErrors2.isEmpty(), "Saturday with 3 periods and recess after period 2 must have 0 recess errors");

        SchoolConfig cfg = model.buildSchoolConfig();
        assertTrue(cfg.isRecessEnabled(Day.SATURDAY));
        assertEquals(2, cfg.getRecessAfterPeriod(Day.SATURDAY));
    }

    @Test
    public void testSaturdayThreePeriodsWithInvalidRecessPositionFailsValidation() {
        ManualTimetableModel model = new ManualTimetableModel();
        model.loadDefaultSchoolPreset();
        model.setDailyPeriod(Day.SATURDAY, 3);
        model.setDailyRecessEnabled(Day.SATURDAY, true);
        model.setWeeklyRequiredPeriodsPerClass(model.getTotalAvailablePeriods());

        // Test recess after period 0 (not permitted when recess is enabled)
        model.getDailyRecessOverrides(); // verify unmodifiable
        // Use reflection or direct map update if setter guards, to simulate corrupt/zero input
        model.setDailyRecessEnabledMap(Map.of(Day.SATURDAY, true));
        // Directly testing invalid period overrides
        model.setDailyRecessOverridesMap(Map.of(Day.SATURDAY, 0));
        ManualTimetableModel.ValidationReport rep0 = model.validate();
        assertFalse(rep0.isValid(), "Recess after period 0 must fail validation");
        assertTrue(rep0.errors().stream().anyMatch(e -> e.contains("Invalid recess placement for Saturday") && e.contains("After period 0") && e.contains("between 1 and 2")),
                "Error message must specify recess must be between 1 and 2: " + rep0.errors());

        // Test recess after period 3 (invalid because day has 3 periods; recess cannot be after the last period)
        model.setDailyRecessOverridesMap(Map.of(Day.SATURDAY, 3));
        ManualTimetableModel.ValidationReport rep3 = model.validate();
        assertFalse(rep3.isValid(), "Recess after period 3 must fail validation for 3-period day");
        assertTrue(rep3.errors().stream().anyMatch(e -> e.contains("Invalid recess placement for Saturday") && e.contains("After period 3") && e.contains("between 1 and 2")),
                "Error message must specify recess must be between 1 and 2: " + rep3.errors());

        // Test recess after period 4
        model.setDailyRecessOverridesMap(Map.of(Day.SATURDAY, 4));
        ManualTimetableModel.ValidationReport rep4 = model.validate();
        assertFalse(rep4.isValid(), "Recess after period 4 must fail validation for 3-period day");
        assertTrue(rep4.errors().stream().anyMatch(e -> e.contains("Invalid recess placement for Saturday") && e.contains("After period 4")),
                "Error message must specify invalid placement for period 4: " + rep4.errors());
    }

    @Test
    public void testSaturdayOnePeriodCannotHaveRecess() {
        ManualTimetableModel model = new ManualTimetableModel();
        model.loadDefaultSchoolPreset();
        model.setDailyPeriod(Day.SATURDAY, 1);
        model.setDailyRecessEnabled(Day.SATURDAY, true);
        model.setDailyRecessOverridesMap(Map.of(Day.SATURDAY, 1));
        model.setWeeklyRequiredPeriodsPerClass(model.getTotalAvailablePeriods());

        ManualTimetableModel.ValidationReport rep = model.validate();
        assertFalse(rep.isValid(), "1-period day cannot have recess enabled");
        assertTrue(rep.errors().stream().anyMatch(e -> e.contains("Day only has 1 period, so recess cannot be scheduled")),
                "Error message must indicate 1-period day cannot schedule recess: " + rep.errors());
    }

    @Test
    public void testSaturdayRecessPersistenceRoundtrip(@TempDir Path tempDir) throws Exception {
        ManualTimetableModel model = new ManualTimetableModel();
        model.loadDefaultSchoolPreset();
        model.setDailyPeriod(Day.SATURDAY, 3);
        model.setDailyRecessEnabled(Day.SATURDAY, false);
        model.setDailyRecessOverride(Day.SATURDAY, 2);
        model.setWeeklyRequiredPeriodsPerClass(model.getTotalAvailablePeriods());

        File cfgFile = tempDir.resolve("roundtrip-no-recess.yaml").toFile();
        assertTrue(model.persistScheduleToConfigFile(cfgFile), "Model must save successfully");

        SchoolConfig loadedCfg = SchoolConfig.load(cfgFile.getAbsolutePath());
        assertEquals(3, loadedCfg.getDailyPeriods(Day.SATURDAY));
        assertFalse(loadedCfg.isRecessEnabled(Day.SATURDAY), "Saturday recess must remain disabled after reload");
        assertEquals(0, loadedCfg.getRecessAfterPeriod(Day.SATURDAY), "Active recess after period must be 0 when disabled");
        assertEquals(2, loadedCfg.getRawRecessAfterPeriod(Day.SATURDAY), "Raw underlying recess position must be preserved as 2");

        ManualTimetableModel reloadedModel = new ManualTimetableModel();
        reloadedModel.loadDefaultSchoolPreset();
        reloadedModel.setDailyPeriodsMap(loadedCfg.getDailyPeriods());
        reloadedModel.setDailyRecessEnabledMap(loadedCfg.getDailyRecessEnabled());
        for (Day d : loadedCfg.getDays()) {
            reloadedModel.setDailyRecessOverride(d, loadedCfg.getRawRecessAfterPeriod(d));
        }
        reloadedModel.setWeeklyRequiredPeriodsPerClass(loadedCfg.getWeeklyRequiredPeriodsPerClass());

        ManualTimetableModel.ValidationReport rep = reloadedModel.validate();
        List<String> recessErrors = rep.errors().stream()
                .filter(e -> e.toLowerCase().contains("recess") && e.toLowerCase().contains("saturday"))
                .toList();
        assertTrue(recessErrors.isEmpty(), "Reloaded model must have 0 Saturday recess errors: " + recessErrors);
        assertFalse(rep.errors().stream().anyMatch(e -> e.contains("Invalid recess placement for Saturday")),
                "Reloaded model must not have Saturday recess placement errors");
    }

    @Test
    public void testSaturdayRecessEnabledPersistenceRoundtrip(@TempDir Path tempDir) throws Exception {
        ManualTimetableModel model = new ManualTimetableModel();
        model.loadDefaultSchoolPreset();
        model.setDailyPeriod(Day.SATURDAY, 3);
        model.setDailyRecessEnabled(Day.SATURDAY, true);
        model.setDailyRecessOverride(Day.SATURDAY, 2);
        model.setWeeklyRequiredPeriodsPerClass(model.getTotalAvailablePeriods());

        File cfgFile = tempDir.resolve("roundtrip-with-recess.yaml").toFile();
        assertTrue(model.persistScheduleToConfigFile(cfgFile), "Model must save successfully");

        SchoolConfig loadedCfg = SchoolConfig.load(cfgFile.getAbsolutePath());
        assertEquals(3, loadedCfg.getDailyPeriods(Day.SATURDAY));
        assertTrue(loadedCfg.isRecessEnabled(Day.SATURDAY), "Saturday recess must remain enabled after reload");
        assertEquals(2, loadedCfg.getRecessAfterPeriod(Day.SATURDAY), "Active recess after period must be 2");

        ManualTimetableModel reloadedModel = new ManualTimetableModel();
        reloadedModel.loadDefaultSchoolPreset();
        reloadedModel.setDailyPeriodsMap(loadedCfg.getDailyPeriods());
        reloadedModel.setDailyRecessEnabledMap(loadedCfg.getDailyRecessEnabled());
        for (Day d : loadedCfg.getDays()) {
            reloadedModel.setDailyRecessOverride(d, loadedCfg.getRawRecessAfterPeriod(d));
        }
        reloadedModel.setWeeklyRequiredPeriodsPerClass(loadedCfg.getWeeklyRequiredPeriodsPerClass());

        ManualTimetableModel.ValidationReport rep = reloadedModel.validate();
        List<String> recessErrors = rep.errors().stream()
                .filter(e -> e.toLowerCase().contains("recess") && e.toLowerCase().contains("saturday"))
                .toList();
        assertTrue(recessErrors.isEmpty(), "Reloaded model with enabled recess must have 0 Saturday recess errors: " + recessErrors);
        assertFalse(rep.errors().stream().anyMatch(e -> e.contains("Invalid recess placement for Saturday")),
                "Reloaded model with enabled recess must not have Saturday recess placement errors");
    }
}
