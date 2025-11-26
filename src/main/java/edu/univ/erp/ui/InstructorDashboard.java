package edu.univ.erp.ui;

import edu.univ.erp.service.InstructorService;
import edu.univ.erp.service.InstructorService.GradeRow;
import edu.univ.erp.service.InstructorService.RosterRow;
import edu.univ.erp.service.InstructorService.SectionRow;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * InstructorDashboard - service-backed version
 *
 * All DB work delegated to InstructorService.
 */
public class InstructorDashboard extends JFrame {

    private final String instructorUserId;
    private final String username;

    private final DefaultTableModel sectionsModel = new DefaultTableModel();
    private final JTable tblSections = new JTable(sectionsModel) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
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
    private final JButton btnComputeFinal = new JButton("Compute Final (20/30/50)");
    private final JButton btnSave = new JButton("Save Grades");
    private final JButton btnExport = new JButton("Export CSV");

    private static final double W_QUIZ = 0.20;
    private static final double W_MID = 0.30;
    private static final double W_END = 0.50;

    // service instance
    private final InstructorService service = new InstructorService();

    public InstructorDashboard(String instructorUserId, String username) {
        super("Instructor Dashboard - " + username);
        this.instructorUserId = instructorUserId;
        this.username = username;

        // Grade model with column types
        gradeModel = new DefaultTableModel(new String[]{
                "Enrollment ID", "Student ID", "Roll No", "Student Name", "Quiz", "Midterm", "EndSem", "Final"
        }, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 4,5,6,7 -> Double.class;
                    default -> String.class;
                };
            }

            @Override
            public boolean isCellEditable(int row, int column) {
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
        setSize(1100,700);
        setLocationRelativeTo(null);

        lblWelcome.setFont(lblWelcome.getFont().deriveFont(Font.BOLD, 16f));
        lblWelcome.setText("Welcome, " + username);

        sectionsModel.setColumnIdentifiers(new String[]{
                "Section ID","Course Code","Title","Semester","Year","Day","Start","End","Room","Capacity"
        });
        tblSections.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane spSections = new JScrollPane(tblSections);
        tblGrades.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane spGrades = new JScrollPane(tblGrades);

        installNumericEditors();

        tblGrades.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int viewCol = tblGrades.columnAtPoint(e.getPoint());
                if (viewCol >= 4 && viewCol <= 6 && maintenanceOn) {
                    JOptionPane.showMessageDialog(
                            InstructorDashboard.this,
                            "System is in maintenance mode. Editing is disabled.",
                            "Maintenance Mode",
                            JOptionPane.WARNING_MESSAGE
                    );
                }
            }
        });

        tblSections.getSelectionModel().addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                int viewRow = tblSections.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = tblSections.convertRowIndexToModel(viewRow);
                    String sectionId = (String) sectionsModel.getValueAt(modelRow, 0);
                    loadRosterForSection(sectionId);
                }
            }
        });

        JPanel top = new JPanel(new BorderLayout(8,8));
        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topLeft.add(lblWelcome);
        topLeft.add(new JLabel("   "));
        topLeft.add(lblDepartment);
        lblMaintenance.setFont(lblMaintenance.getFont().deriveFont(Font.BOLD,12f));
        lblMaintenance.setForeground(new Color(180,0,0));
        lblMaintenance.setVisible(false);
        topLeft.add(Box.createHorizontalStrut(12));
        topLeft.add(lblMaintenance);
        top.add(topLeft, BorderLayout.WEST);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topRight.add(lblStats);
        top.add(topRight, BorderLayout.EAST);

        JPanel left = new JPanel(new BorderLayout(6,6));
        left.add(new JLabel("My Sections"), BorderLayout.NORTH);
        left.add(spSections, BorderLayout.CENTER);
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtons.add(btnRefreshSections);
        leftButtons.add(btnLoadRoster);
        left.add(leftButtons, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(6,6));
        right.add(new JLabel("Gradebook"), BorderLayout.NORTH);
        right.add(spGrades, BorderLayout.CENTER);
        JPanel gradeBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        gradeBtns.add(btnComputeFinal);
        gradeBtns.add(btnSave);
        gradeBtns.add(btnExport);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener((ActionEvent e) -> {
            if (maintenancePollTimer != null && maintenancePollTimer.isRunning()) maintenancePollTimer.stop();
            dispose();
            SwingUtilities.invokeLater(() -> MainApp.main(new String[0]));
        });
        gradeBtns.add(logoutBtn);

        right.add(gradeBtns, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(380);
        getContentPane().setLayout(new BorderLayout(8,8));
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);

        btnRefreshSections.addActionListener((ActionEvent e) -> loadSections());
        btnLoadRoster.addActionListener((ActionEvent e) -> {
            int viewRow = tblSections.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(this, "Select a section first.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = tblSections.convertRowIndexToModel(viewRow);
            String sectionId = (String) sectionsModel.getValueAt(modelRow, 0);
            loadRosterForSection(sectionId);
        });

        btnComputeFinal.addActionListener((ActionEvent e) -> computeFinalAndUpdateTable());
        btnSave.addActionListener((ActionEvent e) -> saveGradesToDB());
        btnExport.addActionListener((ActionEvent e) -> exportGradesCSV());

        refreshMaintenanceBanner();
        maintenancePollTimer = new javax.swing.Timer(30_000, e -> refreshMaintenanceBanner());
        maintenancePollTimer.setInitialDelay(30_000);
        maintenancePollTimer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowActivated(java.awt.event.WindowEvent e) {
                refreshMaintenanceBanner();
            }
        });
    }

    private void installNumericEditors() {
        DoubleEditor doubleEditor = new DoubleEditor();
        TableColumnModel cm = tblGrades.getColumnModel();
        if (cm.getColumnCount() < 8) {
            SwingUtilities.invokeLater(() -> {
                TableColumnModel cm2 = tblGrades.getColumnModel();
                for (int modelCol = 4; modelCol <= 6; modelCol++) {
                    if (cm2.getColumnCount() > modelCol) {
                        TableColumn col = cm2.getColumn(modelCol);
                        col.setCellEditor(doubleEditor);
                    }
                }
            });
            return;
        }
        for (int modelCol = 4; modelCol <= 6; modelCol++) {
            if (cm.getColumnCount() > modelCol) {
                TableColumn col = cm.getColumn(modelCol);
                col.setCellEditor(doubleEditor);
            }
        }
    }

    private void setStatsText(String text) {
        SwingUtilities.invokeLater(() -> lblStats.setText("Stats: " + text));
    }

    private void loadInstructorDepartment() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return service.getDepartment(instructorUserId);
            }
            @Override
            protected void done() {
                try {
                    String dep = get();
                    lblDepartment.setText("Department: " + (dep == null ? "-" : dep));
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void loadSections() {
        sectionsModel.setRowCount(0);
        new SwingWorker<List<SectionRow>, Void>() {
            @Override
            protected List<SectionRow> doInBackground() {
                return service.getSectionsForInstructor(instructorUserId);
            }
            @Override
            protected void done() {
                try {
                    List<SectionRow> rows = get();
                    for (SectionRow s : rows) {
                        Vector<Object> r = new Vector<>();
                        r.add(s.sectionId());
                        r.add(s.courseCode());
                        r.add(s.courseName());
                        r.add(s.semester());
                        r.add(s.year());
                        r.add(s.day());
                        r.add(s.startTime());
                        r.add(s.endTime());
                        r.add(s.room());
                        r.add(s.capacity());
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
            @Override
            protected Boolean doInBackground() {
                return service.isMaintenanceMode();
            }
            @Override
            protected void done() {
                try {
                    applyMaintenanceState(get());
                } catch (Exception ex) {
                    applyMaintenanceState(false);
                }
            }
        }.execute();
    }

    private void applyMaintenanceState(boolean on) {
        SwingUtilities.invokeLater(() -> {
            maintenanceOn = on;
            if (on) {
                lblMaintenance.setText("MAINTENANCE MODE — system is read-only");
                lblMaintenance.setVisible(true);
            } else lblMaintenance.setVisible(false);

            btnSave.setEnabled(!on);
            btnComputeFinal.setEnabled(!on);
            btnLoadRoster.setEnabled(true);
            tblGrades.repaint();
        });
    }

    private void loadRosterForSection(String sectionId) {
        // Defensive check via DB (service)
        new SwingWorker<List<RosterRow>, Void>() {
            @Override
            protected List<RosterRow> doInBackground() {
                // ensure instructor owns the section (simple SQL check inside service not present - confirm via sections list)
                // Since sections shown are owned, we assume OK but double-check: look for section in current sectionsModel
                boolean found = false;
                for (int r = 0; r < sectionsModel.getRowCount(); r++) {
                    if (sectionId.equals(sectionsModel.getValueAt(r, 0))) { found = true; break; }
                }
                if (!found) {
                    throw new RuntimeException("Section not found or you do not own it.");
                }
                return service.getRosterForSection(sectionId);
            }

            @Override
            protected void done() {
                try {
                    List<RosterRow> roster = get();
                    gradeModel.setRowCount(0);
                    double sumFinal = 0; double minFinal = Double.MAX_VALUE; double maxFinal = Double.MIN_VALUE; int countFinal = 0; int pass = 0;

                    for (RosterRow r : roster) {
                        Vector<Object> row = new Vector<>();
                        row.add(r.enrollmentId());
                        row.add(r.studentId());
                        row.add(r.rollNo());
                        row.add(r.studentName() == null ? "" : r.studentName());
                        row.add(r.quiz());
                        row.add(r.midterm());
                        row.add(r.endsem());
                        row.add(r.finalScore());
                        gradeModel.addRow(row);

                        if (r.finalScore() != null) {
                            double f = r.finalScore();
                            sumFinal += f;
                            minFinal = Math.min(minFinal, f);
                            maxFinal = Math.max(maxFinal, f);
                            countFinal++;
                            if (f >= 50.0) pass++;
                        }
                    }

                    if (countFinal > 0) {
                        setStatsText(String.format("Avg: %.2f | Min: %.2f | Max: %.2f | Pass: %.1f%%", sumFinal / countFinal, minFinal, maxFinal, pass * 100.0 / countFinal));
                    } else setStatsText("No final grades yet");

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
            Double quiz = toDouble(gradeModel.getValueAt(r,4));
            Double mid = toDouble(gradeModel.getValueAt(r,5));
            Double end = toDouble(gradeModel.getValueAt(r,6));
            double q = quiz == null ? 0.0 : quiz;
            double m = mid == null ? 0.0 : mid;
            double e = end == null ? 0.0 : end;
            double finalScore = Math.round((q*W_QUIZ + m*W_MID + e*W_END) * 100.0) / 100.0;
            gradeModel.setValueAt(Double.valueOf(finalScore), r, 7);
        }
        SwingUtilities.invokeLater(this::recalculateStatsFromTable);
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number)o).doubleValue();
        try {
            String s = o.toString().trim();
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (Exception ex) { return null; }
    }

    private void recalculateStatsFromTable() {
        double sum=0; double min = Double.MAX_VALUE; double max = Double.MIN_VALUE; int count=0; int pass=0;
        for (int r=0;r<gradeModel.getRowCount();r++) {
            Double f = toDouble(gradeModel.getValueAt(r,7));
            if (f!=null) { sum+=f; min=Math.min(min,f); max=Math.max(max,f); count++; if (f>=50) pass++; }
        }
        if (count>0) setStatsText(String.format("Avg: %.2f | Min: %.2f | Max: %.2f | Pass: %.1f%%", sum/count, min, max, pass*100.0/count));
        else setStatsText("No final grades yet");
    }

    private void saveGradesToDB() {
        int viewRow = tblSections.getSelectedRow();
        if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Select a section first."); return; }
        if (maintenanceOn) { JOptionPane.showMessageDialog(this, "System is in maintenance mode. Cannot save grades.", "Maintenance", JOptionPane.WARNING_MESSAGE); return; }

        // prepare list of GradeRow
        List<GradeRow> toSave = new ArrayList<>();
        for (int r=0;r<gradeModel.getRowCount();r++) {
            String enrollmentId = (String) gradeModel.getValueAt(r,0);
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
            @Override
            protected Void doInBackground() {
                try {
                    service.saveGradesBatch(toSave);
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                return null;
            }
            @Override
            protected void done() {
                try {
                    get(); // rethrow exceptions
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
        int viewRow = tblSections.getSelectedRow();
        if (viewRow >= 0) {
            int modelRow = tblSections.convertRowIndexToModel(viewRow);
            Object secObj = sectionsModel.getValueAt(modelRow, 0);
            if (secObj != null) defaultSection = secObj.toString();
        }
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .format(java.time.LocalDateTime.now());
        String defaultName = String.format("grades_%s_%s.csv", defaultSection, timestamp);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save grades CSV");
        chooser.setSelectedFile(new java.io.File(defaultName));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));

        int userChoice = chooser.showSaveDialog(this);
        if (userChoice != JFileChooser.APPROVE_OPTION) return;

        String path = chooser.getSelectedFile().getAbsolutePath();
        if (!path.toLowerCase().endsWith(".csv")) path += ".csv";

        java.util.function.Function<String,String> esc = s -> {
            if (s==null) return "";
            String str = s;
            if (str.contains("\"") || str.contains(",") || str.contains("\n") || str.contains("\r")) {
                str = str.replace("\"", "\"\"");
                return "\"" + str + "\"";
            } else return str;
        };

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (int c=0;c<gradeModel.getColumnCount();c++) {
                pw.print(esc.apply(gradeModel.getColumnName(c)));
                if (c < gradeModel.getColumnCount()-1) pw.print(",");
            }
            pw.println();

            for (int r=0;r<gradeModel.getRowCount();r++) {
                for (int c=0;c<gradeModel.getColumnCount();c++) {
                    Object v = gradeModel.getValueAt(r,c);
                    String cell = v == null ? "" : v.toString();
                    pw.print(esc.apply(cell));
                    if (c < gradeModel.getColumnCount()-1) pw.print(",");
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

    @Override
    public void dispose() {
        if (maintenancePollTimer != null && maintenancePollTimer.isRunning()) maintenancePollTimer.stop();
        super.dispose();
    }

    // --- helper editor class ---
    static class DoubleEditor extends DefaultCellEditor {
        private final JTextField fld;
        DoubleEditor() {
            super(new JTextField());
            fld = (JTextField) getComponent();
            fld.addActionListener(e -> stopCellEditing());
        }
        @Override
        public Object getCellEditorValue() {
            String txt = fld.getText();
            if (txt == null) return null;
            txt = txt.trim();
            if (txt.isEmpty()) return null;
            try { return Double.parseDouble(txt); } catch (NumberFormatException ex) { return null; }
        }
        @Override
        public boolean stopCellEditing() { return super.stopCellEditing(); }
    }
}
