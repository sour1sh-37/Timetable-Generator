package com.sies.timetable;

import com.google.ortools.sat.CpSolverStatus;
import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.ConstraintSpec;
import com.sies.timetable.parser.*;
import com.sies.timetable.reconciliation.AllocationReconciler;
import com.sies.timetable.solver.CpSatTimetableSolver;
import com.sies.timetable.validator.ScheduleValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EndToEndSolverTest {

    private static SchoolConfig config;
    private static AllocationReconciler.ReconciliationResult recResult;
    private static List<ConstraintSpec> constraintSpecs;

    @BeforeAll
    public static void setUp() throws Exception {
        config = SchoolConfig.load("config/school-config.yaml");
        constraintSpecs = ConstraintsCsvParser.parse(config.getConstraintsFile());
        List<StandardwiseParser.StandardwiseRecord> stdRecords = StandardwiseParser.parse(config.getStandardwiseFile());
        List<TeacherwiseParser.ParsedTeacherBlock> teacherBlocks = TeacherwiseParser.parse(config.getTeacherwiseFile());
        List<ClassTeacherListParser.ParsedClassTeacherRow> ctRows = ClassTeacherListParser.parse(config.getClassTeachersFile());

        recResult = AllocationReconciler.reconcile(stdRecords, teacherBlocks, ctRows, config);
    }

    @Test
    public void testSolveAndValidateZeroHardViolations() throws Exception {
        CpSatTimetableSolver.SolverResult result = CpSatTimetableSolver.solve(recResult, config);

        assertTrue(result.status() == CpSolverStatus.OPTIMAL || result.status() == CpSolverStatus.FEASIBLE,
                "Solver must find an OPTIMAL or FEASIBLE schedule.");

        assertEquals(1248, result.schedule().getTotalAssignmentsCount(),
                "Total scheduled assignments must be exactly 1,248.");

        ScheduleValidator.ScheduleValidationResult valResult = ScheduleValidator.validate(
                result.schedule(), recResult, config, constraintSpecs
        );

        assertTrue(valResult.allHardConstraintsPassed(),
                "All Hard Constraints (H1-H10) must pass with zero violations! Violations: " + valResult.violations());
        assertEquals(0, valResult.totalHardViolations(),
                "Total hard violations must be 0.");
        assertTrue(valResult.overallQualityIndex() >= 50.0,
                "Overall schedule quality index should be >= 50.0.");
    }
}
