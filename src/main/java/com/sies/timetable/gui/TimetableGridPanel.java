package com.sies.timetable.gui;

import com.sies.timetable.service.TimetableService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Custom Swing component that renders an authentic school timetable grid (Day × Period),
 * dynamically adapting to configurable period counts and recess breaks.
 */
public class TimetableGridPanel extends JPanel {

    private final JLabel titleLabel;
    private final JLabel subtitleLabel;
    private final JTable table;
    private final DefaultTableModel tableModel;

    public TimetableGridPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setBackground(Color.WHITE);

        // Header Panel
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);

        titleLabel = new JLabel("TIMETABLE");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(26, 54, 93)); // Navy
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        subtitleLabel = new JLabel("Select an entity to view");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitleLabel.setForeground(new Color(100, 116, 139)); // Slate
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(subtitleLabel);
        headerPanel.add(Box.createVerticalStrut(10));

        add(headerPanel, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(58);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setGridColor(new Color(226, 232, 240));
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setFillsViewportHeight(true);

        JTableHeader th = table.getTableHeader();
        th.setFont(new Font("Segoe UI", Font.BOLD, 12));
        th.setBackground(new Color(26, 54, 93));
        th.setForeground(Color.WHITE);
        th.setPreferredSize(new Dimension(th.getWidth(), 38));

        table.setDefaultRenderer(Object.class, new TimetableCellRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        scrollPane.getViewport().setBackground(Color.WHITE);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void displayGrid(TimetableService.TimetableGrid grid) {
        if (grid == null) return;

        titleLabel.setText(grid.title());
        subtitleLabel.setText(grid.subtitle());

        String[] headers = grid.headers();
        tableModel.setColumnIdentifiers(headers);
        tableModel.setRowCount(0);

        int numCols = headers.length;
        for (int d = 0; d < grid.rowLabels().length; d++) {
            Object[] rowData = new Object[numCols];
            rowData[0] = grid.rowLabels()[d];
            for (int col = 0; col < numCols - 1 && col < grid.cells()[d].length; col++) {
                rowData[col + 1] = grid.cells()[d][col];
            }
            tableModel.addRow(rowData);
        }

        // Adjust column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(95);
        for (int c = 1; c < numCols; c++) {
            String h = headers[c];
            if (h != null && h.toUpperCase().contains("RECESS")) {
                table.getColumnModel().getColumn(c).setPreferredWidth(85);
            } else {
                table.getColumnModel().getColumn(c).setPreferredWidth(115);
            }
        }
    }

    private static class TimetableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setVerticalAlignment(SwingConstants.CENTER);

            if (column == 0) {
                // Day Column
                label.setFont(new Font("Segoe UI", Font.BOLD, 13));
                label.setBackground(new Color(241, 245, 249));
                label.setForeground(new Color(30, 41, 59));
                label.setText(value != null ? value.toString() : "");
            } else if (value instanceof TimetableService.TimetableCell cell) {
                if (cell.isRecess()) {
                    label.setBackground(new Color(254, 243, 199)); // Amber
                    label.setForeground(new Color(146, 64, 14));
                    String sub = (cell.subText() != null && !cell.subText().isEmpty()) ? cell.subText() : "09:30-09:50";
                    label.setText("<html><center><b>RECESS</b><br><small style='color:#92400e;'>" + sub + "</small></center></html>");
                } else if (cell.isFree()) {
                    label.setBackground(new Color(248, 250, 252));
                    label.setForeground(new Color(148, 163, 184));
                    label.setText("<html><center><i style='color:#94a3b8;'>---</i></center></html>");
                } else {
                    label.setBackground(Color.WHITE);
                    label.setForeground(new Color(15, 23, 42));
                    String sub = cell.subText() != null && !cell.subText().isEmpty() ? "<br><span style='color:#2563eb; font-weight:bold;'>" + cell.subText() + "</span>" : "";
                    label.setText("<html><center><b>" + cell.mainText() + "</b>" + sub + "</center></html>");
                }
            }

            return label;
        }
    }
}
