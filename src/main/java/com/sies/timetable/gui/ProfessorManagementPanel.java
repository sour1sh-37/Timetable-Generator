package com.sies.timetable.gui;

import com.sies.timetable.model.*;
import com.sies.timetable.model.ManualTimetableModel.AllocationEntry;
import com.sies.timetable.model.ManualTimetableModel.ProfessorEntry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel for managing Professors and their atomic Subject-Class teaching allocations.
 * Formulates: Professor -> Subject -> Specific Classes -> Weekly Lectures per Class.
 * Distinguishes Class Teacher designation from subject allocations.
 */
public class ProfessorManagementPanel extends JPanel {

    private final ManualTimetableModel model;
    private final Runnable onChangeCallback;

    // Left Panel: Professor List
    private JTable professorTable;
    private DefaultTableModel profTableModel;
    private JLabel totalProfsBadge;

    // Right Panel: Details & Allocations of Selected Professor
    private JPanel rightDetailsPanel;
    private JLabel selectedProfNameLabel;
    private JLabel selectedProfWorkloadBadge;
    private JComboBox<String> classTeacherCombo;
    private JTable allocationTable;
    private DefaultTableModel allocTableModel;
    private JButton addAllocBtn;
    private JButton editAllocBtn;
    private JButton deleteAllocBtn;

    private ProfessorEntry selectedProfessor = null;

    public ProfessorManagementPanel(ManualTimetableModel model, Runnable onChangeCallback) {
        this.model = model;
        this.onChangeCallback = onChangeCallback;

        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 20, 15, 20));
        setBackground(Color.WHITE);

        initComponents();
        loadProfessors();
    }

    private void initComponents() {
        // North Header & Actions
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setOpaque(false);

        JLabel title = new JLabel("Faculty & Teaching Allocation Management");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(26, 54, 93));

        JLabel sub = new JLabel("Define faculty members, assign class teachers, and configure Subject -> Classes -> Weekly Lectures.");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 116, 139));

        northPanel.add(title);
        northPanel.add(Box.createVerticalStrut(4));
        northPanel.add(sub);
        northPanel.add(Box.createVerticalStrut(12));

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);

        JButton addProfBtn = new JButton("+ Add Professor");
        addProfBtn.setBackground(new Color(37, 99, 235));
        addProfBtn.setForeground(Color.WHITE);
        addProfBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addProfBtn.addActionListener(e -> showAddProfessorDialog());
        toolbar.add(addProfBtn);

        JButton editProfBtn = new JButton("Edit Professor");
        editProfBtn.addActionListener(e -> showEditProfessorDialog());
        toolbar.add(editProfBtn);

        JButton deleteProfBtn = new JButton("Delete Professor");
        deleteProfBtn.setForeground(new Color(185, 28, 28));
        deleteProfBtn.addActionListener(e -> deleteSelectedProfessor());
        toolbar.add(deleteProfBtn);

        toolbar.add(Box.createHorizontalStrut(15));

        JButton loadPresetBtn = new JButton("⚡ Load 2026-27 School Preset");
        loadPresetBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        loadPresetBtn.setBackground(new Color(240, 253, 244));
        loadPresetBtn.setForeground(new Color(22, 101, 52));
        loadPresetBtn.addActionListener(e -> loadSchoolPreset());
        toolbar.add(loadPresetBtn);

        JButton clearAllBtn = new JButton("Clear All");
        clearAllBtn.setForeground(new Color(185, 28, 28));
        clearAllBtn.addActionListener(e -> clearAllProfessors());
        toolbar.add(clearAllBtn);

        totalProfsBadge = new JLabel("Faculty: 0 | Total Workload: 0");
        totalProfsBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        totalProfsBadge.setForeground(new Color(37, 99, 235));
        totalProfsBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        toolbar.add(Box.createHorizontalStrut(15));
        toolbar.add(totalProfsBadge);

        northPanel.add(toolbar);
        northPanel.add(Box.createVerticalStrut(10));
        add(northPanel, BorderLayout.NORTH);

        // Center Split: Left = Professor Table, Right = Details & Allocations
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.42);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);

        // Left Panel
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setOpaque(false);
        leftPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                "Faculty Staff Directory",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(30, 41, 59)
        ));

        profTableModel = new DefaultTableModel(new String[]{"Code", "Name", "Workload", "Class Teacher"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        professorTable = new JTable(profTableModel);
        professorTable.setRowHeight(30);
        professorTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        professorTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        professorTable.getTableHeader().setBackground(new Color(241, 245, 249));
        professorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        professorTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        professorTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        professorTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        professorTable.getColumnModel().getColumn(3).setPreferredWidth(90);

        professorTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onProfessorSelected();
            }
        });

        JScrollPane profScroll = new JScrollPane(professorTable);
        leftPanel.add(profScroll, BorderLayout.CENTER);
        splitPane.setLeftComponent(leftPanel);

        // Right Panel: Details & Allocations
        rightDetailsPanel = new JPanel(new BorderLayout(10, 10));
        rightDetailsPanel.setOpaque(false);
        rightDetailsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                "Teaching Allocations & Class Designation",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(30, 41, 59)
        ));

        // Right Top: Header info & Class Teacher Dropdown
        JPanel rightTop = new JPanel(new BorderLayout(8, 8));
        rightTop.setOpaque(false);

        JPanel profHeaderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        profHeaderRow.setOpaque(false);

        selectedProfNameLabel = new JLabel("Select a professor from the left");
        selectedProfNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        selectedProfNameLabel.setForeground(new Color(26, 54, 93));
        profHeaderRow.add(selectedProfNameLabel);

        selectedProfWorkloadBadge = new JLabel("");
        selectedProfWorkloadBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        selectedProfWorkloadBadge.setForeground(new Color(37, 99, 235));
        profHeaderRow.add(selectedProfWorkloadBadge);
        rightTop.add(profHeaderRow, BorderLayout.NORTH);

        JPanel ctRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        ctRow.setOpaque(false);
        ctRow.add(new JLabel("Class Teacher Designation (Constraint H1):"));

        classTeacherCombo = new JComboBox<>();
        classTeacherCombo.setPreferredSize(new Dimension(140, 28));
        classTeacherCombo.addActionListener(e -> onClassTeacherChanged());
        ctRow.add(classTeacherCombo);

        JLabel ctHelp = new JLabel("(Allocates 1st period on all days)");
        ctHelp.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        ctHelp.setForeground(new Color(100, 116, 139));
        ctRow.add(ctHelp);

        rightTop.add(ctRow, BorderLayout.CENTER);
        rightDetailsPanel.add(rightTop, BorderLayout.NORTH);

        // Right Center: Allocations Table
        JPanel allocCenter = new JPanel(new BorderLayout(6, 6));
        allocCenter.setOpaque(false);

        allocTableModel = new DefaultTableModel(new String[]{"Subject", "Classes", "Lec / Wk", "Total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        allocationTable = new JTable(allocTableModel);
        allocationTable.setRowHeight(30);
        allocationTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        allocationTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        allocationTable.getTableHeader().setBackground(new Color(241, 245, 249));
        allocationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        allocationTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        allocationTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        allocationTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        allocationTable.getColumnModel().getColumn(3).setPreferredWidth(60);

        JScrollPane allocScroll = new JScrollPane(allocationTable);
        allocCenter.add(allocScroll, BorderLayout.CENTER);

        // Right Bottom: Allocation Action Buttons
        JPanel allocButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        allocButtons.setOpaque(false);

        addAllocBtn = new JButton("+ Add Allocation");
        addAllocBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addAllocBtn.setBackground(new Color(37, 99, 235));
        addAllocBtn.setForeground(Color.WHITE);
        addAllocBtn.addActionListener(e -> showAddAllocationDialog());
        allocButtons.add(addAllocBtn);

        editAllocBtn = new JButton("Edit Allocation");
        editAllocBtn.addActionListener(e -> showEditAllocationDialog());
        allocButtons.add(editAllocBtn);

        deleteAllocBtn = new JButton("Delete Allocation");
        deleteAllocBtn.setForeground(new Color(185, 28, 28));
        deleteAllocBtn.addActionListener(e -> deleteSelectedAllocation());
        allocButtons.add(deleteAllocBtn);

        allocCenter.add(allocButtons, BorderLayout.SOUTH);
        rightDetailsPanel.add(allocCenter, BorderLayout.CENTER);

        splitPane.setRightComponent(rightDetailsPanel);
        add(splitPane, BorderLayout.CENTER);

        enableRightPanel(false);
    }

    private void enableRightPanel(boolean enable) {
        classTeacherCombo.setEnabled(enable);
        addAllocBtn.setEnabled(enable);
        editAllocBtn.setEnabled(enable);
        deleteAllocBtn.setEnabled(enable);
        allocationTable.setEnabled(enable);
    }

    public void loadProfessors() {
        int selectedRow = professorTable.getSelectedRow();
        String selectedCode = (selectedRow >= 0 && selectedRow < profTableModel.getRowCount())
                ? (String) profTableModel.getValueAt(selectedRow, 0) : null;

        profTableModel.setRowCount(0);
        List<ProfessorEntry> profs = model.getProfessors();
        int totalWorkload = 0;

        for (ProfessorEntry p : profs) {
            int wl = p.calculateTotalWorkload();
            totalWorkload += wl;
            String ctText = p.getClassTeacherOf() != null ? p.getClassTeacherOf().getStandardDivisionName() : "---";
            profTableModel.addRow(new Object[]{p.getCode(), p.getName(), wl + " periods", ctText});
        }

        totalProfsBadge.setText("Faculty: " + profs.size() + " | Total Workload: " + totalWorkload);

        // Restore selection if possible
        if (selectedCode != null) {
            for (int r = 0; r < profTableModel.getRowCount(); r++) {
                if (selectedCode.equals(profTableModel.getValueAt(r, 0))) {
                    professorTable.setRowSelectionInterval(r, r);
                    break;
                }
            }
        }
    }

    private void onProfessorSelected() {
        int row = professorTable.getSelectedRow();
        if (row < 0) {
            selectedProfessor = null;
            selectedProfNameLabel.setText("Select a professor from the left");
            selectedProfWorkloadBadge.setText("");
            allocTableModel.setRowCount(0);
            enableRightPanel(false);
            return;
        }

        String code = (String) profTableModel.getValueAt(row, 0);
        selectedProfessor = model.findProfessor(code);
        if (selectedProfessor == null) return;

        enableRightPanel(true);
        selectedProfNameLabel.setText(selectedProfessor.getName() + " (" + selectedProfessor.getCode() + ")");
        int wl = selectedProfessor.calculateTotalWorkload();
        selectedProfWorkloadBadge.setText("Workload: " + wl + " periods / week");

        // Populate Class Teacher Dropdown
        classTeacherCombo.removeAllItems();
        classTeacherCombo.addItem("None");
        for (SchoolClass sc : model.getGeneratedClasses()) {
            classTeacherCombo.addItem(sc.getStandardDivisionName());
        }

        if (selectedProfessor.getClassTeacherOf() != null) {
            classTeacherCombo.setSelectedItem(selectedProfessor.getClassTeacherOf().getStandardDivisionName());
        } else {
            classTeacherCombo.setSelectedItem("None");
        }

        loadAllocationsForSelected();
    }

    private void loadAllocationsForSelected() {
        allocTableModel.setRowCount(0);
        if (selectedProfessor == null) return;

        for (AllocationEntry a : selectedProfessor.getAllocations()) {
            StringBuilder classes = new StringBuilder();
            for (SchoolClass sc : a.getTargetClasses()) {
                if (!classes.isEmpty()) classes.append(", ");
                classes.append(sc.getStandardDivisionName());
            }
            int total = a.getTargetClasses().size() * a.getWeeklyLectures();
            allocTableModel.addRow(new Object[]{
                    a.getSubject().getNormalizedName(),
                    classes.toString(),
                    a.getWeeklyLectures(),
                    total
            });
        }
    }

    private void onClassTeacherChanged() {
        if (selectedProfessor == null || classTeacherCombo.getSelectedItem() == null) return;
        String selected = (String) classTeacherCombo.getSelectedItem();
        if ("None".equals(selected)) {
            selectedProfessor.setClassTeacherOf(null);
        } else {
            SchoolClass sc = model.findClassByName(selected);
            selectedProfessor.setClassTeacherOf(sc);
        }
        // Update table display
        int row = professorTable.getSelectedRow();
        if (row >= 0) {
            profTableModel.setValueAt(selectedProfessor.getClassTeacherOf() != null
                    ? selectedProfessor.getClassTeacherOf().getStandardDivisionName() : "---", row, 3);
        }
        if (onChangeCallback != null) onChangeCallback.run();
    }

    private void showAddProfessorDialog() {
        JTextField nameField = new JTextField(18);
        JTextField codeField = new JTextField(10);
        JCheckBox freeCheck = new JCheckBox("Designated Substitute / Free Teacher (Zero primary load)");

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Professor Full Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Faculty Code (e.g. SIES_01):"));
        panel.add(codeField);
        panel.add(new JLabel("Free Teacher:"));
        panel.add(freeCheck);

        int res = JOptionPane.showConfirmDialog(this, panel, "Add New Professor", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String code = codeField.getText().trim().toUpperCase();
            if (name.isEmpty() || code.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name and Code cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (model.findProfessor(code) != null) {
                JOptionPane.showMessageDialog(this, "A professor with code '" + code + "' already exists.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            model.addProfessor(new ProfessorEntry(name, code, freeCheck.isSelected()));
            loadProfessors();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private void showEditProfessorDialog() {
        if (selectedProfessor == null) {
            JOptionPane.showMessageDialog(this, "Please select a professor to edit.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JTextField nameField = new JTextField(selectedProfessor.getName(), 18);
        JTextField codeField = new JTextField(selectedProfessor.getCode(), 10);
        codeField.setEditable(false); // Key
        JCheckBox freeCheck = new JCheckBox("Designated Substitute / Free Teacher");
        freeCheck.setSelected(selectedProfessor.isFreeTeacher());

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Professor Full Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Faculty Code:"));
        panel.add(codeField);
        panel.add(new JLabel("Free Teacher:"));
        panel.add(freeCheck);

        int res = JOptionPane.showConfirmDialog(this, panel, "Edit Professor", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            selectedProfessor.setName(name);
            selectedProfessor.setFreeTeacher(freeCheck.isSelected());
            loadProfessors();
            onProfessorSelected();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private void deleteSelectedProfessor() {
        if (selectedProfessor == null) {
            JOptionPane.showMessageDialog(this, "Please select a professor to delete.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int res = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete professor '" + selectedProfessor.getName() + "' (" + selectedProfessor.getCode() + ")?\nAll allocations will be removed.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
            model.removeProfessor(selectedProfessor.getCode());
            selectedProfessor = null;
            loadProfessors();
            onProfessorSelected();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private void showAddAllocationDialog() {
        if (selectedProfessor == null) return;
        if (model.getSubjects().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No subjects defined. Please add subjects first.", "Subjects Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (model.getGeneratedClasses().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No classes defined. Please configure academic standards first.", "Classes Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JComboBox<Subject> subjectCombo = new JComboBox<>(model.getSubjects().toArray(new Subject[0]));
        subjectCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Subject s) setText(s.getNormalizedName() + (s.isRequiresLab() ? " [Lab]" : ""));
                return this;
            }
        });

        // Class Selection List with Checkboxes
        DefaultListModel<SchoolClass> classListModel = new DefaultListModel<>();
        for (SchoolClass sc : model.getGeneratedClasses()) {
            classListModel.addElement(sc);
        }
        JList<SchoolClass> classList = new JList<>(classListModel);
        classList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        classList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SchoolClass sc) setText(sc.getStandardDivisionName());
                return this;
            }
        });

        JSpinner periodsSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 10, 1));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JPanel top = new JPanel(new GridLayout(2, 2, 6, 6));
        top.add(new JLabel("Subject:"));
        top.add(subjectCombo);
        top.add(new JLabel("Weekly Lectures / Class:"));
        top.add(periodsSpinner);
        panel.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(new JLabel("Target Classes (Hold Ctrl to select multiple):"), BorderLayout.NORTH);
        JScrollPane classScroll = new JScrollPane(classList);
        classScroll.setPreferredSize(new Dimension(280, 180));
        center.add(classScroll, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(this, panel, "Add Teaching Allocation", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            Subject subj = (Subject) subjectCombo.getSelectedItem();
            List<SchoolClass> selectedClasses = classList.getSelectedValuesList();
            int periods = (int) periodsSpinner.getValue();

            if (selectedClasses.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one class.", "Selection Required", JOptionPane.ERROR_MESSAGE);
                return;
            }

            selectedProfessor.addAllocation(new AllocationEntry(subj, new ArrayList<>(selectedClasses), periods));
            loadAllocationsForSelected();
            loadProfessors();
            onProfessorSelected();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private void showEditAllocationDialog() {
        if (selectedProfessor == null) return;
        int row = allocationTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an allocation to edit.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        AllocationEntry alloc = selectedProfessor.getAllocations().get(row);

        JComboBox<Subject> subjectCombo = new JComboBox<>(model.getSubjects().toArray(new Subject[0]));
        subjectCombo.setSelectedItem(alloc.getSubject());
        subjectCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Subject s) setText(s.getNormalizedName() + (s.isRequiresLab() ? " [Lab]" : ""));
                return this;
            }
        });

        DefaultListModel<SchoolClass> classListModel = new DefaultListModel<>();
        List<Integer> selectedIndices = new ArrayList<>();
        List<SchoolClass> allClasses = model.getGeneratedClasses();
        for (int i = 0; i < allClasses.size(); i++) {
            SchoolClass sc = allClasses.get(i);
            classListModel.addElement(sc);
            if (alloc.getTargetClasses().contains(sc)) {
                selectedIndices.add(i);
            }
        }
        JList<SchoolClass> classList = new JList<>(classListModel);
        classList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        int[] indices = selectedIndices.stream().mapToInt(Integer::intValue).toArray();
        classList.setSelectedIndices(indices);
        classList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SchoolClass sc) setText(sc.getStandardDivisionName());
                return this;
            }
        });

        JSpinner periodsSpinner = new JSpinner(new SpinnerNumberModel(alloc.getWeeklyLectures(), 1, 10, 1));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JPanel top = new JPanel(new GridLayout(2, 2, 6, 6));
        top.add(new JLabel("Subject:"));
        top.add(subjectCombo);
        top.add(new JLabel("Weekly Lectures / Class:"));
        top.add(periodsSpinner);
        panel.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(new JLabel("Target Classes (Hold Ctrl to select multiple):"), BorderLayout.NORTH);
        JScrollPane classScroll = new JScrollPane(classList);
        classScroll.setPreferredSize(new Dimension(280, 180));
        center.add(classScroll, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(this, panel, "Edit Teaching Allocation", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            Subject subj = (Subject) subjectCombo.getSelectedItem();
            List<SchoolClass> selectedClasses = classList.getSelectedValuesList();
            int periods = (int) periodsSpinner.getValue();

            if (selectedClasses.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one class.", "Selection Required", JOptionPane.ERROR_MESSAGE);
                return;
            }

            alloc.setSubject(subj);
            alloc.setTargetClasses(new ArrayList<>(selectedClasses));
            alloc.setWeeklyLectures(periods);

            loadAllocationsForSelected();
            loadProfessors();
            onProfessorSelected();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private void deleteSelectedAllocation() {
        if (selectedProfessor == null) return;
        int row = allocationTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an allocation to delete.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int res = JOptionPane.showConfirmDialog(this, "Delete this teaching allocation?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (res == JOptionPane.YES_OPTION) {
            selectedProfessor.getAllocations().remove(row);
            loadAllocationsForSelected();
            loadProfessors();
            onProfessorSelected();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private void loadSchoolPreset() {
        int res = JOptionPane.showConfirmDialog(this,
                "Load verified SIES APJ School 2026–27 teaching staff and atomic allocations?\nThis will overwrite current professors and subjects.",
                "Load School Preset", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
            boolean ok = model.loadDefaultSchoolPreset();
            if (ok) {
                loadProfessors();
                selectedProfessor = null;
                onProfessorSelected();
                JOptionPane.showMessageDialog(this,
                        "Successfully loaded 2026–27 School Preset (" + model.getProfessors().size() + " faculty members, "
                                + model.getSubjects().size() + " subjects, " + model.getGeneratedClasses().size() + " classes).",
                        "Preset Loaded", JOptionPane.INFORMATION_MESSAGE);
                if (onChangeCallback != null) onChangeCallback.run();
            } else {
                JOptionPane.showMessageDialog(this, "Could not locate default 2026–27 allotment files in project folder.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void clearAllProfessors() {
        int res = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear all professors?", "Confirm Clear", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
            model.clearProfessors();
            selectedProfessor = null;
            loadProfessors();
            onProfessorSelected();
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }
}
