package edu.univ.erp.ui;

import edu.univ.erp.service.InstructorService;
import edu.univ.erp.service.InstructorService.GradeRow;
import edu.univ.erp.service.InstructorService.RosterRow;
import edu.univ.erp.service.InstructorService.SectionRow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class InstructorDashboard extends JFrame {

    private final String instructorUserId;
    private final String username;

    // --- Colors & Fonts ---
    private static final Color BG = Color.WHITE;
    private static final Color ACCENT = new Color(0, 180, 180);
    private static final Color ACCENT_HOVER = new Color(0, 150, 150);
    private static final Color ACCENT_DARK = new Color(28, 160, 157);
    private static final Color MUTED = new Color(110, 110, 110);

    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 20);
    private static final Font HEADER_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    // --- Layout & Container ---
    private CardLayout cardLayout;
    private JPanel mainCardPanel;
    private static final String VIEW_SECTIONS = "SECTIONS";
    private static final String VIEW_GRADES = "GRADES";

    // --- Components ---
    private final DefaultTableModel sectionsModel = new DefaultTableModel();
    private final JTable tblSections = new JTable(sectionsModel) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    private DefaultTableModel gradeModel;
    private final JTable tblGrades;

    private final JLabel lblWelcome = new JLabel();
    private final JLabel lblDepartment = new JLabel("Department: -");

    // Replaced the old stats label with a button
    private final JButton btnViewStats = new PillButton("View Stats");
    private String currentStatsText = "No data available."; // Stores current stats for the popup

    private final JLabel lblGradebookTitle = new JLabel("Gradebook");

    private final JLabel lblMaintenance = new JLabel();
    private volatile boolean maintenanceOn = false;
    private javax.swing.Timer maintenancePollTimer;

    // --- Buttons ---
    private final JButton btnRefreshSections = new PillButton("Refresh Sections");
    private final JButton btnLoadRoster = new PillButton("Load Roster");

    // Gradebook buttons
    private final JButton btnBack = new PillButton("← Back");
    private final JButton btnComputeFinal = new PillButton("Compute Final");
    private final JButton btnSave = new PillButton("Save Grades");
    private final JButton btnExport = new PillButton("Export CSV");

    private static final double W_QUIZ = 0.20;
    private static final double W_MID = 0.30;
    private static final double W_END = 0.50;

    private final InstructorService service = new InstructorService();

    public InstructorDashboard(String instructorUserId, String username) {
        super("Instructor Dashboard - " + username);
        this.instructorUserId = instructorUserId;
        this.username = username;

        gradeModel = new DefaultTableModel(new String[]{
                "Enrollment ID","Student ID","Roll No","Student Name","Quiz","Midterm","EndSem","Final"
        }, 0) {
            @Override public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 4,5,6,7 -> Double.class;
                    default -> String.class;
                };
            }
            @Override public boolean isCellEditable(int row, int column) {
                if (column >= 4 && column <= 6) return !maintenanceOn;
                return false;
            }
        };

        tblGrades = new JTable(gradeModel);

        initUI();
        loadInstructorDepartment();
        loadSections();
    }

    private void initUI() {
        // frame basics
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1150, 720);
        setLocationRelativeTo(null);

        // root panel
        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(12,12,12,12));
        setContentPane(root);

        // --- HEADER ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(new EmptyBorder(8,12,12,12));

        // Left Header: Welcome Message
        JPanel titleBlock = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        titleBlock.setOpaque(false);
        lblWelcome.setFont(TITLE_FONT);
        lblWelcome.setForeground(Color.BLACK);
        lblWelcome.setText("Welcome, " + username);
        titleBlock.add(lblWelcome);
        header.add(titleBlock, BorderLayout.WEST);

        // Right Header: Maintenance + Stats Button + Department
        JPanel rightBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        rightBlock.setOpaque(false);

        // Maintenance Label
        lblMaintenance.setFont(HEADER_FONT.deriveFont(Font.BOLD, 12f));
        lblMaintenance.setForeground(new Color(180,20,20));
        lblMaintenance.setVisible(false);
        rightBlock.add(lblMaintenance);

        // Stats Button (Small version of PillButton)
        btnViewStats.setPreferredSize(new Dimension(100, 30));
        btnViewStats.setFont(HEADER_FONT.deriveFont(12f));
        // Only enable this button when looking at grades
        btnViewStats.setVisible(false);
        rightBlock.add(btnViewStats);

        // Spacer
        rightBlock.add(Box.createHorizontalStrut(15));

        // Department Label
        lblDepartment.setFont(HEADER_FONT.deriveFont(Font.BOLD));
        lblDepartment.setForeground(MUTED);
        rightBlock.add(lblDepartment);

        header.add(rightBlock, BorderLayout.EAST);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(230,230,230));

        root.add(header, BorderLayout.NORTH);
        root.add(sep, BorderLayout.CENTER);

        // --- CARD LAYOUT SETUP ---
        cardLayout = new CardLayout();
        mainCardPanel = new JPanel(cardLayout);
        mainCardPanel.setOpaque(false);

        // 1. Create Sections View
        JPanel pnlSectionsView = createSectionsView();
        mainCardPanel.add(pnlSectionsView, VIEW_SECTIONS);

        // 2. Create Gradebook View
        JPanel pnlGradebookView = createGradebookView();
        mainCardPanel.add(pnlGradebookView, VIEW_GRADES);

        root.add(mainCardPanel, BorderLayout.CENTER);

        // --- Actions ---

        btnRefreshSections.addActionListener((ActionEvent e) -> loadSections());

        btnLoadRoster.addActionListener((ActionEvent e) -> {
            int viewRow = tblSections.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(this, "Select a section first.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = tblSections.convertRowIndexToModel(viewRow);
            String sectionId = (String) sectionsModel.getValueAt(modelRow, 0);
            String courseName = (String) sectionsModel.getValueAt(modelRow, 2);

            lblGradebookTitle.setText("Gradebook: " + courseName + " (" + sectionId + ")");
            loadRosterForSection(sectionId);

            // Switch views and show stats button
            cardLayout.show(mainCardPanel, VIEW_GRADES);
            btnViewStats.setVisible(true);
        });

        // Grades View Actions
        btnBack.addActionListener(e -> {
            cardLayout.show(mainCardPanel, VIEW_SECTIONS);
            btnViewStats.setVisible(false); // Hide stats button on sections page
        });

        btnComputeFinal.addActionListener((ActionEvent e) -> computeFinalAndUpdateTable());
        btnSave.addActionListener((ActionEvent e) -> saveGradesToDB());
        btnExport.addActionListener((ActionEvent e) -> exportGradesCSV());

        // Stats Button Action
        btnViewStats.addActionListener(e -> showStatsWindow());

        // Maintenance timer
        refreshMaintenanceBanner();
        maintenancePollTimer = new javax.swing.Timer(30_000, e -> refreshMaintenanceBanner());
        maintenancePollTimer.setInitialDelay(30_000);
        maintenancePollTimer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowActivated(java.awt.event.WindowEvent e) { refreshMaintenanceBanner(); }
        });
    }

    // --- Helper: Show Stats Window ---
    private void showStatsWindow() {
        JDialog statsDialog = new JDialog(this, "Section Statistics", true);
        statsDialog.setSize(400, 250);
        statsDialog.setLocationRelativeTo(this);
        statsDialog.setLayout(new BorderLayout());
        statsDialog.getContentPane().setBackground(Color.WHITE);

        // Clean styling for the stats text
        JTextArea textArea = new JTextArea(currentStatsText);
        textArea.setEditable(false);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textArea.setBorder(new EmptyBorder(20, 20, 20, 20));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        statsDialog.add(textArea, BorderLayout.CENTER);

        JButton closeBtn = new PillButton("Close");
        closeBtn.setPreferredSize(new Dimension(80, 35));
        closeBtn.addActionListener(e -> statsDialog.dispose());

        JPanel btnPanel = new JPanel();
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        btnPanel.add(closeBtn);

        statsDialog.add(btnPanel, BorderLayout.SOUTH);
        statsDialog.setVisible(true);
    }

    // --- View Creation Methods ---

    private JPanel createSectionsView() {
        JPanel pnl = new JPanel(new BorderLayout(8, 8));
        pnl.setOpaque(false);

        // Table Setup
        sectionsModel.setColumnIdentifiers(new String[]{
                "Section ID","Course Code","Title","Semester","Year","Day","Start","End","Room","Capacity"
        });
        tblSections.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(tblSections, true);

        JLabel title = new JLabel("My Sections");
        title.setFont(HEADER_FONT.deriveFont(Font.BOLD));
        title.setBorder(new EmptyBorder(6,6,6,6));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btnPanel.setOpaque(false);
        btnPanel.add(btnRefreshSections);
        btnPanel.add(btnLoadRoster);

        JButton logoutBtn = createLogoutButton();
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setOpaque(false);
        bottomContainer.add(btnPanel, BorderLayout.WEST);

        JPanel rightBtnContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        rightBtnContainer.setOpaque(false);
        rightBtnContainer.add(logoutBtn);
        bottomContainer.add(rightBtnContainer, BorderLayout.EAST);

        pnl.add(title, BorderLayout.NORTH);
        pnl.add(new JScrollPane(tblSections), BorderLayout.CENTER);
        pnl.add(bottomContainer, BorderLayout.SOUTH);

        return pnl;
    }

    private JPanel createGradebookView() {
        JPanel pnl = new JPanel(new BorderLayout(8, 8));
        pnl.setOpaque(false);

        tblGrades.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(tblGrades, false);
        installNumericEditors();

        lblGradebookTitle.setFont(HEADER_FONT.deriveFont(Font.BOLD));
        lblGradebookTitle.setBorder(new EmptyBorder(6,6,6,6));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btnPanel.setOpaque(false);

        btnPanel.add(btnBack);
        btnPanel.add(Box.createHorizontalStrut(20));
        btnPanel.add(btnComputeFinal);
        btnPanel.add(btnSave);
        btnPanel.add(btnExport);

        pnl.add(lblGradebookTitle, BorderLayout.NORTH);
        pnl.add(new JScrollPane(tblGrades), BorderLayout.CENTER);
        pnl.add(btnPanel, BorderLayout.SOUTH);

        return pnl;
    }

    private JButton createLogoutButton() {
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFont(HEADER_FONT.deriveFont(12f));
        logoutBtn.setBorder(BorderFactory.createLineBorder(ACCENT_DARK));
        logoutBtn.setBackground(Color.WHITE);
        logoutBtn.setForeground(ACCENT_DARK);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.setFocusPainted(false);
        logoutBtn.setPreferredSize(new Dimension(110, 40));
        logoutBtn.addActionListener((ActionEvent e) -> {
            if (maintenancePollTimer != null && maintenancePollTimer.isRunning()) maintenancePollTimer.stop();
            dispose();
            SwingUtilities.invokeLater(() -> MainApp.main(new String[0]));
        });
        return logoutBtn;
    }

    // ----- styling helpers -----

    private void styleTable(JTable t, boolean compact) {
        t.setRowHeight(compact ? 28 : 30);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(8, 4));
        t.setFillsViewportHeight(true);
        t.setSelectionBackground(ACCENT_DARK);
        t.setSelectionForeground(Color.WHITE);
        t.setFont(HEADER_FONT);
        JTableHeader hdr = t.getTableHeader();
        hdr.setBackground(Color.WHITE);
        hdr.setReorderingAllowed(false);
        hdr.setDefaultRenderer(new DefaultTableCellRenderer() {
            final JLabel lbl = new JLabel();
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                lbl.setText(value == null ? "" : value.toString());
                lbl.setOpaque(true);
                lbl.setBackground(Color.WHITE);
                lbl.setForeground(MUTED);
                lbl.setBorder(new EmptyBorder(8,8,8,8));
                lbl.setFont(HEADER_FONT.deriveFont(Font.BOLD, 12f));
                return lbl;
            }
        });

        DefaultTableCellRenderer cellRend = new DefaultTableCellRenderer();
        cellRend.setBorder(new EmptyBorder(6,8,6,8));
        cellRend.setForeground(Color.BLACK);
        t.setDefaultRenderer(Object.class, cellRend);
    }

    private void installNumericEditors() {
        DoubleEditor doubleEditor = new DoubleEditor();
        SwingUtilities.invokeLater(() -> {
            TableColumnModel cm2 = tblGrades.getColumnModel();
            for (int modelCol = 4; modelCol <= 6; modelCol++) {
                if (cm2.getColumnCount() > modelCol) {
                    TableColumn col = cm2.getColumn(modelCol);
                    col.setCellEditor(doubleEditor);
                    col.setCellRenderer(new RightAlignDoubleRenderer());
                }
            }
            if (cm2.getColumnCount() > 7) {
                TableColumn finalCol = cm2.getColumn(7);
                finalCol.setCellRenderer(new RightAlignDoubleRenderer());
            }
        });
    }

    // ----- data / actions -----

    private void loadInstructorDepartment() {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return service.getDepartment(instructorUserId); }
            @Override protected void done() {
                try {
                    String dep = get();
                    lblDepartment.setText(dep == null ? "Department: -" : "Department: " + dep);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void loadSections() {
        sectionsModel.setRowCount(0);
        new SwingWorker<List<SectionRow>, Void>() {
            @Override protected List<SectionRow> doInBackground() { return service.getSectionsForInstructor(instructorUserId); }
            @Override protected void done() {
                try {
                    List<SectionRow> rows = get();
                    for (SectionRow s : rows) {
                        Vector<Object> r = new Vector<>();
                        r.add(s.sectionId()); r.add(s.courseCode()); r.add(s.courseName());
                        r.add(s.semester()); r.add(s.year()); r.add(s.day());
                        r.add(s.startTime()); r.add(s.endTime()); r.add(s.room()); r.add(s.capacity());
                        sectionsModel.addRow(r);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(InstructorDashboard.this, "Error loading sections: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void refreshMaintenanceBanner() {
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() { return service.isMaintenanceMode(); }
            @Override protected void done() {
                try { applyMaintenanceState(get()); } catch (Exception ex) { applyMaintenanceState(false); }
            }
        }.execute();
    }

    private void applyMaintenanceState(boolean on) {
        SwingUtilities.invokeLater(() -> {
            maintenanceOn = on;
            if (on) { lblMaintenance.setText("MAINTENANCE MODE — system is read-only"); lblMaintenance.setVisible(true); }
            else lblMaintenance.setVisible(false);
            btnSave.setEnabled(!on);
            btnComputeFinal.setEnabled(!on);
            btnLoadRoster.setEnabled(true);
            tblGrades.repaint();
        });
    }

    private void loadRosterForSection(String sectionId) {
        boolean found = false;
        for (int r = 0; r < sectionsModel.getRowCount(); r++) {
            if (sectionId.equals(sectionsModel.getValueAt(r, 0))) { found = true; break; }
        }
        if (!found) {
            JOptionPane.showMessageDialog(this, "You do not own this section or it is not visible.", "Permission denied", JOptionPane.ERROR_MESSAGE);
            return;
        }

        gradeModel.setRowCount(0);
        new SwingWorker<List<RosterRow>, Void>() {
            @Override protected List<RosterRow> doInBackground() { return service.getRosterForSection(sectionId); }
            @Override protected void done() {
                try {
                    List<RosterRow> roster = get();
                    double sumFinal = 0; double minFinal = Double.MAX_VALUE; double maxFinal = Double.MIN_VALUE; int countFinal = 0; int pass = 0;
                    for (RosterRow r : roster) {
                        Vector<Object> row = new Vector<>();
                        row.add(r.enrollmentId()); row.add(r.studentId()); row.add(r.rollNo()); row.add(r.studentName() == null ? "" : r.studentName());
                        row.add(r.quiz()); row.add(r.midterm()); row.add(r.endsem()); row.add(r.finalScore());
                        gradeModel.addRow(row);

                        if (r.finalScore() != null) {
                            sumFinal += r.finalScore(); minFinal = Math.min(minFinal, r.finalScore()); maxFinal = Math.max(maxFinal, r.finalScore());
                            countFinal++; if (r.finalScore() >= 50.0) pass++;
                        }
                    }
                    // Format stats string for the popup
                    if (countFinal > 0) {
                        currentStatsText = String.format(
                                "Class Performance Summary:\n\n" +
                                        "Total Graded Students: %d\n" +
                                        "Average Score: %.2f\n" +
                                        "Highest Score: %.2f\n" +
                                        "Lowest Score: %.2f\n" +
                                        "Pass Rate: %.1f%% (Score >= 50.0)",
                                countFinal, sumFinal / countFinal, maxFinal, minFinal, pass * 100.0 / countFinal
                        );
                    } else {
                        currentStatsText = "No final grades computed yet.";
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(InstructorDashboard.this, "Error loading roster: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void computeFinalAndUpdateTable() {
        if (gradeModel.getRowCount() == 0) return;
        if (maintenanceOn) { JOptionPane.showMessageDialog(this, "System is in maintenance mode. Cannot compute grades.", "Maintenance", JOptionPane.WARNING_MESSAGE); return; }

        for (int r = 0; r < gradeModel.getRowCount(); r++) {
            Double q = toDouble(gradeModel.getValueAt(r, 4));
            Double m = toDouble(gradeModel.getValueAt(r, 5));
            Double e = toDouble(gradeModel.getValueAt(r, 6));
            double finalScore = Math.round(((q == null ? 0.0 : q) * W_QUIZ + (m == null ? 0.0 : m) * W_MID + (e == null ? 0.0 : e) * W_END) * 100.0) / 100.0;
            gradeModel.setValueAt(Double.valueOf(finalScore), r, 7);
        }
        SwingUtilities.invokeLater(this::recalculateStatsFromTable);
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { String s = o.toString().trim(); if (s.isEmpty()) return null; return Double.parseDouble(s); } catch (Exception ex) { return null; }
    }

    private void recalculateStatsFromTable() {
        double sum = 0; double min = Double.MAX_VALUE; double max = Double.MIN_VALUE; int count = 0; int pass = 0;
        for (int r = 0; r < gradeModel.getRowCount(); r++) {
            Double f = toDouble(gradeModel.getValueAt(r, 7));
            if (f != null) { sum += f; min = Math.min(min, f); max = Math.max(max, f); count++; if (f >= 50.0) pass++; }
        }
        // Update stats string
        if (count > 0) {
            currentStatsText = String.format(
                    "Class Performance Summary:\n\n" +
                            "Total Graded Students: %d\n" +
                            "Average Score: %.2f\n" +
                            "Highest Score: %.2f\n" +
                            "Lowest Score: %.2f\n" +
                            "Pass Rate: %.1f%% (Score >= 50.0)",
                    count, sum / count, max, min, pass * 100.0 / count
            );
        } else {
            currentStatsText = "No final grades computed yet.";
        }
    }

    private void saveGradesToDB() {
        if (gradeModel.getRowCount() == 0) return;
        if (maintenanceOn) { JOptionPane.showMessageDialog(this, "System is in maintenance mode. Cannot save grades.", "Maintenance", JOptionPane.WARNING_MESSAGE); return; }

        List<GradeRow> toSave = new ArrayList<>();
        for (int r = 0; r < gradeModel.getRowCount(); r++) {
            String enrollmentId = (String) gradeModel.getValueAt(r, 0);
            Double quiz = toDouble(gradeModel.getValueAt(r,4));
            Double mid = toDouble(gradeModel.getValueAt(r,5));
            Double end = toDouble(gradeModel.getValueAt(r,6));
            Double finalScore = toDouble(gradeModel.getValueAt(r,7));

            if (quiz != null) toSave.add(new GradeRow(enrollmentId, "QUIZ", quiz, (int)Math.round(W_QUIZ*100)));
            if (mid != null) toSave.add(new GradeRow(enrollmentId, "MIDTERM", mid, (int)Math.round(W_MID*100)));
            if (end != null) toSave.add(new GradeRow(enrollmentId, "ENDSEM", end, (int)Math.round(W_END*100)));
            if (finalScore != null) toSave.add(new GradeRow(enrollmentId, "FINAL", finalScore, 100));
        }

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                service.saveGradesBatch(toSave);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(InstructorDashboard.this, "Grades saved.");
                    recalculateStatsFromTable();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(InstructorDashboard.this, "Error saving grades: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void exportGradesCSV() {
        if (gradeModel.getRowCount() == 0) { JOptionPane.showMessageDialog(this, "No grades to export."); return; }

        String defaultSection = "unknown";
        if (lblGradebookTitle.getText().contains("(")) {
            defaultSection = "grades";
        }

        String ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(java.time.LocalDateTime.now());
        String defaultName = String.format("%s_%s.csv", defaultSection, ts);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save grades CSV");
        chooser.setSelectedFile(new java.io.File(defaultName));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));

        int userChoice = chooser.showSaveDialog(this);
        if (userChoice != JFileChooser.APPROVE_OPTION) return;
        String path = chooser.getSelectedFile().getAbsolutePath();
        if (!path.toLowerCase().endsWith(".csv")) path += ".csv";

        java.util.function.Function<String,String> esc = s -> {
            if (s == null) return "";
            String st = s;
            if (st.contains("\"") || st.contains(",") || st.contains("\n") || st.contains("\r")) {
                st = st.replace("\"", "\"\"");
                return "\"" + st + "\"";
            } else return st;
        };

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (int c = 0; c < gradeModel.getColumnCount(); c++) {
                pw.print(esc.apply(gradeModel.getColumnName(c)));
                if (c < gradeModel.getColumnCount() - 1) pw.print(",");
            }
            pw.println();
            for (int r = 0; r < gradeModel.getRowCount(); r++) {
                for (int c = 0; c < gradeModel.getColumnCount(); c++) {
                    Object vobj = gradeModel.getValueAt(r, c);
                    String cell = vobj == null ? "" : vobj.toString();
                    pw.print(esc.apply(cell));
                    if (c < gradeModel.getColumnCount() - 1) pw.print(",");
                }
                pw.println();
            }
            pw.flush();
            JOptionPane.showMessageDialog(this, "CSV exported to: " + path);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error exporting CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override public void dispose() {
        if (maintenancePollTimer != null && maintenancePollTimer.isRunning()) maintenancePollTimer.stop();
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            InstructorDashboard dash = new InstructorDashboard("<instructor-user-id>", "inst1");
            dash.setVisible(true);
        });
    }

    // --- Custom Component Classes ---

    private static class PillButton extends JButton {
        public PillButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setForeground(Color.WHITE);
            setBackground(ACCENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(140, 40));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { setBackground(ACCENT_HOVER); }
                @Override public void mouseExited(MouseEvent e) { setBackground(ACCENT); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isPressed()) { g2.setColor(getBackground().darker()); }
            else { g2.setColor(getBackground()); }
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class DoubleEditor extends DefaultCellEditor {
        private final JTextField fld;
        DoubleEditor() { super(new JTextField()); fld = (JTextField) getComponent(); fld.addActionListener(e -> stopCellEditing()); }
        @Override public Object getCellEditorValue() {
            String t = fld.getText(); if (t == null) return null; t = t.trim(); if (t.isEmpty()) return null;
            try { return Double.parseDouble(t); } catch (NumberFormatException ex) { return null; }
        }
    }

    static class RightAlignDoubleRenderer extends DefaultTableCellRenderer {
        RightAlignDoubleRenderer() { setHorizontalAlignment(SwingConstants.RIGHT); setBorder(new EmptyBorder(6,8,6,8)); }
        @Override public void setValue(Object value) {
            if (value == null) setText("");
            else if (value instanceof Number) setText(String.format("%.2f", ((Number) value).doubleValue()));
            else setText(value.toString());
        }
    }
}