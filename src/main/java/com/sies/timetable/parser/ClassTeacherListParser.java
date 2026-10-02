package com.sies.timetable.parser;

import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.NormalizationDictionary;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * Parses Classteachers and free teachers list 2026-27.xlsx (Sheet 1).
 */
public class ClassTeacherListParser {

    public record ParsedClassTeacherRow(
            String rawClass,
            String rawClassTeacher,
            String normalizedTeacherCode,
            List<String> rawFreeTeachers,
            List<String> normalizedFreeTeacherCodes
    ) {}

    public static List<ParsedClassTeacherRow> parse(String filePath) throws Exception {
        List<ParsedClassTeacherRow> list = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("Class tr and free teacher");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            int lastRow = sheet.getLastRowNum();
            for (int r = 3; r <= lastRow; r++) { // Data starts around row 4 (0-indexed 3)
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String classVal = ExcelParserHelper.getStringValue(row.getCell(0));
                String teacherVal = ExcelParserHelper.getStringValue(row.getCell(1));
                String freeVal = ExcelParserHelper.getStringValue(row.getCell(2));

                if (classVal != null && !classVal.trim().isEmpty() && !classVal.toUpperCase().contains("CLASS")) {
                    String normCode = NormalizationDictionary.normalizeTeacherCode(teacherVal);

                    List<String> freeList = new ArrayList<>();
                    List<String> normFreeList = new ArrayList<>();
                    if (freeVal != null && !freeVal.trim().isEmpty() && !freeVal.equalsIgnoreCase("NONE")) {
                        freeList.add(freeVal.trim());
                        normFreeList.add(NormalizationDictionary.normalizeTeacherCode(freeVal.trim()));
                    }

                    // Check if subsequent rows contain additional free teachers for the same class (merged cell pattern)
                    int nextR = r + 1;
                    while (nextR <= lastRow) {
                        Row nextRow = sheet.getRow(nextR);
                        if (nextRow == null) break;
                        String nextClass = ExcelParserHelper.getStringValue(nextRow.getCell(0));
                        String nextTeacher = ExcelParserHelper.getStringValue(nextRow.getCell(1));
                        String nextFree = ExcelParserHelper.getStringValue(nextRow.getCell(2));

                        if ((nextClass == null || nextClass.trim().isEmpty()) && 
                            (nextTeacher == null || nextTeacher.trim().isEmpty()) && 
                            (nextFree != null && !nextFree.trim().isEmpty())) {
                            freeList.add(nextFree.trim());
                            normFreeList.add(NormalizationDictionary.normalizeTeacherCode(nextFree.trim()));
                            nextR++;
                        } else {
                            break;
                        }
                    }

                    list.add(new ParsedClassTeacherRow(classVal.trim(), teacherVal != null ? teacherVal.trim() : null, normCode, freeList, normFreeList));
                }
            }
        }
        return list;
    }
}
