package edu.univ.erp.ui;

import edu.univ.erp.data.DBConfig;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Vector;

/**
 * InstructorDashboard - patched & improved
 *
 * - grade columns 4..7 are numeric (Double)
 * - loadRoster populates Doubles (or null)
 * - view->model conversion when selecting a section
 * - compute/save/export work with proper numeric types
 * - maintenance mode disables editing/saving but not reading
 */
public class InstructorDashboard extends JFrame {

    private final String instructorUserId;
    private final String username;

    private final DefaultTableModel sectionsModel = new DefaultTableModel();
    private final JTable tblSections = new JTable(sectionsModel) {
        @Override
        public boolean isCellEditable(int row, int column) {
            // Never allow editing in sections list via UI
            return false;
        }
    };

    // gradeModel subclass to expose column classes
    private DefaultTableModel gradeModel;
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

        // create gradeModel with proper column classes
        gradeModel = new DefaultTableModel(new String[]{
                "Enrollment ID", "Student ID", "Roll No", "Student Name", "Quiz", "Midterm", "EndSem", "Final"
        }, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                // numeric columns become Double.class so JTable treats them as numbers
                return switch (columnIndex) {
                    case 4, 5, 6, 7 -> Double.class;
                    default -> String.class;
                };
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                // only numeric assessment columns editable, and only when maintenance is off
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

        // Install numeric editor for Double columns (Quiz/Mid/End/Final)
        installNumericEditors();

        // Warn user if they attempt to edit during maintenance (optional)
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
            int viewRow = tblSections.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(this, "Select a section first.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = tblSections.convertRowIndexToModel(viewRow);
            String sectionId = (String) sectionsModel.getValueAt(modelRow, 0);
            loadRosterForSection(sectionId);
        });
        // --- add logout to the grade button row (bottom-right) ---
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener((ActionEvent e) -> {
            // stop the maintenance timer (if running)
            if (maintenancePollTimer != null && maintenancePollTimer.isRunning()) {
                maintenancePollTimer.stop();
            }
            // close this dashboard
            dispose();

            // reopen the login on the EDT
            SwingUtilities.invokeLater(() -> MainApp.main(new String[0]));
        });
        gradeBtns.add(logoutBtn);


        btnComputeFinal.addActionListener((ActionEvent e) -> computeFinalAndUpdateTable());
        btnSave.addActionListener((ActionEvent e) -> saveGradesToDB());
        btnExport.addActionListener((ActionEvent e) -> exportGradesCSV());

        // initial banner refresh and start poll timer
        refreshMaintenanceBanner();
        maintenancePollTimer = new javax.swing.Timer(30_000, e -> refreshMaintenanceBanner());
        maintenancePollTimer.setInitialDelay(30_000);
        maintenancePollTimer.start();
        // When the instructor window gains focus, refresh the maintenance banner immediately
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                refreshMaintenanceBanner();
            }
        });
    }

    private void installNumericEditors() {
        // simple editor that parses Strings -> Double
        DoubleEditor doubleEditor = new DoubleEditor();

        // set the editor on the view columns 4..6 (Quiz, Midterm, EndSem) and 7 (Final we keep readonly)
        // column indices are model indices; need to map to view columns
        TableColumnModel cm = tblGrades.getColumnModel();
        for (int modelCol = 4; modelCol <= 6; modelCol++) {
            // guard: table may not yet have columns if called at different time — check count
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

    /**
     * Try multiple column names to be resilient to DB schema variations:
     * - `key` or `setting_key`
     * - `value` or `setting_value`
     */
    private boolean isMaintenanceMode() {
        String[] keyCols = new String[]{"`key`", "setting_key", "key", "setting_key"};
        String[] valueCols = new String[]{"`value`", "setting_value", "value", "setting_value"};

        try (Connection conn = DBConfig.getErpConnection()) {
            for (int i = 0; i < keyCols.length; i++) {
                String keyCol = keyCols[i];
                String valueCol = valueCols[i];
                String sql = String.format("SELECT %s as v FROM settings WHERE %s = 'maintenance_mode' LIMIT 1", valueCol, keyCol);
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String s = rs.getString("v");
                        if (s != null && !s.isBlank()) return Boolean.parseBoolean(s.trim());
                    }
                } catch (SQLException ignored) {
                    // try next variation
                }
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
            // allow roster loading even in maintenance (read-only)
            btnLoadRoster.setEnabled(true);
            // repaint table so isCellEditable is consulted
            tblGrades.repaint();
        });
    }

    private void loadRosterForSection(String sectionId) {
        // Defensive check: ensure instructor owns the section
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
            protected Void doInBackground() {
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
                                        Object obj = grs.getObject("score");
                                        Double sc = null;
                                        if (obj instanceof Number) sc = ((Number) obj).doubleValue();
                                        if ("QUIZ".equalsIgnoreCase(comp)) quiz = sc;
                                        else if ("MIDTERM".equalsIgnoreCase(comp)) mid = sc;
                                        else if ("ENDSEM".equalsIgnoreCase(comp)) end = sc;
                                        else if ("FINAL".equalsIgnoreCase(comp)) finalScore = sc;
                                    }
                                }
                            }

                            Vector<Object> row = new Vector<>();
                            row.add(enrollmentId);
                            row.add(studentId);
                            row.add(rollNo);
                            row.add(studentName == null ? "" : studentName);
                            row.add(quiz);       // may be null -> shows blank cell
                            row.add(mid);
                            row.add(end);
                            row.add(finalScore);

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
        int viewRow = tblSections.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a section first.");
            return;
        }
        int modelRow = tblSections.convertRowIndexToModel(viewRow);
        String sectionId = (String) sectionsModel.getValueAt(modelRow, 0);

        if (!isInstructorOwnerOfSection(sectionId)) {
            JOptionPane.showMessageDialog(this, "You are not the instructor for this section.", "Permission denied", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (maintenanceOn) {
            JOptionPane.showMessageDialog(this, "System is in maintenance mode. Cannot compute grades.", "Maintenance", JOptionPane.WARNING_MESSAGE);
            return;
        }

        for (int r = 0; r < gradeModel.getRowCount(); r++) {
            Double quiz = toDouble(gradeModel.getValueAt(r, 4));
            Double mid = toDouble(gradeModel.getValueAt(r, 5));
            Double end = toDouble(gradeModel.getValueAt(r, 6));

            double quizVal = (quiz == null) ? 0.0 : quiz;
            double midVal = (mid == null) ? 0.0 : mid;
            double endVal = (end == null) ? 0.0 : end;

            double finalScore = quizVal * W_QUIZ + midVal * W_MID + endVal * W_END;
            double rounded = Math.round(finalScore * 100.0) / 100.0;
            gradeModel.setValueAt(Double.valueOf(rounded), r, 7);
        }

        SwingUtilities.invokeLater(this::recalculateStatsFromTable);
    }

    private static Double toDouble(Object o) {
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
            Double f = toDouble(gradeModel.getValueAt(r, 7));
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
        int viewRow = tblSections.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a section first.");
            return;
        }
        int modelRow = tblSections.convertRowIndexToModel(viewRow);
        String sectionId = (String) sectionsModel.getValueAt(modelRow, 0);

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
                Connection conn = null;
                try {
                    conn = DBConfig.getErpConnection();
                    conn.setAutoCommit(false);

                    String upsertSql = """
                            INSERT INTO grades (grade_id, enrollment_id, component, score, weight)
                            VALUES (UUID(), ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE score = VALUES(score), weight = VALUES(weight)
                            """;

                    try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                        for (int r = 0; r < gradeModel.getRowCount(); r++) {
                            String enrollmentId = (String) gradeModel.getValueAt(r, 0);
                            Double quiz = toDouble(gradeModel.getValueAt(r, 4));
                            Double mid = toDouble(gradeModel.getValueAt(r, 5));
                            Double end = toDouble(gradeModel.getValueAt(r, 6));
                            Double finalScore = toDouble(gradeModel.getValueAt(r, 7));

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

                        // execute all batches
                        ps.executeBatch();
                    }

                    // commit
                    conn.commit();

                } catch (SQLException ex) {
                    // rollback if anything went wrong
                    if (conn != null) {
                        try {
                            conn.rollback();
                        } catch (SQLException rbe) {
                            rbe.printStackTrace();
                        }
                    }
                    ex.printStackTrace();
                    final String msg = ex.getMessage();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(InstructorDashboard.this,
                            "Error saving grades: " + msg, "DB Error", JOptionPane.ERROR_MESSAGE));
                } finally {
                    if (conn != null) {
                        try {
                            conn.close();
                        } catch (SQLException cerr) {
                            cerr.printStackTrace();
                        }
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(InstructorDashboard.this, "Grades saved.");
                    recalculateStatsFromTable();
                });
            }
        }.execute();
    }

    private void exportGradesCSV() {
        if (gradeModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No grades to export.");
            return;
        }

        // Create sensible default filename using selected section or timestamp
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

        java.util.function.Function<String, String> esc = s -> {
            if (s == null) return "";
            String str = s;
            if (str.contains("\"") || str.contains(",") || str.contains("\n") || str.contains("\r")) {
                str = str.replace("\"", "\"\"");
                return "\"" + str + "\"";
            } else {
                return str;
            }
        };

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            // write header
            for (int c = 0; c < gradeModel.getColumnCount(); c++) {
                pw.print(esc.apply(gradeModel.getColumnName(c)));
                if (c < gradeModel.getColumnCount() - 1) pw.print(",");
            }
            pw.println();

            // write rows
            for (int r = 0; r < gradeModel.getRowCount(); r++) {
                for (int c = 0; c < gradeModel.getColumnCount(); c++) {
                    Object v = gradeModel.getValueAt(r, c);
                    String cell = v == null ? "" : v.toString();
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

    // --- helper classes ---

    /**
     * Editor that parses the cell text into a Double, or null if empty/invalid.
     */
    static class DoubleEditor extends DefaultCellEditor {
        private final JTextField fld;

        DoubleEditor() {
            super(new JTextField());
            fld = (JTextField) getComponent();
            // optional: commit edit on Enter
            fld.addActionListener(e -> stopCellEditing());
        }

        @Override
        public Object getCellEditorValue() {
            String txt = fld.getText();
            if (txt == null) return null;
            txt = txt.trim();
            if (txt.isEmpty()) return null;
            try {
                return Double.parseDouble(txt);
            } catch (NumberFormatException ex) {
                // invalid entry -> return null (the table will show blank)
                return null;
            }
        }

        @Override
        public boolean stopCellEditing() {
            // we accept nulls; invalid numbers become null
            return super.stopCellEditing();
        }
    }
}
