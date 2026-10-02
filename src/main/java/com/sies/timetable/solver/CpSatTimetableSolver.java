package com.sies.timetable.solver;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.AllocationReconciler;

import java.util.*;

/**
 * High-performance Two-Phase Constraint Programming (CP-SAT) solver for the SIES timetable:
 * Phase 1: Constraint satisfaction finding a conflict-free feasible baseline (H1–H10).
 * Phase 2: Warm-started optimization minimizing soft constraint penalty objectives (S1–S6).
 */
public class CpSatTimetableSolver {

    public record SolverResult(
            Schedule schedule,
            CpSolverStatus status,
            double solveTimeSeconds,
            double objectiveValue,
            long totalVariables,
            long totalConstraints
    ) {}

    public static SolverResult solve(AllocationReconciler.ReconciliationResult recResult, SchoolConfig config) throws Exception {
        Loader.loadNativeLibraries();

        int numClasses = recResult.classes().size();
        int numDays = config.getDays().size();
        int numPeriods = config.getPeriodsPerDay();

        Map<Integer, List<TeachingAllocation>> classAllocMap = new HashMap<>();
        for (TeachingAllocation a : recResult.allocations()) {
            int cIdx = recResult.classes().indexOf(a.getSchoolClass());
            classAllocMap.computeIfAbsent(cIdx, k -> new ArrayList<>()).add(a);
        }

        // =============================================================
        // PHASE 1: Find Feasible Schedule Baseline (H1 through H10)
        // =============================================================
        System.out.println("  [Phase 1] Solving pure hard constraint satisfaction baseline (H1–H10)...");
        CpModel satModel = new CpModel();
        IntVar[][][][] xSat = new IntVar[numClasses][numDays][][];
        long varCount = 0;

        for (int c = 0; c < numClasses; c++) {
            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
            xSat[c] = new IntVar[numDays][][];
            for (int d = 0; d < numDays; d++) {
                int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                xSat[c][d] = new IntVar[numPeriodsForDay][allocs.size()];
                for (int p = 0; p < numPeriodsForDay; p++) {
                    xSat[c][d][p] = new IntVar[allocs.size()];
                    for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                        xSat[c][d][p][aIdx] = satModel.newBoolVar(String.format("xs_c%d_d%d_p%d_a%d", c, d, p, aIdx));
                        varCount++;
                    }
                }
            }
        }

        HardConstraintBuilder.applyHardConstraints(satModel, xSat, recResult, config);

        CpSolver satSolver = new CpSolver();
        satSolver.getParameters().setMaxTimeInSeconds(config.getTimeLimitSeconds());
        satSolver.getParameters().setNumSearchWorkers(config.getNumSearchWorkers());
        satSolver.getParameters().setRandomSeed(config.getRandomSeed());

        long t0 = System.currentTimeMillis();
        CpSolverStatus satStatus = satSolver.solve(satModel);
        double satDuration = (System.currentTimeMillis() - t0) / 1000.0;

        if (satStatus != CpSolverStatus.OPTIMAL && satStatus != CpSolverStatus.FEASIBLE) {
            throw new IllegalStateException("Phase 1 satisfaction solve failed. Status: " + satStatus);
        }
        System.out.printf("  ✓ [Phase 1] Feasible baseline found in %.3f seconds.\n", satDuration);

        // =============================================================
        // PHASE 2: Optimization with Soft Constraints & Warm Start Hints
        // =============================================================
        System.out.println("  [Phase 2] Optimizing soft constraints with warm start (S1–S6)...");
        CpModel optModel = new CpModel();
        IntVar[][][][] xOpt = new IntVar[numClasses][numDays][][];

        for (int c = 0; c < numClasses; c++) {
            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
            xOpt[c] = new IntVar[numDays][][];
            for (int d = 0; d < numDays; d++) {
                int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                xOpt[c][d] = new IntVar[numPeriodsForDay][allocs.size()];
                for (int p = 0; p < numPeriodsForDay; p++) {
                    xOpt[c][d][p] = new IntVar[allocs.size()];
                    for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                        xOpt[c][d][p][aIdx] = optModel.newBoolVar(String.format("xo_c%d_d%d_p%d_a%d", c, d, p, aIdx));

                        // Add search hint from Phase 1 solution
                        long hintVal = satSolver.value(xSat[c][d][p][aIdx]);
                        optModel.addHint(xOpt[c][d][p][aIdx], hintVal);
                    }
                }
            }
        }

        HardConstraintBuilder.applyHardConstraints(optModel, xOpt, recResult, config);
        LinearExpr objective = SoftConstraintBuilder.buildObjective(optModel, xOpt, recResult, config);
        optModel.minimize(objective);

        CpSolver optSolver = new CpSolver();
        optSolver.getParameters().setMaxTimeInSeconds(Math.max(15, config.getTimeLimitSeconds() / 2));
        optSolver.getParameters().setNumSearchWorkers(config.getNumSearchWorkers());
        optSolver.getParameters().setRandomSeed(config.getRandomSeed());

        long t1 = System.currentTimeMillis();
        CpSolverStatus optStatus = optSolver.solve(optModel);
        double totalDuration = (System.currentTimeMillis() - t0) / 1000.0;

        boolean optSucceeded = (optStatus == CpSolverStatus.OPTIMAL || optStatus == CpSolverStatus.FEASIBLE);
        CpSolver activeSolver = optSucceeded ? optSolver : satSolver;
        IntVar[][][][] activeVars = optSucceeded ? xOpt : xSat;
        CpSolverStatus finalStatus = optSucceeded ? optStatus : satStatus;

        // Extract Schedule from Best Solution
        List<ScheduleAssignment> assignments = new ArrayList<>();
        for (int c = 0; c < numClasses; c++) {
            SchoolClass sc = recResult.classes().get(c);
            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());

            for (int d = 0; d < numDays; d++) {
                Day day = config.getDays().get(d);
                int numPeriodsForDay = config.getDailyPeriods(day);
                for (int p = 0; p < numPeriodsForDay; p++) {
                    Period period = config.getPeriods().get(p + 1);
                    TimeSlot slot = new TimeSlot(day, period);

                    for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                        if (activeSolver.value(activeVars[c][d][p][aIdx]) == 1) {
                            TeachingAllocation alloc = allocs.get(aIdx);
                            assignments.add(new ScheduleAssignment(
                                    sc,
                                    slot,
                                    alloc.getTeacher(),
                                    alloc.getSubject(),
                                    alloc
                            ));
                            break;
                        }
                    }
                }
            }
        }

        Schedule schedule = new Schedule(assignments);

        return new SolverResult(
                schedule,
                finalStatus,
                totalDuration,
                optSucceeded ? optSolver.objectiveValue() : 0.0,
                varCount,
                optModel.model().getConstraintsCount()
        );
    }
}
