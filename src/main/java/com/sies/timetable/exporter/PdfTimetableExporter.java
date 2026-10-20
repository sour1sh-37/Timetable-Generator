package com.sies.timetable.exporter;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.reconciliation.AllocationReconciler;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

/**
 * Generates beautiful, professional individual PDF timetables for each class division
 * using OpenPDF, respecting dynamic daily period configurations and recess breaks.
 */
public class PdfTimetableExporter {

    // Theme Colors
    private static final Color NAVY = new Color(26, 54, 93);
    private static final Color GOLD = new Color(217, 119, 6);
    private static final Color LIGHT_GRAY = new Color(243, 244, 246);
    private static final Color RECESS_BG = new Color(254, 243, 199);
    private static final Color RECESS_TEXT = new Color(146, 64, 14);
    private static final Color BORDER_COLOR = new Color(209, 213, 219);
    private static final Color ALT_ROW = new Color(249, 250, 251);

    /**
     * Exports individual PDF timetables for all classes into the specified directory.
     * @return List of generated PDF files.
     */
    public static List<File> exportAllClasses(
            Schedule schedule,
            AllocationReconciler.ReconciliationResult recResult,
            SchoolConfig config,
            File outputDir
    ) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        List<File> generatedFiles = new ArrayList<>();
        for (SchoolClass sc : recResult.classes()) {
            String fileName = sc.getStandardDivisionName() + "_Timetable.pdf";
            File pdfFile = new File(outputDir, fileName);
            exportClassTimetable(sc, schedule, recResult, config, pdfFile);
            generatedFiles.add(pdfFile);
        }
        return generatedFiles;
    }

    /**
     * Exports a single class timetable to a PDF file.
     */
    public static void exportClassTimetable(
            SchoolClass sc,
            Schedule schedule,
            AllocationReconciler.ReconciliationResult recResult,
            SchoolConfig config,
            File targetFile
    ) throws Exception {
        Files.createDirectories(targetFile.toPath().getParent());

        // Landscape A4 provides ideal aspect ratio for weekly timetable grids
        Document document = new Document(PageSize.A4.rotate(), 25, 25, 20, 20);
        PdfWriter.getInstance(document, new FileOutputStream(targetFile));
        document.open();

        // Fonts
        Font headerTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, Color.WHITE);
        Font headerSubFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(254, 243, 199));
        Font classInfoFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, NAVY);
        Font colHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font colHeaderSmall = FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(229, 231, 235));
        Font dayFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, NAVY);
        Font subjectFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
        Font teacherFont = FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(75, 85, 99));
        Font recessFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, RECESS_TEXT);
        Font emptyFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.LIGHT_GRAY);
        Font summaryHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font summaryTextFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.DARK_GRAY);

        // 1. Institution Header Banner
        PdfPTable headerBanner = new PdfPTable(1);
        headerBanner.setWidthPercentage(100);
        PdfPCell bannerCell = new PdfPCell();
        bannerCell.setBackgroundColor(NAVY);
        bannerCell.setPadding(8);
        bannerCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pSchool = new Paragraph(config.getSchoolName().toUpperCase(), headerTitleFont);
        pSchool.setAlignment(Element.ALIGN_CENTER);
        bannerCell.addElement(pSchool);

        Paragraph pSub = new Paragraph("ACADEMIC YEAR 2026–2027  |  CLASS TIMETABLE", headerSubFont);
        pSub.setAlignment(Element.ALIGN_CENTER);
        pSub.setSpacingBefore(2);
        bannerCell.addElement(pSub);

        headerBanner.addCell(bannerCell);
        document.add(headerBanner);

        // 2. Class & Class Teacher Info Bar
        ClassTeacherMapping ctm = recResult.classTeacherMappings().get(sc);
        String ctName = (ctm != null && ctm.getClassTeacher() != null)
                ? ctm.getClassTeacher().getFullName() + " (" + ctm.getClassTeacher().getCode() + ")"
                : "Not Assigned";

        PdfPTable infoTable = new PdfPTable(3);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingBefore(6);
        infoTable.setSpacingAfter(8);

        PdfPCell c1 = createInfoCell("CLASS / DIVISION: " + sc.getDisplayName(), Element.ALIGN_LEFT, classInfoFont);
        PdfPCell c2 = createInfoCell("STANDARD: " + sc.getStandard() + " | DIV: " + sc.getDivision(), Element.ALIGN_CENTER, classInfoFont);
        PdfPCell c3 = createInfoCell("CLASS TEACHER: " + ctName, Element.ALIGN_RIGHT, classInfoFont);

        infoTable.addCell(c1);
        infoTable.addCell(c2);
        infoTable.addCell(c3);
        document.add(infoTable);

        // 3. Timetable Grid Table
        int maxPeriods = config.getMaxDailyPeriods();
        int defaultRecess = config.getRecessAfterPeriod(Day.MONDAY);

        // Columns: Day (1) + Periods (1..defaultRecess) + Recess (1) + Periods (defaultRecess+1..maxPeriods)
        int numCols = 1 + maxPeriods + 1;
        float[] colWidths = new float[numCols];
        colWidths[0] = 2.2f; // Day column
        colWidths[defaultRecess + 1] = 1.4f; // Recess column
        for (int i = 1; i <= maxPeriods; i++) {
            int colIdx = (i <= defaultRecess) ? i : i + 1;
            colWidths[colIdx] = 3.0f;
        }

        PdfPTable gridTable = new PdfPTable(colWidths);
        gridTable.setWidthPercentage(100);

        // Column Headers
        PdfPCell dayHeader = new PdfPCell(new Phrase("DAY", colHeaderFont));
        dayHeader.setBackgroundColor(NAVY);
        dayHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        dayHeader.setVerticalAlignment(Element.ALIGN_MIDDLE);
        dayHeader.setPadding(6);
        gridTable.addCell(dayHeader);

        for (int p = 1; p <= defaultRecess; p++) {
            Period period = config.getPeriods().get(p);
            gridTable.addCell(createPeriodHeaderCell(p, period, colHeaderFont, colHeaderSmall));
        }

        String recPdfTime = config.getRecessTiming(Day.MONDAY);
        PdfPCell recessHeader = new PdfPCell(new Phrase("RECESS" + (recPdfTime.isBlank() ? "" : "\n" + recPdfTime), colHeaderFont));
        recessHeader.setBackgroundColor(GOLD);
        recessHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        recessHeader.setVerticalAlignment(Element.ALIGN_MIDDLE);
        recessHeader.setPadding(6);
        gridTable.addCell(recessHeader);

        for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
            Period period = config.getPeriods().get(p);
            gridTable.addCell(createPeriodHeaderCell(p, period, colHeaderFont, colHeaderSmall));
        }

        // Table Rows per Day
        int dayIdx = 0;
        for (Day day : config.getDays()) {
            Color rowBg = (dayIdx % 2 == 0) ? Color.WHITE : ALT_ROW;
            int dailyPeriods = config.getDailyPeriods(day);

            // Day cell
            PdfPCell dayCell = new PdfPCell(new Phrase(day.getDisplayName(), dayFont));
            dayCell.setBackgroundColor(rowBg);
            dayCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            dayCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            dayCell.setPadding(6);
            dayCell.setBorderColor(BORDER_COLOR);
            gridTable.addCell(dayCell);

            // Periods before Recess
            for (int p = 1; p <= defaultRecess; p++) {
                if (p <= dailyPeriods) {
                    Period period = config.getPeriods().get(p);
                    ScheduleAssignment sa = schedule.getAssignment(sc, day, period);
                    gridTable.addCell(createAssignmentCell(sa, rowBg, subjectFont, teacherFont, emptyFont));
                } else {
                    gridTable.addCell(createEmptyCell(rowBg, emptyFont));
                }
            }

            // Recess cell
            PdfPCell recCell;
            if (config.isRecessEnabled(day)) {
                recCell = new PdfPCell(new Phrase("BREAK", recessFont));
                recCell.setBackgroundColor(RECESS_BG);
            } else {
                recCell = new PdfPCell(new Phrase("—", emptyFont));
                recCell.setBackgroundColor(rowBg);
            }
            recCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            recCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            recCell.setBorderColor(BORDER_COLOR);
            gridTable.addCell(recCell);

            // Periods after Recess
            for (int p = defaultRecess + 1; p <= maxPeriods; p++) {
                if (p <= dailyPeriods) {
                    Period period = config.getPeriods().get(p);
                    ScheduleAssignment sa = schedule.getAssignment(sc, day, period);
                    gridTable.addCell(createAssignmentCell(sa, rowBg, subjectFont, teacherFont, emptyFont));
                } else {
                    gridTable.addCell(createEmptyCell(rowBg, emptyFont));
                }
            }

            dayIdx++;
        }

        document.add(gridTable);

        // 4. Teaching Faculty & Subject Allocation Summary Box
        document.add(new Paragraph(" ")); // small space

        List<TeachingAllocation> classAllocs = recResult.allocations().stream()
                .filter(a -> a.getSchoolClass().equals(sc))
                .sorted(Comparator.comparing(a -> a.getSubject().getNormalizedName()))
                .toList();

        float[] summaryWidths = {3.5f, 4.0f, 2.0f, 1.5f};
        PdfPTable summaryTable = new PdfPTable(summaryWidths);
        summaryTable.setWidthPercentage(100);

        String[] sHeaders = {"Subject", "Teacher Name", "Teacher Code", "Periods / Wk"};
        for (String sh : sHeaders) {
            PdfPCell shc = new PdfPCell(new Phrase(sh, summaryHeaderFont));
            shc.setBackgroundColor(NAVY);
            shc.setPadding(4);
            shc.setHorizontalAlignment(Element.ALIGN_CENTER);
            summaryTable.addCell(shc);
        }

        int allocIdx = 0;
        int totalWeeklyPeriods = 0;
        for (TeachingAllocation ta : classAllocs) {
            Color sRowBg = (allocIdx % 2 == 0) ? Color.WHITE : ALT_ROW;
            totalWeeklyPeriods += ta.getPeriodCount();

            PdfPCell cSub = new PdfPCell(new Phrase(ta.getSubject().getNormalizedName(), summaryTextFont));
            cSub.setBackgroundColor(sRowBg);
            cSub.setPadding(3);
            cSub.setBorderColor(BORDER_COLOR);
            summaryTable.addCell(cSub);

            PdfPCell cTName = new PdfPCell(new Phrase(ta.getTeacher().getFullName(), summaryTextFont));
            cTName.setBackgroundColor(sRowBg);
            cTName.setPadding(3);
            cTName.setBorderColor(BORDER_COLOR);
            summaryTable.addCell(cTName);

            PdfPCell cTCode = new PdfPCell(new Phrase(ta.getTeacher().getCode(), summaryTextFont));
            cTCode.setBackgroundColor(sRowBg);
            cTCode.setPadding(3);
            cTCode.setHorizontalAlignment(Element.ALIGN_CENTER);
            cTCode.setBorderColor(BORDER_COLOR);
            summaryTable.addCell(cTCode);

            PdfPCell cPer = new PdfPCell(new Phrase(String.valueOf(ta.getPeriodCount()), summaryTextFont));
            cPer.setBackgroundColor(sRowBg);
            cPer.setPadding(3);
            cPer.setHorizontalAlignment(Element.ALIGN_CENTER);
            cPer.setBorderColor(BORDER_COLOR);
            summaryTable.addCell(cPer);

            allocIdx++;
        }

        // Total row
        PdfPCell cTotLabel = new PdfPCell(new Phrase("Total Scheduled Lectures", summaryHeaderFont));
        cTotLabel.setColspan(3);
        cTotLabel.setBackgroundColor(NAVY);
        cTotLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cTotLabel.setPadding(4);
        summaryTable.addCell(cTotLabel);

        PdfPCell cTotVal = new PdfPCell(new Phrase(String.valueOf(totalWeeklyPeriods), summaryHeaderFont));
        cTotVal.setBackgroundColor(NAVY);
        cTotVal.setHorizontalAlignment(Element.ALIGN_CENTER);
        cTotVal.setPadding(4);
        summaryTable.addCell(cTotVal);

        document.add(summaryTable);

        // 5. Footer Signature Bar
        PdfPTable footerTable = new PdfPTable(3);
        footerTable.setWidthPercentage(100);
        footerTable.setSpacingBefore(12);

        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.DARK_GRAY);
        PdfPCell f1 = createSignatureCell("Class Teacher Signature", footerFont);
        PdfPCell f2 = createSignatureCell("Timetable In-Charge", footerFont);
        PdfPCell f3 = createSignatureCell("Principal Signature", footerFont);

        footerTable.addCell(f1);
        footerTable.addCell(f2);
        footerTable.addCell(f3);
        document.add(footerTable);

        document.close();
    }

    private static PdfPCell createPeriodHeaderCell(int periodNum, Period period, Font fTitle, Font fSub) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(NAVY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);

        Paragraph p1 = new Paragraph("P " + periodNum, fTitle);
        p1.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p1);

        String time = period != null ? period.getStartTime() + "-" + period.getEndTime() : "";
        if (!time.isEmpty()) {
            Paragraph p2 = new Paragraph(time, fSub);
            p2.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(p2);
        }

        return cell;
    }

    private static PdfPCell createAssignmentCell(ScheduleAssignment sa, Color bg, Font fSub, Font fTeach, Font fEmpty) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        if (sa != null) {
            Paragraph pSub = new Paragraph(sa.subject().getNormalizedName(), fSub);
            pSub.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(pSub);

            Paragraph pTch = new Paragraph("[" + sa.teacher().getCode() + "]", fTeach);
            pTch.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(pTch);
        } else {
            Paragraph pDash = new Paragraph("---", fEmpty);
            pDash.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(pDash);
        }

        return cell;
    }

    private static PdfPCell createEmptyCell(Color bg, Font fEmpty) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph pDash = new Paragraph("---", fEmpty);
        pDash.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pDash);
        return cell;
    }

    private static PdfPCell createInfoCell(String text, int alignment, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(2);
        return cell;
    }

    private static PdfPCell createSignatureCell(String title, Font font) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pLine = new Paragraph("___________________________", font);
        pLine.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pLine);

        Paragraph pText = new Paragraph(title, font);
        pText.setAlignment(Element.ALIGN_CENTER);
        pText.setSpacingBefore(2);
        cell.addElement(pText);

        return cell;
    }
}
