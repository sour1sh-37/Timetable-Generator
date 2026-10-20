package com.sies.timetable.solver;

import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.AllocationReconciler;

import java.util.*;

/**
 * Builds and formulates Soft Constraints (S1 through S6) as objective penalty expressions in CP-SAT.
 */
public class SoftConstraintBuilder {

    public static LinearExpr buildObjective(
            CpModel model,
            IntVar[][][][] x,
            AllocationReconciler.ReconciliationResult recResult,
            SchoolConfig config
    ) {
        int numClasses = recResult.classes().size();
        int numDays = config.getDays().size();
        int maxPeriods = config.getMaxDailyPeriods();

        int wS1 = config.getWeight("S1", 100);
        int wS2 = config.getWeight("S2", 80);
        int wS3 = config.getWeight("S3", 50);
        int wS4 = config.getWeight("S4", 40);
        int wS5 = config.getWeight("S5", 30);
        int wS6 = config.getWeight("S6", 20);

        LinearExprBuilder objective = LinearExpr.newBuilder();

        // Map allocations per class
        Map<Integer, List<TeachingAllocation>> classAllocMap = new HashMap<>();
        for (TeachingAllocation a : recResult.allocations()) {
            int cIdx = recResult.classes().indexOf(a.getSchoolClass());
            classAllocMap.computeIfAbsent(cIdx, k -> new ArrayList<>()).add(a);
        }

        // -------------------------------------------------------------
        // S1: Subject Lectures Evenly Distributed Throughout the Week
        // -------------------------------------------------------------
        if (config.isSoftConstraintEnabled("S1") && wS1 > 0) {
            for (int c = 0; c < numClasses; c++) {
                List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
                for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                    TeachingAllocation a = allocs.get(aIdx);
                    int k = a.getPeriodCount();
                    int targetMax = (k <= numDays) ? 1 : 2;

                    for (int d = 0; d < numDays; d++) {
                        int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                        LinearExprBuilder daySum = LinearExpr.newBuilder();
                        for (int p = 0; p < numPeriodsForDay; p++) {
                            daySum.add(x[c][d][p][aIdx]);
                        }
                        IntVar dayLoad = model.newIntVar(0, Math.max(1, numPeriodsForDay), "dayLoad_c" + c + "_a" + aIdx + "_d" + d);
                        model.addEquality(dayLoad, daySum.build());

                        IntVar excess = model.newIntVar(0, Math.max(1, numPeriodsForDay), "excess_c" + c + "_a" + aIdx + "_d" + d);
                        model.addGreaterOrEqual(excess, LinearExpr.newBuilder().add(dayLoad).add(-targetMax).build());
                        model.addGreaterOrEqual(excess, 0);

                        objective.addTerm(excess, wS1);
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // S2: Non-Consecutive Days for 2-Period Subjects
        // -------------------------------------------------------------
        if (config.isSoftConstraintEnabled("S2") && wS2 > 0) {
            for (int c = 0; c < numClasses; c++) {
                List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
                for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                    TeachingAllocation a = allocs.get(aIdx);
                    if (a.getPeriodCount() == 2) {
                        IntVar[] hasDay = new IntVar[numDays];
                        for (int d = 0; d < numDays; d++) {
                            int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                            hasDay[d] = model.newBoolVar("hasDay_c" + c + "_a" + aIdx + "_d" + d);
                            LinearExprBuilder dSum = LinearExpr.newBuilder();
                            for (int p = 0; p < numPeriodsForDay; p++) {
                                dSum.add(x[c][d][p][aIdx]);
                            }
                            model.addGreaterOrEqual(dSum.build(), hasDay[d]);
                        }

                        for (int d = 0; d < numDays - 1; d++) {
                            IntVar adjacent = model.newBoolVar("adj_c" + c + "_a" + aIdx + "_d" + d);
                            model.addGreaterOrEqual(
                                    LinearExpr.newBuilder().add(adjacent).add(1).build(),
                                    LinearExpr.newBuilder().add(hasDay[d]).add(hasDay[d + 1]).build()
                            );
                            objective.addTerm(adjacent, wS2);
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // S4: Teacher Daily Workload Balance
        // -------------------------------------------------------------
        if (config.isSoftConstraintEnabled("S4") && wS4 > 0) {
            for (Teacher teacher : recResult.teachers()) {
                int weeklyLoad = teacher.getTotalWorkload();
                if (weeklyLoad > numDays) {
                    int avgDaily = Math.round((float) weeklyLoad / Math.max(1, numDays));

                    for (int d = 0; d < numDays; d++) {
                        int numPeriodsForDay = config.getDailyPeriods(config.getDays().get(d));
                        LinearExprBuilder teacherDaySum = LinearExpr.newBuilder();
                        for (int c = 0; c < numClasses; c++) {
                            List<TeachingAllocation> allocs = classAllocMap.getOrDefault(c, Collections.emptyList());
                            for (int aIdx = 0; aIdx < allocs.size(); aIdx++) {
                                if (allocs.get(aIdx).getTeacher().equals(teacher)) {
                                    for (int p = 0; p < numPeriodsForDay; p++) {
                                        teacherDaySum.add(x[c][d][p][aIdx]);
                                    }
                                }
                            }
                        }

                        IntVar tDayLoad = model.newIntVar(0, Math.max(1, numPeriodsForDay), "tDayLoad_" + teacher.getCode() + "_d" + d);
                        model.addEquality(tDayLoad, teacherDaySum.build());

                        IntVar dev = model.newIntVar(0, Math.max(1, numPeriodsForDay), "dev_" + teacher.getCode() + "_d" + d);
                        model.addGreaterOrEqual(dev, LinearExpr.newBuilder().add(tDayLoad).add(-avgDaily).build());
                        model.addGreaterOrEqual(dev, LinearExpr.newBuilder().addTerm(tDayLoad, -1).add(avgDaily).build());

                        objective.addTerm(dev, wS4);
                    }
                }
            }
        }

        return objective.build();
    }
}
