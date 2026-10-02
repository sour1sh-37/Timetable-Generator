package com.sies.timetable.parser;

import com.sies.timetable.model.SchoolClass;
import com.sies.timetable.reconciliation.NormalizationDictionary;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses TEACHERWISE ALLOTMENT 26-27 (4).xlsx from the detailed 'TEACHERS' sheet.
 */
public class TeacherwiseParser {

    public record TeacherAssignmentRow(
            int standard,
            String divisionRaw,
            List<String> divisions,
            String rawSubject,
            String normalizedSubject,
            int periods,
            int bundles
    ) {}

    public record ParsedTeacherBlock(
            String rawHeader,
            String fullName,
            String rawCode,
            String normalizedCode,
            int totalPeriods,
            int totalBundles,
            List<TeacherAssignmentRow> assignments
    ) {}

    private static final Pattern TEACHER_HDR_PATTERN = Pattern.compile("TEACHER\\s*:\\s*(.*?)(?:\\s*\\(\\s*(\\w+)\\s*\\)\\s*$|$)", Pattern.CASE_INSENSITIVE);

    public static List<ParsedTeacherBlock> parse(String filePath) throws Exception {
        List<ParsedTeacherBlock> blocks = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("TEACHERS");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            int[] headerRows = new int[]{3, 18}; // 0-indexed for Row 4 and Row 19

            for (int hr : headerRows) {
                Row headerRow = sheet.getRow(hr);
                if (headerRow == null) continue;

                for (int c = 0; c < headerRow.getLastCellNum(); c += 5) {
                    Cell cell = headerRow.getCell(c);
                    String headerVal = ExcelParserHelper.getStringValue(cell);
                    if (headerVal == null || !headerVal.toUpperCase().contains("TEACHER")) continue;

                    String fullName = "UNKNOWN";
                    String rawCode = "";

                    Matcher m = TEACHER_HDR_PATTERN.matcher(headerVal.trim());
                    if (m.find()) {
                        fullName = m.group(1).trim();
                        rawCode = m.group(2) != null ? m.group(2).trim() : fullName;
                    } else {
                        fullName = headerVal.replace("TEACHER", "").replace(":", "").trim();
                        rawCode = fullName;
                    }

                    String normCode = NormalizationDictionary.normalizeTeacherCode(rawCode);
                    if (normCode.equals(rawCode) && !fullName.isEmpty()) {
                        normCode = NormalizationDictionary.normalizeTeacherCode(fullName);
                    }

                    // Total row is 13 rows below header
                    int totalRowIdx = hr + 13;
                    Row totalRow = sheet.getRow(totalRowIdx);
                    Integer totPeriods = 0;
                    Integer totBundles = 0;
                    if (totalRow != null) {
                        totPeriods = ExcelParserHelper.getIntValue(totalRow.getCell(c + 3));
                        totBundles = ExcelParserHelper.getIntValue(totalRow.getCell(c + 4));
                    }
                    if (totPeriods == null) totPeriods = 0;
                    if (totBundles == null) totBundles = 0;

                    List<TeacherAssignmentRow> assigns = new ArrayList<>();

                    for (int subR = hr + 2; subR < totalRowIdx; subR++) {
                        Row r = sheet.getRow(subR);
                        if (r == null) continue;

                        Integer stdVal = ExcelParserHelper.getIntValue(r.getCell(c));
                        String divVal = ExcelParserHelper.getStringValue(r.getCell(c + 1));
                        String subjVal = ExcelParserHelper.getStringValue(r.getCell(c + 2));
                        Integer perVal = ExcelParserHelper.getIntValue(r.getCell(c + 3));
                        Integer bunVal = ExcelParserHelper.getIntValue(r.getCell(c + 4));

                        if (subjVal != null && perVal != null && perVal > 0 && stdVal != null) {
                            String normSubj = NormalizationDictionary.normalizeSubject(subjVal);
                            List<String> divs = new ArrayList<>();
                            if (divVal != null) {
                                for (String d : divVal.split(",")) {
                                    String cleanD = d.trim().toUpperCase();
                                    if (!cleanD.isEmpty()) divs.add(cleanD);
                                }
                            }
                            assigns.add(new TeacherAssignmentRow(
                                    stdVal,
                                    divVal,
                                    divs,
                                    subjVal.trim(),
                                    normSubj,
                                    perVal,
                                    bunVal != null ? bunVal : 0
                            ));
                        }
                    }

                    blocks.add(new ParsedTeacherBlock(
                            headerVal.trim(),
                            NormalizationDictionary.getCanonicalTeacherName(normCode),
                            rawCode,
                            normCode,
                            totPeriods,
                            totBundles,
                            assigns
                    ));
                }
            }
        }
        return blocks;
    }
}
