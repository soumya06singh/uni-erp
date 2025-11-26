package edu.univ.erp.ui;

import edu.univ.erp.data.DBConfig;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Vector;

/**
 * InstructorDashboard
 *
 * - Shows instructor sections
 * - Loads roster + existing grades into an editable JTable
 * - Editable columns: Quiz, Midterm, EndSem (blocked during maintenance)
 * - Compute final, Save grades (upsert), Export CSV
 * - Periodically polls settings table to show maintenance banner and disable editing
 */
public class InstructorDashboard extends JFrame {

    private final String instructorUserId;
    private final String username;

    private final DefaultTableModel sectionsModel = new DefaultTableModel();
    private final JTable tblSections = new JTable(sectionsModel);

    private final DefaultTableModel gradeModel = new DefaultTableModel();
    // We'll instantiate tblGrades as an anonymous subclass below so isCellEditable can check maintenanceOn
    private final JTable tblGrades;

    private final JLabel lblWelcome = new JLabel();
    private final JLabel lblDepartment = new JLabel("Department: -");
    private final JLabel lblStats = new JLabel("Stats: -");

    // maintenance banner & state
    private final JLabel lblMaintenance = new JLabel();
    private volatile boolean maintenanceOn = false;
    private javax.swing.Timer maintenancePollTimer;

    private final JButton btnRefreshSections = new JButton("Refresh Sections");
    private final JButton btnLoadRoster = new JButton("Load Roster");
    private final JButton btnComputeFinal = new JButton("Compute Final (20/30/50)");
    private final JButton btnSave = new JButton("Save Grades");
    private final JButton btnExport = new JButton("Export CSV");

    // weighting constants
    private static final double W_QUIZ = 0.20;
    private static final double W_MID = 0.30;
    private static final double W_END = 0.50;

    public InstructorDashboard(String instructorUserId, String username) {
        super("Instructor Dashboard - " + username);
        this.instructorUserId = instructorUserId;
        this.username = username;

        // initialize gradeModel columns first
        gradeModel.setColumnIdentifiers(new String[]{
                "Enrollment ID", "Student ID", "Roll No", "Student Name", "Quiz", "Midterm", "EndSem", "Final"
        });

        // Create tblGrades as a subclass that blocks edits when maintenanceOn is true
        tblGrades = new JTable(gradeModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // allow editing only for Quiz (4), Midterm (5), EndSem (6) and only when maintenance is OFF
                if (column >= 4 && column <= 6) {
                    return !maintenanceOn;
                }
                return false;
            }
        };

        initUI();
        loadInstructorDepartment();
        loadSections();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        lblWelcome.setFont(lblWelcome.getFont().deriveFont(Font.BOLD, 16f));
        lblWelcome.setText("Welcome, " + username);

        // sectionsModel columns
        sectionsModel.setColumnIdentifiers(new String[]{
                "Section ID", "Course Code", "Title", "Semester", "Year", "Day", "Start", "End", "Room", "Capacity"
        });
        tblSections.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane spSections = new JScrollPane(tblSections);

        tblGrades.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane spGrades = new JScrollPane(tblGrades);

        // top panel
        JPanel top = new JPanel(new BorderLayout(8, 8));
        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topLeft.add(lblWelcome);
        topLeft.add(new JLabel("   "));
        topLeft.add(lblDepartment);

        // maintenance banner (initially hidden)
        lblMaintenance.setFont(lblMaintenance.getFont().deriveFont(Font.BOLD, 12f));
        lblMaintenance.setForeground(new Color(180, 0, 0)); // dark red
        lblMaintenance.setVisible(false);
        topLeft.add(Box.createHorizontalStrut(12));
        topLeft.add(lblMaintenance);

        top.add(topLeft, BorderLayout.WEST);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topRight.add(lblStats);
        top.add(topRight, BorderLayout.EAST);

        // left panel contains sections and controls
        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.add(new JLabel("My Sections"), BorderLayout.NORTH);
        left.add(spSections, BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtons.add(btnRefreshSections);
        leftButtons.add(btnLoadRoster);
        left.add(leftButtons, BorderLayout.SOUTH);

        // right panel gradebook
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.add(new JLabel("Gradebook"), BorderLayout.NORTH);
        right.add(spGrades, BorderLayout.CENTER);

        JPanel gradeBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        gradeBtns.add(btnComputeFinal);
        gradeBtns.add(btnSave);
        gradeBtns.add(btnExport);
        right.add(gradeBtns, BorderLayout.SOUTH);

        // main split
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(380);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);

        // actions
        btnRefreshSections.addActionListener((ActionEvent e) -> loadSections());
        btnLoadRoster.addActionListener((ActionEvent e) -> {
            int sel = tblSections.getSelectedRow();
            if (sel < 0) {
                JOptionPane.showMessageDialog(this, "Select a section first.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String sectionId = (String) sectionsModel.getValueAt(sel, 0);
            loadRosterForSection(sectionId);
        });

        btnComputeFinal.addActionListener((ActionEvent e) -> computeFinalAndUpdateTable());
        btnSave.addActionListener((ActionEvent e) -> saveGradesToDB());
        btnExport.addActionListener((ActionEvent e) -> exportGradesCSV());

        // initial banner refresh and start poll timer
        refreshMaintenanceBanner();
        maintenancePollTimer = new javax.swing.Timer(30_000, e -> refreshMaintenanceBanner());
        maintenancePollTimer.setInitialDelay(30_000);
        maintenancePollTimer.start();
    }

    private void setStatsText(String text) {
        SwingUtilities.invokeLater(() -> lblStats.setText("Stats: " + text));
    }

    private void loadInstructorDepartment() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                String sql = "SELECT department FROM instructors WHERE user_id = ?";
                try (Connection conn = DBConfig.getErpConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, instructorUserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getString("department");
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    String dep = get();
                    lblDepartment.setText("Department: " + (dep == null ? "-" : dep));
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void loadSections() {
        sectionsModel.setRowCount(0);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                String sql = "SELECT s.section_id, c.course_code, c.course_name, s.semester, s.year, s.day, s.start_time, s.end_time, s.room, s.capacity " +
                        "FROM sections s JOIN courses c ON s.course_id = c.course_id " +
                        "WHERE s.instructor_id = ?";

                try (Connection conn = DBConfig.getErpConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, instructorUserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Vector<Object> r = new Vector<>();
                            r.add(rs.getString("section_id"));
                            r.add(rs.getString("course_code"));
                            r.add(rs.getString("course_name"));
                            r.add(rs.getString("semester"));
                            r.add(rs.getInt("year"));
                            r.add(rs.getString("day"));
                            r.add(rs.getString("start_time"));
                            r.add(rs.getString("end_time"));
                            r.add(rs.getString("room"));
                            r.add(rs.getInt("capacity"));
                            sectionsModel.addRow(r);
                        }
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(InstructorDashboard.this, "Error loading sections: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }
        }.execute();
    }

    private boolean isMaintenanceMode() {
        String sql = "SELECT value FROM settings WHERE setting_key = 'maintenance_mode'";
        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Boolean.parseBoolean(rs.getString("value"));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    /**
     * Polls DB and updates banner + UI elements.
     */
    private void refreshMaintenanceBanner() {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return isMaintenanceMode();
            }

            @Override
            protected void done() {
                try {
                    boolean on = get();
                    applyMaintenanceState(on);
                } catch (Exception ex) {
                    // if anything fails, assume not in maintenance to avoid locking users out accidentally
                    applyMaintenanceState(false);
                }
            }
        }.execute();
    }

    /**
     * Update UI interactivity based on maintenance state.
     */
    private void applyMaintenanceState(boolean on) {
        SwingUtilities.invokeLater(() -> {
            maintenanceOn = on;
            if (on) {
                lblMaintenance.setText("MAINTENANCE MODE — system is read-only");
                lblMaintenance.setVisible(true);
            } else {
                lblMaintenance.setVisible(false);
            }
            // disable actions that would modify data
            btnSave.setEnabled(!on);
            btnComputeFinal.setEnabled(!on);
            btnLoadRoster.setEnabled(!on);
            // disable section refresh? we allow refresh (read-only) — keep Refresh enabled
            // block editing in table by revalidating; JTable.isCellEditable consults maintenanceOn
            tblGrades.repaint();
        });
    }

    private void loadRosterForSection(String sectionId) {
        // Before loading, check that instructor owns the section (defensive)
        if (!isInstructorOwnerOfSection(sectionId)) {
            JOptionPane.showMessageDialog(this, "You are not the instructor for this section.", "Permission denied", JOptionPane.ERROR_MESSAGE);
            return;
        }

        gradeModel.setRowCount(0);

        new SwingWorker<Void, Void>() {
            double sumFinal = 0;
            double minFinal = Double.MAX_VALUE;
            double maxFinal = Double.MIN_VALUE;
            int countFinal = 0;
            int passCount = 0;

            @Override
            protected Void doInBackground() throws Exception {
                String sql = "SELECT e.enrollment_id, st.user_id as student_id, st.roll_no, u.username as student_name " +
                        "FROM enrollments e JOIN students st ON e.student_id = st.user_id " +
                        "LEFT JOIN auth_db.users_auth u ON st.user_id = u.user_id " +
                        "WHERE e.section_id = ? AND (e.status IS NULL OR e.status = 'ENROLLED')";
                try (Connection conn = DBConfig.getErpConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, sectionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String enrollmentId = rs.getString("enrollment_id");
                            String studentId = rs.getString("student_id");
                            String rollNo = rs.getString("roll_no");
                            String studentName = rs.getString("student_name");

                            // fetch existing component scores (QUIZ, MIDTERM, ENDSEM, FINAL)
                            Double quiz = null, mid = null, end = null, finalScore = null;
                            String gsql = "SELECT component, score FROM grades WHERE enrollment_id = ?";
                            try (PreparedStatement gps = conn.prepareStatement(gsql)) {
                                gps.setString(1, enrollmentId);
                                try (ResultSet grs = gps.executeQuery()) {
                                    while (grs.next()) {
                                        String comp = grs.getString("component");
                                        double sc = grs.getDouble("score");
                                        switch (comp.toUpperCase()) {
                                            case "QUIZ" -> quiz = sc;
                                            case "MIDTERM" -> mid = sc;
                                            case "ENDSEM" -> end = sc;
                                            case "FINAL" -> finalScore = sc;
                                        }
                                    }
                                }
                            }

                            Vector<Object> row = new Vector<>();
                            row.add(enrollmentId);
                            row.add(studentId);
                            row.add(rollNo);
                            row.add(studentName == null ? "" : studentName);
                            row.add(quiz == null ? "" : quiz);
                            row.add(mid == null ? "" : mid);
                            row.add(end == null ? "" : end);
                            row.add(finalScore == null ? "" : finalScore);

                            gradeModel.addRow(row);

                            // compute stats if final present
                            if (finalScore != null) {
                                sumFinal += finalScore;
                                minFinal = Math.min(minFinal, finalScore);
                                maxFinal = Math.max(maxFinal, finalScore);
                                countFinal++;
                                if (finalScore >= 50.0) passCount++;
                            }
                        }
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(InstructorDashboard.this, "Error loading roster: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE));
                }

                return null;
            }

            @Override
            protected void done() {
                // update stats text
                if (countFinal > 0) {
                    double avg = sumFinal / countFinal;
                    setStatsText(String.format("Avg: %.2f | Min: %.2f | Max: %.2f | Pass: %.1f%%", avg, minFinal, maxFinal, (passCount * 100.0 / countFinal)));
                } else {
                    setStatsText("No final grades yet");
                }
            }
        }.execute();
    }

    private boolean isInstructorOwnerOfSection(String sectionId) {
        String sql = "SELECT 1 FROM sections WHERE section_id = ? AND instructor_id = ?";
        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sectionId);
            ps.setString(2, instructorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private void computeFinalAndUpdateTable() {
        int sel = tblSections.getSelectedRow();
        if (sel < 0) { JOptionPane.showMessageDialog(this, "Select a section first."); return; }
        String sectionId = (String) sectionsModel.getValueAt(sel, 0);

        if (!isInstructorOwnerOfSection(sectionId)) {
            JOptionPane.showMessageDialog(this, "You are not the instructor for this section.", "Permission denied", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (maintenanceOn) {
            JOptionPane.showMessageDialog(this, "System is in maintenance mode. Cannot compute grades.", "Maintenance", JOptionPane.WARNING_MESSAGE);
            return;
        }

        for (int r = 0; r < gradeModel.getRowCount(); r++) {
            Object oQuiz = gradeModel.getValueAt(r, 4);
            Object oMid = gradeModel.getValueAt(r, 5);
            Object oEnd = gradeModel.getValueAt(r, 6);

            Double quiz = parseDoubleOrNull(oQuiz);
            Double mid = parseDoubleOrNull(oMid);
            Double end = parseDoubleOrNull(oEnd);

            double quizVal = quiz == null ? 0.0 : quiz;
            double midVal = mid == null ? 0.0 : mid;
            double endVal = end == null ? 0.0 : end;

            double finalScore = quizVal * W_QUIZ + midVal * W_MID + endVal * W_END;
            double rounded = Math.round(finalScore * 100.0) / 100.0;
            gradeModel.setValueAt(rounded, r, 7);
        }

        SwingUtilities.invokeLater(this::recalculateStatsFromTable);
    }

    private Double parseDoubleOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            String s = o.toString().trim();
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void recalculateStatsFromTable() {
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int count = 0;
        int pass = 0;
        for (int r = 0; r < gradeModel.getRowCount(); r++) {
            Object oFinal = gradeModel.getValueAt(r, 7);
            Double f = parseDoubleOrNull(oFinal);
            if (f != null) {
                sum += f;
                min = Math.min(min, f);
                max = Math.max(max, f);
                count++;
                if (f >= 50.0) pass++;
            }
        }
        if (count > 0) {
            double avg = sum / count;
            setStatsText(String.format("Avg: %.2f | Min: %.2f | Max: %.2f | Pass: %.1f%%", avg, min, max, (pass * 100.0 / count)));
        } else {
            setStatsText("No final grades yet");
        }
    }

    private void saveGradesToDB() {
        int sel = tblSections.getSelectedRow();
        if (sel < 0) { JOptionPane.showMessageDialog(this, "Select a section first."); return; }
        String sectionId = (String) sectionsModel.getValueAt(sel, 0);

        if (!isInstructorOwnerOfSection(sectionId)) {
            JOptionPane.showMessageDialog(this, "You are not the instructor for this section.", "Permission denied", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (maintenanceOn) {
            JOptionPane.showMessageDialog(this, "System is in maintenance mode. Cannot save grades.", "Maintenance", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (Connection conn = DBConfig.getErpConnection()) {
                    conn.setAutoCommit(false);
                    String upsertSql = """
                            INSERT INTO grades (grade_id, enrollment_id, component, score, weight)
                            VALUES (UUID(), ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE score = VALUES(score), weight = VALUES(weight)
                            """;
                    try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                        for (int r = 0; r < gradeModel.getRowCount(); r++) {
                            String enrollmentId = (String) gradeModel.getValueAt(r, 0);
                            Double quiz = parseDoubleOrNull(gradeModel.getValueAt(r, 4));
                            Double mid = parseDoubleOrNull(gradeModel.getValueAt(r, 5));
                            Double end = parseDoubleOrNull(gradeModel.getValueAt(r, 6));
                            Double finalScore = parseDoubleOrNull(gradeModel.getValueAt(r, 7));

                            if (quiz != null) {
                                ps.setString(1, enrollmentId);
                                ps.setString(2, "QUIZ");
                                ps.setDouble(3, quiz);
                                ps.setInt(4, (int) Math.round(W_QUIZ * 100));
                                ps.addBatch();
                            }
                            if (mid != null) {
                                ps.setString(1, enrollmentId);
                                ps.setString(2, "MIDTERM");
                                ps.setDouble(3, mid);
                                ps.setInt(4, (int) Math.round(W_MID * 100));
                                ps.addBatch();
                            }
                            if (end != null) {
                                ps.setString(1, enrollmentId);
                                ps.setString(2, "ENDSEM");
                                ps.setDouble(3, end);
                                ps.setInt(4, (int) Math.round(W_END * 100));
                                ps.addBatch();
                            }
                            if (finalScore != null) {
                                ps.setString(1, enrollmentId);
                                ps.setString(2, "FINAL");
                                ps.setDouble(3, finalScore);
                                ps.setInt(4, 100);
                                ps.addBatch();
                            }
                        }
                        ps.executeBatch();
                    }
                    conn.commit();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(InstructorDashboard.this, "Error saving grades: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }

            @Override
            protected void done() {
                JOptionPane.showMessageDialog(InstructorDashboard.this, "Grades saved.");
                recalculateStatsFromTable();
            }
        }.execute();
    }

    private void exportGradesCSV() {
        if (gradeModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No grades to export.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save grades CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        int sel = chooser.showSaveDialog(this);
        if (sel != JFileChooser.APPROVE_OPTION) return;
        String path = chooser.getSelectedFile().getAbsolutePath();
        if (!path.toLowerCase().endsWith(".csv")) path += ".csv";

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (int c = 0; c < gradeModel.getColumnCount(); c++) {
                pw.print(gradeModel.getColumnName(c));
                if (c < gradeModel.getColumnCount() - 1) pw.print(",");
            }
            pw.println();
            for (int r = 0; r < gradeModel.getRowCount(); r++) {
                for (int c = 0; c < gradeModel.getColumnCount(); c++) {
                    Object v = gradeModel.getValueAt(r, c);
                    pw.print(v == null ? "" : v.toString());
                    if (c < gradeModel.getColumnCount() - 1) pw.print(",");
                }
                pw.println();
            }
            JOptionPane.showMessageDialog(this, "CSV exported to: " + path);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error exporting CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void dispose() {
        if (maintenancePollTimer != null && maintenancePollTimer.isRunning()) {
            maintenancePollTimer.stop();
        }
        super.dispose();
    }

    // convenience main for testing (replace argument with an actual instructor user_id)
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            InstructorDashboard dash = new InstructorDashboard("<instructor-user-id>", "inst1");
            dash.setVisible(true);
        });
    }
}
