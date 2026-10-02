package com.sies.timetable.gui;

import com.sies.timetable.model.SchoolClass;
import com.sies.timetable.model.Teacher;
import com.sies.timetable.service.TimetableService;
import com.sies.timetable.validator.ScheduleValidator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated Interactive Dialog for inspecting every Constraint Violation with exact location,
 * human-readable explanation, and one-click navigation to affected class and teacher timetables.
 */
public class ValidationReportDialog extends JDialog {

    private final Frame parentFrame;
    private final TimetableService.GenerationResult genResult;
    private final DefaultListModel<ScheduleValidator.ViolationDetail> listModel;
    private final JList<ScheduleValidator.ViolationDetail> violationList;

    private final JLabel headerTitleLabel;
    private final JTextArea detailsTextArea;
    private final JPanel actionBtnPanel;

    public ValidationReportDialog(Frame parent, TimetableService.GenerationResult genResult) {
        super(parent, "SIES APJ School — Validation & Quality Diagnostics", true);
        this.parentFrame = parent;
        this.genResult = genResult;

        setLayout(new BorderLayout(12, 12));
        setSize(920, 620);
        setMinimumSize(new Dimension(800, 520));
        setLocationRelativeTo(parent);

        // Header Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        topPanel.setBorder(new EmptyBorder(15, 18, 12, 18));
        topPanel.setBackground(new Color(248, 250, 252));

        ScheduleValidator.ScheduleValidationResult valResult = genResult.validationResult();
        String passStatus = valResult.allHardConstraintsPassed() ?
                "<b style='color:#16a34a;'>PASS (0 Hard Violations)</b>" :
                "<b style='color:#dc2626;'>FAIL (" + valResult.totalHardViolations() + " Hard Violations)</b>";

        JLabel statusLbl = new JLabel("<html><span style='font-size:16px; font-weight:bold; color:#1a365d;'>Schedule Validation & Quality Diagnostics</span><br>" +
                "<span style='font-size:12px; color:#475569;'>Hard Constraints Status: " + passStatus +
                " &nbsp; | &nbsp; Overall Quality Score: <b style='color:#2563eb;'>" + String.format("%.2f / 100.0", valResult.overallQualityIndex()) + "</b>" +
                " &nbsp; | &nbsp; Soft Penalties: <b>" + valResult.totalSoftPenalty() + "</b></span></html>");

        topPanel.add(statusLbl, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // Split Pane with List on Left and Detailed Inspector on Right
        listModel = new DefaultListModel<>();
        for (ScheduleValidator.ViolationDetail v : valResult.violations()) {
            listModel.addElement(v);
        }

        violationList = new JList<>(listModel);
        violationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        violationList.setCellRenderer(new ViolationCellRenderer());
        violationList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateInspector();
            }
        });

        JScrollPane listScroll = new JScrollPane(violationList);
        listScroll.setPreferredSize(new Dimension(380, 400));
        listScroll.setBorder(BorderFactory.createTitledBorder("Diagnosed Items (" + listModel.size() + ")"));

        // Inspector Panel on Right
        JPanel inspectorPanel = new JPanel(new BorderLayout(10, 10));
        inspectorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Violation / Optimization Details"),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        inspectorPanel.setBackground(Color.WHITE);

        headerTitleLabel = new JLabel("Select an item on the left to inspect");
        headerTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        headerTitleLabel.setForeground(new Color(26, 54, 93));

        detailsTextArea = new JTextArea();
        detailsTextArea.setEditable(false);
        detailsTextArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        detailsTextArea.setLineWrap(true);
        detailsTextArea.setWrapStyleWord(true);
        detailsTextArea.setBackground(new Color(248, 250, 252));
        detailsTextArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane detailsScroll = new JScrollPane(detailsTextArea);
        detailsScroll.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));

        actionBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        actionBtnPanel.setOpaque(false);

        inspectorPanel.add(headerTitleLabel, BorderLayout.NORTH);
        inspectorPanel.add(detailsScroll, BorderLayout.CENTER);
        inspectorPanel.add(actionBtnPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, inspectorPanel);
        splitPane.setDividerLocation(380);
        splitPane.setResizeWeight(0.4);
        splitPane.setBorder(new EmptyBorder(0, 15, 0, 15));

        add(splitPane, BorderLayout.CENTER);

        // Bottom Button Bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeBtn.addActionListener(e -> dispose());
        bottomBar.add(closeBtn);

        add(bottomBar, BorderLayout.SOUTH);

        if (!listModel.isEmpty()) {
            violationList.setSelectedIndex(0);
        }
    }

    private void updateInspector() {
        ScheduleValidator.ViolationDetail selected = violationList.getSelectedValue();
        actionBtnPanel.removeAll();

        if (selected == null) {
            headerTitleLabel.setText("No item selected");
            detailsTextArea.setText("");
            actionBtnPanel.revalidate();
            actionBtnPanel.repaint();
            return;
        }

        headerTitleLabel.setText(String.format("[%s] %s (%s)", selected.constraintId(), selected.constraintName(), selected.severity()));
        detailsTextArea.setText(selected.getFormattedSummary());

        // Dynamic Navigation Buttons
        // 1. Classes involved
        List<SchoolClass> matchedClasses = findClasses(selected.className());
        for (SchoolClass sc : matchedClasses) {
            JButton classBtn = new JButton("📊 View Class " + sc.getDisplayName());
            classBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            classBtn.addActionListener(e -> openClassTimetable(sc));
            actionBtnPanel.add(classBtn);
        }

        // 2. Teachers involved
        List<Teacher> matchedTeachers = findTeachers(selected.teacher());
        for (Teacher t : matchedTeachers) {
            JButton teacherBtn = new JButton("👨‍🏫 View Teacher " + t.getCode());
            teacherBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            teacherBtn.addActionListener(e -> openTeacherTimetable(t));
            actionBtnPanel.add(teacherBtn);
        }

        actionBtnPanel.revalidate();
        actionBtnPanel.repaint();
    }

    private List<SchoolClass> findClasses(String classStr) {
        List<SchoolClass> result = new ArrayList<>();
        if (classStr == null || classStr.isEmpty()) return result;

        for (SchoolClass sc : genResult.reconciliationResult().classes()) {
            if (classStr.contains(sc.getDisplayName()) || classStr.contains(sc.getCanonicalName())) {
                result.add(sc);
            }
        }
        return result;
    }

    private List<Teacher> findTeachers(String teacherStr) {
        List<Teacher> result = new ArrayList<>();
        if (teacherStr == null || teacherStr.isEmpty()) return result;

        for (Teacher t : genResult.reconciliationResult().teachers()) {
            if (teacherStr.contains(t.getCode()) || teacherStr.contains(t.getFullName())) {
                result.add(t);
            }
        }
        return result;
    }

    private void openClassTimetable(SchoolClass sc) {
        ClassTimetableDialog dialog = new ClassTimetableDialog(parentFrame, genResult);
        dialog.setVisible(true);
    }

    private void openTeacherTimetable(Teacher t) {
        TeacherTimetableDialog dialog = new TeacherTimetableDialog(parentFrame, genResult);
        dialog.setVisible(true);
    }

    private static class ViolationCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ScheduleValidator.ViolationDetail v) {
                String badge = "HARD".equalsIgnoreCase(v.severity()) ?
                        "<span style='color:#dc2626; font-weight:bold;'>[HARD]</span>" :
                        "<span style='color:#d97706; font-weight:bold;'>[SOFT]</span>";

                String loc = (!v.day().isEmpty() ? v.day() : "") + (!v.period().isEmpty() ? " P" + v.period() : "");
                if (loc.isEmpty() && !v.className().isEmpty()) loc = v.className();

                label.setText(String.format("<html><b>%s %s</b><br><small style='color:#64748b;'>%s &nbsp;•&nbsp; %s</small></html>",
                        badge, v.constraintId() + " — " + v.constraintName(), loc, v.reason().length() > 45 ? v.reason().substring(0, 45) + "..." : v.reason()));
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(241, 245, 249)),
                        BorderFactory.createEmptyBorder(6, 8, 6, 8)
                ));
            }
            return label;
        }
    }
}
