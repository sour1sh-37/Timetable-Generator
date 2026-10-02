package com.sies.timetable;

import com.sies.timetable.model.SchoolClass;
import com.sies.timetable.model.Teacher;
import com.sies.timetable.service.TimetableService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class TimetableServiceTest {

    private static TimetableService.GenerationResult genResult;

    @BeforeAll
    public static void setUp() {
        File folder = new File(".");
        TimetableService.FolderDetectionResult detection = TimetableService.detectInputFiles(folder);
        assertTrue(detection.isValid(), "Folder detection must find all required input files in current workspace.");

        genResult = TimetableService.generate(detection, null);
        assertTrue(genResult.isSuccess(), "TimetableService generation must succeed: " + genResult.errorMessage());
    }

    @Test
    public void testFolderDetection() {
        File folder = new File(".");
        TimetableService.FolderDetectionResult detection = TimetableService.detectInputFiles(folder);
        assertTrue(detection.isValid());
        assertNotNull(detection.standardwiseFile());
        assertNotNull(detection.teacherwiseFile());
        assertNotNull(detection.classTeachersFile());
        assertNotNull(detection.constraintsFile());
    }

    @Test
    public void testClassGridFormatting() {
        SchoolClass va = genResult.reconciliationResult().classes().stream()
                .filter(c -> c.getStandard() == 5 && c.getDivision().equals("A"))
                .findFirst()
                .orElse(null);

        assertNotNull(va, "Class 5-A must exist.");
        TimetableService.TimetableGrid grid = TimetableService.getClassGrid(
                genResult.schedule(), va, genResult.reconciliationResult(), genResult.config()
        );

        assertNotNull(grid);
        assertTrue(grid.title().contains("V/A") || grid.title().contains("V-A"));
        assertEquals(6, grid.rowLabels().length, "Must have 6 day rows (Mon..Sat).");
        assertEquals(9, grid.headers().length - 1, "Must have 9 slot columns (4 pre-recess, 1 recess, 4 post-recess).");

        // Verify Recess at column index 4
        for (int d = 0; d < 6; d++) {
            assertTrue(grid.cells()[d][4].isRecess(), "Column 4 must be RECESS on day " + d);
            assertEquals("RECESS", grid.cells()[d][4].mainText());
        }

        // Verify non-recess cells have subjects and teachers
        for (int d = 0; d < 6; d++) {
            for (int p = 0; p < 9; p++) {
                if (p != 4) {
                    assertFalse(grid.cells()[d][p].mainText().isEmpty(), "Class cell must have subject at d=" + d + ", p=" + p);
                    assertFalse(grid.cells()[d][p].subText().isEmpty(), "Class cell must have teacher code at d=" + d + ", p=" + p);
                }
            }
        }
    }

    @Test
    public void testTeacherGridFormatting() {
        Teacher aak = genResult.reconciliationResult().teachers().stream()
                .filter(t -> t.getCode().equals("AAK"))
                .findFirst()
                .orElse(null);

        assertNotNull(aak, "Teacher AAK must exist.");
        TimetableService.TimetableGrid grid = TimetableService.getTeacherGrid(
                genResult.schedule(), aak, genResult.config()
        );

        assertNotNull(grid);
        assertTrue(grid.title().contains("Teacher AAK") || grid.title().contains("AAK"));
        assertEquals(6, grid.rowLabels().length);

        // Verify Recess at column 4
        for (int d = 0; d < 6; d++) {
            assertTrue(grid.cells()[d][4].isRecess());
        }

        // Count teaching periods for AAK
        int teachingCount = 0;
        for (int d = 0; d < 6; d++) {
            for (int p = 0; p < 9; p++) {
                if (p != 4 && !grid.cells()[d][p].isFree()) {
                    teachingCount++;
                }
            }
        }
        assertEquals(aak.getTotalWorkload(), teachingCount, "Grid teaching count must equal teacher total workload.");
    }
}
