package edu.univ.erp.ui;

import edu.univ.erp.domain.ServiceResult;
import edu.univ.erp.service.AdminService;
import edu.univ.erp.service.AdminService.*;
import edu.univ.erp.data.DBConfig;
import edu.univ.erp.domain.ServiceResult;
import edu.univ.erp.domain.ERP;


import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Vector;

public class AdminDashboard extends JFrame {
    private final String userId;
    private final String username;
    private final AdminService adminService;

    private JTable userTable;
    private JTable courseTable;    // ✅ ADD THIS
    private JTable sectionTable;   // ✅ ADD THIS
    private JLabel maintenanceBanner;
    private DefaultTableModel userModel, courseModel, sectionModel;


    public AdminDashboard(String userId, String username) {
        super("Admin Dashboard - " + username);
        this.userId = userId;
        this.username = username;
        this.adminService = new AdminService();

        // ❌ remove this: it was creating a table with a null model
        // userTable = new JTable(userModel);

        initUI();
        loadAllData();
    }

    private void initUI() {
        setSize(1200, 750);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("🔧 Admin Control Panel - " + username);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        topPanel.add(title, BorderLayout.WEST);

        maintenanceBanner = new JLabel("⚠ MAINTENANCE MODE ACTIVE", SwingConstants.CENTER);
        maintenanceBanner.setFont(new Font("SansSerif", Font.BOLD, 14));
        maintenanceBanner.setBackground(new Color(255, 140, 0));
        maintenanceBanner.setForeground(Color.WHITE);
        maintenanceBanner.setOpaque(true);
        maintenanceBanner.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        maintenanceBanner.setVisible(false);
        topPanel.add(maintenanceBanner, BorderLayout.NORTH);

        add(topPanel, BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.PLAIN, 14));

        tabbedPane.addTab("👥 User Management", createUserManagementPanel());
        tabbedPane.addTab("📚 Course Management", createCourseManagementPanel());
        tabbedPane.addTab("📅 Section Management", createSectionManagementPanel());
        tabbedPane.addTab("⚙️ System Settings", createSettingsPanel());

        add(tabbedPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton refreshBtn = new JButton("🔄 Refresh All");
        refreshBtn.addActionListener(e -> {
            loadAllData();
            JOptionPane.showMessageDialog(this, "All data refreshed!");
        });

        JButton logoutBtn = new JButton("🚪 Logout");
        logoutBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to logout?");
            if (confirm == JOptionPane.YES_OPTION) {
                dispose();
            }
        });

        bottomPanel.add(refreshBtn);
        bottomPanel.add(logoutBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        checkMaintenanceStatus();
    }

    // ==================== USER MANAGEMENT TAB ====================
    private JPanel createUserManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton addStudentBtn = new JButton("➕ Add Student");
        JButton addInstructorBtn = new JButton("➕ Add Instructor");
        JButton addAdminBtn = new JButton("➕ Add Admin");
        JButton deleteUserBtn = new JButton("❌ Delete Selected User");
        JButton refreshBtn = new JButton("🔄 Refresh");

        addStudentBtn.addActionListener(e -> showAddStudentDialog());
        addInstructorBtn.addActionListener(e -> showAddInstructorDialog());
        addAdminBtn.addActionListener(e -> showAddAdminDialog());
        deleteUserBtn.addActionListener(e -> deleteSelectedUser());
        refreshBtn.addActionListener(e -> loadUsers());

        buttonPanel.add(addStudentBtn);
        buttonPanel.add(addInstructorBtn);
        buttonPanel.add(addAdminBtn);
        buttonPanel.add(deleteUserBtn);
        buttonPanel.add(refreshBtn);

        userModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        userModel.setColumnIdentifiers(new Object[]{"User ID", "Username", "Role", "Status", "Roll No/Dept"});

        // ✅ assign the JTable to the FIELD, not a new local variable
        userTable = new JTable(userModel);
        userTable.setRowHeight(25);

        JScrollPane scroll = new JScrollPane(userTable);

        scroll.setBorder(BorderFactory.createTitledBorder("All Users"));

        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void loadUsers() {
        userModel.setRowCount(0);
        List<UserView> users = adminService.getAllUsers();
        for (UserView user : users) {
            Vector<Object> row = new Vector<>();
            row.add(user.userId());
            row.add(user.username());
            row.add(user.role());
            row.add(user.status());
            row.add(user.specificId());
            userModel.addRow(row);
        }
    }

    private void deleteSelectedUser() {
        int row = userTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a user to delete!");
            return;
        }

        String selectedUserId = (String) userModel.getValueAt(row, 0);
        Object roleObj = userModel.getValueAt(row, 2);
        String role = roleObj == null ? null : roleObj.toString();

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete user: " + selectedUserId + "?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            ServiceResult<String> sr = adminService.deleteUser(selectedUserId, role);
            if (sr.isSuccess()) {
                JOptionPane.showMessageDialog(this, sr.getMessage());
                loadUsers();
            } else {
                JOptionPane.showMessageDialog(this, sr.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    // ==================== COURSE MANAGEMENT TAB ====================
    private JPanel createCourseManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton addBtn = new JButton("➕ Add Course");
        JButton editBtn = new JButton("✏️ Edit Selected");
        JButton deleteBtn = new JButton("❌ Delete Selected");
        JButton refreshBtn = new JButton("🔄 Refresh");

        addBtn.addActionListener(e -> showAddCourseDialog());
        editBtn.addActionListener(e -> showEditCourseDialog());
        deleteBtn.addActionListener(e -> deleteSelectedCourse());
        refreshBtn.addActionListener(e -> loadCourses());

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);

        // Table
        courseModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        courseModel.setColumnIdentifiers(new Object[]{"Course ID", "Code", "Name", "Credits", "Description"});

        courseTable = new JTable(courseModel);  // ✅ No "JTable" keyword!
        courseTable.setRowHeight(25);
        JScrollPane scroll = new JScrollPane(courseTable);
        scroll.setBorder(BorderFactory.createTitledBorder("All Courses"));

        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void loadCourses() {
        courseModel.setRowCount(0);
        List<CourseView> courses = adminService.getAllCourses();
        for (CourseView course : courses) {
            Vector<Object> row = new Vector<>();
            row.add(course.courseId());
            row.add(course.courseCode());
            row.add(course.courseName());
            row.add(course.credits());
            row.add(course.description());
            courseModel.addRow(row);
        }
    }

    private void showAddCourseDialog() {
        JTextField codeField = new JTextField(15);
        JTextField nameField = new JTextField(15);
        JSpinner creditsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 6, 1));
        JTextArea descArea = new JTextArea(3, 15);
        descArea.setLineWrap(true);

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.add(new JLabel("Course Code:"));
        panel.add(codeField);
        panel.add(new JLabel("Course Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Credits:"));
        panel.add(creditsSpinner);
        panel.add(new JLabel("Description:"));
        panel.add(new JScrollPane(descArea));

        int result = JOptionPane.showConfirmDialog(this, panel, "Add New Course",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            ServiceResult<Integer> sr = adminService.addCourse(
                    codeField.getText().trim(),
                    nameField.getText().trim(),
                    (int) creditsSpinner.getValue(),
                    descArea.getText().trim()
            );
            if (sr.isSuccess()) {
                JOptionPane.showMessageDialog(this, sr.getMessage());
                loadCourses();
            } else {
                JOptionPane.showMessageDialog(this, sr.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showEditCourseDialog() {
        int row = courseTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a course to edit!");
            return;
        }

        // Get current values
        int courseId = (int) courseModel.getValueAt(row, 0);
        String currentCode = (String) courseModel.getValueAt(row, 1);
        String currentName = (String) courseModel.getValueAt(row, 2);
        int currentCredits = (int) courseModel.getValueAt(row, 3);
        String currentDesc = (String) courseModel.getValueAt(row, 4);

        // Create dialog with pre-filled values
        JTextField codeField = new JTextField(currentCode, 15);
        JTextField nameField = new JTextField(currentName, 15);
        JSpinner creditsSpinner = new JSpinner(new SpinnerNumberModel(currentCredits, 1, 6, 1));
        JTextArea descArea = new JTextArea(currentDesc, 3, 15);
        descArea.setLineWrap(true);

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.add(new JLabel("Course Code:"));
        panel.add(codeField);
        panel.add(new JLabel("Course Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Credits:"));
        panel.add(creditsSpinner);
        panel.add(new JLabel("Description:"));
        panel.add(new JScrollPane(descArea));

        if (JOptionPane.showConfirmDialog(this, panel, "Edit Course",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {

            ServiceResult<String> result = adminService.updateCourse(
                    courseId,
                    codeField.getText().trim(),
                    nameField.getText().trim(),
                    (int) creditsSpinner.getValue(),
                    descArea.getText().trim()
            );

            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadCourses();
        }
    }

    private void deleteSelectedCourse() {
        int row = courseTable.getSelectedRow();  // ✅ Now this works!
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a course to delete!");
            return;
        }

        int courseId = (int) courseModel.getValueAt(row, 0);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete course: " + courseModel.getValueAt(row, 2) + "?");

        if (confirm == JOptionPane.YES_OPTION) {
            ServiceResult<String> result = adminService.deleteCourse(courseId);
            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadCourses();
        }
    }

    // ==================== SECTION MANAGEMENT TAB ====================

    private void loadSections() {
        sectionModel.setRowCount(0);
        List<SectionView> sections = adminService.getAllSections();
        for (SectionView section : sections) {
            Vector<Object> row = new Vector<>();
            row.add(section.sectionId());
            row.add(section.courseCode() + " - " + section.courseName());
            row.add(section.instructorId());
            row.add(section.semester());
            row.add(section.year());
            row.add(section.day());
            row.add(section.startTime() + " - " + section.endTime());
            row.add(section.room());
            row.add(section.capacity());
            row.add(section.enrolled());
            sectionModel.addRow(row);
        }
    }


    // ==================== SYSTEM SETTINGS TAB ====================


    private void checkMaintenanceStatus() {
        boolean isOn = adminService.getMaintenanceMode();
        maintenanceBanner.setVisible(isOn);
    }

    private void loadAllData() {
        loadUsers();
        loadCourses();
        loadSections();
        checkMaintenanceStatus();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AdminDashboard dashboard = new AdminDashboard("admin001", "admin1");
            dashboard.setVisible(true);
        });
    }

    private void showAddStudentDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);
        JTextField rollNoField = new JTextField(15);

        // 🔹 Program selection dropdown (prevents invalid input)
        String[] programs = {
                "Computer Science",
                "Electrical Engineering",
                "Mechanical Engineering",
                "Civil Engineering",
                "Mathematics"
        };
        JComboBox<String> programCombo = new JComboBox<>(programs);

        // 🔹 Restrict year 1–4 only
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 4, 1));

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);
        panel.add(new JLabel("Roll Number:"));
        panel.add(rollNoField);
        panel.add(new JLabel("Program:"));
        panel.add(programCombo);
        panel.add(new JLabel("Year of Study:"));
        panel.add(yearSpinner);

        if (JOptionPane.showConfirmDialog(this, panel, "Add Student",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {

            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String rollNo = rollNoField.getText().trim();
            String program = (String) programCombo.getSelectedItem();
            int year = (int) yearSpinner.getValue();

            // 🔹 VALIDATIONS
            if (username.isEmpty() || password.isEmpty() || rollNo.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!rollNo.matches("^\\d{7}$")) {
                JOptionPane.showMessageDialog(this,
                        "Roll number must be 7 digits (e.g. 2021001)",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (year < 1 || year > 4) {
                JOptionPane.showMessageDialog(this,
                        "Year of study must be between 1 and 4!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 🔹 Proceed with service call only if validation passes
            ServiceResult<String> result = adminService.addStudent(
                    username, password, rollNo, program, year);

            JOptionPane.showMessageDialog(this, result.getMessage(),
                    result.isSuccess() ? "Success" : "Error",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE :
                            JOptionPane.ERROR_MESSAGE);

            if (result.isSuccess()) {
                loadUsers();
            }
        }
    }


    private void showAddAdminDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);

        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);

        if (JOptionPane.showConfirmDialog(this, panel, "Add Admin",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {

            ServiceResult<String> result = adminService.addAdmin(
                    usernameField.getText().trim(),
                    new String(passwordField.getPassword())
            );

            JOptionPane.showMessageDialog(this, result.getMessage());
            loadUsers();
        }
    }

    private void showAddInstructorDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);

        // Dropdown department selection
        String[] departments = {
                "Computer Science",
                "Biology",
                "ECE",
                "Mathematics",
                "Physics",
                "Chemistry"
        };
        JComboBox<String> deptDropdown = new JComboBox<>(departments);

        JTextField desigField = new JTextField(15);

        // Office must be ≤ 10 characters
        JTextField roomField = new JTextField(10);

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);
        panel.add(new JLabel("Department:"));
        panel.add(deptDropdown);
        panel.add(new JLabel("Designation:"));
        panel.add(desigField);
        panel.add(new JLabel("Office Room (Max 10):"));
        panel.add(roomField);

        if (JOptionPane.showConfirmDialog(this, panel, "Add Instructor",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {

            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String department = deptDropdown.getSelectedItem().toString();
            String designation = desigField.getText().trim();
            String office = roomField.getText().trim();

            // ✔ Validations
            if (username.isEmpty() || password.isEmpty() || designation.isEmpty() || office.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (password.length() < 6) {
                JOptionPane.showMessageDialog(this, "Password must be at least 6 characters.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (office.length() > 10) {
                JOptionPane.showMessageDialog(this, "Office Room must be 10 characters or fewer.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            ServiceResult<String> result = adminService.addInstructor(
                    username,
                    password,
                    department,
                    designation,
                    office
            );

            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadUsers();
        }
    }
    // ==================== SECTION MANAGEMENT TAB ====================
    private JPanel createSectionManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton addBtn = new JButton("➕ Add Section");
        JButton editBtn = new JButton("✏️ Edit Selected");
        JButton assignBtn = new JButton("👤 Assign Instructor");
        JButton deleteBtn = new JButton("❌ Delete Selected");
        JButton refreshBtn = new JButton("🔄 Refresh");

        addBtn.addActionListener(e -> showAddSectionDialog());
        editBtn.addActionListener(e -> showEditSectionDialog());
        assignBtn.addActionListener(e -> showAssignInstructorDialog());
        deleteBtn.addActionListener(e -> deleteSelectedSection());
        refreshBtn.addActionListener(e -> loadSections());

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(assignBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);

        // Table
        sectionModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        sectionModel.setColumnIdentifiers(new Object[]{
                "Section ID", "Course", "Instructor", "Semester", "Year",
                "Day", "Time", "Room", "Capacity", "Enrolled"
        });

        sectionTable = new JTable(sectionModel);
        sectionTable.setRowHeight(25);
        JScrollPane scroll = new JScrollPane(sectionTable);
        scroll.setBorder(BorderFactory.createTitledBorder("All Sections"));

        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ==================== ADD SECTION DIALOG ====================
    private void showAddSectionDialog() {
        var courses = adminService.getAllCourses();
        var instructors = adminService.getAllInstructors();

        JComboBox<String> courseBox = new JComboBox<>();
        JComboBox<String> instructorBox = new JComboBox<>();
        JComboBox<String> semesterBox = new JComboBox<>(new String[] {"Spring", "Fall", "Summer","Winter"});
        JComboBox<String> dayBox = new JComboBox<>(new String[] {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"});

        // ✅ FIX: Remove comma from year display
        SpinnerNumberModel yearModel = new SpinnerNumberModel(2024, 2000, 2100, 1);
        JSpinner yearSpinner = new JSpinner(yearModel);
        JSpinner.NumberEditor yearEditor = new JSpinner.NumberEditor(yearSpinner, "#");
        yearSpinner.setEditor(yearEditor);

        JTextField startField = new JTextField(10);
        JTextField endField = new JTextField(10);
        JTextField roomField = new JTextField(10);

        // ✅ FIX: Prevent negative capacity
        JSpinner capSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 300, 1));

        // ✅ Add courses to dropdown
        for (var c : courses) courseBox.addItem(c.courseCode() + " - " + c.courseName());

        // ✅ NULL OPTION: Add "No Instructor (Assign Later)" option
        instructorBox.addItem("--- No Instructor (Assign Later) ---");
        for (var i : instructors) instructorBox.addItem(i.userId() + " (" + i.department() + ")");

        // Use BoxLayout for clean layout
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(createLabeledRow("Course:", courseBox));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Instructor:", instructorBox));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Semester:", semesterBox));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Year:", yearSpinner));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Day:", dayBox));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Start Time (HH:MM):", startField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("End Time (HH:MM):", endField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Room:", roomField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Capacity:", capSpinner));

        if (JOptionPane.showConfirmDialog(this, panel, "Add Section",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {

            int cIndex = courseBox.getSelectedIndex();
            int iIndex = instructorBox.getSelectedIndex();

            if (cIndex == -1) {
                JOptionPane.showMessageDialog(this, "Please select a course!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ✅ VALIDATE TIME FORMAT
            if (!startField.getText().matches("\\d{2}:\\d{2}") || !endField.getText().matches("\\d{2}:\\d{2}")) {
                JOptionPane.showMessageDialog(this, "Time must be in HH:MM format (e.g., 09:00)", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ✅ VALIDATE CAPACITY
            int capacity = (int) capSpinner.getValue();
            if (capacity <= 0) {
                JOptionPane.showMessageDialog(this, "Capacity must be positive!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ✅ Handle NULL instructor case
            String instructorId = null;
            if (iIndex > 0) { // 0 is "No Instructor"
                instructorId = instructors.get(iIndex - 1).userId();
            }

            var result = adminService.addSection(
                    courses.get(cIndex).courseId(),
                    instructorId,
                    semesterBox.getSelectedItem().toString(),
                    (int) yearSpinner.getValue(),
                    dayBox.getSelectedItem().toString(),
                    startField.getText().trim(),
                    endField.getText().trim(),
                    roomField.getText().trim(),
                    capacity
            );

            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadSections();
        }
    }

    // ==================== EDIT SECTION DIALOG ====================
    private void showEditSectionDialog() {
        int row = sectionTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a section to edit!");
            return;
        }

        // Get current values
        String sectionId = (String) sectionModel.getValueAt(row, 0);
        String currentInstructor = (String) sectionModel.getValueAt(row, 2);
        String currentSemester = (String) sectionModel.getValueAt(row, 3);
        int currentYear = (int) sectionModel.getValueAt(row, 4);
        String currentDay = (String) sectionModel.getValueAt(row, 5);
        String currentTime = (String) sectionModel.getValueAt(row, 6);
        String currentRoom = (String) sectionModel.getValueAt(row, 7);
        int currentCapacity = (int) sectionModel.getValueAt(row, 8);

        // Parse start and end time
        String[] times = currentTime.split(" - ");
        String startTime = times.length > 0 ? times[0] : "09:00";
        String endTime = times.length > 1 ? times[1] : "10:00";

        var instructors = adminService.getAllInstructors();

        JComboBox<String> instructorBox = new JComboBox<>();
        JComboBox<String> semesterBox = new JComboBox<>(new String[] {"Spring", "Fall", "Summer"});
        JComboBox<String> dayBox = new JComboBox<>(new String[] {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"});

        SpinnerNumberModel yearModel = new SpinnerNumberModel(currentYear, 2000, 2100, 1);
        JSpinner yearSpinner = new JSpinner(yearModel);
        JSpinner.NumberEditor yearEditor = new JSpinner.NumberEditor(yearSpinner, "#");
        yearSpinner.setEditor(yearEditor);

        JTextField startField = new JTextField(startTime, 10);
        JTextField endField = new JTextField(endTime, 10);
        JTextField roomField = new JTextField(currentRoom, 10);
        JSpinner capSpinner = new JSpinner(new SpinnerNumberModel(currentCapacity, 1, 300, 1));

        // ✅ Add NULL option and instructors
        instructorBox.addItem("--- No Instructor ---");
        int selectedIndex = 0;
        for (int i = 0; i < instructors.size(); i++) {
            var inst = instructors.get(i);
            instructorBox.addItem(inst.userId() + " (" + inst.department() + ")");
            if (inst.userId().equals(currentInstructor)) {
                selectedIndex = i + 1;
            }
        }
        instructorBox.setSelectedIndex(selectedIndex);

        semesterBox.setSelectedItem(currentSemester);
        dayBox.setSelectedItem(currentDay);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(createLabeledRow("Instructor:", instructorBox));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Semester:", semesterBox));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Year:", yearSpinner));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Day:", dayBox));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Start Time (HH:MM):", startField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("End Time (HH:MM):", endField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Room:", roomField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createLabeledRow("Capacity:", capSpinner));

        if (JOptionPane.showConfirmDialog(this, panel, "Edit Section",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {

            if (!startField.getText().matches("\\d{2}:\\d{2}") || !endField.getText().matches("\\d{2}:\\d{2}")) {
                JOptionPane.showMessageDialog(this, "Time must be in HH:MM format!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int capacity = (int) capSpinner.getValue();
            if (capacity <= 0) {
                JOptionPane.showMessageDialog(this, "Capacity must be positive!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String instructorId = null;
            int iIndex = instructorBox.getSelectedIndex();
            if (iIndex > 0) {
                instructorId = instructors.get(iIndex - 1).userId();
            }

            ServiceResult<String> result = adminService.updateSection(
                    sectionId,
                    instructorId,
                    semesterBox.getSelectedItem().toString(),
                    (int) yearSpinner.getValue(),
                    dayBox.getSelectedItem().toString(),
                    startField.getText().trim(),
                    endField.getText().trim(),
                    roomField.getText().trim(),
                    capacity
            );

            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadSections();
        }
    }

    // ==================== ASSIGN INSTRUCTOR DIALOG ====================
    private void showAssignInstructorDialog() {
        int row = sectionTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a section first!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sectionId = (String) sectionModel.getValueAt(row, 0);
        String currentInstructor = (String) sectionModel.getValueAt(row, 2);
        String courseInfo = (String) sectionModel.getValueAt(row, 1);

        var instructors = adminService.getAllInstructors();
        JComboBox<String> instructorBox = new JComboBox<>();
        for (var i : instructors) {
            instructorBox.addItem(i.userId() + " (" + i.department() + ")");
        }

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel infoLabel = new JLabel("<html><b>Section:</b> " + sectionId +
                "<br><b>Course:</b> " + courseInfo +
                "<br><b>Current Instructor:</b> " + currentInstructor + "</html>");
        panel.add(infoLabel, BorderLayout.NORTH);

        JPanel selectPanel = new JPanel(new BorderLayout(10, 0));
        selectPanel.add(new JLabel("New Instructor:"), BorderLayout.WEST);
        selectPanel.add(instructorBox, BorderLayout.CENTER);
        panel.add(selectPanel, BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(this, panel, "Assign Instructor to Section",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {

            int iIndex = instructorBox.getSelectedIndex();
            if (iIndex == -1) {
                JOptionPane.showMessageDialog(this, "Please select an instructor!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String newInstructorId = instructors.get(iIndex).userId();
            ServiceResult<String> result = adminService.assignInstructor(sectionId, newInstructorId);

            JOptionPane.showMessageDialog(this, result.getMessage(),
                    result.isSuccess() ? "Success" : "Error",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (result.isSuccess()) loadSections();
        }
    }

    // ==================== DELETE SECTION ====================
    private void deleteSelectedSection() {
        int row = sectionTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a section to delete!");
            return;
        }

        String sectionId = (String) sectionModel.getValueAt(row, 0);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete section: " + sectionId + "?");

        if (confirm == JOptionPane.YES_OPTION) {
            ServiceResult<String> result = adminService.deleteSection(sectionId);
            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadSections();
        }
    }

    // ==================== SYSTEM SETTINGS TAB ====================
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ==================== MAINTENANCE MODE ====================
        JLabel maintenanceLabel = new JLabel("Maintenance Mode:");
        maintenanceLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        JToggleButton maintenanceToggle = new JToggleButton("OFF");
        maintenanceToggle.setFont(new Font("SansSerif", Font.BOLD, 14));
        maintenanceToggle.setSelected(adminService.getMaintenanceMode());
        maintenanceToggle.setText(maintenanceToggle.isSelected() ? "ON" : "OFF");
        maintenanceToggle.setBackground(maintenanceToggle.isSelected() ? Color.ORANGE : Color.GREEN);

        maintenanceToggle.addActionListener(e -> {
            boolean enable = maintenanceToggle.isSelected();
            ServiceResult<Boolean> sr = adminService.toggleMaintenanceMode(enable);

            if (sr.isSuccess()) {
                maintenanceToggle.setText(enable ? "ON" : "OFF");
                maintenanceToggle.setBackground(enable ? Color.ORANGE : Color.GREEN);
                maintenanceBanner.setVisible(enable);
                JOptionPane.showMessageDialog(this, sr.getMessage());
            } else {
                JOptionPane.showMessageDialog(this, sr.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                maintenanceToggle.setSelected(!enable);
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(maintenanceLabel, gbc);
        gbc.gridx = 1;
        panel.add(maintenanceToggle, gbc);

        // ==================== DROP DEADLINE ====================

        // ==================== ADD/DROP DEADLINE ====================
        JLabel addDropLabel = new JLabel("Add/Drop Deadline:");
        addDropLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        JButton changeAddDropBtn = new JButton("📅 Change Add/Drop Deadline");
        changeAddDropBtn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        changeAddDropBtn.addActionListener(e -> showChangeDeadlineDialog("drop_or_add_deadline", "Add/Drop Deadline"));

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(addDropLabel, gbc);
        gbc.gridx = 1;
        panel.add(changeAddDropBtn, gbc);

        return panel;
    }

    // ==================== CHANGE DEADLINE DIALOG ====================
    private void showChangeDeadlineDialog(String settingKey, String displayName) {
        // Get current deadline
        String currentDeadline = adminService.getSettingValue(settingKey);

        JLabel infoLabel = new JLabel("Current " + displayName + ": " + currentDeadline);
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        JLabel instructionLabel = new JLabel("Enter new date (YYYY-MM-DD):");
        JTextField dateField = new JTextField(currentDeadline, 15);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(infoLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(instructionLabel);
        panel.add(dateField);

        if (JOptionPane.showConfirmDialog(this, panel, "Change " + displayName,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {

            String newDate = dateField.getText().trim();

            // ✅ VALIDATE DATE FORMAT
            if (!newDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                JOptionPane.showMessageDialog(this, "Invalid date format! Use YYYY-MM-DD", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            ServiceResult<String> result = adminService.updateSetting(settingKey, newDate);
            JOptionPane.showMessageDialog(this, result.getMessage(),
                    result.isSuccess() ? "Success" : "Error",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== HELPER METHOD ====================
    private JPanel createLabeledRow(String labelText, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(150, 25));
        row.add(label, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        return row;
    }
    // ==================== FIXED ADD SECTION DIALOG ====================

    // ✅ HELPER METHOD TO CREATE CLEAN ROWS

    // ==================== ASSIGN INSTRUCTOR FUNCTIONALITY ====================
}