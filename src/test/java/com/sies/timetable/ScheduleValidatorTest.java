package com.sies.timetable;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.parser.ConstraintsCsvParser;
import com.sies.timetable.service.TimetableService;
import com.sies.timetable.validator.ScheduleValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScheduleValidatorTest {

    private static TimetableService.GenerationResult genResult;

    @BeforeAll
    public static void setUp() {
        File folder = new File(".");
        TimetableService.FolderDetectionResult detection = TimetableService.detectInputFiles(folder);
        assertTrue(detection.isValid());

        genResult = TimetableService.generate(detection, null);
        assertTrue(genResult.isSuccess());
    }

    @Test
    public void testValidScheduleHasZeroHardViolations() {
        ScheduleValidator.ScheduleValidationResult valResult = genResult.validationResult();
        assertNotNull(valResult);
        assertTrue(valResult.allHardConstraintsPassed(), "Baseline generated schedule must pass all hard constraints.");
        assertEquals(0, valResult.totalHardViolations(), "Total hard violations must be 0.");
        assertEquals(0, valResult.getHardViolations().size(), "Hard violation list must be empty.");
        assertTrue(valResult.overallQualityIndex() >= 50.0, "Quality index must be >= 50.0.");
    }

    @Test
    public void testDiagnosticDetectsArtificialTeacherClashWithExactLocation() throws Exception {
        // Create an artificial schedule by duplicating an assignment for teacher AAK
        Schedule validSchedule = genResult.schedule();
        List<ScheduleAssignment> assignments = new ArrayList<>(validSchedule.getAssignments());

        Teacher aak = genResult.reconciliationResult().teachers().stream()
                .filter(t -> t.getCode().equals("AAK"))
                .findFirst().orElseThrow();

        SchoolClass va = genResult.reconciliationResult().classes().stream()
                .filter(c -> c.getCanonicalName().equals("V-A") || c.getDisplayName().equals("V/A"))
                .findFirst().orElseThrow();

        SchoolClass vb = genResult.reconciliationResult().classes().stream()
                .filter(c -> c.getCanonicalName().equals("V-B") || c.getDisplayName().equals("V/B"))
                .findFirst().orElseThrow();

        Day tuesday = Day.TUESDAY;
        Period p3 = genResult.config().getPeriods().get(3);
        TimeSlot slot = new TimeSlot(tuesday, p3);

        TeachingAllocation allocB = genResult.reconciliationResult().allocations().stream()
                .filter(a -> a.getSchoolClass().equals(vb) && a.getTeacher().equals(aak))
                .findFirst().orElse(null);

        if (allocB != null) {
            // Replace assignment in V-B at Tuesday P3 with AAK
            assignments.removeIf(a -> a.schoolClass().equals(vb) && a.timeSlot().equals(slot));
            assignments.add(new ScheduleAssignment(vb, slot, aak, allocB.getSubject(), allocB));

            // Also ensure V-A at Tuesday P3 is assigned to AAK
            assignments.removeIf(a -> a.schoolClass().equals(va) && a.timeSlot().equals(slot));
            TeachingAllocation allocA = genResult.reconciliationResult().allocations().stream()
                    .filter(a -> a.getSchoolClass().equals(va) && a.getTeacher().equals(aak))
                    .findFirst().orElse(allocB);
            assignments.add(new ScheduleAssignment(va, slot, aak, allocA.getSubject(), allocA));

            Schedule corruptSchedule = new Schedule(assignments);
            List<ConstraintSpec> specs = ConstraintsCsvParser.parse(genResult.config().getConstraintsFile());

            ScheduleValidator.ScheduleValidationResult corruptValResult = ScheduleValidator.validate(
                    corruptSchedule, genResult.reconciliationResult(), genResult.config(), specs
            );

            assertFalse(corruptValResult.allHardConstraintsPassed(), "Validator must detect corrupt schedule.");
            assertTrue(corruptValResult.totalHardViolations() > 0);

            // Find H4 violation
            ScheduleValidator.ViolationDetail h4 = corruptValResult.getHardViolations().stream()
                    .filter(v -> v.constraintId().equals("H4"))
                    .findFirst()
                    .orElse(null);

            assertNotNull(h4, "Must identify H4 violation.");
            assertEquals("H4", h4.constraintId());
            assertEquals("Teacher Clash", h4.constraintName());
            assertEquals("HARD", h4.severity());
            assertEquals("Tuesday", h4.day(), "Must report Tuesday.");
            assertEquals("3", h4.period(), "Must report Period 3.");
            assertTrue(h4.teacher().contains("AAK"), "Must report teacher AAK.");
            assertTrue(h4.conflictingAssignments().contains("V/A") || h4.conflictingAssignments().contains("V-A"));
            assertTrue(h4.reason().contains("same teacher is assigned to multiple classes"));
        }
    }

    @Test
    public void testDiagnosticDetectsArtificialPeriod1PEViolationWithExactLocation() throws Exception {
        Schedule validSchedule = genResult.schedule();
        List<ScheduleAssignment> assignments = new ArrayList<>(validSchedule.getAssignments());

        SchoolClass va = genResult.reconciliationResult().classes().stream()
                .filter(c -> c.getCanonicalName().equals("V-A") || c.getDisplayName().equals("V/A"))
                .findFirst().orElseThrow();

        TeachingAllocation peAlloc = genResult.reconciliationResult().allocations().stream()
                .filter(a -> a.getSchoolClass().equals(va) && a.isPhysicalEducation())
                .findFirst().orElse(null);

        assertNotNull(peAlloc, "P.E. allocation for V-A must exist.");

        Day monday = Day.MONDAY;
        Period p1 = genResult.config().getPeriods().get(1);
        TimeSlot slot = new TimeSlot(monday, p1);

        assignments.removeIf(a -> a.schoolClass().equals(va) && a.timeSlot().equals(slot));
        assignments.add(new ScheduleAssignment(va, slot, peAlloc.getTeacher(), peAlloc.getSubject(), peAlloc));

        Schedule corruptSchedule = new Schedule(assignments);
        List<ConstraintSpec> specs = ConstraintsCsvParser.parse(genResult.config().getConstraintsFile());

        ScheduleValidator.ScheduleValidationResult corruptValResult = ScheduleValidator.validate(
                corruptSchedule, genResult.reconciliationResult(), genResult.config(), specs
        );

        assertFalse(corruptValResult.allHardConstraintsPassed());

        ScheduleValidator.ViolationDetail h6 = corruptValResult.getHardViolations().stream()
                .filter(v -> v.constraintId().equals("H6"))
                .findFirst()
                .orElse(null);

        assertNotNull(h6, "Must identify H6 violation.");
        assertEquals("H6", h6.constraintId());
        assertEquals("HARD", h6.severity());
        assertEquals("Monday", h6.day());
        assertEquals("1", h6.period());
        assertTrue(h6.subject().contains("Physical Education"));
        assertTrue(h6.reason().contains("Period 1"));
    }
}
