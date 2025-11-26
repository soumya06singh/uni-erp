package edu.univ.erp.ui;

import edu.univ.erp.domain.ServiceResult;
import edu.univ.erp.service.AdminService;
import edu.univ.erp.service.AdminService.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Vector;

public class AdminDashboard extends JFrame {
    private final String userId;
    private final String username;
    private final AdminService adminService;

    private JTable userTable;              // <-- this is the table we must use everywhere
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
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

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
            public boolean isCellEditable(int row, int column) { return false; }
        };
        courseModel.setColumnIdentifiers(new Object[]{"Course ID", "Code", "Name", "Credits", "Description"});

        JTable courseTable = new JTable(courseModel);
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
        // Similar to add but pre-fill with selected course data
        JOptionPane.showMessageDialog(this, "Edit feature: Select a course and modify its details");
    }

    private void deleteSelectedCourse() {
        // Implementation similar to deleteSelectedUser
        JOptionPane.showMessageDialog(this, "Delete course functionality");
    }

    // ==================== SECTION MANAGEMENT TAB ====================
    private JPanel createSectionManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton addBtn = new JButton("➕ Add Section");
        JButton assignBtn = new JButton("👤 Assign Instructor");
        JButton deleteBtn = new JButton("❌ Delete Selected");
        JButton refreshBtn = new JButton("🔄 Refresh");

        addBtn.addActionListener(e -> showAddSectionDialog());
        assignBtn.addActionListener(e -> showAssignInstructorDialog());
        deleteBtn.addActionListener(e -> deleteSelectedSection());
        refreshBtn.addActionListener(e -> loadSections());

        buttonPanel.add(addBtn);
        buttonPanel.add(assignBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);

        // Table
        sectionModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        sectionModel.setColumnIdentifiers(new Object[]{
                "Section ID", "Course", "Instructor", "Semester", "Year",
                "Day", "Time", "Room", "Capacity", "Enrolled"
        });

        JTable sectionTable = new JTable(sectionModel);
        sectionTable.setRowHeight(25);
        JScrollPane scroll = new JScrollPane(sectionTable);
        scroll.setBorder(BorderFactory.createTitledBorder("All Sections"));

        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

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

    private void showAddSectionDialog() {
        JOptionPane.showMessageDialog(this, "Add section dialog with course/instructor selection");
    }

    private void showAssignInstructorDialog() {
        JOptionPane.showMessageDialog(this, "Assign instructor to selected section");
    }

    private void deleteSelectedSection() {
        JOptionPane.showMessageDialog(this, "Delete selected section");
    }

    // ==================== SYSTEM SETTINGS TAB ====================
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

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

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(maintenanceLabel, gbc);
        gbc.gridx = 1;
        panel.add(maintenanceToggle, gbc);

        return panel;
    }

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
        JTextField programField = new JTextField(15);
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 4, 1));

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);
        panel.add(new JLabel("Roll Number:"));
        panel.add(rollNoField);
        panel.add(new JLabel("Program:"));
        panel.add(programField);
        panel.add(new JLabel("Year of Study:"));
        panel.add(yearSpinner);

        if (JOptionPane.showConfirmDialog(this, panel, "Add Student",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {

            ServiceResult<String> result = adminService.addStudent(
                    usernameField.getText().trim(),
                    new String(passwordField.getPassword()),
                    rollNoField.getText().trim(),
                    programField.getText().trim(),
                    (int) yearSpinner.getValue()
            );

            JOptionPane.showMessageDialog(this, result.getMessage());
            loadUsers();
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
        JTextField deptField = new JTextField(15);
        JTextField desigField = new JTextField(15);
        JTextField roomField = new JTextField(15);

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);
        panel.add(new JLabel("Department:"));
        panel.add(deptField);
        panel.add(new JLabel("Designation:"));
        panel.add(desigField);
        panel.add(new JLabel("Room:"));
        panel.add(roomField);

        if (JOptionPane.showConfirmDialog(this, panel, "Add Instructor",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {

            ServiceResult<String> result = adminService.addInstructor(
                    usernameField.getText().trim(),
                    new String(passwordField.getPassword()),
                    deptField.getText(),
                    desigField.getText(),
                    roomField.getText()
            );

            JOptionPane.showMessageDialog(this, result.getMessage());
            loadUsers();
        }
    }



}