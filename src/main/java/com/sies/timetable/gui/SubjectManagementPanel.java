package com.sies.timetable.gui;

import com.sies.timetable.model.ManualTimetableModel;
import com.sies.timetable.model.Subject;
import com.sies.timetable.model.SubjectCategory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Panel for managing Subjects, their Categories, and Computer Lab requirements.
 * Supports Add, Edit, and Delete operations.
 */
public class SubjectManagementPanel extends JPanel {

    private final ManualTimetableModel model;
    private final Runnable onChangeCallback;

    private JTable subjectTable;
    private DefaultTableModel tableModel;
    private JLabel totalSubjectsBadge;

    public SubjectManagementPanel(ManualTimetableModel model, Runnable onChangeCallback) {
        this.model = model;
        this.onChangeCallback = onChangeCallback;

        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 20, 15, 20));
        setBackground(Color.WHITE);

        initComponents();
        loadDataFromModel();
    }

    private void initComponents() {
        // North Header
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setOpaque(false);

        JLabel title = new JLabel("Subject Management");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(26, 54, 93));

        JLabel sub = new JLabel("Add, edit, or remove instructional subjects and designate Computer Lab requirements.");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 116, 139));

        northPanel.add(title);
        northPanel.add(Box.createVerticalStrut(4));
        northPanel.add(sub);
        northPanel.add(Box.createVerticalStrut(15));

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);

        JButton addBtn = new JButton("+ Add Subject");
        addBtn.setBackground(new Color(37, 99, 235));
        addBtn.setForeground(Color.WHITE);
        addBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addBtn.addActionListener(e -> showAddSubjectDialog());
        toolbar.add(addBtn);

        JButton editBtn = new JButton("Edit Selected");
        editBtn.addActionListener(e -> showEditSubjectDialog());
        toolbar.add(editBtn);

        JButton deleteBtn = new JButton("Delete Selected");
        deleteBtn.setForeground(new Color(185, 28, 28));
        deleteBtn.addActionListener(e -> deleteSelectedSubject());
        toolbar.add(deleteBtn);

        totalSubjectsBadge = new JLabel("Total Subjects: " + model.getSubjects().size());
        totalSubjectsBadge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        totalSubjectsBadge.setForeground(new Color(37, 99, 235));
        totalSubjectsBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(totalSubjectsBadge);

        northPanel.add(toolbar);
        northPanel.add(Box.createVerticalStrut(10));
        add(northPanel, BorderLayout.NORTH);

        // Center Table
        tableModel = new DefaultTableModel(new String[]{"#", "Subject Name", "Category", "Requires Computer Lab"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        subjectTable = new JTable(tableModel);
        subjectTable.setRowHeight(30);
        subjectTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subjectTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        subjectTable.getTableHeader().setBackground(new Color(241, 245, 249));
        subjectTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        subjectTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        subjectTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        subjectTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        subjectTable.getColumnModel().getColumn(3).setPreferredWidth(160);

        JScrollPane scrollPane = new JScrollPane(subjectTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        add(scrollPane, BorderLayout.CENTER);
    }

    public void loadDataFromModel() {
        tableModel.setRowCount(0);
        List<Subject> subjects = model.getSubjects();
        int idx = 1;
        for (Subject s : subjects) {
            tableModel.addRow(new Object[]{
                    idx++,
                    s.getNormalizedName(),
                    s.getCategory().name(),
                    s.isRequiresLab() ? "YES (Computer Lab)" : "NO"
            });
        }
        totalSubjectsBadge.setText("Total Subjects: " + subjects.size());
    }

    private void showAddSubjectDialog() {
        JTextField nameField = new JTextField(20);
        JComboBox<SubjectCategory> categoryCombo = new JComboBox<>(SubjectCategory.values());
        categoryCombo.setSelectedItem(SubjectCategory.CORE_ACADEMIC);
        JCheckBox labCheck = new JCheckBox("Requires Computer Lab exclusivity (Constraint H5)");

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Subject Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Category:"));
        panel.add(categoryCombo);
        panel.add(new JLabel("Lab Exclusivity:"));
        panel.add(labCheck);

        int res = JOptionPane.showConfirmDialog(this, panel, "Add New Subject", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Subject name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            SubjectCategory cat = (SubjectCategory) categoryCombo.getSelectedItem();
            boolean lab = labCheck.isSelected();

            model.addSubject(new Subject(name, cat, lab));
            loadDataFromModel();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private void showEditSubjectDialog() {
        int row = subjectTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a subject to edit.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String currentName = (String) tableModel.getValueAt(row, 1);
        Subject subj = model.findSubject(currentName);
        if (subj == null) return;

        JTextField nameField = new JTextField(subj.getNormalizedName(), 20);
        JComboBox<SubjectCategory> categoryCombo = new JComboBox<>(SubjectCategory.values());
        categoryCombo.setSelectedItem(subj.getCategory());
        JCheckBox labCheck = new JCheckBox("Requires Computer Lab exclusivity (Constraint H5)");
        labCheck.setSelected(subj.isRequiresLab());

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Subject Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Category:"));
        panel.add(categoryCombo);
        panel.add(new JLabel("Lab Exclusivity:"));
        panel.add(labCheck);

        int res = JOptionPane.showConfirmDialog(this, panel, "Edit Subject", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Subject name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            SubjectCategory cat = (SubjectCategory) categoryCombo.getSelectedItem();
            boolean lab = labCheck.isSelected();

            model.updateSubject(currentName, new Subject(newName, cat, lab));
            loadDataFromModel();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private void deleteSelectedSubject() {
        int row = subjectTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a subject to delete.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String name = (String) tableModel.getValueAt(row, 1);
        int res = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete subject '" + name + "'?\nAny teaching allocations using this subject will also be removed.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
            model.removeSubject(name);
            loadDataFromModel();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }
}
