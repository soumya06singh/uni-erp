package edu.univ.erp.ui;

import edu.univ.erp.service.StudentService;
import edu.univ.erp.service.StudentService.*;
import edu.univ.erp.domain.ServiceResult;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Vector;
import edu.univ.erp.service.StudentService.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTable;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Dimension;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;






public class StudentDashboard extends JFrame {

    // CORRECT color scheme - matching admin dashboard
    private static final Color TEAL_COLOR = new Color(0, 180, 180);           // RGB(0, 180, 180)
    private static final Color TEAL_DARK = new Color(0, 150, 150);            // Darker teal for hover
    private static final Color DELETE_RED = new Color(200, 50, 50);           // Red for delete
    private static final Color LIGHT_TEAL_BG = new Color(224, 247, 250);      // Very light teal for selected rows
    private static final Color SIDEBAR_BG = new Color(240, 248, 255);         // Light sidebar
    private static final Color BG_LIGHT = new Color(248, 249, 250);
    private static final Color TEXT_DARK = new Color(52, 58, 64);
    private static final Color BORDER_GRAY = new Color(222, 226, 230);

    private final String userId;
    private final String username;
    private final StudentService studentService;
    private JTable enrollTable;
    private DefaultTableModel enrollModel;
    private JLabel maintenanceBanner;
    private DefaultTableModel catalogModel;
    private DefaultTableModel timetableModel;
    private DefaultTableModel gradesModel;
    private JTabbedPane mainTabbedPane;


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
        setSize(1400, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        // LEFT SIDEBAR (like admin dashboard)
        JPanel sidebar = createSidebar();
        add(sidebar, BorderLayout.WEST);

        // RIGHT CONTENT AREA
        JPanel contentArea = new JPanel(new BorderLayout(0, 0));
        contentArea.setBackground(Color.WHITE);

        // Maintenance banner at very top
        maintenanceBanner = new JLabel("MAINTENANCE MODE - View Only", SwingConstants.CENTER);
        maintenanceBanner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        maintenanceBanner.setBackground(new Color(255, 193, 7));
        maintenanceBanner.setForeground(new Color(102, 60, 0));
        maintenanceBanner.setOpaque(true);
        maintenanceBanner.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        maintenanceBanner.setVisible(false);
        contentArea.add(maintenanceBanner, BorderLayout.NORTH);

        // Main tabbed pane
        mainTabbedPane = new JTabbedPane();
        JTabbedPane tabbedPane = mainTabbedPane;

        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabbedPane.setBackground(Color.WHITE);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        tabbedPane.setTabPlacement(JTabbedPane.TOP);  // Keep tabs but we'll hide them

// Add tabs WITHOUT labels (empty strings)
        tabbedPane.addTab("", createEnrollmentsPanel());
        tabbedPane.addTab("", createCourseCatalogPanel());
        tabbedPane.addTab("", createTimetablePanel());
        tabbedPane.addTab("", createGradesPanel());

// Hide the tab area completely
        tabbedPane.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override
            protected int calculateTabAreaHeight(int tabPlacement, int horizRunCount, int maxTabHeight) {
                return 0;
            }
        });

        contentArea.add(tabbedPane, BorderLayout.CENTER);
        // Bottom buttons panel
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_GRAY));

        JButton refreshBtn = createRoundedButton("Refresh All", TEAL_COLOR);
        refreshBtn.addActionListener(e -> refreshAllData());

        JButton transcriptBtn = createRoundedButton("Download Transcript", TEAL_COLOR);
        transcriptBtn.addActionListener(e -> downloadTranscript());

        bottomPanel.add(refreshBtn);
        bottomPanel.add(transcriptBtn);

        contentArea.add(bottomPanel, BorderLayout.SOUTH);

        add(contentArea, BorderLayout.CENTER);
    }
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BorderLayout());
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(280, getHeight()));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_GRAY));

        // Top section with title
        JPanel topSection = new JPanel();
        topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
        topSection.setBackground(SIDEBAR_BG);
        topSection.setBorder(BorderFactory.createEmptyBorder(30, 25, 30, 25));

        JLabel titleLabel = new JLabel("Student Portal");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(TEAL_COLOR);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        topSection.add(titleLabel);
        sidebar.add(topSection, BorderLayout.NORTH);

        // Center section with navigation
        JPanel navSection = new JPanel();
        navSection.setLayout(new BoxLayout(navSection, BoxLayout.Y_AXIS));
        navSection.setBackground(SIDEBAR_BG);
        navSection.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        // Add navigation buttons
        addNavButton(navSection, "My Enrollments", 0);
        addNavButton(navSection, "Course Catalog", 1);
        addNavButton(navSection, "Timetable", 2);
        addNavButton(navSection, "Grades", 3);

        sidebar.add(navSection, BorderLayout.CENTER);

        // Bottom section with LOGOUT button (RED)
        JPanel bottomSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 25));
        bottomSection.setBackground(SIDEBAR_BG);

        // NEW CODE - Text-only logout
        JLabel logoutLabel = new JLabel("Logout");
        logoutLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        logoutLabel.setForeground(DELETE_RED);
        logoutLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        logoutLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int confirm = JOptionPane.showConfirmDialog(StudentDashboard.this,
                        "Are you sure you want to logout?",
                        "Confirm Logout",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    SwingUtilities.invokeLater(() -> MainApp.main(null));
                    dispose();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                logoutLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                logoutLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            }
        });

        bottomSection.add(logoutLabel);
        sidebar.add(bottomSection, BorderLayout.SOUTH);

        return sidebar;
    }

    // Add this field at the top of your class with other fields:

    // Add this helper method:
    private void addNavButton(JPanel parent, String text, int tabIndex) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btn.setForeground(TEXT_DARK);
        btn.setBackground(SIDEBAR_BG);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(BorderFactory.createEmptyBorder(12, 25, 12, 25));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

        btn.addActionListener(e -> {
            if (mainTabbedPane != null) {
                mainTabbedPane.setSelectedIndex(tabIndex);
                // Update all buttons in parent to show which is active
                updateNavButtonStates(parent, btn);
            }
        });

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isButtonActive(btn)) {
                    btn.setBackground(LIGHT_TEAL_BG);
                    btn.setOpaque(true);
                    // REMOVED: btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isButtonActive(btn)) {
                    btn.setBackground(SIDEBAR_BG);
                    btn.setOpaque(false);
                    // REMOVED: btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                }
            }
        });

        parent.add(btn);
        parent.add(Box.createRigidArea(new Dimension(0, 5)));
    }

    // Add these helper methods:
    private boolean isButtonActive(JButton btn) {
        return btn.getFont().isBold() && btn.isOpaque() &&
                btn.getBackground().equals(LIGHT_TEAL_BG);
    }

    private void updateNavButtonStates(JPanel parent, JButton activeButton) {
        for (Component comp : parent.getComponents()) {
            if (comp instanceof JButton) {
                JButton btn = (JButton) comp;
                if (btn == activeButton) {
                    // Make active button bold with background and left teal border
                    btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    btn.setBackground(LIGHT_TEAL_BG);
                    btn.setOpaque(true);
                    btn.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 4, 0, 0, TEAL_COLOR),
                            BorderFactory.createEmptyBorder(12, 21, 12, 25)
                    ));
                } else {
                    // Make other buttons normal
                    btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    btn.setBackground(SIDEBAR_BG);
                    btn.setOpaque(false);
                    btn.setBorder(BorderFactory.createEmptyBorder(12, 25, 12, 25));
                }
            }
        }
    }
        private JButton createRoundedButton(String text, Color bgColor) {
            JButton btn = new JButton(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    if (getModel().isPressed()) {
                        g2.setColor(TEAL_DARK);  // Use the darker teal constant
                    } else if (getModel().isRollover()) {
                        g2.setColor(TEAL_DARK);  // Same darker teal for hover
                    } else {
                        g2.setColor(bgColor);
                    }
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    g2.dispose();

                    super.paintComponent(g);
                }
            };

            btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btn.setForeground(Color.WHITE);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setOpaque(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

            return btn;
        }
    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(40);
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(true);
        table.setGridColor(BORDER_GRAY);
        table.setSelectionBackground(LIGHT_TEAL_BG);
        table.setSelectionForeground(TEXT_DARK);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setBackground(Color.WHITE);

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBackground(Color.WHITE);
        header.setForeground(TEXT_DARK);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, TEAL_COLOR));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 45));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        centerRenderer.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        return table;
    }

    // ==================== MY ENROLLMENTS TAB ====================
    private JPanel createEnrollmentsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // ADD THIS - Welcome header
        JLabel headerLabel = new JLabel("Welcome, " + username + " !");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        headerLabel.setForeground(TEAL_COLOR);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        panel.add(headerLabel, BorderLayout.NORTH);  // Add this line

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

        enrollTable = createStyledTable(enrollModel);
        enrollTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(enrollModel);
        enrollTable.setRowSorter(sorter);

        JScrollPane scroll = new JScrollPane(enrollTable);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_GRAY, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);


        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonPanel.setBackground(Color.WHITE);

        JButton dropBtn = createRoundedButton("Drop Selected Section", DELETE_RED);
        dropBtn.addActionListener(e -> dropSection());
        buttonPanel.add(dropBtn);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadEnrollments() {
        enrollModel.setRowCount(0);
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
            JPanel panel = new JPanel(new BorderLayout(0, 20));
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
            // ADD THIS - Header
            JLabel headerLabel = new JLabel("Browse & Register for Courses");
            headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
            headerLabel.setForeground(TEAL_COLOR);
            headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

            // Wrap header and search panel together
            JPanel topWrapper = new JPanel(new BorderLayout(0, 15));
            topWrapper.setBackground(Color.WHITE);
            topWrapper.add(headerLabel, BorderLayout.NORTH);



            // Search panel
            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
            searchPanel.setBackground(Color.WHITE);
            searchPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_GRAY, 1),
                    BorderFactory.createEmptyBorder(10, 15, 10, 15)
            ));
        topWrapper.add(searchPanel, BorderLayout.CENTER);


        JLabel searchLabel = new JLabel("Search Course:");
            searchLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            searchPanel.add(searchLabel);

            JTextField searchField = new JTextField(20);
            searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            searchField.setPreferredSize(new Dimension(200, 30));
            searchPanel.add(searchField);

            JLabel semLabel = new JLabel("Semester:");
            semLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            searchPanel.add(semLabel);

            JComboBox<String> semesterCombo = new JComboBox<>(new String[]{"All", "Fall", "Spring", "Summer","Winter"});
            semesterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            semesterCombo.setPreferredSize(new Dimension(120, 30));
            searchPanel.add(semesterCombo);

            JButton searchBtn = createRoundedButton("Search", TEAL_COLOR);
            searchPanel.add(searchBtn);

            JButton clearBtn = createRoundedButton("Clear", TEAL_COLOR);
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

            JTable catalogTable = createStyledTable(catalogModel);
            catalogTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(catalogModel);
            catalogTable.setRowSorter(sorter);

            JScrollPane scroll = new JScrollPane(catalogTable);
            scroll.setBorder(BorderFactory.createLineBorder(BORDER_GRAY, 1));
            scroll.getViewport().setBackground(Color.WHITE);
            scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);


            // Action buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            buttonPanel.setBackground(Color.WHITE);

            JButton registerBtn = createRoundedButton("Register for Selected Section", TEAL_COLOR);
            registerBtn.addActionListener(e -> registerForSection(catalogTable, catalogModel));

            JButton refreshCatalogBtn = createRoundedButton("Refresh Catalog", TEAL_COLOR);
            refreshCatalogBtn.addActionListener(e -> {
                loadCourseCatalog(catalogModel, null, null);
                searchField.setText("");
                semesterCombo.setSelectedIndex(0);
            });

            JButton viewDetailsBtn = createRoundedButton("View Section Details", TEAL_COLOR);
            viewDetailsBtn.addActionListener(e -> viewSectionDetails(catalogTable, catalogModel));

            buttonPanel.add(registerBtn);
            buttonPanel.add(refreshCatalogBtn);
            buttonPanel.add(viewDetailsBtn);

            JLabel hintLabel = new JLabel("Tip: Select a section from the table and click 'Register' to enroll");
            hintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            hintLabel.setForeground(Color.GRAY);
            hintLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            searchBtn.addActionListener(e -> {
                String keyword = searchField.getText().trim();
                String semester = (String) semesterCombo.getSelectedItem();
                if ("All".equals(semester)) semester = null;
                loadCourseCatalog(catalogModel, keyword, semester);
            });
            panel.add(topWrapper, BorderLayout.NORTH);

            JPanel bottomSection = new JPanel(new BorderLayout());
            bottomSection.setBackground(Color.WHITE);
            bottomSection.add(buttonPanel, BorderLayout.NORTH);
            bottomSection.add(hintLabel, BorderLayout.SOUTH);

            panel.add(scroll, BorderLayout.CENTER);
            panel.add(bottomSection, BorderLayout.SOUTH);

            loadCourseCatalog(catalogModel, null, null);
            return panel;
    }

    private void loadCourseCatalog(DefaultTableModel model, String keyword, String semester) {
        model.setRowCount(0);
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

        // Create a cleaner panel layout
        JPanel detailsPanel = new JPanel();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.setBackground(Color.WHITE);
        detailsPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Section ID
        JLabel sectionLabel = new JLabel("Section: " + sectionId);
        sectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sectionLabel.setForeground(TEAL_COLOR);

        // Course info
        JLabel courseLabel = new JLabel(courseCode + " - " + courseTitle);
        courseLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        courseLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));

        JLabel creditsLabel = new JLabel("Credits: " + credits);
        creditsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Instructor & location
        JLabel instructorLabel = new JLabel("Instructor: " + instructor);
        instructorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        instructorLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JLabel semesterLabel = new JLabel("Semester: " + semester);
        semesterLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel roomLabel = new JLabel("Room: " + room);
        roomLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Enrollment info
        JLabel enrollTitle = new JLabel("Enrollment Information");
        enrollTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        enrollTitle.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        JLabel capacityLabel = new JLabel("Capacity: " + capacity);
        capacityLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel enrolledLabel = new JLabel("Enrolled: " + enrolled);
        enrolledLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel availableLabel = new JLabel("Available: " + available);
        availableLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        if (available > 0) {
            availableLabel.setForeground(TEAL_COLOR);
        } else {
            availableLabel.setForeground(DELETE_RED);
        }

        detailsPanel.add(sectionLabel);
        detailsPanel.add(courseLabel);
        detailsPanel.add(creditsLabel);
        detailsPanel.add(instructorLabel);
        detailsPanel.add(semesterLabel);
        detailsPanel.add(roomLabel);
        detailsPanel.add(enrollTitle);
        detailsPanel.add(capacityLabel);
        detailsPanel.add(enrolledLabel);
        detailsPanel.add(availableLabel);

        JOptionPane.showMessageDialog(this, detailsPanel,
                "Section Details", JOptionPane.PLAIN_MESSAGE);
    }
    // ==================== TIMETABLE TAB ====================
    private JPanel createTimetablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        JLabel headerLabel = new JLabel("Your Weekly Schedule");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        headerLabel.setForeground(TEAL_COLOR);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        JPanel calendarPanel = createCalendarTimetable();
        JPanel contentWrapper = new JPanel(new BorderLayout(0, 0));
        contentWrapper.setBackground(Color.WHITE);
        contentWrapper.add(headerLabel, BorderLayout.NORTH);
        contentWrapper.add(calendarPanel, BorderLayout.CENTER);


        JButton refreshBtn = createRoundedButton("Refresh Timetable", TEAL_COLOR);
        refreshBtn.addActionListener(e -> refreshTimetable());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.add(refreshBtn);

        panel.add(contentWrapper, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }
    private JPanel createCalendarTimetable() {
        JPanel calendarPanel = new JPanel(new BorderLayout(10, 10));
        calendarPanel.setBackground(Color.WHITE);

        // Days of the week
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

        // Time slots - 30 minute intervals
        String[] timeSlots = {"08:00", "08:30", "09:00", "09:30", "10:00", "10:30",
                "11:00", "11:30", "12:00", "12:30", "13:00", "13:30",
                "14:00", "14:30", "15:00", "15:30", "16:00", "16:30",
                "17:00", "17:30", "18:00"};

        // Main grid panel
        JPanel gridPanel = new JPanel(new GridBagLayout());
        gridPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        // Add day headers
        gbc.gridy = 0;
        gbc.gridx = 0;
        JLabel cornerLabel = new JLabel("Time", SwingConstants.CENTER);
        cornerLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cornerLabel.setBorder(BorderFactory.createMatteBorder(1, 1, 2, 1, BORDER_GRAY));
        cornerLabel.setBackground(new Color(245, 245, 245));
        cornerLabel.setOpaque(true);
        gridPanel.add(cornerLabel, gbc);

        for (int i = 0; i < days.length; i++) {
            gbc.gridx = i + 1;
            JLabel dayLabel = new JLabel(days[i], SwingConstants.CENTER);
            dayLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            dayLabel.setBorder(BorderFactory.createMatteBorder(1, 1, 2, 1, BORDER_GRAY));
            dayLabel.setBackground(new Color(245, 245, 245));
            dayLabel.setOpaque(true);
            dayLabel.setPreferredSize(new Dimension(150, 40));
            gridPanel.add(dayLabel, gbc);
        }

        // Load timetable data
// Load timetable data
        List<TimetableView> timetable = studentService.getStudentTimetable(userId);

// Create a map: day -> timeSlot -> list of courses at that START time only
        Map<String, Map<String, List<TimetableView>>> scheduleMap = new HashMap<>();
        for (TimetableView entry : timetable) {
            scheduleMap.putIfAbsent(entry.day(), new HashMap<>());
            String timeKey = entry.time().substring(0, 5); // "09:30:00" -> "09:30"

            scheduleMap.get(entry.day()).putIfAbsent(timeKey, new ArrayList<>());
            scheduleMap.get(entry.day()).get(timeKey).add(entry);
        }

// Track which cells are occupied by spanning cells
        Set<String> occupiedCells = new HashSet<>();

// Add time slots and course cells
        for (int row = 0; row < timeSlots.length; row++) {
            gbc.gridy = row + 1;
            gbc.gridx = 0;
            gbc.gridheight = 1;

            // Time label
            JLabel timeLabel = new JLabel(timeSlots[row], SwingConstants.CENTER);
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            timeLabel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, BORDER_GRAY));
            timeLabel.setBackground(new Color(250, 250, 250));
            timeLabel.setOpaque(true);
            gridPanel.add(timeLabel, gbc);

            // Day cells
            for (int col = 0; col < days.length; col++) {
                gbc.gridx = col + 1;
                gbc.gridheight = 1;

                String day = days[col];
                String currentTime = timeSlots[row];
                String cellKey = day + "-" + row;

                // Skip if this cell is already occupied by a spanning cell
                if (occupiedCells.contains(cellKey)) {
                    continue;
                }

                // Check if there are classes STARTING at this time
                List<TimetableView> classesAtThisTime = null;
                if (scheduleMap.containsKey(day) && scheduleMap.get(day).containsKey(currentTime)) {
                    classesAtThisTime = scheduleMap.get(day).get(currentTime);
                }

                JPanel cellPanel = new JPanel();
                cellPanel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, BORDER_GRAY));
                cellPanel.setBackground(Color.WHITE);

                if (classesAtThisTime != null && !classesAtThisTime.isEmpty()) {
                    // Get the first class to determine duration
                    TimetableView firstClass = classesAtThisTime.get(0);

                    // Parse start time from current slot
                    String[] startParts = currentTime.split(":");
                    int startHour = Integer.parseInt(startParts[0]);
                    int startMin = Integer.parseInt(startParts[1]);
                    int startTotalMinutes = startHour * 60 + startMin;

                    // Parse end time from the database
                    String endTimeStr = firstClass.endTime().substring(0, 5); // Get HH:MM from HH:MM:SS or HH:MM
                    String[] endParts = endTimeStr.split(":");
                    int endHour = Integer.parseInt(endParts[0]);
                    int endMin = Integer.parseInt(endParts[1]);
                    int endTotalMinutes = endHour * 60 + endMin;

                    // Calculate duration in minutes
                    int durationMinutes = endTotalMinutes - startTotalMinutes;

                    // Calculate how many 30-minute slots needed
                    int slotsNeeded = (int) Math.ceil(durationMinutes / 30.0);

                    // Set gridheight to span multiple rows
                    gbc.gridheight = slotsNeeded;

                    // Mark all occupied cells
                    for (int i = 0; i < slotsNeeded; i++) {
                        if (row + i < timeSlots.length) {
                            occupiedCells.add(day + "-" + (row + i));
                        }
                    }

                    cellPanel.setBackground(LIGHT_TEAL_BG);
                    cellPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(1, 1, 1, 1, BORDER_GRAY),
                            BorderFactory.createMatteBorder(0, 3, 0, 0, TEAL_COLOR)
                    ));

                    // If multiple classes at same time, divide the cell
                    if (classesAtThisTime.size() == 1) {
                        // Single class - use entire cell
                        cellPanel.setLayout(new BorderLayout(3, 3));
                        TimetableView entry = classesAtThisTime.get(0);

                        JLabel courseLabel = new JLabel("<html><b>" + entry.course() + "</b><br/>" +
                                entry.sectionId() + "<br/>" +
                                entry.room() + "</html>");
                        courseLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                        courseLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                        cellPanel.add(courseLabel, BorderLayout.CENTER);
                    } else {
                        // Multiple classes - divide cell horizontally
                        cellPanel.setLayout(new GridLayout(classesAtThisTime.size(), 1, 0, 2));

                        for (TimetableView entry : classesAtThisTime) {
                            JPanel subPanel = new JPanel(new BorderLayout());
                            subPanel.setBackground(LIGHT_TEAL_BG);
                            subPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, TEAL_COLOR));

                            JLabel courseLabel = new JLabel("<html><b>" + entry.course() + "</b><br/>" +
                                    entry.sectionId() + "<br/>" +
                                    entry.room() + "</html>");
                            courseLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                            courseLabel.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
                            subPanel.add(courseLabel, BorderLayout.CENTER);

                            cellPanel.add(subPanel);
                        }
                    }

                    cellPanel.setPreferredSize(new Dimension(150, 40 * slotsNeeded));
                } else {
                    // Empty cell
                    cellPanel.setLayout(new BorderLayout());
                    cellPanel.setPreferredSize(new Dimension(150, 40));
                }
                gridPanel.add(cellPanel, gbc);
            }
        }
        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_GRAY, 1));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        calendarPanel.add(scrollPane, BorderLayout.CENTER);

        return calendarPanel;
    }    private void refreshTimetable() {
        // Get the timetable tab panel (index 2)
        Component timetableTab = mainTabbedPane.getComponentAt(2);
        if (timetableTab instanceof JPanel) {
            JPanel panel = (JPanel) timetableTab;

            // Remove all components
            panel.removeAll();

            // Set layout again
            panel.setLayout(new BorderLayout(0, 20));
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

            // Create fresh calendar with updated data
            JPanel calendarPanel = createCalendarTimetable();

            // Create refresh button
            JButton refreshBtn = createRoundedButton("Refresh Timetable", TEAL_COLOR);
            refreshBtn.addActionListener(e -> refreshTimetable());

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPanel.setBackground(Color.WHITE);
            buttonPanel.add(refreshBtn);

            // Add components back
            panel.add(calendarPanel, BorderLayout.CENTER);
            panel.add(buttonPanel, BorderLayout.SOUTH);

            // Refresh the display
            panel.revalidate();
            panel.repaint();
        }
    }    // ==================== GRADES TAB ====================
    private JPanel createGradesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        JLabel headerLabel = new JLabel("Your Academic Performance");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        headerLabel.setForeground(TEAL_COLOR);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        panel.add(headerLabel, BorderLayout.NORTH);  // Add this line



        gradesModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        gradesModel.setColumnIdentifiers(new Object[]{
                "Course Code", "Course Name", "Section", "Component", "Score", "Final Grade"
        });

        JTable gradesTable = createStyledTable(gradesModel);
        JScrollPane scroll = new JScrollPane(gradesTable);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_GRAY, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);


        JButton refreshBtn = createRoundedButton("Refresh Grades", TEAL_COLOR);
        refreshBtn.addActionListener(e -> loadGrades(gradesModel));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.add(refreshBtn);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        loadGrades(gradesModel);
        return panel;
    }

    private void loadGrades(DefaultTableModel model) {
        model.setRowCount(0);
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
        boolean maintenanceMode = studentService.isMaintenanceMode();
        maintenanceBanner.setVisible(maintenanceMode);
    }

    private void refreshAllData() {
        checkMaintenanceMode();

        loadEnrollments();

        if (catalogModel != null) {
            loadCourseCatalog(catalogModel, null, null);
        }

        refreshTimetable();

        if (gradesModel != null) {
            loadGrades(gradesModel);
        }

        JOptionPane.showMessageDialog(this,
                "All data refreshed successfully!",
                "Refresh Complete", JOptionPane.INFORMATION_MESSAGE);
    }
}