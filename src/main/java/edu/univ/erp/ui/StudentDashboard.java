package edu.univ.erp.ui;

import edu.univ.erp.data.DBConfig;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;

public class StudentDashboard extends JFrame {

    private final String userId;
    private final String username;
    private JTable enrollTable;
    private DefaultTableModel enrollModel;
    private JPanel mainPanel;
    private JLabel maintenanceBanner;
    private boolean maintenanceMode;

    public StudentDashboard(String userId, String username) {
        super("Student Dashboard - " + username);
        this.userId = userId;
        this.username = username;

        initUI();
        checkMaintenanceMode();
        loadEnrollments();
    }

    private void initUI() {
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Top panel with title and maintenance banner
        JPanel topPanel = new JPanel(new BorderLayout());

        JLabel title = new JLabel("Welcome, " + username);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        topPanel.add(title, BorderLayout.WEST);

        // Maintenance banner (initially hidden)
        maintenanceBanner = new JLabel("⚠ MAINTENANCE MODE - View Only", SwingConstants.CENTER);
        maintenanceBanner.setFont(new Font("SansSerif", Font.BOLD, 14));
        maintenanceBanner.setBackground(new Color(255, 165, 0));
        maintenanceBanner.setForeground(Color.WHITE);
        maintenanceBanner.setOpaque(true);
        maintenanceBanner.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        maintenanceBanner.setVisible(false);
        topPanel.add(maintenanceBanner, BorderLayout.NORTH);

        add(topPanel, BorderLayout.NORTH);

        // Main tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.PLAIN, 13));

        // Tab 1: My Enrollments
        tabbedPane.addTab("📚 My Enrollments", createEnrollmentsPanel());

        // Tab 2: Course Catalog (Browse & Register)
        tabbedPane.addTab("🔍 Course Catalog", createCourseCatalogPanel());

        // Tab 3: My Timetable
        tabbedPane.addTab("📅 Timetable", createTimetablePanel());

        // Tab 4: My Grades
        tabbedPane.addTab("📊 Grades", createGradesPanel());

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom panel with action buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton refreshBtn = new JButton("🔄 Refresh All");
        refreshBtn.addActionListener(e -> refreshAllData());

        JButton transcriptBtn = new JButton("📄 Download Transcript");
        transcriptBtn.addActionListener(e -> downloadTranscript());

        JButton logoutBtn = new JButton("🚪 Logout");
        logoutBtn.addActionListener(e -> {
            dispose();
            // You can add code here to return to login screen
        });

        bottomPanel.add(refreshBtn);
        bottomPanel.add(transcriptBtn);
        bottomPanel.add(logoutBtn);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ==================== MY ENROLLMENTS TAB ====================
    private JPanel createEnrollmentsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("Your Registered Sections");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));

        enrollModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        enrollModel.setColumnIdentifiers(new Object[]{
                "Section ID", "Course Code", "Course Title", "Credits", "Instructor",
                "Semester", "Year", "Room", "Day", "Time", "Status"
        });

        enrollTable = new JTable(enrollModel);
        enrollTable.setRowHeight(25);
        enrollTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(enrollModel);
        enrollTable.setRowSorter(sorter);

        JScrollPane scroll = new JScrollPane(enrollTable);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton dropBtn = new JButton("❌ Drop Selected Section");
        dropBtn.addActionListener(e -> dropSection());
        buttonPanel.add(dropBtn);

        panel.add(label, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadEnrollments() {
        enrollModel.setRowCount(0);

        String sql = "SELECT sec.section_id, c.course_code, c.course_name, c.credits, " +
                "COALESCE(instr.user_id, 'TBA') AS instructor_id, " +
                "sec.semester, sec.year, sec.room, sec.day, " +
                "CONCAT(sec.start_time, ' - ', sec.end_time) AS time, e.status " +
                "FROM enrollments e " +
                "JOIN sections sec ON e.section_id = sec.section_id " +
                "JOIN courses c ON sec.course_id = c.course_id " +
                "LEFT JOIN instructors instr ON sec.instructor_id = instr.user_id " +
                "WHERE e.student_id = ?";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("section_id"));
                    row.add(rs.getString("course_code"));
                    row.add(rs.getString("course_name"));
                    row.add(rs.getInt("credits"));
                    row.add(rs.getString("instructor_id"));
                    row.add(rs.getString("semester"));
                    row.add(rs.getInt("year"));
                    row.add(rs.getString("room"));
                    row.add(rs.getString("day"));
                    row.add(rs.getString("time"));
                    row.add(rs.getString("status"));
                    enrollModel.addRow(row);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load enrollments: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== COURSE CATALOG TAB ====================
    private JPanel createCourseCatalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search:"));
        JTextField searchField = new JTextField(20);
        searchPanel.add(searchField);

        JComboBox<String> semesterCombo = new JComboBox<>(new String[]{"All", "Fall", "Spring", "Summer"});
        searchPanel.add(new JLabel("Semester:"));
        searchPanel.add(semesterCombo);

        JButton searchBtn = new JButton("🔍 Search");
        searchPanel.add(searchBtn);

        // Catalog table
        DefaultTableModel catalogModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        catalogModel.setColumnIdentifiers(new Object[]{
                "Section ID", "Course Code", "Course Title", "Credits", "Instructor",
                "Semester", "Year", "Day", "Time", "Room", "Capacity", "Enrolled", "Available"
        });

        JTable catalogTable = new JTable(catalogModel);
        catalogTable.setRowHeight(25);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(catalogModel);
        catalogTable.setRowSorter(sorter);

        JScrollPane scroll = new JScrollPane(catalogTable);

        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton registerBtn = new JButton("✅ Register for Selected Section");
        registerBtn.addActionListener(e -> registerForSection(catalogTable, catalogModel));

        JButton refreshCatalogBtn = new JButton("🔄 Refresh Catalog");
        refreshCatalogBtn.addActionListener(e -> loadCourseCatalog(catalogModel, null, null));

        buttonPanel.add(registerBtn);
        buttonPanel.add(refreshCatalogBtn);

        searchBtn.addActionListener(e -> {
            String keyword = searchField.getText().trim();
            String semester = (String) semesterCombo.getSelectedItem();
            if ("All".equals(semester)) semester = null;
            loadCourseCatalog(catalogModel, keyword, semester);
        });

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        // Load initial data
        loadCourseCatalog(catalogModel, null, null);

        return panel;
    }

    private void loadCourseCatalog(DefaultTableModel model, String keyword, String semester) {
        model.setRowCount(0);

        StringBuilder sql = new StringBuilder(
                "SELECT sec.section_id, c.course_code, c.course_name, c.credits, " +
                        "COALESCE(instr.user_id, 'TBA') AS instructor_id, " +
                        "sec.semester, sec.year, sec.day, " +
                        "CONCAT(sec.start_time, ' - ', sec.end_time) AS time, " +
                        "sec.room, sec.capacity, " +
                        "COALESCE(COUNT(e.enrollment_id), 0) AS enrolled " +
                        "FROM sections sec " +
                        "JOIN courses c ON sec.course_id = c.course_id " +
                        "LEFT JOIN instructors instr ON sec.instructor_id = instr.user_id " +
                        "LEFT JOIN enrollments e ON sec.section_id = e.section_id AND e.status = 'ENROLLED' " +
                        "WHERE 1=1 "
        );

        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (c.course_code LIKE ? OR c.course_name LIKE ?) ");
        }
        if (semester != null && !semester.isEmpty()) {
            sql.append("AND sec.semester = ? ");
        }

        sql.append("GROUP BY sec.section_id, c.course_code, c.course_name, c.credits, " +
                "instr.user_id, sec.semester, sec.year, sec.day, sec.start_time, sec.end_time, " +
                "sec.room, sec.capacity " +
                "ORDER BY c.course_code, sec.section_id");

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (keyword != null && !keyword.isEmpty()) {
                String search = "%" + keyword + "%";
                ps.setString(paramIndex++, search);
                ps.setString(paramIndex++, search);
            }
            if (semester != null && !semester.isEmpty()) {
                ps.setString(paramIndex++, semester);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("section_id"));
                    row.add(rs.getString("course_code"));
                    row.add(rs.getString("course_name"));
                    row.add(rs.getInt("credits"));
                    row.add(rs.getString("instructor_id"));
                    row.add(rs.getString("semester"));
                    row.add(rs.getInt("year"));
                    row.add(rs.getString("day"));
                    row.add(rs.getString("time"));
                    row.add(rs.getString("room"));
                    int capacity = rs.getInt("capacity");
                    int enrolled = rs.getInt("enrolled");
                    int available = capacity - enrolled;
                    row.add(capacity);
                    row.add(enrolled);
                    row.add(available);
                    model.addRow(row);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load catalog: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== TIMETABLE TAB ====================
    private JPanel createTimetablePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("Your Weekly Schedule");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));

        DefaultTableModel timetableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        timetableModel.setColumnIdentifiers(new Object[]{
                "Day", "Time", "Course", "Section", "Room", "Instructor"
        });

        JTable timetableTable = new JTable(timetableModel);
        timetableTable.setRowHeight(30);
        JScrollPane scroll = new JScrollPane(timetableTable);

        JButton refreshBtn = new JButton("🔄 Refresh Timetable");
        refreshBtn.addActionListener(e -> loadTimetable(timetableModel));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(refreshBtn);

        panel.add(label, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        loadTimetable(timetableModel);

        return panel;
    }

    private void loadTimetable(DefaultTableModel model) {
        model.setRowCount(0);

        String sql = "SELECT sec.day, CONCAT(sec.start_time, ' - ', sec.end_time) AS time, " +
                "CONCAT(c.course_code, ' - ', c.course_name) AS course, " +
                "sec.section_id, sec.room, COALESCE(instr.user_id, 'TBA') AS instructor_id " +
                "FROM enrollments e " +
                "JOIN sections sec ON e.section_id = sec.section_id " +
                "JOIN courses c ON sec.course_id = c.course_id " +
                "LEFT JOIN instructors instr ON sec.instructor_id = instr.user_id " +
                "WHERE e.student_id = ? AND e.status = 'ENROLLED' " +
                "ORDER BY FIELD(sec.day, 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'), " +
                "sec.start_time";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("day"));
                    row.add(rs.getString("time"));
                    row.add(rs.getString("course"));
                    row.add(rs.getString("section_id"));
                    row.add(rs.getString("room"));
                    row.add(rs.getString("instructor_id"));
                    model.addRow(row);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load timetable: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== GRADES TAB ====================
    private JPanel createGradesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("Your Grades");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));

        DefaultTableModel gradesModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        gradesModel.setColumnIdentifiers(new Object[]{
                "Course Code", "Course Name", "Section", "Component", "Score", "Max Score", "Final Grade"
        });

        JTable gradesTable = new JTable(gradesModel);
        gradesTable.setRowHeight(25);
        JScrollPane scroll = new JScrollPane(gradesTable);

        JButton refreshBtn = new JButton("🔄 Refresh Grades");
        refreshBtn.addActionListener(e -> loadGrades(gradesModel));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(refreshBtn);

        panel.add(label, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        loadGrades(gradesModel);

        return panel;
    }

    private void loadGrades(DefaultTableModel model) {
        model.setRowCount(0);

        String sql = "SELECT c.course_code, c.course_name, sec.section_id, " +
                "g.component, g.score, g.max_score, g.final_grade " +
                "FROM enrollments e " +
                "JOIN sections sec ON e.section_id = sec.section_id " +
                "JOIN courses c ON sec.course_id = c.course_id " +
                "LEFT JOIN grades g ON g.enrollment_id = e.enrollment_id " +
                "WHERE e.student_id = ? " +
                "ORDER BY c.course_code, sec.section_id, g.component";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("course_code"));
                    row.add(rs.getString("course_name"));
                    row.add(rs.getString("section_id"));
                    row.add(rs.getString("component") == null ? "N/A" : rs.getString("component"));

                    Object score = rs.getObject("score");
                    row.add(score == null ? "N/A" : score);

                    Object maxScore = rs.getObject("max_score");
                    row.add(maxScore == null ? "N/A" : maxScore);

                    row.add(rs.getString("final_grade") == null ? "Pending" : rs.getString("final_grade"));
                    model.addRow(row);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load grades: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== ACTIONS ====================
    private void registerForSection(JTable table, DefaultTableModel model) {
        if (maintenanceMode) {
            JOptionPane.showMessageDialog(this,
                    "Cannot register during maintenance mode.",
                    "Maintenance Mode", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a section to register.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        String sectionId = (String) model.getValueAt(modelRow, 0);
        int available = (int) model.getValueAt(modelRow, 12);

        if (available <= 0) {
            JOptionPane.showMessageDialog(this,
                    "This section is full. No seats available.",
                    "Section Full", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check for duplicate enrollment
        if (isDuplicateEnrollment(sectionId)) {
            JOptionPane.showMessageDialog(this,
                    "You are already enrolled in this section.",
                    "Duplicate Enrollment", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Register for section " + sectionId + "?",
                "Confirm Registration", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (registerStudent(sectionId)) {
                JOptionPane.showMessageDialog(this,
                        "Successfully registered for section " + sectionId,
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                loadEnrollments();
                loadCourseCatalog(model, null, null);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to register. Please try again.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean isDuplicateEnrollment(String sectionId) {
        String sql = "SELECT COUNT(*) FROM enrollments WHERE student_id = ? AND section_id = ? AND status = 'ENROLLED'";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, sectionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private boolean registerStudent(String sectionId) {
        String sql = "INSERT INTO enrollments (student_id, section_id, status, enrollment_date) VALUES (?, ?, 'ENROLLED', NOW())";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, sectionId);
            return ps.executeUpdate() > 0;

        } catch (SQLException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void dropSection() {
        if (maintenanceMode) {
            JOptionPane.showMessageDialog(this,
                    "Cannot drop sections during maintenance mode.",
                    "Maintenance Mode", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int selectedRow = enrollTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a section to drop.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = enrollTable.convertRowIndexToModel(selectedRow);
        String sectionId = (String) enrollModel.getValueAt(modelRow, 0);
        String courseCode = (String) enrollModel.getValueAt(modelRow, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Drop section " + sectionId + " (" + courseCode + ")?",
                "Confirm Drop", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (dropEnrollment(sectionId)) {
                JOptionPane.showMessageDialog(this,
                        "Successfully dropped section " + sectionId,
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                loadEnrollments();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to drop section. Please try again.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean dropEnrollment(String sectionId) {
        String sql = "UPDATE enrollments SET status = 'DROPPED' WHERE student_id = ? AND section_id = ? AND status = 'ENROLLED'";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, sectionId);
            return ps.executeUpdate() > 0;

        } catch (SQLException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void downloadTranscript() {
        try (Connection conn = DBConfig.getErpConnection()) {
            String sql = "SELECT c.course_code, c.course_name, c.credits, " +
                    "sec.semester, sec.year, g.final_grade " +
                    "FROM enrollments e " +
                    "JOIN sections sec ON e.section_id = sec.section_id " +
                    "JOIN courses c ON sec.course_id = c.course_id " +
                    "LEFT JOIN grades g ON g.enrollment_id = e.enrollment_id " +
                    "WHERE e.student_id = ? AND e.status = 'ENROLLED' " +
                    "ORDER BY sec.year, sec.semester, c.course_code";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {

                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    String file = "transcript_" + username + "_" + timestamp + ".csv";

                    try (FileWriter fw = new FileWriter(file)) {
                        fw.write("Course Code,Course Name,Credits,Semester,Year,Final Grade\n");
                        while (rs.next()) {
                            String code = rs.getString("course_code");
                            String name = rs.getString("course_name");
                            int credits = rs.getInt("credits");
                            String semester = rs.getString("semester");
                            int year = rs.getInt("year");
                            String grade = rs.getString("final_grade");

                            fw.write(String.format("%s,%s,%d,%s,%d,%s\n",
                                    escapeCsv(code),
                                    escapeCsv(name),
                                    credits,
                                    semester,
                                    year,
                                    grade == null ? "Pending" : escapeCsv(grade)));
                        }
                    }
                    JOptionPane.showMessageDialog(this,
                            "Transcript saved as: " + file,
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to download transcript: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\n") || s.contains("\r") || s.contains("\"")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    // ==================== MAINTENANCE MODE ====================
    private void checkMaintenanceMode() {
        String sql = "SELECT value FROM settings WHERE `key` = 'maintenance_mode'";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                String value = rs.getString("value");
                maintenanceMode = "true".equalsIgnoreCase(value);
                maintenanceBanner.setVisible(maintenanceMode);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void refreshAllData() {
        checkMaintenanceMode();
        loadEnrollments();
        JOptionPane.showMessageDialog(this,
                "All data refreshed successfully!",
                "Refresh Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== MAIN (FOR TESTING) ====================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Replace with actual user_id from your database
            StudentDashboard dashboard = new StudentDashboard(
                    "33ea148a-c4c8-11f0-bc78-00090faa0001",
                    "stu1001"
            );
            dashboard.setVisible(true);
        });
    }
}