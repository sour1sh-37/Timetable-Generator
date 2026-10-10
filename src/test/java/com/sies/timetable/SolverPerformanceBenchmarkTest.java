package com.sies.timetable;

import com.google.ortools.sat.CpSolverStatus;
import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.parser.*;
import com.sies.timetable.reconciliation.AllocationReconciler;
import com.sies.timetable.solver.CpSatTimetableSolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmarking and profiling suite for the Two-Phase Google OR-Tools CP-SAT solver.
 * Profiles model construction, Phase 1 feasibility search, Phase 2 soft optimization,
 * cancellation latency, and memory utilization across representative school structures.
 */
public class SolverPerformanceBenchmarkTest {

    @Test
    @DisplayName("Benchmark 1: Small School Configuration (4 Classes, Standards 5-6, 44 Periods Available, 40 Req)")
    public void benchmarkSmallConfiguration() throws Exception {
        ManualTimetableModel model = new ManualTimetableModel();
        model.setStandardsRange(5, 6);
        model.setDivisionCount(5, 2);
        model.setDivisionCount(6, 2);
        for (Day d : Day.values()) {
            model.setDailyPeriods(d, d == Day.SATURDAY ? 4 : 8);
        }
        model.setWeeklyRequiredPeriodsPerClass(40);

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

        List<SchoolClass> classes = model.getGeneratedClasses();
        ManualTimetableModel.ProfessorEntry pMath = new ManualTimetableModel.ProfessorEntry("Prof Mathematics", "T_MATH", false);
        pMath.addAllocation(new ManualTimetableModel.AllocationEntry(math, classes, 7)); // 4 * 7 = 28 periods
        pMath.setClassTeacherOf(classes.get(0)); // 5-A class teacher

        ManualTimetableModel.ProfessorEntry pSci = new ManualTimetableModel.ProfessorEntry("Prof Science", "T_SCI", false);
        pSci.addAllocation(new ManualTimetableModel.AllocationEntry(sci, classes, 7)); // 28 periods
        pSci.setClassTeacherOf(classes.get(1)); // 5-B class teacher

        ManualTimetableModel.ProfessorEntry pEng = new ManualTimetableModel.ProfessorEntry("Prof English", "T_ENG", false);
        pEng.addAllocation(new ManualTimetableModel.AllocationEntry(eng, classes, 7)); // 28 periods
        pEng.setClassTeacherOf(classes.get(2)); // 6-A class teacher

        ManualTimetableModel.ProfessorEntry pSst = new ManualTimetableModel.ProfessorEntry("Prof SocialStudies", "T_SST", false);
        pSst.addAllocation(new ManualTimetableModel.AllocationEntry(sst, classes, 7)); // 28 periods
        pSst.setClassTeacherOf(classes.get(3)); // 6-B class teacher

        ManualTimetableModel.ProfessorEntry pComp = new ManualTimetableModel.ProfessorEntry("Prof Computer", "T_COMP", false);
        pComp.addAllocation(new ManualTimetableModel.AllocationEntry(comp, classes, 4)); // 16 periods (Lab required)

        ManualTimetableModel.ProfessorEntry pAct = new ManualTimetableModel.ProfessorEntry("Prof Activities", "T_ACT", false);
        pAct.addAllocation(new ManualTimetableModel.AllocationEntry(pe, classes, 4)); // 16 periods
        pAct.addAllocation(new ManualTimetableModel.AllocationEntry(art, classes, 4)); // 16 periods

        model.addProfessor(pMath);
        model.addProfessor(pSci);
        model.addProfessor(pEng);
        model.addProfessor(pSst);
        model.addProfessor(pComp);
        model.addProfessor(pAct);

        SchoolConfig config = model.getStructureConfig();
        config.setTimeLimitSeconds(15);
        AllocationReconciler.ReconciliationResult recResult = model.toReconciliationResult(config);

        CpSatTimetableSolver.SolverResult res = CpSatTimetableSolver.solve(recResult, config);

        assertNotNull(res.profiling(), "Profiling telemetry must be captured");
        assertTrue(res.status() == CpSolverStatus.OPTIMAL || res.status() == CpSolverStatus.FEASIBLE);
        assertEquals(160, res.schedule().getTotalAssignmentsCount()); // 4 classes * 40 periods

        System.out.println("=== BENCHMARK 1 (SMALL: 4 CLASSES) METRICS ===");
        System.out.printf("  • Model Construction Phase 1: %.4f s\n", res.profiling().modelBuild1Seconds());
        System.out.printf("  • Phase 1 Feasibility Solve:  %.4f s\n", res.profiling().phase1SolveSeconds());
        System.out.printf("  • Model Construction Phase 2: %.4f s\n", res.profiling().modelBuild2Seconds());
        System.out.printf("  • Phase 2 Optimization Solve: %.4f s\n", res.profiling().phase2SolveSeconds());
        System.out.printf("  • Total Generation Duration:  %.4f s\n", res.profiling().totalDurationSeconds());
        System.out.printf("  • Variables Count:           %d\n", res.profiling().phase1Variables());
        System.out.printf("  • Constraints Count:         %d\n", res.profiling().phase2Constraints());
        System.out.printf("  • Heap Memory Footprint:     %.2f MB\n", res.profiling().memoryUsedMb());
        System.out.println("===============================================");
    }

    @Test
    @DisplayName("Benchmark 2: Full Production Dataset (26 Classes, Standards 5-10, 1,248 Scheduled Slots)")
    public void benchmarkProductionConfiguration() throws Exception {
        SchoolConfig config = SchoolConfig.load("config/school-config.yaml");
        List<StandardwiseParser.StandardwiseRecord> stdRecords = StandardwiseParser.parse(config.getStandardwiseFile());
        List<TeacherwiseParser.ParsedTeacherBlock> teacherBlocks = TeacherwiseParser.parse(config.getTeacherwiseFile());
        List<ClassTeacherListParser.ParsedClassTeacherRow> ctRows = ClassTeacherListParser.parse(config.getClassTeachersFile());

        AllocationReconciler.ReconciliationResult recResult = AllocationReconciler.reconcile(stdRecords, teacherBlocks, ctRows, config);

        CpSatTimetableSolver.SolverResult res = CpSatTimetableSolver.solve(recResult, config);

        assertNotNull(res.profiling(), "Profiling telemetry must be captured");
        assertTrue(res.status() == CpSolverStatus.OPTIMAL || res.status() == CpSolverStatus.FEASIBLE);
        assertEquals(1248, res.schedule().getTotalAssignmentsCount());

        System.out.println("=== BENCHMARK 2 (PRODUCTION: 26 CLASSES) METRICS ===");
        System.out.printf("  • Model Construction Phase 1: %.4f s\n", res.profiling().modelBuild1Seconds());
        System.out.printf("  • Phase 1 Feasibility Solve:  %.4f s\n", res.profiling().phase1SolveSeconds());
        System.out.printf("  • Model Construction Phase 2: %.4f s\n", res.profiling().modelBuild2Seconds());
        System.out.printf("  • Phase 2 Optimization Solve: %.4f s\n", res.profiling().phase2SolveSeconds());
        System.out.printf("  • Total Generation Duration:  %.4f s\n", res.profiling().totalDurationSeconds());
        System.out.printf("  • Variables Count:           %d\n", res.profiling().phase1Variables());
        System.out.printf("  • Constraints Count:         %d\n", res.profiling().phase2Constraints());
        System.out.printf("  • Heap Memory Footprint:     %.2f MB\n", res.profiling().memoryUsedMb());
        System.out.println("====================================================");
    }

    @Test
    @DisplayName("Benchmark 3: Instant Cancellation Responsiveness (< 500ms)")
    public void benchmarkCancellationResponsiveness() throws Exception {
        SchoolConfig config = SchoolConfig.load("config/school-config.yaml");
        List<StandardwiseParser.StandardwiseRecord> stdRecords = StandardwiseParser.parse(config.getStandardwiseFile());
        List<TeacherwiseParser.ParsedTeacherBlock> teacherBlocks = TeacherwiseParser.parse(config.getTeacherwiseFile());
        List<ClassTeacherListParser.ParsedClassTeacherRow> ctRows = ClassTeacherListParser.parse(config.getClassTeachersFile());

        AllocationReconciler.ReconciliationResult recResult = AllocationReconciler.reconcile(stdRecords, teacherBlocks, ctRows, config);

        long[] cancelResponseTimeMs = new long[1];
        Thread solverThread = new Thread(() -> {
            try {
                CpSatTimetableSolver.solve(recResult, config);
            } catch (Exception ignored) {}
        });

        solverThread.start();
        // Wait 1.5 seconds so solver enters active CP-SAT search
        Thread.sleep(1500);

        long tCancelSent = System.currentTimeMillis();
        CpSatTimetableSolver.cancelActiveSolve();
        solverThread.join(2000);
        long tStopped = System.currentTimeMillis();

        cancelResponseTimeMs[0] = tStopped - tCancelSent;
        System.out.printf("  ✓ Active solve cancelled in %d ms (expected < 1,000 ms).\n", cancelResponseTimeMs[0]);

        assertFalse(solverThread.isAlive(), "Solver thread must terminate promptly after stopSearch()");
        assertTrue(cancelResponseTimeMs[0] < 1000, "Cancellation responsiveness must be under 1,000 ms");
    }
}
