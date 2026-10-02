package com.sies.timetable;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.exporter.PdfTimetableExporter;
import com.sies.timetable.model.*;
import com.sies.timetable.model.ManualTimetableModel.AllocationEntry;
import com.sies.timetable.model.ManualTimetableModel.ProfessorEntry;
import com.sies.timetable.model.ManualTimetableModel.ValidationReport;
import com.sies.timetable.reconciliation.AllocationReconciler;
import com.sies.timetable.solver.CpSatTimetableSolver;
import com.sies.timetable.validator.ScheduleValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicStructureSolverTest {

    @Test
    public void testCapacityValidationLogic() {
        ManualTimetableModel model = new ManualTimetableModel();

        // 1. Available 48, Required 44 -> VALID (4 spare periods)
        for (Day d : Day.values()) model.setDailyPeriods(d, 8);
        model.setWeeklyRequiredPeriodsPerClass(44);
        ValidationReport r1 = model.validate();
        assertTrue(r1.notices().stream().anyMatch(n -> n.contains("4 periods per class will be free/spare slots")),
                "Should notify about 4 spare periods.");

        // 2. Available 44 (Mon-Fri 8, Sat 4), Required 48 -> INVALID (deficit 4)
        for (Day d : Day.values()) {
            model.setDailyPeriods(d, d == Day.SATURDAY ? 4 : 8);
        }
        model.setWeeklyRequiredPeriodsPerClass(48);
        ValidationReport r2 = model.validate();
        assertFalse(r2.isValid(), "Capacity should be invalid when Available (44) < Required (48).");
        assertTrue(r2.errors().stream().anyMatch(e -> e.contains("Deficit: 4 periods")),
                "Should report deficit of 4 periods.");
    }

    @Test
    public void testSolverWithSparePeriodsAndSaturdayFourPeriods(@TempDir Path tempDir) throws Exception {
        // Setup manual model: 2 standards (5 and 6), 2 divisions each -> 4 classes (5-A, 5-B, 6-A, 6-B)
        ManualTimetableModel model = new ManualTimetableModel();
        model.setStandardsRange(5, 6);
        model.setDivisionCount(5, 2);
        model.setDivisionCount(6, 2);
        assertEquals(4, model.getGeneratedClasses().size());

        // Mon-Fri: 8 periods, Sat: 4 periods -> Available = 44 periods
        for (Day d : Day.values()) {
            model.setDailyPeriods(d, d == Day.SATURDAY ? 4 : 8);
        }
        assertEquals(44, model.getTotalAvailablePeriods());

        // We require 40 periods per class -> exactly 4 spare periods per class!
        // This simultaneously tests:
        // 1. Saturday = 4 periods
        // 2. Available (44) > Required (40) with spare periods
        int weeklyReq = 40;
        model.setWeeklyRequiredPeriodsPerClass(weeklyReq);

        // Add Subjects
        Subject math = new Subject("MATHS", SubjectCategory.CORE_ACADEMIC, false);
        Subject sci = new Subject("SCIENCE", SubjectCategory.CORE_ACADEMIC, false);
        Subject eng = new Subject("ENGLISH", SubjectCategory.CORE_ACADEMIC, false);
        Subject sst = new Subject("SST", SubjectCategory.CORE_ACADEMIC, false);
        Subject comp = new Subject("COMP", SubjectCategory.LAB, true); // Lab exclusivity
        Subject pe = new Subject("P.E", SubjectCategory.ACTIVITY, false);
        Subject art = new Subject("ART", SubjectCategory.ACTIVITY, false);

        model.addSubject(math);
        model.addSubject(sci);
        model.addSubject(eng);
        model.addSubject(sst);
        model.addSubject(comp);
        model.addSubject(pe);
        model.addSubject(art);

        // Add 6 Professors with allocations totaling 40 periods for each of the 4 classes:
        // Math: 7 periods, Sci: 7 periods, Eng: 7 periods, SST: 7 periods, Comp: 4 periods, PE: 4 periods, Art: 4 periods = 40 periods!
        List<SchoolClass> allClasses = model.getGeneratedClasses();
        List<SchoolClass> group1 = allClasses.subList(0, 2); // 5-A, 5-B
        List<SchoolClass> group2 = allClasses.subList(2, 4); // 6-A, 6-B

        ProfessorEntry pMath = new ProfessorEntry("Prof Mathematics", "T_MATH", false);
        pMath.addAllocation(new AllocationEntry(math, allClasses, 7)); // 4 * 7 = 28 periods
        pMath.setClassTeacherOf(allClasses.get(0)); // 5-A class teacher

        ProfessorEntry pSci = new ProfessorEntry("Prof Science", "T_SCI", false);
        pSci.addAllocation(new AllocationEntry(sci, allClasses, 7)); // 28 periods
        pSci.setClassTeacherOf(allClasses.get(1)); // 5-B class teacher

        ProfessorEntry pEng = new ProfessorEntry("Prof English", "T_ENG", false);
        pEng.addAllocation(new AllocationEntry(eng, allClasses, 7)); // 28 periods
        pEng.setClassTeacherOf(allClasses.get(2)); // 6-A class teacher

        ProfessorEntry pSst = new ProfessorEntry("Prof SocialStudies", "T_SST", false);
        pSst.addAllocation(new AllocationEntry(sst, allClasses, 7)); // 28 periods
        pSst.setClassTeacherOf(allClasses.get(3)); // 6-B class teacher

        ProfessorEntry pComp = new ProfessorEntry("Prof Computer", "T_COMP", false);
        pComp.addAllocation(new AllocationEntry(comp, allClasses, 4)); // 16 periods (Lab required)

        ProfessorEntry pAct = new ProfessorEntry("Prof Activities", "T_ACT", false);
        pAct.addAllocation(new AllocationEntry(pe, allClasses, 4)); // 16 periods
        pAct.addAllocation(new AllocationEntry(art, allClasses, 4)); // 16 periods

        model.addProfessor(pMath);
        model.addProfessor(pSci);
        model.addProfessor(pEng);
        model.addProfessor(pSst);
        model.addProfessor(pComp);
        model.addProfessor(pAct);

        // Validation Check
        ValidationReport vRep = model.validate();
        assertTrue(vRep.isValid(), "Model should be valid: " + vRep.errors());
        assertTrue(vRep.notices().stream().anyMatch(n -> n.contains("4 periods per class will be free/spare slots")));

        // Convert to domain model and solve
        SchoolConfig config = model.getStructureConfig();
        config.setOutputDir(tempDir.toString());
        AllocationReconciler.ReconciliationResult recResult = model.toReconciliationResult(config);

        CpSatTimetableSolver.SolverResult solverResult = CpSatTimetableSolver.solve(recResult, config);
        assertNotNull(solverResult.schedule(), "Solver must find a feasible schedule with dynamic Saturday 4 periods & spare slots.");

        Schedule schedule = solverResult.schedule();

        // 1. Verify exact total periods scheduled per class == 40
        for (SchoolClass sc : allClasses) {
            List<ScheduleAssignment> cAssigns = schedule.getAssignmentsForClass(sc);
            assertEquals(40, cAssigns.size(), "Class " + sc.getDisplayName() + " must have exactly 40 scheduled lectures.");
        }

        // 2. Verify Saturday has NO assignments beyond period 4
        for (SchoolClass sc : allClasses) {
            for (int p = 5; p <= 8; p++) {
                Period period = config.getPeriods().get(p);
                ScheduleAssignment sa = schedule.getAssignment(sc, Day.SATURDAY, period);
                assertNull(sa, "Saturday Period " + p + " must not have any scheduled lecture for " + sc.getDisplayName());
            }
        }

        // 3. Post-Solve Validation: Verify 0 Hard Constraint Violations!
        ScheduleValidator.ScheduleValidationResult valResult = ScheduleValidator.validate(
                schedule, recResult, config, Collections.emptyList()
        );
        assertTrue(valResult.allHardConstraintsPassed(), "All 10 Hard Constraints must pass with 0 violations.");
        assertEquals(0, valResult.totalHardViolations());

        // 4. Test PDF Exporter: Generates individual PDF for each of the 4 classes
        File pdfDir = new File(tempDir.toFile(), "pdf");
        List<File> pdfFiles = PdfTimetableExporter.exportAllClasses(schedule, recResult, config, pdfDir);
        assertEquals(4, pdfFiles.size(), "Must generate exactly 4 PDF files (1 per class).");

        for (File pdf : pdfFiles) {
            assertTrue(pdf.exists(), "PDF file must exist: " + pdf.getName());
            assertTrue(pdf.length() > 500, "PDF file must not be empty (" + pdf.length() + " bytes): " + pdf.getName());
        }

        // Specific file names check
        assertTrue(new File(pdfDir, "5-A_Timetable.pdf").exists());
        assertTrue(new File(pdfDir, "5-B_Timetable.pdf").exists());
        assertTrue(new File(pdfDir, "6-A_Timetable.pdf").exists());
        assertTrue(new File(pdfDir, "6-B_Timetable.pdf").exists());
    }
}
