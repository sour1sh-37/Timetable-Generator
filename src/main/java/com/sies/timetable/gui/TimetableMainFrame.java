package com.sies.timetable.gui;

import com.formdev.flatlaf.FlatLightLaf;
import com.sies.timetable.model.ManualTimetableModel;
import com.sies.timetable.service.TimetableService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Main Application Window for SIES APJ School Timetable Generator.
 * Transforms the workflow into a comprehensive, tabbed GUI-first experience
 * supporting manual entry of Academic Structure, Capacity, Subjects, Faculty Allocations,
 * Review & Generation with individual class PDF exports, plus internal file-import fallback.
 */
public class TimetableMainFrame extends JFrame {

    private final ManualTimetableModel manualModel;

    // GUI Tabs
    private JTabbedPane tabbedPane;
    private AcademicStructurePanel academicPanel;
    private TimetableStructurePanel structurePanel;
    private SubjectManagementPanel subjectPanel;
    private ProfessorManagementPanel professorPanel;
    private ReviewSummaryPanel reviewPanel;

    // Fallback File Import tab components
    private File currentProjectFolder;
    private TimetableService.FolderDetectionResult currentDetection;
    private TimetableService.GenerationResult fallbackResult;
    private JTextField folderTextField;
    private JLabel fallbackStatusLabel;
    private JButton fallbackSelectFolderBtn;
    private JButton fallbackGenerateBtn;
    private JProgressBar fallbackProgressBar;
    private JTextArea fallbackLogTextArea;

    public TimetableMainFrame() {
        super("SIES APJ School — 2026–27 Timetable Generator & Scheduler");
        initLookAndFeel();

        manualModel = new ManualTimetableModel();

        // Attempt to load 2026-27 preset automatically if files are in directory
        manualModel.loadDefaultSchoolPreset();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1020, 780);
        setMinimumSize(new Dimension(900, 680));
        setLocationRelativeTo(null);

        initComponents();

        // Initialize fallback folder detection
        File defaultDir = new File(".").getAbsoluteFile().getParentFile();
        setProjectFolder(defaultDir != null && defaultDir.exists() ? defaultDir : new File("."));
    }

    private void initLookAndFeel() {
        try {
            FlatLightLaf.setup();
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("TabbedPane.showTabSeparators", true);
        } catch (Exception ignored) {
        }
    }

    private void initComponents() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 8));
        contentPane.setBorder(new EmptyBorder(12, 16, 12, 16));
        contentPane.setBackground(new Color(248, 250, 252));
        setContentPane(contentPane);

        // Header Section
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel titleLabel = new JLabel("SIES APJ SCHOOL");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(new Color(26, 54, 93));

        JLabel subLabel = new JLabel("ACADEMIC YEAR 2026–27  |  AUTOMATED TIMETABLE GENERATOR & SCHEDULER");
        subLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        subLabel.setForeground(new Color(100, 116, 139));

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subLabel);
        headerPanel.add(titleBox, BorderLayout.WEST);

        // Quick Preset Button in Header
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        headerActions.setOpaque(false);

        JButton loadPresetHeaderBtn = new JButton("⚡ Load 2026-27 School Preset");
        loadPresetHeaderBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        loadPresetHeaderBtn.setBackground(new Color(240, 253, 244));
        loadPresetHeaderBtn.setForeground(new Color(22, 101, 52));
        loadPresetHeaderBtn.addActionListener(e -> {
            boolean ok = manualModel.loadDefaultSchoolPreset();
            if (ok) {
                refreshAllPanels();
                JOptionPane.showMessageDialog(this,
                        "Successfully loaded 2026–27 SIES APJ School Preset ("
                                + manualModel.getProfessors().size() + " professors, "
                                + manualModel.getSubjects().size() + " subjects, "
                                + manualModel.getGeneratedClasses().size() + " classes).",
                        "Preset Loaded", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Could not locate 2026-27 allotment files in workspace.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        headerActions.add(loadPresetHeaderBtn);
        headerPanel.add(headerActions, BorderLayout.EAST);

        contentPane.add(headerPanel, BorderLayout.NORTH);

        // Main Tabbed Interface
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Create individual panels
        academicPanel = new AcademicStructurePanel(manualModel, this::onModelUpdated);
        structurePanel = new TimetableStructurePanel(manualModel, this::onModelUpdated);
        subjectPanel = new SubjectManagementPanel(manualModel, this::onModelUpdated);
        professorPanel = new ProfessorManagementPanel(manualModel, this::onModelUpdated);
        reviewPanel = new ReviewSummaryPanel(manualModel);

        tabbedPane.addTab("1. Academic Structure", academicPanel);
        tabbedPane.addTab("2. Timetable Capacity", structurePanel);
        tabbedPane.addTab("3. Subjects", subjectPanel);
        tabbedPane.addTab("4. Faculty & Allocations", professorPanel);
        tabbedPane.addTab("5. Review & Generate", reviewPanel);

        // 6. Developer / Fallback File-Import tab
        tabbedPane.addTab("6. File Import (Developer)", createFallbackImportPanel());

        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedComponent() == reviewPanel) {
                reviewPanel.refreshSummary();
            }
        });

        contentPane.add(tabbedPane, BorderLayout.CENTER);
    }

    private void onModelUpdated() {
        if (reviewPanel != null) {
            reviewPanel.refreshSummary();
        }
    }

    private void refreshAllPanels() {
        if (academicPanel != null) academicPanel.loadDataFromModel();
        if (structurePanel != null) structurePanel.loadDataFromModel();
        if (subjectPanel != null) subjectPanel.loadDataFromModel();
        if (professorPanel != null) professorPanel.loadProfessors();
        if (reviewPanel != null) reviewPanel.refreshSummary();
    }

    // =========================================================================
    // Fallback File Import Panel (Preserving legacy feature)
    // =========================================================================

    private JPanel createFallbackImportPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));
        panel.setBackground(Color.WHITE);

        JPanel infoBox = new JPanel();
        infoBox.setLayout(new BoxLayout(infoBox, BoxLayout.Y_AXIS));
        infoBox.setOpaque(false);

        JLabel fallbackTitle = new JLabel("File-Based Batch Import (Developer / Fallback Mode)");
        fallbackTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        fallbackTitle.setForeground(new Color(26, 54, 93));

        JLabel fallbackSub = new JLabel("Directly load and solve from external allotment spreadsheets (Standardwise, Teacherwise, Classteachers).");
        fallbackSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fallbackSub.setForeground(new Color(100, 116, 139));

        infoBox.add(fallbackTitle);
        infoBox.add(Box.createVerticalStrut(4));
        infoBox.add(fallbackSub);
        infoBox.add(Box.createVerticalStrut(12));
        panel.add(infoBox, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // Folder Row
        JPanel folderRow = new JPanel(new BorderLayout(8, 0));
        folderRow.setOpaque(false);

        folderTextField = new JTextField();
        folderTextField.setEditable(false);
        folderTextField.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        fallbackSelectFolderBtn = new JButton("Select Folder...");
        fallbackSelectFolderBtn.addActionListener(e -> chooseFolder());

        folderRow.add(folderTextField, BorderLayout.CENTER);
        folderRow.add(fallbackSelectFolderBtn, BorderLayout.EAST);
        centerPanel.add(folderRow);
        centerPanel.add(Box.createVerticalStrut(6));

        fallbackStatusLabel = new JLabel("Status: Checking folder...");
        fallbackStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        centerPanel.add(fallbackStatusLabel);
        centerPanel.add(Box.createVerticalStrut(15));

        // Generate Action
        JPanel actRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        actRow.setOpaque(false);
        fallbackGenerateBtn = new JButton("⚡ GENERATE FROM SPREADSHEETS");
        fallbackGenerateBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        fallbackGenerateBtn.setBackground(new Color(37, 99, 235));
        fallbackGenerateBtn.setForeground(Color.WHITE);
        fallbackGenerateBtn.setPreferredSize(new Dimension(320, 40));
        fallbackGenerateBtn.addActionListener(e -> runFallbackGeneration());
        actRow.add(fallbackGenerateBtn);
        centerPanel.add(actRow);
        centerPanel.add(Box.createVerticalStrut(15));

        // Progress & Log
        fallbackProgressBar = new JProgressBar(0, 7);
        fallbackProgressBar.setStringPainted(true);
        fallbackProgressBar.setString("Ready");
        centerPanel.add(fallbackProgressBar);
        centerPanel.add(Box.createVerticalStrut(6));

        fallbackLogTextArea = new JTextArea(8, 40);
        fallbackLogTextArea.setEditable(false);
        fallbackLogTextArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        fallbackLogTextArea.setBackground(new Color(248, 250, 252));
        JScrollPane logScroll = new JScrollPane(fallbackLogTextArea);
        centerPanel.add(logScroll);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(currentProjectFolder != null ? currentProjectFolder : new File("."));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Folder Containing School Allotment Spreadsheets");

        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            setProjectFolder(chooser.getSelectedFile());
        }
    }

    public void setProjectFolder(File folder) {
        this.currentProjectFolder = folder;
        if (folderTextField != null) {
            folderTextField.setText(folder.getAbsolutePath());
        }

        currentDetection = TimetableService.detectInputFiles(folder);
        if (fallbackStatusLabel != null) {
            if (currentDetection.isValid()) {
                fallbackStatusLabel.setText("<html><b style='color:#16a34a;'>✓ All required files found</b> <span style='color:#64748b;'>(" + currentDetection.folder().getName() + ")</span></html>");
                fallbackGenerateBtn.setEnabled(true);
            } else {
                fallbackStatusLabel.setText("<html><b style='color:#dc2626;'>⚠ Missing files:</b> " + String.join(", ", currentDetection.missingFiles()) + "</html>");
                fallbackGenerateBtn.setEnabled(false);
            }
        }
    }

    private void runFallbackGeneration() {
        if (currentDetection == null || !currentDetection.isValid()) {
            JOptionPane.showMessageDialog(this, "Please select a folder with all required spreadsheet files.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        fallbackGenerateBtn.setEnabled(false);
        fallbackProgressBar.setValue(0);
        fallbackLogTextArea.setText("");

        SwingWorker<TimetableService.GenerationResult, String> worker = new SwingWorker<>() {
            @Override
            protected TimetableService.GenerationResult doInBackground() {
                return TimetableService.generate(currentDetection, (step, total, msg) -> {
                    publish(String.format("[%d/%d] %s", step, total, msg));
                    setProgress((step * 100) / total);
                });
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) {
                    fallbackLogTextArea.append(msg + "\n");
                    fallbackLogTextArea.setCaretPosition(fallbackLogTextArea.getDocument().getLength());
                    fallbackProgressBar.setString(msg);
                }
                fallbackProgressBar.setValue(getProgress());
            }

            @Override
            protected void done() {
                fallbackGenerateBtn.setEnabled(true);
                try {
                    fallbackResult = get();
                    if (fallbackResult.isSuccess()) {
                        fallbackProgressBar.setValue(7);
                        fallbackProgressBar.setString("Completed Successfully");
                        JOptionPane.showMessageDialog(TimetableMainFrame.this,
                                "Batch generation succeeded!\nPDFs exported to: "
                                        + (fallbackResult.pdfDir() != null ? fallbackResult.pdfDir().getPath() : "output/pdf"),
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        fallbackProgressBar.setString("Failed");
                        JOptionPane.showMessageDialog(TimetableMainFrame.this,
                                "Generation failed: " + fallbackResult.errorMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(TimetableMainFrame.this, "Execution failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TimetableMainFrame frame = new TimetableMainFrame();
            frame.setVisible(true);
        });
    }
}
