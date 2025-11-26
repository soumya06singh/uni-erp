package edu.univ.erp.ui;

import edu.univ.erp.domain.ServiceResult;
import edu.univ.erp.service.AdminService;
import edu.univ.erp.service.AdminService.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AdminDashboard extends JFrame {
    private final String userId;
    private final String username;
    private final AdminService adminService;

    // --- Modern Theme Colors ---
    private static final Color BG = new Color(245, 247, 250);
    private static final Color ACCENT = new Color(0, 180, 180);
    private static final Color ACCENT_HOVER = new Color(0, 150, 150);
    private static final Color ACCENT_DARK = new Color(28, 160, 157);
    private static final Color MUTED = new Color(110, 110, 110);
    private static final Color SELECTION_COLOR = new Color(225, 252, 251);

    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 20);
    private static final Font HEADER_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font NAV_FONT = new Font("Segoe UI", Font.PLAIN, 15);
    private static final Font NAV_FONT_SELECTED = new Font("Segoe UI", Font.BOLD, 15);

    private CardLayout cardLayout;
    private JPanel mainContentPanel;
    private JLabel lblPageTitle;
    private JLabel maintenanceBanner;
    private final List<NavButton> navButtons = new ArrayList<>();

    private static final String VIEW_USERS = "USERS";
    private static final String VIEW_COURSES = "COURSES";
    private static final String VIEW_SECTIONS = "SECTIONS";
    private static final String VIEW_SETTINGS = "SETTINGS";

    private JTable userTable;
    private DefaultTableModel userModel;
    private JTable courseTable;
    private DefaultTableModel courseModel;
    private JTable sectionTable;
    private DefaultTableModel sectionModel;

    public AdminDashboard(String userId, String username) {
        super("Admin Dashboard - " + username);
        this.userId = userId;
        this.username = username;
        this.adminService = new AdminService();

        initUI();
        loadAllData();
    }

    private void initUI() {
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- SIDEBAR ---
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Color.WHITE);
        sidebar.setPreferredSize(new Dimension(280, 800));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(230, 230, 230)));

        JPanel sideHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 25));
        sideHeader.setBackground(Color.WHITE);
        sideHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        sideHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JLabel appTitle = new JLabel("Admin Portal");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        appTitle.setForeground(ACCENT);
        sideHeader.add(appTitle);
        sidebar.add(sideHeader);

        sidebar.add(Box.createVerticalStrut(20));

        addNavButton(sidebar, "User Management", VIEW_USERS);
        addNavButton(sidebar, "Course Management", VIEW_COURSES);
        addNavButton(sidebar, "Section Management", VIEW_SECTIONS);
        addNavButton(sidebar, "System Settings", VIEW_SETTINGS);

        sidebar.add(Box.createVerticalGlue());

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFont(HEADER_FONT);
        logoutBtn.setForeground(new Color(200, 50, 50));
        logoutBtn.setBackground(Color.WHITE);
        logoutBtn.setBorder(new EmptyBorder(15, 25, 15, 0));
        logoutBtn.setFocusPainted(false);
        logoutBtn.setContentAreaFilled(false);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.setHorizontalAlignment(SwingConstants.LEFT);
        logoutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        logoutBtn.addActionListener(e -> logout());

        sidebar.add(logoutBtn);
        sidebar.add(Box.createVerticalStrut(20));

        add(sidebar, BorderLayout.WEST);

        // --- MAIN CONTENT ---
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setBackground(BG);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG);
        topBar.setBorder(new EmptyBorder(20, 30, 10, 30));

        lblPageTitle = new JLabel("User Management");
        lblPageTitle.setFont(TITLE_FONT);
        lblPageTitle.setForeground(Color.DARK_GRAY);

        maintenanceBanner = new JLabel("⚠ MAINTENANCE MODE ACTIVE");
        maintenanceBanner.setFont(new Font("Segoe UI", Font.BOLD, 12));
        maintenanceBanner.setForeground(new Color(200, 50, 50));
        maintenanceBanner.setVisible(false);

        topBar.add(lblPageTitle, BorderLayout.WEST);
        topBar.add(maintenanceBanner, BorderLayout.EAST);
        contentWrapper.add(topBar, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        mainContentPanel = new JPanel(cardLayout);
        mainContentPanel.setOpaque(false);
        mainContentPanel.setBorder(new EmptyBorder(10, 30, 30, 30));

        mainContentPanel.add(createUserPanel(), VIEW_USERS);
        mainContentPanel.add(createCoursePanel(), VIEW_COURSES);
        mainContentPanel.add(createSectionPanel(), VIEW_SECTIONS);
        mainContentPanel.add(createSettingsPanel(), VIEW_SETTINGS);

        contentWrapper.add(mainContentPanel, BorderLayout.CENTER);
        add(contentWrapper, BorderLayout.CENTER);

        checkMaintenanceStatus();
        if (!navButtons.isEmpty()) setSelectedNav(navButtons.get(0));
    }

    private void addNavButton(JPanel sidebar, String text, String viewName) {
        NavButton btn = new NavButton(text, viewName);
        btn.addActionListener(e -> {
            cardLayout.show(mainContentPanel, viewName);
            lblPageTitle.setText(text);
            setSelectedNav(btn);
        });
        sidebar.add(btn);
        navButtons.add(btn);
    }

    private void setSelectedNav(NavButton selected) {
        for (NavButton btn : navButtons) btn.setActive(btn == selected);
    }

    // ==================== HELPER: CUSTOM DIALOG ====================
    private void showCustomDialog(String title, JPanel content, Consumer<Boolean> onConfirm) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(Color.WHITE);
        dialog.setSize(450, 550);
        dialog.setLocationRelativeTo(this);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 15));
        header.setBackground(BG);
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(ACCENT_DARK);
        header.add(lblTitle);
        dialog.add(header, BorderLayout.NORTH);

        content.setBorder(new EmptyBorder(20, 30, 20, 30));
        content.setBackground(Color.WHITE);
        dialog.add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        footer.setBackground(Color.WHITE);

        JButton btnCancel = new JButton("Cancel");
        btnCancel.setFont(HEADER_FONT);
        btnCancel.setForeground(MUTED);
        btnCancel.setBackground(Color.WHITE);
        btnCancel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        btnCancel.setPreferredSize(new Dimension(90, 34));
        btnCancel.setFocusPainted(false);
        btnCancel.addActionListener(e -> dialog.dispose());

        JButton btnSave = new PillButton("Save");
        btnSave.setPreferredSize(new Dimension(90, 34));
        btnSave.addActionListener(e -> {
            dialog.dispose();
            onConfirm.accept(true);
        });

        footer.add(btnCancel);
        footer.add(btnSave);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.pack();
        if(dialog.getWidth() < 450) dialog.setSize(450, dialog.getHeight());
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel createFormRow(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBackground(Color.WHITE);
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(80, 80, 80));
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        p.setBorder(new EmptyBorder(0, 0, 10, 0));
        return p;
    }

    private String cleanTime(String t) {
        if (t == null) return "";
        if (t.matches("^\\d{2}:\\d{2}$")) return t;
        if (t.matches("^\\d{2}:\\d{2}:\\d{2}$")) return t.substring(0, 5);
        try {
            return java.time.LocalTime.parse(t).toString().substring(0, 5);
        } catch (Exception e) {
            return t;
        }
    }

    // ==================== PANELS & LOGIC ====================

    private JPanel createUserPanel() {
        return createCard(panel -> {
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
            toolbar.setOpaque(false);
            toolbar.setBorder(new EmptyBorder(10, 0, 0, 0));

            JButton btnStudent = new PillButton("Add Student");
            JButton btnInstr = new PillButton("Add Instructor");
            JButton btnAdmin = new PillButton("Add Admin");
            JButton btnDelete = new PillButton("Delete Selected");
            btnDelete.setBackground(new Color(220, 60, 60));
            JButton btnRefresh = new PillButton("Refresh");

            btnStudent.addActionListener(e -> showAddStudentDialog());
            btnInstr.addActionListener(e -> showAddInstructorDialog());
            btnAdmin.addActionListener(e -> showAddAdminDialog());
            btnDelete.addActionListener(e -> deleteSelectedUser());
            btnRefresh.addActionListener(e -> loadUsers());

            toolbar.add(btnStudent);
            toolbar.add(btnInstr);
            toolbar.add(btnAdmin);
            toolbar.add(btnDelete);
            toolbar.add(btnRefresh);

            userModel = new DefaultTableModel(new Object[]{"User ID", "Username", "Role", "Status", "Roll No/Dept"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            userTable = new JTable(userModel);
            styleTable(userTable);

            panel.add(createTableScroll(userTable), BorderLayout.CENTER);
            panel.add(toolbar, BorderLayout.SOUTH);
        });
    }

    private JPanel createCoursePanel() {
        return createCard(panel -> {
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
            toolbar.setOpaque(false);
            toolbar.setBorder(new EmptyBorder(10, 0, 0, 0));

            JButton btnAdd = new PillButton("Add Course");
            JButton btnEdit = new PillButton("Edit Selected");
            JButton btnDelete = new PillButton("Delete Selected");
            btnDelete.setBackground(new Color(220, 60, 60));
            JButton btnRefresh = new PillButton("Refresh");

            btnAdd.addActionListener(e -> showAddCourseDialog());
            btnEdit.addActionListener(e -> showEditCourseDialog());
            btnDelete.addActionListener(e -> deleteSelectedCourse());
            btnRefresh.addActionListener(e -> loadCourses());

            toolbar.add(btnAdd);
            toolbar.add(btnEdit);
            toolbar.add(btnDelete);
            toolbar.add(btnRefresh);

            courseModel = new DefaultTableModel(new Object[]{"Course ID", "Code", "Name", "Credits", "Description"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            courseTable = new JTable(courseModel);
            styleTable(courseTable);

            panel.add(createTableScroll(courseTable), BorderLayout.CENTER);
            panel.add(toolbar, BorderLayout.SOUTH);
        });
    }

    private JPanel createSectionPanel() {
        return createCard(panel -> {
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
            toolbar.setOpaque(false);
            toolbar.setBorder(new EmptyBorder(10, 0, 0, 0));

            JButton btnAdd = new PillButton("Add Section");
            JButton btnEdit = new PillButton("Edit Selected");
            JButton btnAssign = new PillButton("Assign Instructor");
            JButton btnDelete = new PillButton("Delete Selected");
            btnDelete.setBackground(new Color(220, 60, 60));
            JButton btnRefresh = new PillButton("Refresh");

            btnAdd.addActionListener(e -> showAddSectionDialog());
            btnEdit.addActionListener(e -> showEditSectionDialog());
            btnAssign.addActionListener(e -> showAssignInstructorDialog());
            btnDelete.addActionListener(e -> deleteSelectedSection());
            btnRefresh.addActionListener(e -> loadSections());

            toolbar.add(btnAdd);
            toolbar.add(btnEdit);
            toolbar.add(btnAssign);
            toolbar.add(btnDelete);
            toolbar.add(btnRefresh);

            sectionModel = new DefaultTableModel(new Object[]{
                    "Section ID", "Course", "Instructor", "Semester", "Year",
                    "Day", "Time", "Room", "Capacity", "Enrolled"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            sectionTable = new JTable(sectionModel);
            styleTable(sectionTable);

            panel.add(createTableScroll(sectionTable), BorderLayout.CENTER);
            panel.add(toolbar, BorderLayout.SOUTH);
        });
    }

    private JPanel createSettingsPanel() {
        return createCard(panel -> {
            JPanel content = new JPanel(new GridBagLayout());
            content.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(15, 15, 15, 15);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0; gbc.gridy = 0;

            JLabel lblMaint = new JLabel("Maintenance Mode:");
            lblMaint.setFont(new Font("Segoe UI", Font.BOLD, 16));

            JToggleButton tglMaint = new JToggleButton("OFF");
            tglMaint.setFont(new Font("Segoe UI", Font.BOLD, 14));
            tglMaint.setPreferredSize(new Dimension(100, 40));
            styleToggle(tglMaint, adminService.getMaintenanceMode());

            tglMaint.addActionListener(e -> {
                boolean enable = tglMaint.isSelected();
                ServiceResult<Boolean> sr = adminService.toggleMaintenanceMode(enable);
                if (sr.isSuccess()) {
                    styleToggle(tglMaint, enable);
                    checkMaintenanceStatus();
                    JOptionPane.showMessageDialog(this, sr.getMessage());
                } else {
                    tglMaint.setSelected(!enable);
                }
            });

            content.add(lblMaint, gbc);
            gbc.gridx = 1;
            content.add(tglMaint, gbc);

            gbc.gridx = 0; gbc.gridy++;
            JLabel lblDeadline = new JLabel("Add/Drop Deadline:");
            lblDeadline.setFont(new Font("Segoe UI", Font.BOLD, 16));

            JButton btnDeadline = new PillButton("Change Deadline");
            btnDeadline.addActionListener(e -> showChangeDeadlineDialog("drop_or_add_deadline", "Add/Drop Deadline"));

            content.add(lblDeadline, gbc);
            gbc.gridx = 1;
            content.add(btnDeadline, gbc);

            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
            wrapper.setOpaque(false);
            wrapper.add(content);

            panel.add(wrapper, BorderLayout.CENTER);
        });
    }

    // -------------------- DIALOG IMPLEMENTATIONS --------------------

    private void showAddAdminDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Username", usernameField));
        panel.add(createFormRow("Password", passwordField));

        showCustomDialog("Add Administrator", panel, (ok) -> {
            ServiceResult<String> result = adminService.addAdmin(usernameField.getText().trim(), new String(passwordField.getPassword()));
            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadUsers();
        });
    }

    private void showAddStudentDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);
        JTextField rollNoField = new JTextField(15);
        String[] programs = {"Computer Science", "Electrical Engineering", "Mechanical Engineering", "Civil Engineering", "Mathematics"};
        JComboBox<String> programCombo = new JComboBox<>(programs);
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 4, 1));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Username", usernameField));
        panel.add(createFormRow("Password", passwordField));
        panel.add(createFormRow("Roll No", rollNoField));
        panel.add(createFormRow("Program", programCombo));
        panel.add(createFormRow("Year", yearSpinner));

        showCustomDialog("Add Student", panel, (ok) -> {
            ServiceResult<String> result = adminService.addStudent(
                    usernameField.getText().trim(), new String(passwordField.getPassword()),
                    rollNoField.getText().trim(), (String) programCombo.getSelectedItem(), (int) yearSpinner.getValue());
            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadUsers();
        });
    }

    private void showAddInstructorDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);
        JComboBox<String> deptDropdown = new JComboBox<>(new String[]{"Computer Science", "Biology", "ECE", "Mathematics", "Physics"});
        JTextField desigField = new JTextField(15);
        JTextField roomField = new JTextField(10);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Username", usernameField));
        panel.add(createFormRow("Password", passwordField));
        panel.add(createFormRow("Department", deptDropdown));
        panel.add(createFormRow("Designation", desigField));
        panel.add(createFormRow("Office", roomField));

        showCustomDialog("Add Instructor", panel, (ok) -> {
            ServiceResult<String> result = adminService.addInstructor(
                    usernameField.getText().trim(), new String(passwordField.getPassword()),
                    (String) deptDropdown.getSelectedItem(), desigField.getText().trim(), roomField.getText().trim());
            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadUsers();
        });
    }

    private void showAddCourseDialog() {
        JTextField codeField = new JTextField();
        JTextField nameField = new JTextField();
        JSpinner creditSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 6, 1));
        JTextArea descArea = new JTextArea(3, 20);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Code", codeField));
        panel.add(createFormRow("Name", nameField));
        panel.add(createFormRow("Credits", creditSpinner));
        panel.add(createFormRow("Description", new JScrollPane(descArea)));

        showCustomDialog("Add Course", panel, (ok) -> {
            ServiceResult<Integer> sr = adminService.addCourse(codeField.getText(), nameField.getText(), (int)creditSpinner.getValue(), descArea.getText());
            JOptionPane.showMessageDialog(this, sr.getMessage());
            if(sr.isSuccess()) loadCourses();
        });
    }

    private void showEditCourseDialog() {
        int row = courseTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a course first."); return; }
        int cid = (int) courseModel.getValueAt(row, 0);

        JTextField codeField = new JTextField((String)courseModel.getValueAt(row, 1));
        JTextField nameField = new JTextField((String)courseModel.getValueAt(row, 2));
        JSpinner creditSpinner = new JSpinner(new SpinnerNumberModel((int)courseModel.getValueAt(row, 3), 1, 6, 1));
        JTextArea descArea = new JTextArea((String)courseModel.getValueAt(row, 4), 3, 20);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Code", codeField));
        panel.add(createFormRow("Name", nameField));
        panel.add(createFormRow("Credits", creditSpinner));
        panel.add(createFormRow("Description", new JScrollPane(descArea)));

        showCustomDialog("Edit Course", panel, (ok) -> {
            ServiceResult<String> sr = adminService.updateCourse(cid, codeField.getText(), nameField.getText(), (int)creditSpinner.getValue(), descArea.getText());
            JOptionPane.showMessageDialog(this, sr.getMessage());
            if(sr.isSuccess()) loadCourses();
        });
    }

    private void showAddSectionDialog() {
        var courses = adminService.getAllCourses();
        var instructors = adminService.getAllInstructors();

        JComboBox<String> courseBox = new JComboBox<>();
        for (var c : courses) courseBox.addItem(c.courseCode() + " - " + c.courseName());

        JComboBox<String> instructorBox = new JComboBox<>();
        instructorBox.addItem("--- No Instructor ---");
        for (var i : instructors) instructorBox.addItem(i.userId() + " (" + i.department() + ")");

        JComboBox<String> semesterBox = new JComboBox<>(new String[] {"Spring", "Fall", "Summer"});
        JComboBox<String> dayBox = new JComboBox<>(new String[] {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"});
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(2024, 2000, 2100, 1));
        JSpinner.NumberEditor yearEditor = new JSpinner.NumberEditor(yearSpinner, "#");
        yearSpinner.setEditor(yearEditor);

        JTextField startField = new JTextField(10);
        JTextField endField = new JTextField(10);
        JTextField roomField = new JTextField(10);
        JSpinner capSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 300, 1));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Course", courseBox));
        panel.add(createFormRow("Instructor", instructorBox));
        panel.add(createFormRow("Semester", semesterBox));
        panel.add(createFormRow("Year", yearSpinner));
        panel.add(createFormRow("Day", dayBox));
        panel.add(createFormRow("Start (HH:MM)", startField));
        panel.add(createFormRow("End (HH:MM)", endField));
        panel.add(createFormRow("Room", roomField));
        panel.add(createFormRow("Capacity", capSpinner));

        showCustomDialog("Add Section", panel, (ok) -> {
            int cIndex = courseBox.getSelectedIndex();
            int iIndex = instructorBox.getSelectedIndex();
            if(cIndex == -1) return;

            String instructorId = null;
            if (iIndex > 0) instructorId = instructors.get(iIndex - 1).userId();

            var result = adminService.addSection(
                    courses.get(cIndex).courseId(), instructorId, semesterBox.getSelectedItem().toString(),
                    (int) yearSpinner.getValue(), dayBox.getSelectedItem().toString(),
                    startField.getText().trim(), endField.getText().trim(),
                    roomField.getText().trim(), (int) capSpinner.getValue()
            );
            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadSections();
        });
    }

    // --- FIXED EDIT SECTION LOGIC ---
    private void showEditSectionDialog() {
        int row = sectionTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select section first."); return; }

        // 1. Get values from table
        String sectionId = (String) sectionModel.getValueAt(row, 0);
        String currentInstructor = (String) sectionModel.getValueAt(row, 2);
        String currentSemester = (String) sectionModel.getValueAt(row, 3);
        int currentYear = (int) sectionModel.getValueAt(row, 4);
        String currentDay = (String) sectionModel.getValueAt(row, 5);
        String timeRange = (String) sectionModel.getValueAt(row, 6); // "09:00 - 10:30"
        String currentRoom = (String) sectionModel.getValueAt(row, 7);
        int currentCapacity = (int) sectionModel.getValueAt(row, 8);

        // 2. Parse Time
        String[] times = timeRange.split(" - ");
        String startTime = times.length > 0 ? cleanTime(times[0].trim()) : "09:00";
        String endTime = times.length > 1 ? cleanTime(times[1].trim()) : "10:00";

        // 3. Prepare Components
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

        // Populate Instructors and select current
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

        // 4. Build Panel
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Instructor", instructorBox));
        panel.add(createFormRow("Semester", semesterBox));
        panel.add(createFormRow("Year", yearSpinner));
        panel.add(createFormRow("Day", dayBox));
        panel.add(createFormRow("Start (HH:MM)", startField));
        panel.add(createFormRow("End (HH:MM)", endField));
        panel.add(createFormRow("Room", roomField));
        panel.add(createFormRow("Capacity", capSpinner));

        // 5. Show Dialog
        showCustomDialog("Edit Section", panel, (ok) -> {
            if (!startField.getText().matches("\\d{2}:\\d{2}") || !endField.getText().matches("\\d{2}:\\d{2}")) {
                JOptionPane.showMessageDialog(this, "Time must be in HH:MM format!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String instructorId = null;
            int iIndex = instructorBox.getSelectedIndex();
            if (iIndex > 0) {
                instructorId = instructors.get(iIndex - 1).userId();
            }

            ServiceResult<String> result = adminService.updateSection(
                    sectionId, instructorId, semesterBox.getSelectedItem().toString(),
                    (int) yearSpinner.getValue(), dayBox.getSelectedItem().toString(),
                    startField.getText().trim(), endField.getText().trim(),
                    roomField.getText().trim(), (int) capSpinner.getValue()
            );

            JOptionPane.showMessageDialog(this, result.getMessage());
            if (result.isSuccess()) loadSections();
        });
    }

    private void showAssignInstructorDialog() {
        int row = sectionTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select section first."); return; }
        String sectionId = (String) sectionModel.getValueAt(row, 0);

        var instructors = adminService.getAllInstructors();
        JComboBox<String> instructorBox = new JComboBox<>();
        for (var i : instructors) instructorBox.addItem(i.userId() + " (" + i.department() + ")");

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Assign new instructor to " + sectionId));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createFormRow("Instructor", instructorBox));

        showCustomDialog("Assign Instructor", panel, (ok) -> {
            int idx = instructorBox.getSelectedIndex();
            if(idx >= 0) {
                String newId = instructors.get(idx).userId();
                ServiceResult<String> res = adminService.assignInstructor(sectionId, newId);
                JOptionPane.showMessageDialog(this, res.getMessage());
                if(res.isSuccess()) loadSections();
            }
        });
    }

    private void deleteSelectedUser() {
        int row = userTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        String uid = (String) userModel.getValueAt(row, 0);
        String role = (String) userModel.getValueAt(row, 2);
        if (JOptionPane.showConfirmDialog(this, "Delete user " + uid + "?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            ServiceResult<String> sr = adminService.deleteUser(uid, role);
            JOptionPane.showMessageDialog(this, sr.getMessage());
            if (sr.isSuccess()) loadUsers();
        }
    }

    private void deleteSelectedCourse() {
        int row = courseTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a course first."); return; }
        int cid = (int) courseModel.getValueAt(row, 0);
        if (JOptionPane.showConfirmDialog(this, "Delete course?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            ServiceResult<String> sr = adminService.deleteCourse(cid);
            JOptionPane.showMessageDialog(this, sr.getMessage());
            if(sr.isSuccess()) loadCourses();
        }
    }

    private void deleteSelectedSection() {
        int row = sectionTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select section first."); return; }
        String sid = (String) sectionModel.getValueAt(row, 0);
        if (JOptionPane.showConfirmDialog(this, "Delete section " + sid + "?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            ServiceResult<String> sr = adminService.deleteSection(sid);
            JOptionPane.showMessageDialog(this, sr.getMessage());
            if(sr.isSuccess()) loadSections();
        }
    }

    private void showChangeDeadlineDialog(String key, String title) {
        JTextField dateField = new JTextField(15);
        JPanel panel = new JPanel();
        panel.add(createFormRow("New Date (YYYY-MM-DD)", dateField));

        showCustomDialog("Change Deadline", panel, (ok) -> {
            ServiceResult<String> sr = adminService.updateSetting(key, dateField.getText());
            JOptionPane.showMessageDialog(this, sr.getMessage());
        });
    }

    // -------------------- UI HELPERS --------------------

    private JPanel createCard(ContentBuilder builder) {
        JPanel card = new JPanel(new BorderLayout(20, 20));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230), 1),
                new EmptyBorder(20, 20, 20, 20)
        ));
        builder.build(card);
        return card;
    }

    private JScrollPane createTableScroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(230, 230, 230)));
        return scroll;
    }

    private void styleTable(JTable t) {
        t.setRowHeight(35);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);

        t.setSelectionBackground(SELECTION_COLOR);
        t.setSelectionForeground(Color.BLACK);

        t.setFont(HEADER_FONT);

        JTableHeader hdr = t.getTableHeader();
        hdr.setBackground(new Color(248, 249, 250));
        hdr.setForeground(MUTED);
        hdr.setFont(new Font("Segoe UI", Font.BOLD, 12));
        hdr.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230)));
        hdr.setPreferredSize(new Dimension(0, 40));
        ((DefaultTableCellRenderer)hdr.getDefaultRenderer()).setHorizontalAlignment(JLabel.LEFT);
    }

    private void styleToggle(JToggleButton btn, boolean isOn) {
        btn.setSelected(isOn);
        btn.setText(isOn ? "ON" : "OFF");
        btn.setBackground(isOn ? new Color(220, 60, 60) : new Color(60, 180, 100));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Logout?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            dispose();
            SwingUtilities.invokeLater(() -> MainApp.main(null));
        }
    }

    private void checkMaintenanceStatus() {
        boolean isOn = adminService.getMaintenanceMode();
        maintenanceBanner.setVisible(isOn);
    }

    // -------------------- DATA LOADING --------------------
    private void loadAllData() {
        loadUsers();
        loadCourses();
        loadSections();
        checkMaintenanceStatus();
    }

    private void loadUsers() {
        userModel.setRowCount(0);
        List<UserView> users = adminService.getAllUsers();
        for (UserView u : users) userModel.addRow(new Object[]{u.userId(), u.username(), u.role(), u.status(), u.specificId()});
    }

    private void loadCourses() {
        courseModel.setRowCount(0);
        List<CourseView> courses = adminService.getAllCourses();
        for (CourseView c : courses) courseModel.addRow(new Object[]{c.courseId(), c.courseCode(), c.courseName(), c.credits(), c.description()});
    }

    private void loadSections() {
        sectionModel.setRowCount(0);
        List<SectionView> sections = adminService.getAllSections();
        for (SectionView s : sections) sectionModel.addRow(new Object[]{
                s.sectionId(), s.courseCode(), s.instructorId(), s.semester(),
                s.year(), s.day(), s.startTime() + " - " + s.endTime(), s.room(),
                s.capacity(), s.enrolled()});
    }

    // -------------------- CUSTOM COMPONENTS --------------------

    interface ContentBuilder {
        void build(JPanel panel);
    }

    private static class NavButton extends JButton {
        private boolean isActive = false;
        private boolean isHovered = false;

        public NavButton(String text, String viewName) {
            super(text);
            setFont(NAV_FONT);
            setForeground(Color.DARK_GRAY);
            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(12, 25, 12, 10));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalAlignment(SwingConstants.LEFT);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
            });
        }

        public void setActive(boolean active) {
            this.isActive = active;
            setFont(active ? NAV_FONT_SELECTED : NAV_FONT);
            setForeground(active ? ACCENT_DARK : Color.DARK_GRAY);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isActive) {
                g2.setColor(SELECTION_COLOR);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ACCENT);
                g2.fillRect(0, 0, 5, getHeight());
            } else if (isHovered) {
                g2.setColor(new Color(248, 250, 250));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } else {
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

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
            setPreferredSize(new Dimension(130, 36));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { if(getBackground().equals(ACCENT)) setBackground(ACCENT_HOVER); }
                public void mouseExited(MouseEvent e) { if(getBackground().equals(ACCENT_HOVER)) setBackground(ACCENT); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdminDashboard("admin", "Admin").setVisible(true));
    }
}