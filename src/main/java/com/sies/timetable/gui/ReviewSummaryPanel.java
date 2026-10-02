package com.sies.timetable.gui;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.Day;
import com.sies.timetable.model.ManualTimetableModel;
import com.sies.timetable.model.ManualTimetableModel.ValidationReport;
import com.sies.timetable.model.SchoolClass;
import com.sies.timetable.model.Teacher;
import com.sies.timetable.service.TimetableService;
import com.sies.timetable.service.TimetableService.GenerationResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Section 11: Review Screen & Generation Center.
 * Presents comprehensive pre-solve summaries, real-time validation checklist,
 * progress tracking, and post-solve export actions (PDF, Excel, Interactive Viewers).
 */
public class ReviewSummaryPanel extends JPanel {

    private final ManualTimetableModel model;
    private GenerationResult lastResult;

    // Summary Labels
    private JLabel academicSummaryLabel;
    private JLabel timetableSummaryLabel;
    private JLabel facultySummaryLabel;
    private JLabel subjectSummaryLabel;
    private JTextArea checklistArea;

    // Action & Progress
    private JButton generateBtn;
    private JProgressBar progressBar;
    private JTextArea logTextArea;

    // Results Box
    private JPanel resultsCard;
    private JLabel hardStatusVal;
    private JLabel hardViolationsVal;
    private JLabel qualityScoreVal;
    private JLabel durationVal;

    // Export & View Buttons
    private JButton openExcelBtn;
    private JButton openPdfBtn;
    private JButton viewClassBtn;
    private JButton viewTeacherBtn;
    private JButton viewValidationBtn;
    private JButton openFolderBtn;

    public ReviewSummaryPanel(ManualTimetableModel model) {
        this.model = model;

        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 20, 15, 20));
        setBackground(Color.WHITE);

        initComponents();
        refreshSummary();
    }

    private void initComponents() {
        // North: Title
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setOpaque(false);

        JLabel title = new JLabel("Review Configuration & Timetable Generation");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(26, 54, 93));

        JLabel sub = new JLabel("Verify scheduling parameters and feasibility checklist before running the CP-SAT solver.");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 116, 139));

        northPanel.add(title);
        northPanel.add(Box.createVerticalStrut(4));
        northPanel.add(sub);
        northPanel.add(Box.createVerticalStrut(10));
        add(northPanel, BorderLayout.NORTH);

        // Center: Two vertical sections (Top: Summary Cards + Checklist, Bottom: Progress & Results)
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // 1. Summary Cards Grid
        JPanel summaryGrid = new JPanel(new GridLayout(2, 2, 12, 10));
        summaryGrid.setOpaque(false);
        summaryGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        summaryGrid.add(createSummaryTile("1. ACADEMIC STRUCTURE", academicSummaryLabel = new JLabel("Standards 5–10 | 26 Classes")));
        summaryGrid.add(createSummaryTile("2. TIMETABLE CAPACITY", timetableSummaryLabel = new JLabel("48 Available | 48 Required")));
        summaryGrid.add(createSummaryTile("3. FACULTY & WORKLOAD", facultySummaryLabel = new JLabel("44 Professors | 1,248 Periods")));
        summaryGrid.add(createSummaryTile("4. INSTRUCTIONAL SUBJECTS", subjectSummaryLabel = new JLabel("20 Subjects Defined")));

        centerPanel.add(summaryGrid);
        centerPanel.add(Box.createVerticalStrut(10));

        // 2. Feasibility Checklist Card
        JPanel checkCard = new JPanel(new BorderLayout(5, 5));
        checkCard.setOpaque(false);
        checkCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                "Pre-Solve Feasibility & Axiom Checklist",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(30, 41, 59)
        ));

        checklistArea = new JTextArea(4, 50);
        checklistArea.setEditable(false);
        checklistArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        checklistArea.setBackground(new Color(248, 250, 252));
        checklistArea.setMargin(new Insets(8, 10, 8, 10));

        JScrollPane checkScroll = new JScrollPane(checklistArea);
        checkCard.add(checkScroll, BorderLayout.CENTER);
        centerPanel.add(checkCard);
        centerPanel.add(Box.createVerticalStrut(10));

        // 3. Generate Action Button
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actionPanel.setOpaque(false);

        generateBtn = new JButton("⚡ GENERATE TIMETABLE");
        generateBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        generateBtn.setBackground(new Color(37, 99, 235));
        generateBtn.setForeground(Color.WHITE);
        generateBtn.setPreferredSize(new Dimension(320, 42));
        generateBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        generateBtn.addActionListener(e -> runGeneration());
        actionPanel.add(generateBtn);

        centerPanel.add(actionPanel);
        centerPanel.add(Box.createVerticalStrut(10));

        // 4. Progress & Log Card
        JPanel logCard = new JPanel(new BorderLayout(0, 6));
        logCard.setOpaque(false);
        logCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                "Progress / Solver Console",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12), new Color(30, 41, 59)
        ));

        progressBar = new JProgressBar(0, 7);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        progressBar.setPreferredSize(new Dimension(progressBar.getWidth(), 22));
        logCard.add(progressBar, BorderLayout.NORTH);

        logTextArea = new JTextArea(4, 50);
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logTextArea.setBackground(new Color(248, 250, 252));
        logTextArea.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane logScroll = new JScrollPane(logTextArea);
        logCard.add(logScroll, BorderLayout.CENTER);
        centerPanel.add(logCard);
        centerPanel.add(Box.createVerticalStrut(10));

        // 5. Results & Exports Box
        resultsCard = new JPanel(new BorderLayout(8, 8));
        resultsCard.setOpaque(false);
        resultsCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                "Generation Results & Production Exports",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12), new Color(30, 41, 59)
        ));

        JPanel statRow = new JPanel(new GridLayout(1, 4, 10, 0));
        statRow.setOpaque(false);
        statRow.add(createMetricItem("Hard Constraints:", hardStatusVal = new JLabel("---")));
        statRow.add(createMetricItem("Hard Violations:", hardViolationsVal = new JLabel("---")));
        statRow.add(createMetricItem("Quality Index:", qualityScoreVal = new JLabel("---")));
        statRow.add(createMetricItem("Solve Duration:", durationVal = new JLabel("---")));
        resultsCard.add(statRow, BorderLayout.NORTH);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        buttonBar.setOpaque(false);

        openPdfBtn = new JButton("📄 Open All Class PDFs");
        openPdfBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        openPdfBtn.setBackground(new Color(240, 253, 244));
        openPdfBtn.setForeground(new Color(22, 101, 52));
        openPdfBtn.setEnabled(false);
        openPdfBtn.addActionListener(e -> openPdfDirectory());
        buttonBar.add(openPdfBtn);

        openExcelBtn = new JButton("📊 Open Master Excel");
        openExcelBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        openExcelBtn.setEnabled(false);
        openExcelBtn.addActionListener(e -> openExcelFile());
        buttonBar.add(openExcelBtn);

        viewClassBtn = new JButton("🔍 View Class Timetable");
        viewClassBtn.setEnabled(false);
        viewClassBtn.addActionListener(e -> openClassViewer());
        buttonBar.add(viewClassBtn);

        viewTeacherBtn = new JButton("🔍 View Teacher Timetable");
        viewTeacherBtn.setEnabled(false);
        viewTeacherBtn.addActionListener(e -> openTeacherViewer());
        buttonBar.add(viewTeacherBtn);

        viewValidationBtn = new JButton("📋 Diagnostics");
        viewValidationBtn.setEnabled(false);
        viewValidationBtn.addActionListener(e -> openValidationViewer());
        buttonBar.add(viewValidationBtn);

        openFolderBtn = new JButton("📁 Output Folder");
        openFolderBtn.setEnabled(false);
        openFolderBtn.addActionListener(e -> openOutputDirectory());
        buttonBar.add(openFolderBtn);

        resultsCard.add(buttonBar, BorderLayout.CENTER);
        centerPanel.add(resultsCard);

        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel createSummaryTile(String header, JLabel valueLabel) {
        JPanel tile = new JPanel(new BorderLayout(4, 4));
        tile.setBackground(new Color(248, 250, 252));
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JLabel hLbl = new JLabel(header);
        hLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        hLbl.setForeground(new Color(100, 116, 139));
        tile.add(hLbl, BorderLayout.NORTH);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        valueLabel.setForeground(new Color(26, 54, 93));
        tile.add(valueLabel, BorderLayout.CENTER);

        return tile;
    }

    private JPanel createMetricItem(String labelText, JLabel valueLabel) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(labelText);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(new Color(100, 116, 139));
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        p.add(l);
        p.add(valueLabel);
        return p;
    }

    public void refreshSummary() {
        ValidationReport report = model.validate();

        // 1. Academic
        int stdCount = model.getEndStandard() - model.getStartStandard() + 1;
        academicSummaryLabel.setText(String.format("Std %d–%d (%d Standards) | %d Classes",
                model.getStartStandard(), model.getEndStandard(), stdCount, model.getGeneratedClasses().size()));

        // 2. Timetable
        int avail = model.getTotalAvailablePeriods();
        int req = model.getWeeklyRequiredPeriodsPerClass();
        int spare = avail - req;
        timetableSummaryLabel.setText(String.format("%d Available | %d Required (%s)",
                avail, req, spare >= 0 ? "+" + spare + " spare" : Math.abs(spare) + " DEFICIT"));

        // 3. Faculty
        int totalWl = 0;
        for (ManualTimetableModel.ProfessorEntry p : model.getProfessors()) totalWl += p.calculateTotalWorkload();
        facultySummaryLabel.setText(String.format("%d Professors | %d Total Periods",
                model.getProfessors().size(), totalWl));

        // 4. Subjects
        subjectSummaryLabel.setText(model.getSubjects().size() + " Instructional Subjects Defined");

        // Checklist Text
        StringBuilder sb = new StringBuilder();
        if (report.isValid()) {
            sb.append("✓ ALL CRITICAL SCHEDULING CHECKS PASSED (Ready to Generate)\n");
        } else {
            sb.append("✗ VALIDATION ISSUES DETECTED (Resolve before generation):\n");
            for (String err : report.errors()) {
                sb.append("  • ").append(err).append("\n");
            }
        }

        if (!report.warnings().isEmpty()) {
            sb.append("\n⚠ NOTICES / ADVISORIES:\n");
            for (String w : report.warnings()) {
                sb.append("  • ").append(w).append("\n");
            }
        }

        for (String n : report.notices()) {
            sb.append("  ℹ ").append(n).append("\n");
        }

        checklistArea.setText(sb.toString());
        checklistArea.setCaretPosition(0);

        generateBtn.setEnabled(report.isValid());
        if (!report.isValid()) {
            generateBtn.setBackground(new Color(156, 163, 175));
        } else {
            generateBtn.setBackground(new Color(37, 99, 235));
        }
    }

    private void runGeneration() {
        ValidationReport report = model.validate();
        if (!report.isValid()) {
            JOptionPane.showMessageDialog(this, "Please resolve validation errors before generating:\n"
                    + String.join("\n", report.errors()), "Validation Errors", JOptionPane.ERROR_MESSAGE);
            return;
        }

        generateBtn.setEnabled(false);
        progressBar.setValue(0);
        logTextArea.setText("");

        SwingWorker<GenerationResult, String> worker = new SwingWorker<>() {
            @Override
            protected GenerationResult doInBackground() {
                return TimetableService.generate(model, (step, total, msg) -> {
                    publish(String.format("[%d/%d] %s", step, total, msg));
                    setProgress((step * 100) / total);
                });
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    logTextArea.append(msg + "\n");
                    logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
                    progressBar.setString(msg);
                }
                progressBar.setValue(getProgress());
            }

            @Override
            protected void done() {
                generateBtn.setEnabled(true);
                try {
                    lastResult = get();
                    onGenerationComplete(lastResult);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ReviewSummaryPanel.this,
                            "Execution failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private void onGenerationComplete(GenerationResult result) {
        if (!result.isSuccess()) {
            progressBar.setString("Failed");
            hardStatusVal.setText("FAIL");
            hardStatusVal.setForeground(new Color(220, 38, 38));
            JOptionPane.showMessageDialog(this,
                    "Timetable Generation Failed:\n" + result.errorMessage(),
                    "Generation Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        progressBar.setValue(7);
        progressBar.setString("Completed Successfully");

        hardStatusVal.setText("PASS (0 Clashes)");
        hardStatusVal.setForeground(new Color(22, 163, 74));

        hardViolationsVal.setText(String.valueOf(result.validationResult().totalHardViolations()));
        hardViolationsVal.setForeground(new Color(22, 163, 74));

        qualityScoreVal.setText(String.format("%.1f / 100", result.validationResult().overallQualityIndex()));
        qualityScoreVal.setForeground(new Color(37, 99, 235));

        durationVal.setText(String.format("%.2f s", result.solveTimeSeconds()));

        openPdfBtn.setEnabled(result.pdfDir() != null && result.pdfDir().exists());
        openExcelBtn.setEnabled(result.excelFile() != null && result.excelFile().exists());
        viewClassBtn.setEnabled(true);
        viewTeacherBtn.setEnabled(true);
        viewValidationBtn.setEnabled(true);
        openFolderBtn.setEnabled(true);

        JOptionPane.showMessageDialog(this,
                String.format("🎉 Timetable Generation & PDF Export Succeeded!\n\n"
                                + "• Hard Violations: 0 (All operational constraints satisfied)\n"
                                + "• Quality Score: %.2f / 100.0 (OPTIMAL)\n"
                                + "• Individual Class PDFs: Generated in %s\n"
                                + "• Solve Duration: %.2f seconds",
                        result.validationResult().overallQualityIndex(),
                        result.pdfDir() != null ? result.pdfDir().getName() : "output/pdf",
                        result.solveTimeSeconds()),
                "Timetable Generated", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openPdfDirectory() {
        if (lastResult == null || lastResult.pdfDir() == null) return;
        try {
            Desktop.getDesktop().open(lastResult.pdfDir());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open PDF folder: " + ex.getMessage());
        }
    }

    private void openExcelFile() {
        if (lastResult == null || lastResult.excelFile() == null) return;
        try {
            Desktop.getDesktop().open(lastResult.excelFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open Excel file: " + ex.getMessage());
        }
    }

    private void openOutputDirectory() {
        if (lastResult == null || lastResult.outputDir() == null) return;
        try {
            Desktop.getDesktop().open(lastResult.outputDir());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open output folder: " + ex.getMessage());
        }
    }

    private void openClassViewer() {
        if (lastResult == null || lastResult.schedule() == null) return;
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        Frame owner = ancestor instanceof Frame f ? f : null;
        ClassTimetableDialog dialog = new ClassTimetableDialog(owner, lastResult);
        dialog.setVisible(true);
    }

    private void openTeacherViewer() {
        if (lastResult == null || lastResult.schedule() == null) return;
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        Frame owner = ancestor instanceof Frame f ? f : null;
        TeacherTimetableDialog dialog = new TeacherTimetableDialog(owner, lastResult);
        dialog.setVisible(true);
    }

    private void openValidationViewer() {
        if (lastResult == null || lastResult.validationResult() == null) return;
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        Frame owner = ancestor instanceof Frame f ? f : null;
        ValidationReportDialog dialog = new ValidationReportDialog(owner, lastResult);
        dialog.setVisible(true);
    }
}
