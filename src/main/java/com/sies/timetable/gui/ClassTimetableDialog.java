package com.sies.timetable.gui;

import com.sies.timetable.model.SchoolClass;
import com.sies.timetable.service.TimetableService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

/**
 * Interactive Dialog for viewing individual Class Timetables (V-A through X-D).
 */
public class ClassTimetableDialog extends JDialog {

    private final TimetableService.GenerationResult genResult;
    private final JComboBox<SchoolClass> classComboBox;
    private final TimetableGridPanel gridPanel;

    public ClassTimetableDialog(Frame parent, TimetableService.GenerationResult genResult) {
        this(parent, genResult, null);
    }

    public ClassTimetableDialog(Frame parent, TimetableService.GenerationResult genResult, SchoolClass initialClass) {
        super(parent, "SIES APJ School — Class Timetable Viewer", true);
        this.genResult = genResult;

        setLayout(new BorderLayout(10, 10));
        setSize(1150, 680);
        setLocationRelativeTo(parent);

        // Control Panel
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 12));
        topPanel.setBackground(new Color(248, 250, 252));
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)));

        JLabel selectLabel = new JLabel("Select Class:");
        selectLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        selectLabel.setForeground(new Color(30, 41, 59));

        List<SchoolClass> classes = genResult.reconciliationResult().classes();
        classComboBox = new JComboBox<>(classes.toArray(new SchoolClass[0]));
        classComboBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        classComboBox.setPreferredSize(new Dimension(200, 32));
        classComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SchoolClass sc) {
                    setText("Class " + sc.getDisplayName());
                }
                return this;
            }
        });

        classComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateGrid();
            }
        });

        JButton prevBtn = new JButton("◀ Prev");
        prevBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        prevBtn.addActionListener(e -> {
            int idx = classComboBox.getSelectedIndex();
            if (idx > 0) classComboBox.setSelectedIndex(idx - 1);
        });

        JButton nextBtn = new JButton("Next ▶");
        nextBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        nextBtn.addActionListener(e -> {
            int idx = classComboBox.getSelectedIndex();
            if (idx < classComboBox.getItemCount() - 1) classComboBox.setSelectedIndex(idx + 1);
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeBtn.addActionListener(e -> dispose());

        topPanel.add(selectLabel);
        topPanel.add(classComboBox);
        topPanel.add(prevBtn);
        topPanel.add(nextBtn);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(closeBtn);

        add(topPanel, BorderLayout.NORTH);

        // Center Grid
        gridPanel = new TimetableGridPanel();
        add(gridPanel, BorderLayout.CENTER);

        // Initialize with initialClass or first class
        if (initialClass != null) {
            classComboBox.setSelectedItem(initialClass);
        } else if (!classes.isEmpty()) {
            classComboBox.setSelectedIndex(0);
        }
        updateGrid();
    }

    private void updateGrid() {
        SchoolClass selected = (SchoolClass) classComboBox.getSelectedItem();
        if (selected != null) {
            TimetableService.TimetableGrid grid = TimetableService.getClassGrid(
                    genResult.schedule(),
                    selected,
                    genResult.reconciliationResult(),
                    genResult.config()
            );
            gridPanel.displayGrid(grid);
        }
    }
}
