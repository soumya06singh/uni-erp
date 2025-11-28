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

    //Color Scheme
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

        //SIDEBAR
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

        //MAIN CONTENT
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setBackground(BG);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG);
        topBar.setBorder(new EmptyBorder(20, 30, 10, 30));

        lblPageTitle = new JLabel("User Management");
        lblPageTitle.setFont(TITLE_FONT);
        lblPageTitle.setForeground(Color.DARK_GRAY);

        maintenanceBanner = new JLabel("MAINTENANCE MODE ACTIVE");
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

    //DIALOG BOX
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


            btnAssign.setPreferredSize(new Dimension(180, 36));  // Wider + Taller

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

    // SETTINGS PANEL
    private JPanel createSettingsPanel() {
        return createCard(panel -> {
            JPanel content = new JPanel(new GridBagLayout());
            content.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(15, 15, 15, 15);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;
            gbc.gridy = 0;

            JLabel lblMaint = new JLabel("Maintenance Mode:");
            lblMaint.setFont(new Font("Segoe UI", Font.BOLD, 16));

            JToggleButton tglMaint = new JToggleButton("OFF");
            tglMaint.setFont(new Font("Segoe UI", Font.BOLD, 14));
            tglMaint.setPreferredSize(new Dimension(100, 40));

            // ✅ FIX: Use colored backgrounds instead of white
            boolean currentMode = adminService.getMaintenanceMode();
            styleToggle(tglMaint, currentMode);

            tglMaint.addActionListener(e -> {
                boolean enable = tglMaint.isSelected();
                ServiceResult<Boolean> sr = adminService.toggleMaintenanceMode(enable);
                if (sr.isSuccess()) {
                    styleToggle(tglMaint, enable);
                    checkMaintenanceStatus();
                    JOptionPane.showMessageDialog(this, sr.getMessage());
                } else {
                    tglMaint.setSelected(!enable);
                    JOptionPane.showMessageDialog(this,
                            sr.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            });

            content.add(lblMaint, gbc);
            gbc.gridx = 1;
            content.add(tglMaint, gbc);

            gbc.gridx = 0;
            gbc.gridy++;
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


    private void styleToggle(JToggleButton btn, boolean isOn) {
        btn.setSelected(isOn);
        btn.setText(isOn ? "ON" : "OFF");

        if (isOn) {
            btn.setBackground(new Color(220, 60, 60));  // Red for ON (maintenance active)
            btn.setForeground(Color.WHITE);
        } else {
            btn.setBackground(new Color(60, 180, 100)); // Green for OFF (normal operation)
            btn.setForeground(Color.WHITE);
        }

        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
    }
    // DIALOG

    private void showAddAdminDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Username", usernameField));
        panel.add(createFormRow("Password", passwordField));

        showCustomDialog("Add Administrator", panel, (ok) -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "All fields are required!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (password.length() < 6) {
                JOptionPane.showMessageDialog(this,
                        "Password must be at least 6 characters.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            ServiceResult<String> result = adminService.addAdmin(username, password);

            JOptionPane.showMessageDialog(this,
                    result.getMessage(),
                    result.isSuccess() ? "Success" : "Error",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (result.isSuccess()) {
                loadUsers();
            }
        });
    }
    private void showAddStudentDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);
        JTextField rollNoField = new JTextField(15);

        String[] programs = {
                "Computer Science",
                "Electrical Engineering",
                "Mechanical Engineering",
                "Civil Engineering",
                "Mathematics"
        };
        JComboBox<String> programCombo = new JComboBox<>(programs);

        //Restrict year 1–4 only
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 4, 1));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Username", usernameField));
        panel.add(createFormRow("Password", passwordField));
        panel.add(createFormRow("Roll Number", rollNoField));
        panel.add(createFormRow("Program", programCombo));
        panel.add(createFormRow("Year of Study", yearSpinner));

        showCustomDialog("Add Student", panel, (ok) -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String rollNo = rollNoField.getText().trim();
            String program = (String) programCombo.getSelectedItem();
            int year = (int) yearSpinner.getValue();

            if (username.isEmpty() || password.isEmpty() || rollNo.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "All fields are required!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (password.length() < 6) {
                JOptionPane.showMessageDialog(this,
                        "Password must be at least 6 characters.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!rollNo.matches("^\\d{7}$")) {
                JOptionPane.showMessageDialog(this,
                        "Roll number must be exactly 7 digits (e.g. 2021001)",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (year < 1 || year > 4) {
                JOptionPane.showMessageDialog(this,
                        "Year of study must be between 1 and 4!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            ServiceResult<String> result = adminService.addStudent(
                    username, password, rollNo, program, year);

            JOptionPane.showMessageDialog(this,
                    result.getMessage(),
                    result.isSuccess() ? "Success" : "Error",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (result.isSuccess()) {
                loadUsers();
            }
        });
    }
    private void showAddInstructorDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);

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
        JTextField roomField = new JTextField(10);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Username", usernameField));
        panel.add(createFormRow("Password", passwordField));
        panel.add(createFormRow("Department", deptDropdown));
        panel.add(createFormRow("Designation", desigField));
        panel.add(createFormRow("Office Room (Max 10 chars)", roomField));

        showCustomDialog("Add Instructor", panel, (ok) -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String department = deptDropdown.getSelectedItem().toString();
            String designation = desigField.getText().trim();
            String office = roomField.getText().trim();

            if (username.isEmpty() || password.isEmpty() || designation.isEmpty() || office.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "All fields are required!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (password.length() < 6) {
                JOptionPane.showMessageDialog(this,
                        "Password must be at least 6 characters.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (office.length() > 10) {
                JOptionPane.showMessageDialog(this,
                        "Office Room must be 10 characters or fewer.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            ServiceResult<String> result = adminService.addInstructor(
                    username,
                    password,
                    department,
                    designation,
                    office
            );

            JOptionPane.showMessageDialog(this,
                    result.getMessage(),
                    result.isSuccess() ? "Success" : "Error",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (result.isSuccess()) {
                loadUsers();
            }
        });
    }
    //ADD COURSE
    private void showAddCourseDialog() {
        JTextField codeField = new JTextField();
        JTextField nameField = new JTextField();
        JSpinner creditSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 6, 1));
        JTextArea descArea = new JTextArea(3, 20);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Course Code", codeField));
        panel.add(createFormRow("Course Name", nameField));
        panel.add(createFormRow("Credits", creditSpinner));
        panel.add(createFormRow("Description (Optional)", new JScrollPane(descArea))); // ✅ NOW OPTIONAL

        showCustomDialog("Add Course", panel, (ok) -> {
            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            int credits = (int) creditSpinner.getValue();
            String description = descArea.getText().trim(); // ✅ Can be empty

            if (code.isEmpty() || name.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Course Code and Name are required!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (credits < 1 || credits > 6) {
                JOptionPane.showMessageDialog(this,
                        "Credits must be between 1 and 6!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            ServiceResult<Integer> sr = adminService.addCourse(code, name, credits, description);

            JOptionPane.showMessageDialog(this,
                    sr.getMessage(),
                    sr.isSuccess() ? "Success" : "Error",
                    sr.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (sr.isSuccess()) {
                loadCourses();
            }
        });
    }

    //EDIT COURSE
    private void showEditCourseDialog() {
        int row = courseTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a course to edit!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int cid = (int) courseModel.getValueAt(row, 0);

        JTextField codeField = new JTextField((String) courseModel.getValueAt(row, 1));
        JTextField nameField = new JTextField((String) courseModel.getValueAt(row, 2));
        JSpinner creditSpinner = new JSpinner(new SpinnerNumberModel((int) courseModel.getValueAt(row, 3), 1, 6, 1));

        String currentDesc = (String) courseModel.getValueAt(row, 4);
        JTextArea descArea = new JTextArea(currentDesc != null ? currentDesc : "", 3, 20);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Course Code", codeField));
        panel.add(createFormRow("Course Name", nameField));
        panel.add(createFormRow("Credits", creditSpinner));
        panel.add(createFormRow("Description (Optional)", new JScrollPane(descArea))); // ✅ NOW OPTIONAL

        showCustomDialog("Edit Course", panel, (ok) -> {
            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            int credits = (int) creditSpinner.getValue();
            String description = descArea.getText().trim(); // ✅ Can be empty

            // ✔ VALIDATIONS (Description removed)
            if (code.isEmpty() || name.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Course Code and Name are required!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (credits < 1 || credits > 6) {
                JOptionPane.showMessageDialog(this,
                        "Credits must be between 1 and 6!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            ServiceResult<String> sr = adminService.updateCourse(cid, code, name, credits, description);

            JOptionPane.showMessageDialog(this,
                    sr.getMessage(),
                    sr.isSuccess() ? "Success" : "Error",
                    sr.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (sr.isSuccess()) {
                loadCourses();
            }
        });
    }    //ADD SECTION
    private void showAddSectionDialog() {
        var courses = adminService.getAllCourses();
        var instructors = adminService.getAllInstructors();

        JComboBox<String> courseBox = new JComboBox<>();
        JComboBox<String> instructorBox = new JComboBox<>();
        JComboBox<String> semesterBox = new JComboBox<>(new String[] {"Spring", "Fall", "Summer", "Winter"});
        JComboBox<String> dayBox = new JComboBox<>(new String[] {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"});

        SpinnerNumberModel yearModel = new SpinnerNumberModel(2024, 2000, 2100, 1);
        JSpinner yearSpinner = new JSpinner(yearModel);
        JSpinner.NumberEditor yearEditor = new JSpinner.NumberEditor(yearSpinner, "#");
        yearSpinner.setEditor(yearEditor);

        JTextField startField = new JTextField(10);
        JTextField endField = new JTextField(10);
        JTextField roomField = new JTextField(10);
        JSpinner capSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 300, 1));

        for (var c : courses) courseBox.addItem(c.courseCode() + " - " + c.courseName());

        instructorBox.addItem("--- No Instructor (Assign Later) ---");
        for (var i : instructors) instructorBox.addItem(i.userId() + " (" + i.department() + ")");

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createFormRow("Course", courseBox));
        panel.add(createFormRow("Instructor", instructorBox));
        panel.add(createFormRow("Semester", semesterBox));
        panel.add(createFormRow("Year", yearSpinner));
        panel.add(createFormRow("Day", dayBox));
        panel.add(createFormRow("Start Time (HH:MM)", startField));
        panel.add(createFormRow("End Time (HH:MM)", endField));
        panel.add(createFormRow("Room (Optional)", roomField)); // ✅ NOW OPTIONAL
        panel.add(createFormRow("Capacity", capSpinner));

        showCustomDialog("Add Section", panel, (ok) -> {
            int cIndex = courseBox.getSelectedIndex();
            int iIndex = instructorBox.getSelectedIndex();

            if (cIndex == -1) {
                JOptionPane.showMessageDialog(this,
                        "Please select a course!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String room = roomField.getText().trim();

            String startTime = startField.getText().trim();
            String endTime = endField.getText().trim();

            if (!startTime.matches("\\d{2}:\\d{2}") || !endTime.matches("\\d{2}:\\d{2}")) {
                JOptionPane.showMessageDialog(this,
                        "Time must be in HH:MM format (e.g., 09:00)",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            int capacity = (int) capSpinner.getValue();
            if (capacity <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Capacity must be positive!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String instructorId = null;
            if (iIndex > 0) {
                instructorId = instructors.get(iIndex - 1).userId();
            }

            var result = adminService.addSection(
                    courses.get(cIndex).courseId(),
                    instructorId,
                    semesterBox.getSelectedItem().toString(),
                    (int) yearSpinner.getValue(),
                    dayBox.getSelectedItem().toString(),
                    startTime,
                    endTime,
                    room, // ✅ Can be empty now
                    capacity
            );

            JOptionPane.showMessageDialog(this,
                    result.getMessage(),
                    result.isSuccess() ? "Success" : "Error",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (result.isSuccess()) {
                loadSections();
            }
        });
    }

    // EDIT SECTION
    private void showEditSectionDialog() {
        int row = sectionTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a section to edit!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sectionId = (String) sectionModel.getValueAt(row, 0);
        String currentInstructor = (String) sectionModel.getValueAt(row, 2);
        String currentSemester = (String) sectionModel.getValueAt(row, 3);
        int currentYear = (int) sectionModel.getValueAt(row, 4);
        String currentDay = (String) sectionModel.getValueAt(row, 5);
        String timeRange = (String) sectionModel.getValueAt(row, 6);
        String currentRoom = (String) sectionModel.getValueAt(row, 7);
        int currentCapacity = (int) sectionModel.getValueAt(row, 8);
        int currentEnrolled = (int) sectionModel.getValueAt(row, 9);

        String[] times = timeRange.split(" - ");
        String startTime = times.length > 0 ? cleanTime(times[0].trim()) : "09:00";
        String endTime = times.length > 1 ? cleanTime(times[1].trim()) : "10:00";

        var instructors = adminService.getAllInstructors();
        JComboBox<String> instructorBox = new JComboBox<>();
        JComboBox<String> semesterBox = new JComboBox<>(new String[] {"Spring", "Fall", "Summer", "Winter"});
        JComboBox<String> dayBox = new JComboBox<>(new String[] {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"});

        SpinnerNumberModel yearModel = new SpinnerNumberModel(currentYear, 2000, 2100, 1);
        JSpinner yearSpinner = new JSpinner(yearModel);
        JSpinner.NumberEditor yearEditor = new JSpinner.NumberEditor(yearSpinner, "#");
        yearSpinner.setEditor(yearEditor);

        JTextField startField = new JTextField(startTime, 10);
        JTextField endField = new JTextField(endTime, 10);
        JTextField roomField = new JTextField(currentRoom != null ? currentRoom : "", 10);
        JSpinner capSpinner = new JSpinner(new SpinnerNumberModel(currentCapacity, 1, 300, 1));

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
        panel.add(createFormRow("Instructor", instructorBox));
        panel.add(createFormRow("Semester", semesterBox));
        panel.add(createFormRow("Year", yearSpinner));
        panel.add(createFormRow("Day", dayBox));
        panel.add(createFormRow("Start Time (HH:MM)", startField));
        panel.add(createFormRow("End Time (HH:MM)", endField));
        panel.add(createFormRow("Room (Optional)", roomField)); // ✅ NOW OPTIONAL
        panel.add(createFormRow("Capacity (Currently: " + currentEnrolled + " enrolled)", capSpinner));

        showCustomDialog("Edit Section", panel, (ok) -> {
            String newStartTime = startField.getText().trim();
            String newEndTime = endField.getText().trim();
            String newRoom = roomField.getText().trim(); // ✅ Can be empty
            int newCapacity = (int) capSpinner.getValue();


            if (!newStartTime.matches("\\d{2}:\\d{2}") || !newEndTime.matches("\\d{2}:\\d{2}")) {
                JOptionPane.showMessageDialog(this,
                        "Time must be in HH:MM format (e.g., 09:00)!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (newCapacity < currentEnrolled) {
                JOptionPane.showMessageDialog(this,
                        "Cannot reduce capacity below current enrollment!\n" +
                                "Currently enrolled: " + currentEnrolled + " students\n" +
                                "You tried to set capacity to: " + newCapacity,
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (newCapacity <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Capacity must be positive!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
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
                    newStartTime,
                    newEndTime,
                    newRoom, // ✅ Can be empty now
                    newCapacity
            );

            JOptionPane.showMessageDialog(this,
                    result.getMessage(),
                    result.isSuccess() ? "Success" : "Error",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (result.isSuccess()) {
                loadSections();
            }
        });
    }    private void showAssignInstructorDialog() {
        int row = sectionTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a section first!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sectionId = (String) sectionModel.getValueAt(row, 0);
        String courseInfo = (String) sectionModel.getValueAt(row, 1);
        String currentInstructor = (String) sectionModel.getValueAt(row, 2);

        var instructors = adminService.getAllInstructors();

        // Check if instructors are available
        if (instructors == null || instructors.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No instructors available in the system!\n" +
                            "Please add instructors first.",
                    "No Instructors",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JComboBox<String> instructorBox = new JComboBox<>();
        for (var i : instructors) {
            instructorBox.addItem(i.userId() + " (" + i.department() + ")");
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Info section
        JLabel infoLabel = new JLabel(
                "<html><b>Section:</b> " + sectionId + "<br>" +
                        "<b>Course:</b> " + courseInfo + "<br>" +
                        "<b>Current Instructor:</b> " + (currentInstructor == null || currentInstructor.isEmpty() ? "None" : currentInstructor) +
                        "</html>"
        );
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoLabel.setBorder(new EmptyBorder(0, 0, 15, 0));

        panel.add(infoLabel);
        panel.add(createFormRow("New Instructor", instructorBox));

        showCustomDialog("Assign Instructor to Section", panel, (ok) -> {
            int idx = instructorBox.getSelectedIndex();

            if (idx == -1) {
                JOptionPane.showMessageDialog(this,
                        "Please select an instructor!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String newId = instructors.get(idx).userId();

            if (newId.equals(currentInstructor)) {
                int proceed = JOptionPane.showConfirmDialog(this,
                        "This instructor is already assigned to this section.\n" +
                                "Do you want to proceed anyway?",
                        "Same Instructor",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (proceed != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            ServiceResult<String> res = adminService.assignInstructor(sectionId, newId);

            JOptionPane.showMessageDialog(this,
                    res.getMessage(),
                    res.isSuccess() ? "Success" : "Error",
                    res.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (res.isSuccess()) {
                loadSections();
            }
        });
    }
    //DELETE USER
    private void deleteSelectedUser() {
        int row = userTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a user to delete!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String uid = (String) userModel.getValueAt(row, 0);
        String username = (String) userModel.getValueAt(row, 1);
        String role = (String) userModel.getValueAt(row, 2);

        if ("Admin".equalsIgnoreCase(role)) {
            int preConfirm = JOptionPane.showConfirmDialog(
                    this,
                    "⚠️ WARNING: You are about to delete an ADMIN account!\n\n" +
                            "User ID: " + uid + "\n" +
                            "Username: " + username + "\n\n" +
                            "This is a sensitive operation. Are you sure?",
                    "Confirm Admin Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (preConfirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete this user?\n\n" +
                        "User ID: " + uid + "\n" +
                        "Username: " + username + "\n" +
                        "Role: " + role + "\n\n" +
                        "This action cannot be undone.",
                "Confirm Delete User",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            ServiceResult<String> sr = adminService.deleteUser(uid, role);

            JOptionPane.showMessageDialog(this,
                    sr.getMessage(),
                    sr.isSuccess() ? "Success" : "Error",
                    sr.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (sr.isSuccess()) {
                loadUsers();
            }
        }
    }

    // ==================== DELETE COURSE WITH BETTER CONFIRMATION ====================
    private void deleteSelectedCourse() {
        int row = courseTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a course to delete!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int cid = (int) courseModel.getValueAt(row, 0);
        String courseCode = (String) courseModel.getValueAt(row, 1);
        String courseName = (String) courseModel.getValueAt(row, 2);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete this course?\n\n" +
                        "Course Code: " + courseCode + "\n" +
                        "Course Name: " + courseName + "\n\n" +
                        "⚠️ WARNING: This will also delete all sections of this course!\n" +
                        "This action cannot be undone.",
                "Confirm Delete Course",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            ServiceResult<String> sr = adminService.deleteCourse(cid);

            JOptionPane.showMessageDialog(this,
                    sr.getMessage(),
                    sr.isSuccess() ? "Success" : "Error",
                    sr.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (sr.isSuccess()) {
                loadCourses();
            }
        }
    }
    private void deleteSelectedSection() {
        int row = sectionTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a section to delete!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sectionId = (String) sectionModel.getValueAt(row, 0);
        String courseInfo = (String) sectionModel.getValueAt(row, 1);
        int enrolled = (int) sectionModel.getValueAt(row, 9); // column 9 = enrolled count

        // 🚫 CRITICAL: Prevent deletion if students are enrolled
        if (enrolled > 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "Cannot delete this section!\n\n" +
                            "Section: " + sectionId + "\n" +
                            "Course: " + courseInfo + "\n" +
                            "Enrolled Students: " + enrolled + "\n\n" +
                            "Please drop or move all students first.",
                    "Delete Blocked - Students Enrolled",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // ✅ Safe to delete - no students enrolled
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete this section?\n\n" +
                        "Section ID: " + sectionId + "\n" +
                        "Course: " + courseInfo + "\n\n" +
                        "This action cannot be undone.",
                "Confirm Delete Section",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            ServiceResult<String> result = adminService.deleteSection(sectionId);

            JOptionPane.showMessageDialog(this,
                    result.getMessage(),
                    result.isSuccess() ? "Success" : "Error",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (result.isSuccess()) {
                loadSections();
            }
        }
    }
    private void showChangeDeadlineDialog(String key, String title) {
        // Get current deadline
        String currentDeadline = adminService.getSettingValue(key);

        JLabel infoLabel = new JLabel(
                "<html><b>Current " + title + ":</b> " + currentDeadline + "</html>"
        );
        infoLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        infoLabel.setBorder(new EmptyBorder(0, 0, 15, 0));

        JTextField dateField = new JTextField(currentDeadline != null ? currentDeadline : "", 15);

        JLabel formatLabel = new JLabel("Format: YYYY-MM-DD (e.g., 2024-12-31)");
        formatLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        formatLabel.setForeground(new Color(100, 100, 100));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(infoLabel);
        panel.add(createFormRow("New Deadline Date", dateField));
        panel.add(formatLabel);

        showCustomDialog("Change " + title, panel, (ok) -> {
            String newDate = dateField.getText().trim();

            // ✅ VALIDATION: Cannot be empty
            if (newDate.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Date cannot be empty!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ✅ VALIDATION: Check date format YYYY-MM-DD
            if (!newDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                JOptionPane.showMessageDialog(this,
                        "Invalid date format!\n\n" +
                                "Required format: YYYY-MM-DD\n" +
                                "Example: 2024-12-31",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ✅ VALIDATION: Validate actual date (month 1-12, day 1-31)
            try {
                String[] parts = newDate.split("-");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);

                if (year < 2000 || year > 2100) {
                    throw new IllegalArgumentException("Year must be between 2000 and 2100");
                }
                if (month < 1 || month > 12) {
                    throw new IllegalArgumentException("Month must be between 1 and 12");
                }
                if (day < 1 || day > 31) {
                    throw new IllegalArgumentException("Day must be between 1 and 31");
                }

                // Additional validation using Java's date API
                java.time.LocalDate.parse(newDate);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Invalid date!\n\n" +
                                "Please enter a valid calendar date.\n" +
                                "Error: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ✅ Confirm if date is same as current
            if (newDate.equals(currentDeadline)) {
                int proceed = JOptionPane.showConfirmDialog(this,
                        "The new deadline is the same as the current deadline.\n" +
                                "Do you want to proceed anyway?",
                        "Same Date",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (proceed != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            ServiceResult<String> sr = adminService.updateSetting(key, newDate);

            JOptionPane.showMessageDialog(this,
                    sr.getMessage(),
                    sr.isSuccess() ? "Success" : "Error",
                    sr.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
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