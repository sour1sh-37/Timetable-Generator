package com.sies.timetable.gui;

import com.sies.timetable.model.Day;
import com.sies.timetable.model.ManualTimetableModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Panel for configuring daily periods, break position, and required weekly lectures.
 * Features real-time mathematical validation banner enforcing Available >= Required.
 */
public class TimetableStructurePanel extends JPanel {

    private final ManualTimetableModel model;
    private final Runnable onChangeCallback;

    private final Map<Day, JSpinner> daySpinners = new HashMap<>();
    private JSpinner requiredWeeklySpinner;
    private JSpinner recessAfterSpinner;

    private JPanel validationBanner;
    private JLabel validationIconLabel;
    private JLabel validationStatusLabel;
    private JLabel validationFormulaLabel;

    public TimetableStructurePanel(ManualTimetableModel model, Runnable onChangeCallback) {
        this.model = model;
        this.onChangeCallback = onChangeCallback;

        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 20, 15, 20));
        setBackground(Color.WHITE);

        initComponents();
        loadDataFromModel();
    }

    private void initComponents() {
        // North Title
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setOpaque(false);

        JLabel title = new JLabel("Timetable Structure & Capacity Configuration");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(26, 54, 93));

        JLabel sub = new JLabel("Configure daily periods, recess break position, and weekly required lectures per class.");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 116, 139));

        northPanel.add(title);
        northPanel.add(Box.createVerticalStrut(4));
        northPanel.add(sub);
        northPanel.add(Box.createVerticalStrut(15));

        // Live Validation Banner
        validationBanner = new JPanel(new BorderLayout(12, 6));
        validationBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(187, 247, 208), 2),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        validationBanner.setBackground(new Color(240, 253, 244));

        validationIconLabel = new JLabel("✓");
        validationIconLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        validationIconLabel.setForeground(new Color(22, 101, 52));
        validationBanner.add(validationIconLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        validationStatusLabel = new JLabel("VALID CAPACITY: Total Available Periods >= Weekly Required");
        validationStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        validationStatusLabel.setForeground(new Color(22, 101, 52));

        validationFormulaLabel = new JLabel("Available: 48 | Required: 48 | Spare Periods: 0");
        validationFormulaLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        validationFormulaLabel.setForeground(new Color(21, 128, 61));

        textPanel.add(validationStatusLabel);
        textPanel.add(Box.createVerticalStrut(3));
        textPanel.add(validationFormulaLabel);
        validationBanner.add(textPanel, BorderLayout.CENTER);

        northPanel.add(validationBanner);
        northPanel.add(Box.createVerticalStrut(15));

        add(northPanel, BorderLayout.NORTH);

        // Center: Settings Cards
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        centerPanel.setOpaque(false);

        // 1. Daily Periods Card
        JPanel dailyCard = new JPanel();
        dailyCard.setLayout(new BoxLayout(dailyCard, BoxLayout.Y_AXIS));
        dailyCard.setOpaque(false);
        dailyCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                "Daily Periods (Monday – Saturday)",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(30, 41, 59)
        ));

        for (Day day : Day.values()) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
            row.setOpaque(false);

            JLabel lbl = new JLabel(day.getDisplayName() + ":");
            lbl.setPreferredSize(new Dimension(110, 26));
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            row.add(lbl);

            int defVal = (day == Day.SATURDAY) ? 8 : 8;
            JSpinner spin = new JSpinner(new SpinnerNumberModel(defVal, 0, 12, 1));
            spin.setPreferredSize(new Dimension(65, 30));
            spin.addChangeListener(e -> onValuesChanged());
            daySpinners.put(day, spin);
            row.add(spin);

            JLabel pLbl = new JLabel("periods / day");
            pLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            pLbl.setForeground(new Color(100, 116, 139));
            row.add(pLbl);

            dailyCard.add(row);
        }

        // Quick Preset Buttons for Daily Periods
        JPanel presetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        presetRow.setOpaque(false);

        JButton btnAll8 = new JButton("Set All 8 (48 Slots)");
        btnAll8.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnAll8.addActionListener(e -> {
            for (Day d : Day.values()) daySpinners.get(d).setValue(8);
            requiredWeeklySpinner.setValue(48);
            onValuesChanged();
        });
        presetRow.add(btnAll8);

        JButton btnSat4 = new JButton("Mon–Fri 8, Sat 4 (44 Slots)");
        btnSat4.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSat4.addActionListener(e -> {
            for (Day d : Day.values()) {
                daySpinners.get(d).setValue(d == Day.SATURDAY ? 4 : 8);
            }
            requiredWeeklySpinner.setValue(44);
            onValuesChanged();
        });
        presetRow.add(btnSat4);

        dailyCard.add(presetRow);
        centerPanel.add(dailyCard);

        // 2. Class Requirements & Recess Card
        JPanel ruleCard = new JPanel();
        ruleCard.setLayout(new BoxLayout(ruleCard, BoxLayout.Y_AXIS));
        ruleCard.setOpaque(false);
        ruleCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                "Class Load & Recess Position",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(30, 41, 59)
        ));

        // Required Weekly Lectures
        JPanel reqRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        reqRow.setOpaque(false);

        JLabel reqLbl = new JLabel("Weekly Required Periods / Class:");
        reqLbl.setPreferredSize(new Dimension(220, 26));
        reqLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        reqRow.add(reqLbl);

        requiredWeeklySpinner = new JSpinner(new SpinnerNumberModel(model.getWeeklyRequiredPeriodsPerClass(), 20, 60, 1));
        requiredWeeklySpinner.setPreferredSize(new Dimension(65, 30));
        requiredWeeklySpinner.addChangeListener(e -> onValuesChanged());
        reqRow.add(requiredWeeklySpinner);

        ruleCard.add(reqRow);

        JLabel reqHelp = new JLabel("<html><p style='width:300px; color:#64748b; font-size:11px;'>"
                + "The exact number of teaching periods each class division must be scheduled per week. "
                + "Subject period allocations must sum to this value.</p></html>");
        reqHelp.setBorder(new EmptyBorder(0, 15, 10, 15));
        ruleCard.add(reqHelp);

        // Recess Break Position
        JPanel recRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        recRow.setOpaque(false);

        JLabel recLbl = new JLabel("Recess Break Position:");
        recLbl.setPreferredSize(new Dimension(220, 26));
        recLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        recRow.add(recLbl);

        recessAfterSpinner = new JSpinner(new SpinnerNumberModel(model.getRecessAfterPeriod(), 1, 8, 1));
        recessAfterSpinner.setPreferredSize(new Dimension(65, 30));
        recessAfterSpinner.addChangeListener(e -> onValuesChanged());
        recRow.add(recessAfterSpinner);

        JLabel recSuffix = new JLabel("after period");
        recSuffix.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        recSuffix.setForeground(new Color(100, 116, 139));
        recRow.add(recSuffix);

        ruleCard.add(recRow);

        JLabel recHelp = new JLabel("<html><p style='width:300px; color:#64748b; font-size:11px;'>"
                + "Recess partitions the day into two continuous blocks (Session 1 and Session 2). "
                + "Default is after Period 4 (e.g., 09:30–09:50).</p></html>");
        recHelp.setBorder(new EmptyBorder(0, 15, 10, 15));
        ruleCard.add(recHelp);

        centerPanel.add(ruleCard);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void onValuesChanged() {
        for (Day d : Day.values()) {
            int p = (int) daySpinners.get(d).getValue();
            model.setDailyPeriods(d, p);
        }
        int req = (int) requiredWeeklySpinner.getValue();
        model.setWeeklyRequiredPeriodsPerClass(req);

        int rec = (int) recessAfterSpinner.getValue();
        model.setRecessAfterPeriod(rec);

        updateValidationBanner();
        if (onChangeCallback != null) onChangeCallback.run();
    }

    public void loadDataFromModel() {
        for (Day d : Day.values()) {
            daySpinners.get(d).setValue(model.getDailyPeriods().getOrDefault(d, 8));
        }
        requiredWeeklySpinner.setValue(model.getWeeklyRequiredPeriodsPerClass());
        recessAfterSpinner.setValue(model.getRecessAfterPeriod());
        updateValidationBanner();
    }

    private void updateValidationBanner() {
        int available = model.getTotalAvailablePeriods();
        int required = model.getWeeklyRequiredPeriodsPerClass();

        StringBuilder dailyBreakdown = new StringBuilder();
        for (Day d : Day.values()) {
            if (!dailyBreakdown.isEmpty()) dailyBreakdown.append(" + ");
            dailyBreakdown.append(model.getDailyPeriods().getOrDefault(d, 8));
        }

        if (available >= required) {
            int spare = available - required;
            validationBanner.setBackground(new Color(240, 253, 244));
            validationBanner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(187, 247, 208), 2),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)
            ));
            validationIconLabel.setText("✓");
            validationIconLabel.setForeground(new Color(22, 101, 52));

            if (spare > 0) {
                validationStatusLabel.setText("VALID CAPACITY: Feasible Schedule (" + spare + " spare periods per class)");
                validationStatusLabel.setForeground(new Color(22, 101, 52));
                validationFormulaLabel.setText(String.format("Available: %d periods (%s)  >=  Required: %d periods/class  (Remaining %d slots will be free)",
                        available, dailyBreakdown.toString(), required, spare));
            } else {
                validationStatusLabel.setText("VALID CAPACITY: Exact Match (0 spare periods)");
                validationStatusLabel.setForeground(new Color(22, 101, 52));
                validationFormulaLabel.setText(String.format("Available: %d periods (%s)  ==  Required: %d periods/class",
                        available, dailyBreakdown.toString(), required));
            }
        } else {
            int deficit = required - available;
            validationBanner.setBackground(new Color(254, 242, 242));
            validationBanner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(254, 202, 202), 2),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)
            ));
            validationIconLabel.setText("✗");
            validationIconLabel.setForeground(new Color(185, 28, 28));

            validationStatusLabel.setText("INVALID CAPACITY: Insufficient Available Periods (Deficit: " + deficit + " periods)");
            validationStatusLabel.setForeground(new Color(185, 28, 28));
            validationFormulaLabel.setText(String.format("Available: %d periods (%s)  <  Required: %d periods/class  (Increase daily periods or decrease required)",
                    available, dailyBreakdown.toString(), required));
        }
    }
}
