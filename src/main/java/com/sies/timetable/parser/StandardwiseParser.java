package com.sies.timetable.parser;

import com.sies.timetable.model.SchoolClass;
import com.sies.timetable.reconciliation.NormalizationDictionary;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * Parses Standardwise allotment 26-27 (4).xlsx across sheets V, VI, VII, VIII, IX, X.
 */
public class StandardwiseParser {

    public record StandardwiseRecord(
            String standardStr,
            String divisionStr,
            String canonicalClass,
            String rawSubject,
            String normalizedSubject,
            int periods,
            String rawTeacher,
            String normalizedTeacherCode
    ) {}

    public static List<StandardwiseRecord> parse(String filePath) throws Exception {
        List<StandardwiseRecord> records = new ArrayList<>();
        List<String> sheetNames = List.of("V", "VI", "VII", "VIII", "IX", "X");

        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = WorkbookFactory.create(fis)) {

            for (String sname : sheetNames) {
                Sheet sheet = workbook.getSheet(sname);
                if (sheet == null) continue;

                int standardInt = SchoolClass.parseStandard(sname);

                // Find division columns
                Map<Integer, String> divCols = new TreeMap<>();
                for (int c = 2; c < sheet.getRow(3).getLastCellNum(); c++) {
                    for (int r = 2; r <= 4; r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;
                        String val = ExcelParserHelper.getStringValue(row.getCell(c));
                        if (val != null && (val.contains(sname) || val.contains("/") || val.matches("[A-E]"))) {
                            String divLetter = val.replaceAll("[^A-Ea-e]", "").toUpperCase();
                            if (!divLetter.isEmpty()) {
                                divCols.put(c, divLetter);
                                break;
                            }
                        }
                    }
                }

                // Read subject rows
                int lastRow = sheet.getLastRowNum();
                for (int r = 3; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    String rawSubj = ExcelParserHelper.getStringValue(row.getCell(0));
                    Integer periods = ExcelParserHelper.getIntValue(row.getCell(1));

                    if (rawSubj == null || periods == null || periods <= 0) continue;
                    String upperSubj = rawSubj.trim().toUpperCase();
                    if (upperSubj.equals("SUBJECT") || upperSubj.contains("TOTAL")) continue;

                    String normSubj = NormalizationDictionary.normalizeSubject(rawSubj);

                    for (Map.Entry<Integer, String> entry : divCols.entrySet()) {
                        int colIdx = entry.getKey();
                        String divLetter = entry.getValue();
                        String rawTeacher = ExcelParserHelper.getStringValue(row.getCell(colIdx));

                        if (rawTeacher != null && !rawTeacher.trim().isEmpty() && !rawTeacher.trim().equals("---")) {
                            String normTeacher = NormalizationDictionary.normalizeTeacherCode(rawTeacher);
                            SchoolClass sc = new SchoolClass(standardInt, divLetter);
                            records.add(new StandardwiseRecord(
                                    sname, divLetter, sc.getCanonicalName(), rawSubj.trim(), normSubj, periods, rawTeacher.trim(), normTeacher
                            ));
                        }
                    }
                }
            }
        }
        return records;
    }
}
