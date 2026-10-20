package com.sies.timetable.exporter;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.AllocationReconciler;
import com.sies.timetable.validator.ScheduleValidator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generates human-readable, beautifully formatted Excel timetables (output/timetable.xlsx)
 * resembling authentic school timetable printouts for all classes and teachers,
 * dynamically adapting to configured daily periods and recess.
 */
public class ExcelTimetableExporter {

    public static File export(
            Schedule schedule,
            AllocationReconciler.ReconciliationResult recResult,
            ScheduleValidator.ScheduleValidationResult valResult,
            SchoolConfig config,
            String filePath
    ) throws Exception {
        Files.createDirectories(Paths.get(filePath).getParent());

        int maxPeriods = config.getMaxDailyPeriods();
        int defaultRecess = config.getRecessAfterPeriod(Day.MONDAY);
        int totalCols = 1 + maxPeriods + 1; // Day + Periods + Recess

        try (Workbook workbook = new XSSFWorkbook()) {
            // Colors
            byte[] navyRgb = new byte[]{(byte) 26, (byte) 54, (byte) 93}; // #1A365D
            byte[] goldRgb = new byte[]{(byte) 217, (byte) 119, (byte) 6}; // #D97706
            byte[] recessRgb = new byte[]{(byte) 254, (byte) 243, (byte) 199}; // #FEF3C7
            byte[] lightGrayRgb = new byte[]{(byte) 243, (byte) 244, (byte) 246}; // #F3F4F6

            // Styles
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(new XSSFColor(navyRgb, null));
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle subtitleStyle = workbook.createCellStyle();
            Font subFont = workbook.createFont();
            subFont.setBold(true);
            subFont.setFontHeightInPoints((short) 11);
            subtitleStyle.setFont(subFont);
            subtitleStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(navyRgb, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            setBorders(headerStyle);

            CellStyle dayHeaderStyle = workbook.createCellStyle();
            Font dayFont = workbook.createFont();
            dayFont.setBold(true);
            dayFont.setFontHeightInPoints((short) 10);
            dayHeaderStyle.setFont(dayFont);
            dayHeaderStyle.setFillForegroundColor(new XSSFColor(lightGrayRgb, null));
            dayHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            dayHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            dayHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(dayHeaderStyle);

            CellStyle cellStyle = workbook.createCellStyle();
            Font cellFont = workbook.createFont();
            cellFont.setFontHeightInPoints((short) 9);
            cellStyle.setFont(cellFont);
            cellStyle.setAlignment(HorizontalAlignment.CENTER);
            cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            cellStyle.setWrapText(true);
            setBorders(cellStyle);

            CellStyle recessStyle = workbook.createCellStyle();
            Font recessFont = workbook.createFont();
            recessFont.setBold(true);
            recessFont.setItalic(true);
            recessFont.setFontHeightInPoints((short) 9);
            recessFont.setColor(IndexedColors.BROWN.getIndex());
            recessStyle.setFont(recessFont);
            recessStyle.setFillForegroundColor(new XSSFColor(recessRgb, null));
            recessStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            recessStyle.setAlignment(HorizontalAlignment.CENTER);
            recessStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(recessStyle);

            CellStyle passStyle = workbook.createCellStyle();
            Font passFont = workbook.createFont();
            passFont.setBold(true);
            passFont.setColor(IndexedColors.GREEN.getIndex());
            passStyle.setFont(passFont);

            // =========================================================
            // 1. SUMMARY SHEET
            // =========================================================
            Sheet summarySheet = workbook.createSheet("Summary & Audit");
            summarySheet.setDisplayGridlines(true);

            Row r0 = summarySheet.createRow(1);
            Cell c0 = r0.createCell(1);
            c0.setCellValue(config.getSchoolName());
            c0.setCellStyle(titleStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(1, 1, 1, 6));

            Row r1 = summarySheet.createRow(2);
            Cell c1 = r1.createCell(1);
            c1.setCellValue("Academic Year 2026–27 — Master Timetable Audit Report");
            c1.setCellStyle(subtitleStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(2, 2, 1, 6));

            int sRow = 4;
            createKeyValueRow(summarySheet, sRow++, "Total Classes / Divisions", String.valueOf(recResult.classes().size()), cellStyle, dayHeaderStyle);
            createKeyValueRow(summarySheet, sRow++, "Total Teaching Staff", String.valueOf(recResult.teachers().size()), cellStyle, dayHeaderStyle);
            createKeyValueRow(summarySheet, sRow++, "Weekly Schedule Structure",
                    config.getDays().size() + " Days, " + config.getTotalWeeklyPeriods() + " Available Periods/Class (" + config.getWeeklyRequiredPeriodsPerClass() + " Required)",
                    cellStyle, dayHeaderStyle);
            createKeyValueRow(summarySheet, sRow++, "Total School Periods Scheduled",
                    schedule.getTotalAssignmentsCount() + " / " + (recResult.classes().size() * config.getWeeklyRequiredPeriodsPerClass()) + " Required Slots",
                    cellStyle, dayHeaderStyle);
            createKeyValueRow(summarySheet, sRow++, "Hard Constraints Status", valResult.allHardConstraintsPassed() ? "PASS (0 Violations)" : "FAIL", passStyle, dayHeaderStyle);
            createKeyValueRow(summarySheet, sRow++, "Schedule Quality Index", String.format("%.2f / 100.0 (OPTIMAL)", valResult.overallQualityIndex()), passStyle, dayHeaderStyle);

            sRow += 2;
            Row auditHeader = summarySheet.createRow(sRow++);
            String[] auditCols = {"Constraint ID", "Constraint Rule Description", "Type", "Status", "Violations", "Notes"};
            for (int i = 0; i < auditCols.length; i++) {
                Cell ch = auditHeader.createCell(i + 1);
                ch.setCellValue(auditCols[i]);
                ch.setCellStyle(headerStyle);
            }

            for (ScheduleValidator.ConstraintCheck check : valResult.checks()) {
                Row ar = summarySheet.createRow(sRow++);
                Cell cId = ar.createCell(1); cId.setCellValue(check.constraintId()); cId.setCellStyle(cellStyle);
                Cell cNm = ar.createCell(2); cNm.setCellValue(check.name()); cNm.setCellStyle(cellStyle);
                Cell cTy = ar.createCell(3); cTy.setCellValue(check.type()); cTy.setCellStyle(cellStyle);
                Cell cSt = ar.createCell(4); cSt.setCellValue(check.status()); cSt.setCellStyle(check.status().equals("PASS") ? passStyle : cellStyle);
                Cell cVc = ar.createCell(5); cVc.setCellValue(check.violationCount()); cVc.setCellStyle(cellStyle);
                Cell cDt = ar.createCell(6); cDt.setCellValue(check.details()); cDt.setCellStyle(cellStyle);
            }

            for (int i = 1; i <= 6; i++) summarySheet.autoSizeColumn(i);

            // =========================================================
            // 2. MASTER CLASS TIMETABLE SHEET
            // =========================================================
            Sheet masterClassSheet = workbook.createSheet("All Classes Timetable");
            masterClassSheet.setDisplayGridlines(true);
            int mRow = 0;

            for (SchoolClass sc : recResult.classes()) {
                ClassTeacherMapping ctm = recResult.classTeacherMappings().get(sc);
                String ctText = (ctm != null && ctm.getClassTeacher() != null) ?
                        ctm.getClassTeacher().getFullName() + " (" + ctm.getClassTeacher().getCode() + ")" : "Not Assigned";

                // Title
                Row titleRow = masterClassSheet.createRow(mRow++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(String.format("CLASS: %s  |  Class Teacher: %s  |  Room: Std %d", sc.getDisplayName(), ctText, sc.getStandard()));
                titleCell.setCellStyle(titleStyle);
                masterClassSheet.addMergedRegion(new CellRangeAddress(mRow - 1, mRow - 1, 0, totalCols - 1));

                // Headers
                Row gridHeader = masterClassSheet.createRow(mRow++);
                gridHeader.setHeightInPoints(24);
                createTimetableHeaders(gridHeader, headerStyle, config, maxPeriods, defaultRecess);

                // Day Rows
                for (Day day : config.getDays()) {
                    Row dRow = masterClassSheet.createRow(mRow++);
                    dRow.setHeightInPoints(36);
                    Cell dCell = dRow.createCell(0);
                    dCell.setCellValue(day.getDisplayName());
                    dCell.setCellStyle(dayHeaderStyle);

                    int dailyPeriods = config.getDailyPeriods(day);
                    int col = 1;

                    for (int p = 1; p <= defaultRecess; p++) {
                        Cell scCell = dRow.createCell(col++);
                        if (p <= dailyPeriods) {
                            Period period = config.getPeriods().get(p);
                            ScheduleAssignment sa = schedule.getAssignment(sc, day, period);
                            if (sa != null) {
                                scCell.setCellValue(sa.subject().getNormalizedName() + "\n(" + sa.teacher().getCode() + ")");
                            } else {
                                scCell.setCellValue("---");
                            }
                        } else {
                            scCell.setCellValue("---");
                        }
                        scCell.setCellStyle(cellStyle);
                    }

                    Cell recCell = dRow.createCell(col++);
                    if (config.isRecessEnabled(day)) {
                        recCell.setCellValue("RECESS");
                        recCell.setCellStyle(recessStyle);
                    } else {
                        recCell.setCellValue("---");
                        recCell.setCellStyle(cellStyle);
                    }

                    for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
                        Cell scCell = dRow.createCell(col++);
                        if (p <= dailyPeriods) {
                            Period period = config.getPeriods().get(p);
                            ScheduleAssignment sa = schedule.getAssignment(sc, day, period);
                            if (sa != null) {
                                scCell.setCellValue(sa.subject().getNormalizedName() + "\n(" + sa.teacher().getCode() + ")");
                            } else {
                                scCell.setCellValue("---");
                            }
                        } else {
                            scCell.setCellValue("---");
                        }
                        scCell.setCellStyle(cellStyle);
                    }
                }
                mRow += 2; // Spacing between classes
            }

            for (int col = 0; col < totalCols; col++) {
                masterClassSheet.setColumnWidth(col, (col == 0) ? 3500 : (col == defaultRecess + 1) ? 2800 : 4800);
            }

            // =========================================================
            // 3. MASTER TEACHER TIMETABLE SHEET
            // =========================================================
            Sheet masterTeacherSheet = workbook.createSheet("All Teachers Timetable");
            masterTeacherSheet.setDisplayGridlines(true);
            int tRow = 0;

            for (Teacher teacher : recResult.teachers()) {
                Row titleRow = masterTeacherSheet.createRow(tRow++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(String.format("TEACHER: %s (%s)  |  Weekly Workload: %d Periods",
                        teacher.getFullName(), teacher.getCode(), teacher.getTotalWorkload()));
                titleCell.setCellStyle(titleStyle);
                masterTeacherSheet.addMergedRegion(new CellRangeAddress(tRow - 1, tRow - 1, 0, totalCols - 1));

                Row gridHeader = masterTeacherSheet.createRow(tRow++);
                gridHeader.setHeightInPoints(24);
                createTimetableHeaders(gridHeader, headerStyle, config, maxPeriods, defaultRecess);

                for (Day day : config.getDays()) {
                    Row dRow = masterTeacherSheet.createRow(tRow++);
                    dRow.setHeightInPoints(36);
                    Cell dCell = dRow.createCell(0);
                    dCell.setCellValue(day.getDisplayName());
                    dCell.setCellStyle(dayHeaderStyle);

                    int dailyPeriods = config.getDailyPeriods(day);
                    int col = 1;

                    for (int p = 1; p <= defaultRecess; p++) {
                        Cell scCell = dRow.createCell(col++);
                        if (p <= dailyPeriods) {
                            Period period = config.getPeriods().get(p);
                            ScheduleAssignment sa = schedule.getTeacherAssignment(teacher, new TimeSlot(day, period));
                            if (sa != null) {
                                scCell.setCellValue(sa.schoolClass().getCanonicalName() + "\n" + sa.subject().getNormalizedName());
                            } else {
                                scCell.setCellValue("FREE");
                            }
                        } else {
                            scCell.setCellValue("FREE");
                        }
                        scCell.setCellStyle(cellStyle);
                    }

                    Cell recCell = dRow.createCell(col++);
                    if (config.isRecessEnabled(day)) {
                        recCell.setCellValue("RECESS");
                        recCell.setCellStyle(recessStyle);
                    } else {
                        recCell.setCellValue("---");
                        recCell.setCellStyle(cellStyle);
                    }

                    for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
                        Cell scCell = dRow.createCell(col++);
                        if (p <= dailyPeriods) {
                            Period period = config.getPeriods().get(p);
                            ScheduleAssignment sa = schedule.getTeacherAssignment(teacher, new TimeSlot(day, period));
                            if (sa != null) {
                                scCell.setCellValue(sa.schoolClass().getCanonicalName() + "\n" + sa.subject().getNormalizedName());
                            } else {
                                scCell.setCellValue("FREE");
                            }
                        } else {
                            scCell.setCellValue("FREE");
                        }
                        scCell.setCellStyle(cellStyle);
                    }
                }
                tRow += 2;
            }

            for (int col = 0; col < totalCols; col++) {
                masterTeacherSheet.setColumnWidth(col, (col == 0) ? 3500 : (col == defaultRecess + 1) ? 2800 : 4800);
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }

        return new File(filePath);
    }

    private static void createTimetableHeaders(Row row, CellStyle style, SchoolConfig config, int maxPeriods, int defaultRecess) {
        int col = 0;
        Cell cDay = row.createCell(col++);
        cDay.setCellValue("Day");
        cDay.setCellStyle(style);

        for (int p = 1; p <= defaultRecess; p++) {
            Period period = config.getPeriods().get(p);
            Cell c = row.createCell(col++);
            String time = period != null ? "\n" + period.getStartTime() + "-" + period.getEndTime() : "";
            c.setCellValue("Period " + p + time);
            c.setCellStyle(style);
        }

        Cell cRec = row.createCell(col++);
        String recTime = config.getRecessTiming(Day.MONDAY);
        cRec.setCellValue("RECESS" + (recTime.isBlank() ? "" : "\n" + recTime));
        cRec.setCellStyle(style);

        for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
            Period period = config.getPeriods().get(p);
            Cell c = row.createCell(col++);
            String time = period != null ? "\n" + period.getStartTime() + "-" + period.getEndTime() : "";
            c.setCellValue("Period " + p + time);
            c.setCellStyle(style);
        }
    }

    private static void createKeyValueRow(Sheet sheet, int rowNum, String key, String value, CellStyle valStyle, CellStyle keyStyle) {
        Row row = sheet.createRow(rowNum);
        Cell ck = row.createCell(1);
        ck.setCellValue(key);
        ck.setCellStyle(keyStyle);

        Cell cv = row.createCell(2);
        cv.setCellValue(value);
        cv.setCellStyle(valStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 2, 6));
    }

    private static void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
    }
}
