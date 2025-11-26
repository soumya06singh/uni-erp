package edu.univ.erp.ui;

import edu.univ.erp.service.StudentService;
import edu.univ.erp.service.StudentService.*;
import edu.univ.erp.domain.ServiceResult;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Vector;

public class StudentDashboard extends JFrame {
    private final String userId;
    private final String username;
    private final StudentService studentService;
    private JTable enrollTable;
    private DefaultTableModel enrollModel;
    private JLabel maintenanceBanner;
    private DefaultTableModel catalogModel;
    private DefaultTableModel timetableModel;
    private DefaultTableModel gradesModel;


    public StudentDashboard(String userId, String username) {
        super("Student Dashboard - " + username);
        this.userId = userId;
        this.username = username;
        this.studentService = new StudentService();

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
        logoutBtn.addActionListener(e -> dispose());

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
                "Semester", "Room", "Status"
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

        // USE SERVICE LAYER INSTEAD OF DIRECT SQL
        List<EnrollmentView> enrollments = studentService.getStudentEnrollments(userId);

        for (EnrollmentView enrollment : enrollments) {
            Vector<Object> row = new Vector<>();
            row.add(enrollment.sectionId());
            row.add(enrollment.courseCode());
            row.add(enrollment.courseName());
            row.add(enrollment.credits());
            row.add(enrollment.instructorId());
            row.add(enrollment.semester());
            row.add(enrollment.room());
            row.add(enrollment.status());
            enrollModel.addRow(row);
        }
    }

    // ==================== COURSE CATALOG TAB ====================
    private JPanel createCourseCatalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel headerLabel = new JLabel("Browse & Register for Courses");
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));

        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchPanel.add(new JLabel("Search Course:"));
        JTextField searchField = new JTextField(20);
        searchPanel.add(searchField);

        JComboBox<String> semesterCombo = new JComboBox<>(new String[]{"All", "Fall", "Spring", "Summer"});
        searchPanel.add(new JLabel("Semester:"));
        searchPanel.add(semesterCombo);

        JButton searchBtn = new JButton("🔍 Search");
        searchPanel.add(searchBtn);

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            semesterCombo.setSelectedIndex(0);
        });
        searchPanel.add(clearBtn);

        // Catalog table
        catalogModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        catalogModel.setColumnIdentifiers(new Object[]{
                "Section ID", "Course Code", "Course Title", "Credits", "Instructor",
                "Semester", "Room", "Capacity", "Enrolled", "Available"
        });

        JTable catalogTable = new JTable(catalogModel);
        catalogTable.setRowHeight(28);
        catalogTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalogTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(catalogModel);
        catalogTable.setRowSorter(sorter);

        JScrollPane scroll = new JScrollPane(catalogTable);
        scroll.setBorder(BorderFactory.createTitledBorder("Available Sections"));

        // Action buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton registerBtn = new JButton("✅ Register for Selected Section");
        registerBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        registerBtn.addActionListener(e -> registerForSection(catalogTable, catalogModel));

        JButton refreshCatalogBtn = new JButton("🔄 Refresh Catalog");
        refreshCatalogBtn.addActionListener(e -> {
            loadCourseCatalog(catalogModel, null, null);
            searchField.setText("");
            semesterCombo.setSelectedIndex(0);
        });

        JButton viewDetailsBtn = new JButton("ℹ View Section Details");
        viewDetailsBtn.addActionListener(e -> viewSectionDetails(catalogTable, catalogModel));

        buttonPanel.add(registerBtn);
        buttonPanel.add(refreshCatalogBtn);
        buttonPanel.add(viewDetailsBtn);

        // Add hint label
        JLabel hintLabel = new JLabel("💡 Tip: Select a section from the table and click 'Register' to enroll");
        hintLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        searchBtn.addActionListener(e -> {
            String keyword = searchField.getText().trim();
            String semester = (String) semesterCombo.getSelectedItem();
            if ("All".equals(semester)) semester = null;
            loadCourseCatalog(catalogModel, keyword, semester);
        });

        // Layout
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.add(headerLabel, BorderLayout.NORTH);
        topSection.add(searchPanel, BorderLayout.CENTER);

        JPanel bottomSection = new JPanel(new BorderLayout());
        bottomSection.add(buttonPanel, BorderLayout.NORTH);
        bottomSection.add(hintLabel, BorderLayout.SOUTH);

        panel.add(topSection, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottomSection, BorderLayout.SOUTH);

        // Load initial data
        loadCourseCatalog(catalogModel, null, null);

        return panel;
    }

    private void loadCourseCatalog(DefaultTableModel model, String keyword, String semester) {
        model.setRowCount(0);

        // USE SERVICE LAYER INSTEAD OF DIRECT SQL
        List<CourseCatalogView> sections = studentService.getCourseCatalog(keyword, semester);

        for (CourseCatalogView section : sections) {
            Vector<Object> row = new Vector<>();
            row.add(section.sectionId());
            row.add(section.courseCode());
            row.add(section.courseName());
            row.add(section.credits());
            row.add(section.instructorId());
            row.add(section.semester());
            row.add(section.room());
            row.add(section.capacity());
            row.add(section.enrolled());
            row.add(section.available());
            model.addRow(row);
        }
    }

    private void viewSectionDetails(JTable table, DefaultTableModel model) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a section to view details.",
                    "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        String sectionId = (String) model.getValueAt(modelRow, 0);
        String courseCode = (String) model.getValueAt(modelRow, 1);
        String courseTitle = (String) model.getValueAt(modelRow, 2);
        int credits = (int) model.getValueAt(modelRow, 3);
        String instructor = (String) model.getValueAt(modelRow, 4);
        String semester = (String) model.getValueAt(modelRow, 5);
        String room = (String) model.getValueAt(modelRow, 6);
        int capacity = (int) model.getValueAt(modelRow, 7);
        int enrolled = (int) model.getValueAt(modelRow, 8);
        int available = (int) model.getValueAt(modelRow, 9);

        String details = String.format(
                "Section ID: %s\n\n" +
                        "Course: %s - %s\n" +
                        "Credits: %d\n\n" +
                        "Instructor: %s\n" +
                        "Semester: %s\n" +
                        "Room: %s\n\n" +
                        "Enrollment:\n" +
                        "  Capacity: %d\n" +
                        "  Enrolled: %d\n" +
                        "  Available: %d",
                sectionId, courseCode, courseTitle, credits,
                instructor, semester, room,
                capacity, enrolled, available
        );

        JTextArea textArea = new JTextArea(details);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        JOptionPane.showMessageDialog(this, scrollPane,
                "Section Details", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== TIMETABLE TAB ====================
    private JPanel createTimetablePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("Your Weekly Schedule");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));

        timetableModel = new DefaultTableModel() {
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

        // USE SERVICE LAYER INSTEAD OF DIRECT SQL
        List<TimetableView> timetable = studentService.getStudentTimetable(userId);

        for (TimetableView entry : timetable) {
            Vector<Object> row = new Vector<>();
            row.add(entry.day());
            row.add(entry.time());
            row.add(entry.course());
            row.add(entry.sectionId());
            row.add(entry.room());
            row.add(entry.instructorId());
            model.addRow(row);
        }
    }

    // ==================== GRADES TAB ====================
    private JPanel createGradesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("Your Grades");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));

        gradesModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        gradesModel.setColumnIdentifiers(new Object[]{
                "Course Code", "Course Name", "Section", "Component", "Score", "Final Grade"
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

        // USE SERVICE LAYER INSTEAD OF DIRECT SQL
        List<GradeView> grades = studentService.getStudentGrades(userId);

        for (GradeView grade : grades) {
            Vector<Object> row = new Vector<>();
            row.add(grade.courseCode());
            row.add(grade.courseName());
            row.add(grade.sectionId());
            row.add(grade.component() == null ? "N/A" : grade.component());
            row.add(grade.score() == null ? "N/A" : grade.score());
            row.add(grade.finalGrade() == null ? "Pending" : grade.finalGrade());
            model.addRow(row);
        }
    }

    // ==================== ACTIONS ====================
    private void registerForSection(JTable table, DefaultTableModel model) {
        // Check if a row is selected
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "⚠ Please select a section from the table first.\n\n" +
                            "Click on a row to select it, then click Register.",
                    "No Section Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        String sectionId = (String) model.getValueAt(modelRow, 0);
        String courseCode = (String) model.getValueAt(modelRow, 1);
        String courseTitle = (String) model.getValueAt(modelRow, 2);
        int available = (int) model.getValueAt(modelRow, 9);
        int capacity = (int) model.getValueAt(modelRow, 7);

        // Show confirmation dialog
        String confirmMessage = String.format(
                "Are you sure you want to register for:\n\n" +
                        "Section: %s\n" +
                        "Course: %s - %s\n" +
                        "Available Seats: %d / %d\n\n" +
                        "Click YES to confirm registration.",
                sectionId, courseCode, courseTitle, available, capacity
        );

        int confirm = JOptionPane.showConfirmDialog(this,
                confirmMessage,
                "Confirm Registration",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // USE SERVICE LAYER INSTEAD OF DIRECT SQL
            ServiceResult<String> result = studentService.registerForSection(userId, sectionId);

            if (result.isSuccess()) {
                JOptionPane.showMessageDialog(this,
                        String.format("✅ Registration Successful!\n\n" +
                                        "You have been enrolled in:\n" +
                                        "Section: %s\n" +
                                        "Course: %s - %s\n\n" +
                                        "Check 'My Enrollments' and 'Timetable' tabs.",
                                sectionId, courseCode, courseTitle),
                        "Success", JOptionPane.INFORMATION_MESSAGE);

                // Refresh data
                loadEnrollments();
                loadCourseCatalog(model, null, null);

            } else {
                JOptionPane.showMessageDialog(this,
                        "❌ " + result.getMessage(),
                        "Registration Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void dropSection() {
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
            // USE SERVICE LAYER INSTEAD OF DIRECT SQL
            ServiceResult<String> result = studentService.dropSection(userId, sectionId);

            if (result.isSuccess()) {
                JOptionPane.showMessageDialog(this,
                        "✅ " + result.getMessage(),
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                loadEnrollments();
            } else {
                JOptionPane.showMessageDialog(this,
                        "❌ " + result.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void downloadTranscript() {
        // USE SERVICE LAYER INSTEAD OF DIRECT SQL
        List<TranscriptView> transcript = studentService.getTranscript(userId);

        if (transcript.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No enrollment records found.",
                    "Empty Transcript", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String file = "transcript_" + username + "_" + timestamp + ".csv";

            try (FileWriter fw = new FileWriter(file)) {
                fw.write("Course Code,Course Name,Credits,Semester,Year,Final Grade\n");

                for (TranscriptView entry : transcript) {
                    fw.write(String.format("%s,%s,%d,%s,%d,%s\n",
                            escapeCsv(entry.courseCode()),
                            escapeCsv(entry.courseName()),
                            entry.credits(),
                            entry.semester(),
                            entry.year(),
                            entry.finalGrade() == null ? "Pending" : escapeCsv(entry.finalGrade())));
                }
            }

            JOptionPane.showMessageDialog(this,
                    "Transcript saved as: " + file,
                    "Success", JOptionPane.INFORMATION_MESSAGE);

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
        // USE SERVICE LAYER INSTEAD OF DIRECT SQL
        boolean maintenanceMode = studentService.isMaintenanceMode();
        maintenanceBanner.setVisible(maintenanceMode);
    }

    private void refreshAllData() {
        checkMaintenanceMode();

        // My Enrollments
        loadEnrollments();

        // Course Catalog (reset filters for global refresh)
        if (catalogModel != null) {
            loadCourseCatalog(catalogModel, null, null);
        }

        // Timetable
        if (timetableModel != null) {
            loadTimetable(timetableModel);
        }

        // Grades
        if (gradesModel != null) {
            loadGrades(gradesModel);
        }

        JOptionPane.showMessageDialog(this,
                "All data refreshed successfully!",
                "Refresh Complete", JOptionPane.INFORMATION_MESSAGE);
    }
}

