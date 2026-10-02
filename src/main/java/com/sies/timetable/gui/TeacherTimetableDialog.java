package com.sies.timetable.gui;

import com.sies.timetable.model.Teacher;
import com.sies.timetable.service.TimetableService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Comparator;
import java.util.List;

/**
 * Interactive Dialog for viewing individual Teacher Timetables.
 */
public class TeacherTimetableDialog extends JDialog {

    private final TimetableService.GenerationResult genResult;
    private final JComboBox<Teacher> teacherComboBox;
    private final TimetableGridPanel gridPanel;

    public TeacherTimetableDialog(Frame parent, TimetableService.GenerationResult genResult) {
        this(parent, genResult, null);
    }

    public TeacherTimetableDialog(Frame parent, TimetableService.GenerationResult genResult, Teacher initialTeacher) {
        super(parent, "SIES APJ School — Teacher Timetable Viewer", true);
        this.genResult = genResult;

        setLayout(new BorderLayout(10, 10));
        setSize(1150, 680);
        setLocationRelativeTo(parent);

        // Control Panel
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 12));
        topPanel.setBackground(new Color(248, 250, 252));
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)));

        JLabel selectLabel = new JLabel("Select Teacher:");
        selectLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        selectLabel.setForeground(new Color(30, 41, 59));

        List<Teacher> teachers = genResult.reconciliationResult().teachers().stream()
                .sorted(Comparator.comparing(Teacher::getFullName))
                .toList();

        teacherComboBox = new JComboBox<>(teachers.toArray(new Teacher[0]));
        teacherComboBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        teacherComboBox.setPreferredSize(new Dimension(320, 32));
        teacherComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Teacher t) {
                    setText(String.format("%s (%s) — %d Periods", t.getFullName(), t.getCode(), t.getTotalWorkload()));
                }
                return this;
            }
        });

        teacherComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateGrid();
            }
        });

        JButton prevBtn = new JButton("◀ Prev");
        prevBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        prevBtn.addActionListener(e -> {
            int idx = teacherComboBox.getSelectedIndex();
            if (idx > 0) teacherComboBox.setSelectedIndex(idx - 1);
        });

        JButton nextBtn = new JButton("Next ▶");
        nextBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        nextBtn.addActionListener(e -> {
            int idx = teacherComboBox.getSelectedIndex();
            if (idx < teacherComboBox.getItemCount() - 1) teacherComboBox.setSelectedIndex(idx + 1);
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeBtn.addActionListener(e -> dispose());

        topPanel.add(selectLabel);
        topPanel.add(teacherComboBox);
        topPanel.add(prevBtn);
        topPanel.add(nextBtn);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(closeBtn);

        add(topPanel, BorderLayout.NORTH);

        // Center Grid
        gridPanel = new TimetableGridPanel();
        add(gridPanel, BorderLayout.CENTER);

        // Initialize with initialTeacher or first teacher
        if (initialTeacher != null) {
            teacherComboBox.setSelectedItem(initialTeacher);
        } else if (!teachers.isEmpty()) {
            teacherComboBox.setSelectedIndex(0);
        }
        updateGrid();
    }

    private void updateGrid() {
        Teacher selected = (Teacher) teacherComboBox.getSelectedItem();
        if (selected != null) {
            TimetableService.TimetableGrid grid = TimetableService.getTeacherGrid(
                    genResult.schedule(),
                    selected,
                    genResult.config()
            );
            gridPanel.displayGrid(grid);
        }
    }
}
