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
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * InstructorDashboard - patched to hide gradebook by default and show it on "Load Roster"
 * or via a toggle button. Styling and behavior otherwise preserved from your previous version.
 */
public class InstructorDashboard extends JFrame {

    private final String instructorUserId;
    private final String username;

    // theme colors (lighter teal accent requested)
    private static final Color BG = Color.WHITE;
    private static final Color ACCENT = new Color(96, 213, 207);
    private static final Color ACCENT_HOVER = new Color(64, 196, 185);
    private static final Color ACCENT_DARK = new Color(28, 160, 157);
    private static final Color MUTED = new Color(110, 110, 110);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 20);
    private static final Font HEADER_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    private final DefaultTableModel sectionsModel = new DefaultTableModel();
    private final JTable tblSections = new JTable(sectionsModel) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    private DefaultTableModel gradeModel;
    private final JTable tblGrades;

    private final JLabel lblWelcome = new JLabel();
    private final JLabel lblDepartment = new JLabel("Department: -");
    private final JLabel lblStats = new JLabel("Stats: -");

    private final JLabel lblMaintenance = new JLabel();
    private volatile boolean maintenanceOn = false;
    private javax.swing.Timer maintenancePollTimer;

    private final JButton btnRefreshSections = new JButton("Refresh Sections");
    private final JButton btnLoadRoster = new JButton("Load Roster");
    private final JButton btnToggleGradebook = new JButton("Show Gradebook"); // new toggle

    private final JButton btnComputeFinal = new JButton("Compute Final (20/30/50)");
    private final JButton btnSave = new JButton("Save Grades");
    private final JButton btnExport = new JButton("Export CSV");

    private static final double W_QUIZ = 0.20;
    private static final double W_MID = 0.30;
    private static final double W_END = 0.50;

    private final InstructorService service = new InstructorService();

    // reference to right panel and split so we can hide/show gradebook
    private JPanel rightPanel;
    private JSplitPane split;

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
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1150, 720);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(12,12,12,12));
        setContentPane(root);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(8,12,12,12));
        lblWelcome.setFont(TITLE_FONT);
        lblWelcome.setText("Welcome, " + username);
        lblDepartment.setFont(HEADER_FONT);
        lblDepartment.setForeground(MUTED);

        JPanel leftTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        leftTitle.setOpaque(false);
        leftTitle.add(lblWelcome);
        leftTitle.add(lblDepartment);
        header.add(leftTitle, BorderLayout.WEST);

        JPanel rightTitle = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        rightTitle.setOpaque(false);
        lblStats.setFont(HEADER_FONT.deriveFont(12f));
        lblStats.setForeground(MUTED);
        lblMaintenance.setFont(HEADER_FONT.deriveFont(Font.BOLD, 12f));
        lblMaintenance.setForeground(new Color(180,20,20));
        lblMaintenance.setVisible(false);
        rightTitle.add(lblStats);
        rightTitle.add(lblMaintenance);
        header.add(rightTitle, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);
        root.add(new JSeparator(), BorderLayout.CENTER);

        // Tables
        sectionsModel.setColumnIdentifiers(new String[]{
                "Section ID","Course Code","Title","Semester","Year","Day","Start","End","Room","Capacity"
        });
        tblSections.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(tblSections, true);

        tblGrades.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(tblGrades, false);

        installNumericEditors();

        // Left panel (sections + controls)
        JPanel leftPanel = new JPanel(new BorderLayout(8,8));
        leftPanel.setOpaque(false);
        JLabel leftTitleLbl = new JLabel("My Sections");
        leftTitleLbl.setFont(HEADER_FONT.deriveFont(Font.BOLD));
        leftTitleLbl.setBorder(new EmptyBorder(6,6,6,6));
        leftPanel.add(leftTitleLbl, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(tblSections), BorderLayout.CENTER);

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        leftBtns.setOpaque(false);
        stylePillButton(btnRefreshSections);
        stylePillButton(btnLoadRoster);
        styleOutlineToggle(btnToggleGradebook); // toggle is outline style
        leftBtns.add(btnRefreshSections);
        leftBtns.add(btnLoadRoster);
        leftBtns.add(btnToggleGradebook);
        leftPanel.add(leftBtns, BorderLayout.SOUTH);

        // Right panel (gradebook) initially created but hidden
        rightPanel = new JPanel(new BorderLayout(8,8));
        rightPanel.setOpaque(false);
        JLabel rightTitleLbl = new JLabel("Gradebook");
        rightTitleLbl.setFont(HEADER_FONT.deriveFont(Font.BOLD));
        rightTitleLbl.setBorder(new EmptyBorder(6,6,6,6));
        rightPanel.add(rightTitleLbl, BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(tblGrades), BorderLayout.CENTER);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        rightBtns.setOpaque(false);
        stylePillButton(btnComputeFinal);
        stylePillButton(btnSave);
        stylePillButton(btnExport);
        rightBtns.add(btnComputeFinal);
        rightBtns.add(btnSave);
        rightBtns.add(btnExport);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFont(HEADER_FONT.deriveFont(12f));
        logoutBtn.setBorder(BorderFactory.createLineBorder(ACCENT_DARK));
        logoutBtn.setBackground(Color.WHITE);
        logoutBtn.setForeground(ACCENT_DARK);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.setFocusPainted(false);
        logoutBtn.setPreferredSize(new Dimension(110, 34));
        logoutBtn.addActionListener((ActionEvent e) -> {
            if (maintenancePollTimer != null && maintenancePollTimer.isRunning()) maintenancePollTimer.stop();
            dispose();
            SwingUtilities.invokeLater(() -> MainApp.main(new String[0]));
        });
        rightBtns.add(logoutBtn);

        rightPanel.add(rightBtns, BorderLayout.SOUTH);

        // Split pane - rightPanel will be hidden initially
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(380);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);

        // Hide gradebook at startup
        setGradebookVisible(false, true);

        // Actions
        btnRefreshSections.addActionListener((ActionEvent e) -> loadSections());

        btnLoadRoster.addActionListener((ActionEvent e) -> {
            int viewRow = tblSections.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(this, "Select a section first.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = tblSections.convertRowIndexToModel(viewRow);
            String sectionId = (String) sectionsModel.getValueAt(modelRow, 0);
            // show gradebook when loading roster
            setGradebookVisible(true, false);
            btnToggleGradebook.setText("Hide Gradebook");
            loadRosterForSection(sectionId);
        });

        btnToggleGradebook.addActionListener((ActionEvent e) -> {
            boolean currentlyVisible = rightPanel.isVisible();
            setGradebookVisible(!currentlyVisible, false);
            btnToggleGradebook.setText(!currentlyVisible ? "Hide Gradebook" : "Show Gradebook");
        });

        btnComputeFinal.addActionListener((ActionEvent e) -> computeFinalAndUpdateTable());
        btnSave.addActionListener((ActionEvent e) -> saveGradesToDB());
        btnExport.addActionListener((ActionEvent e) -> exportGradesCSV());

        tblSections.getSelectionModel().addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                int v = tblSections.getSelectedRow();
                if (v >= 0) {
                    int m = tblSections.convertRowIndexToModel(v);
                    String sec = (String) sectionsModel.getValueAt(m, 0);
                    // do not auto-load roster on selection; user must click Load Roster
                    // but we can optionally prefetch if you want.
                }
            }
        });

        // maintenance timer
        refreshMaintenanceBanner();
        maintenancePollTimer = new javax.swing.Timer(30_000, e -> refreshMaintenanceBanner());
        maintenancePollTimer.setInitialDelay(30_000);
        maintenancePollTimer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowActivated(java.awt.event.WindowEvent e) { refreshMaintenanceBanner(); }
        });
    }

    /**
     * Show/hide gradebook panel.
     * @param visible desired visibility
     * @param forceDividerWhenHiding if true, immediately place divider at right edge when hiding (for initial layout)
     */
    private void setGradebookVisible(boolean visible, boolean forceDividerWhenHiding) {
        rightPanel.setVisible(visible);
        split.setRightComponent(visible ? rightPanel : new JPanel()); // replace with empty panel to allow full-width left
        if (!visible && forceDividerWhenHiding) {
            // move divider fully right so left panel fills (small hack: set divider to very large)
            split.setDividerLocation(1.0);
        } else if (!visible) {
            split.setDividerLocation(1.0);
        } else {
            split.setDividerLocation(380);
        }
        revalidate();
        repaint();
    }

    // ----- styling utilities -----

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

    private void stylePillButton(JButton b) {
        b.setFont(HEADER_FONT.deriveFont(13f));
        b.setForeground(Color.WHITE);
        b.setBackground(ACCENT);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(160, 36));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { if (b.isEnabled()) b.setBackground(ACCENT_HOVER); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { if (b.isEnabled()) b.setBackground(ACCENT); }
        });
    }

    private void styleOutlineToggle(JButton b) {
        b.setFont(HEADER_FONT.deriveFont(13f));
        b.setForeground(ACCENT_DARK);
        b.setBackground(Color.WHITE);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createLineBorder(ACCENT_DARK));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(140, 36));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { if (b.isEnabled()) b.setBackground(new Color(240, 255, 254)); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { if (b.isEnabled()) b.setBackground(Color.WHITE); }
        });
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

    // ----- data & actions (delegated to InstructorService) -----

    private void setStatsText(String text) {
        SwingUtilities.invokeLater(() -> lblStats.setText(text));
    }

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
                    if (countFinal > 0) lblStats.setText(String.format("Avg: %.2f | Min: %.2f | Max: %.2f | Pass: %.1f%%", sumFinal / countFinal, minFinal, maxFinal, pass * 100.0 / countFinal));
                    else lblStats.setText("No final grades yet");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(InstructorDashboard.this, "Error loading roster: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void computeFinalAndUpdateTable() {
        int viewRow = tblSections.getSelectedRow();
        if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Select a section first."); return; }
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
        if (count > 0) lblStats.setText(String.format("Avg: %.2f | Min: %.2f | Max: %.2f | Pass: %.1f%%", sum / count, min, max, pass * 100.0 / count));
        else lblStats.setText("No final grades yet");
    }

    private void saveGradesToDB() {
        int viewRow = tblSections.getSelectedRow();
        if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Select a section first."); return; }
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

        String defaultSection = "all";
        int v = tblSections.getSelectedRow();
        if (v >= 0) {
            int m = tblSections.convertRowIndexToModel(v);
            Object sec = sectionsModel.getValueAt(m, 0);
            if (sec != null) defaultSection = sec.toString();
        }
        String ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(java.time.LocalDateTime.now());
        String defaultName = String.format("grades_%s_%s.csv", defaultSection, ts);

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

    // --- small editor & renderers ---

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
