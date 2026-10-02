package com.sies.timetable.gui;

import com.sies.timetable.model.ManualTimetableModel;
import com.sies.timetable.model.SchoolClass;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Panel for configuring Academic Standards Range and Division counts.
 * Dynamically computes and displays the generated school classes (e.g. 5-A, 5-B).
 */
public class AcademicStructurePanel extends JPanel {

    private final ManualTimetableModel model;
    private final Runnable onChangeCallback;

    private JSpinner startStdSpinner;
    private JSpinner endStdSpinner;
    private JTable divisionsTable;
    private DefaultTableModel tableModel;
    private JLabel totalClassesBadge;
    private JTextArea classesPreviewArea;

    public AcademicStructurePanel(ManualTimetableModel model, Runnable onChangeCallback) {
        this.model = model;
        this.onChangeCallback = onChangeCallback;

        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 20, 15, 20));
        setBackground(Color.WHITE);

        initComponents();
        loadDataFromModel();
    }

    private void initComponents() {
        // North Panel: Range selection & Instructions
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setOpaque(false);

        JLabel title = new JLabel("Academic Standards & Divisions Setup");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(26, 54, 93));

        JLabel sub = new JLabel("Configure the active standard range (e.g., 5 to 10) and set division count per standard.");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 116, 139));

        northPanel.add(title);
        northPanel.add(Box.createVerticalStrut(4));
        northPanel.add(sub);
        northPanel.add(Box.createVerticalStrut(15));

        // Range Control Row
        JPanel rangeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        rangeRow.setOpaque(false);

        rangeRow.add(new JLabel("Start Standard:"));
        startStdSpinner = new JSpinner(new SpinnerNumberModel(model.getStartStandard(), 1, 12, 1));
        startStdSpinner.setPreferredSize(new Dimension(65, 30));
        startStdSpinner.addChangeListener(e -> onRangeChanged());
        rangeRow.add(startStdSpinner);

        rangeRow.add(new JLabel("End Standard:"));
        endStdSpinner = new JSpinner(new SpinnerNumberModel(model.getEndStandard(), 1, 12, 1));
        endStdSpinner.setPreferredSize(new Dimension(65, 30));
        endStdSpinner.addChangeListener(e -> onRangeChanged());
        rangeRow.add(endStdSpinner);

        JButton applyRangeBtn = new JButton("Update Standards");
        applyRangeBtn.setBackground(new Color(241, 245, 249));
        applyRangeBtn.addActionListener(e -> onRangeChanged());
        rangeRow.add(applyRangeBtn);

        totalClassesBadge = new JLabel("Total Classes: " + model.getGeneratedClasses().size());
        totalClassesBadge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        totalClassesBadge.setForeground(new Color(37, 99, 235));
        totalClassesBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        rangeRow.add(Box.createHorizontalStrut(20));
        rangeRow.add(totalClassesBadge);

        northPanel.add(rangeRow);
        northPanel.add(Box.createVerticalStrut(10));

        add(northPanel, BorderLayout.NORTH);

        // Center Split: Left = Divisions Table, Right = Classes Preview
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);

        // Left: Table
        JPanel tableContainer = new JPanel(new BorderLayout(5, 5));
        tableContainer.setOpaque(false);
        tableContainer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                "Divisions Per Standard",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(30, 41, 59)
        ));

        tableModel = new DefaultTableModel(new String[]{"Standard", "Divisions Count", "Division Letters"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1; // Only count is editable
            }
        };

        divisionsTable = new JTable(tableModel);
        divisionsTable.setRowHeight(32);
        divisionsTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        divisionsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        divisionsTable.getTableHeader().setBackground(new Color(241, 245, 249));

        tableModel.addTableModelListener(e -> {
            if (e.getColumn() == 1 && e.getFirstRow() >= 0) {
                int row = e.getFirstRow();
                try {
                    int std = Integer.parseInt(tableModel.getValueAt(row, 0).toString().replace("Standard ", ""));
                    int count = Integer.parseInt(tableModel.getValueAt(row, 1).toString());
                    if (count > 0 && count <= 10) {
                        model.setDivisionCount(std, count);
                        updatePreview();
                        if (onChangeCallback != null) onChangeCallback.run();
                    }
                } catch (Exception ignored) {}
            }
        });

        JScrollPane tableScroll = new JScrollPane(divisionsTable);
        tableContainer.add(tableScroll, BorderLayout.CENTER);
        splitPane.setLeftComponent(tableContainer);

        // Right: Generated Classes List
        JPanel previewContainer = new JPanel(new BorderLayout(5, 5));
        previewContainer.setOpaque(false);
        previewContainer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                "Generated School Classes (Auto-Updated)",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(30, 41, 59)
        ));

        classesPreviewArea = new JTextArea();
        classesPreviewArea.setEditable(false);
        classesPreviewArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        classesPreviewArea.setMargin(new java.awt.Insets(10, 10, 10, 10));
        classesPreviewArea.setBackground(new Color(248, 250, 252));

        JScrollPane previewScroll = new JScrollPane(classesPreviewArea);
        previewContainer.add(previewScroll, BorderLayout.CENTER);
        splitPane.setRightComponent(previewContainer);

        add(splitPane, BorderLayout.CENTER);
    }

    private void onRangeChanged() {
        int start = (int) startStdSpinner.getValue();
        int end = (int) endStdSpinner.getValue();
        if (start > end) {
            end = start;
            endStdSpinner.setValue(end);
        }
        model.setStandardsRange(start, end);
        loadDataFromModel();
        if (onChangeCallback != null) onChangeCallback.run();
    }

    public void loadDataFromModel() {
        startStdSpinner.setValue(model.getStartStandard());
        endStdSpinner.setValue(model.getEndStandard());

        tableModel.setRowCount(0);
        for (int std = model.getStartStandard(); std <= model.getEndStandard(); std++) {
            int count = model.getStandardDivisionsCount().getOrDefault(std, 4);
            StringBuilder letters = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (!letters.isEmpty()) letters.append(", ");
                letters.append((char) ('A' + i));
            }
            tableModel.addRow(new Object[]{"Standard " + std, count, letters.toString()});
        }

        updatePreview();
    }

    private void updatePreview() {
        List<SchoolClass> classes = model.getGeneratedClasses();
        totalClassesBadge.setText("Total Classes: " + classes.size());

        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("  ACTIVE SCHOOL CLASSES (TOTAL: ").append(classes.size()).append(")\n");
        sb.append("====================================================\n\n");

        for (int std = model.getStartStandard(); std <= model.getEndStandard(); std++) {
            final int s = std;
            List<SchoolClass> stdClasses = classes.stream()
                    .filter(c -> c.getStandard() == s)
                    .toList();

            sb.append("Standard ").append(std).append(" (").append(stdClasses.size()).append(" divisions):\n  ");
            for (int i = 0; i < stdClasses.size(); i++) {
                if (i > 0) sb.append(",  ");
                sb.append(stdClasses.get(i).getStandardDivisionName());
            }
            sb.append("\n\n");
        }

        classesPreviewArea.setText(sb.toString());
        classesPreviewArea.setCaretPosition(0);
    }
}
