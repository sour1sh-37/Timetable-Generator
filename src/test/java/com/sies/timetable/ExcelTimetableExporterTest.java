package com.sies.timetable;

import com.sies.timetable.service.TimetableService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ExcelTimetableExporterTest {

    @Test
    public void testExcelWorkbookGenerationAndIntegrity() throws Exception {
        File folder = new File(".");
        TimetableService.FolderDetectionResult detection = TimetableService.detectInputFiles(folder);
        assertTrue(detection.isValid());

        TimetableService.GenerationResult genResult = TimetableService.generate(detection, null);
        assertTrue(genResult.isSuccess());

        File excelFile = genResult.excelFile();
        assertNotNull(excelFile);
        assertTrue(excelFile.exists(), "output/timetable.xlsx must exist.");
        assertTrue(excelFile.length() > 5000, "output/timetable.xlsx must not be empty.");

        // Read back workbook with Apache POI
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            assertEquals(3, workbook.getNumberOfSheets(), "Workbook should contain 3 master sheets.");

            Sheet summarySheet = workbook.getSheet("Summary & Audit");
            assertNotNull(summarySheet, "Must have Summary & Audit sheet.");
            assertTrue(summarySheet.getRow(1).getCell(1).getStringCellValue().contains(genResult.config().getSchoolName()));

            Sheet classSheet = workbook.getSheet("All Classes Timetable");
            assertNotNull(classSheet, "Must have All Classes Timetable sheet.");
            assertTrue(classSheet.getRow(0).getCell(0).getStringCellValue().contains("CLASS:"));

            Sheet teacherSheet = workbook.getSheet("All Teachers Timetable");
            assertNotNull(teacherSheet, "Must have All Teachers Timetable sheet.");
            assertTrue(teacherSheet.getRow(0).getCell(0).getStringCellValue().contains("TEACHER:"));
        }
    }
}
