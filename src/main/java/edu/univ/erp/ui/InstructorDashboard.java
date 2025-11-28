package edu.univ.erp.ui;

import edu.univ.erp.service.InstructorService;
import edu.univ.erp.service.InstructorService.GradeRow;
import edu.univ.erp.service.InstructorService.RosterRow;
import edu.univ.erp.service.InstructorService.SectionRow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class InstructorDashboard extends JFrame {

    private final String instructorUserId;
    private final String username;

    private static final Color BG = new Color(240, 244, 248);              // Light blue-gray background
    private static final Color ACCENT = new Color(20, 184, 166);           // Teal
    private static final Color ACCENT_HOVER = new Color(13, 148, 136);     // Darker teal on hover
    private static final Color ACCENT_DARK = new Color(15, 118, 110);
    private static final Color MUTED = new Color(100, 116, 139);           // Slate gray for secondary text
    private static final Color SELECTION_COLOR = new Color(204, 251, 241); // Light teal selection
    private static final Color CARD_BG = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(226, 232, 240);    // Light gray border
    private static final Color TEXT_PRIMARY = new Color(30, 41, 59);
    private static final Color TEXT_SECONDARY = new Color(100, 116, 139);

    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font HEADER_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font CARD_TITLE_FONT = new Font("Segoe UI", Font.BOLD, 18);
    private static final Font STATS_FONT = new Font("Segoe UI", Font.PLAIN, 15);
    private static final Font STATS_VALUE_FONT = new Font("Segoe UI", Font.BOLD, 20);


    private CardLayout cardLayout;
    private JPanel mainCardPanel;
    private static final String VIEW_SECTIONS = "SECTIONS";
    private static final String VIEW_GRADES = "GRADES";

    // --- Components ---
    private final DefaultTableModel sectionsModel = new DefaultTableModel();
    private final JTable tblSections = new JTable(sectionsModel) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    private DefaultTableModel gradeModel;
    private final JTable tblGrades;

    private final JLabel lblWelcome = new JLabel();
    private final JLabel lblDepartment = new JLabel("Department: -");

    private String currentStatsText = "No data available.";


    private int totalStudents = 0;
    private double avgScore = 0.0;
    private double maxScore = 0.0;
    private double minScore = 0.0;
    private double passRate = 0.0;

    private final JLabel lblGradebookTitle = new JLabel("Gradebook");

    private final JLabel lblMaintenance = new JLabel();
    private volatile boolean maintenanceOn = false;
    private javax.swing.Timer maintenancePollTimer;

    private final JButton btnRefreshSections = new ModernButton("Refresh Sections", true);
    private final JButton btnLoadRoster = new ModernButton("Load Roster", true);
    private final JButton btnBack = new ModernButton("Back", false);
    private final JButton btnViewStats = new ModernButton("View Statistics", true);
    private final JButton btnComputeFinal = new ModernButton("Compute Final Grades", true);
    private final JButton btnSave = new ModernButton("Save Grades", true);
    private final JButton btnExport = new ModernButton("Export CSV", true);

    private static final double W_QUIZ = 0.20;
    private static final double W_MID = 0.30;
    private static final double W_END = 0.50;

    private final InstructorService service = new InstructorService();public InstructorDashboard(String instructorUserId, String username) {
        super("Instructor Dashboard");
        this.instructorUserId = instructorUserId;
        this.username = username;

        gradeModel = new DefaultTableModel(new String[]{
                "Enrollment ID","Student ID","Roll No","Student Name","Quiz","Midterm","EndSem","Final"
        }, 0) {
            @Override public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 4,5,6,7 -> Double.class;
                    default -> String.class;
                };
            }
            @Override public boolean isCellEditable(int row, int column) {
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
        setSize(1200, 750);
        setLocationRelativeTo(null);


        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        setContentPane(root);

        JPanel header = createModernHeader();
        root.add(header, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        mainCardPanel = new JPanel(cardLayout);
        mainCardPanel.setOpaque(false);
        mainCardPanel.setBorder(new EmptyBorder(20, 30, 30, 30));

        JPanel pnlSectionsView = createSectionsView();
        mainCardPanel.add(pnlSectionsView, VIEW_SECTIONS);

        JPanel pnlGradebookView = createGradebookView();
        mainCardPanel.add(pnlGradebookView, VIEW_GRADES);

        root.add(mainCardPanel, BorderLayout.CENTER);

        // --- Actions ---
        btnRefreshSections.addActionListener((ActionEvent e) -> loadSections());

        btnLoadRoster.addActionListener((ActionEvent e) -> {
            int viewRow = tblSections.getSelectedRow();
            if (viewRow < 0) {
                showModernDialog("Please select a section first.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = tblSections.convertRowIndexToModel(viewRow);
            String sectionId = (String) sectionsModel.getValueAt(modelRow, 0);
            String courseName = (String) sectionsModel.getValueAt(modelRow, 2);

            lblGradebookTitle.setText("Gradebook: " + courseName + " (" + sectionId + ")");
            loadRosterForSection(sectionId);

            cardLayout.show(mainCardPanel, VIEW_GRADES);
        });

        btnBack.addActionListener(e -> {
            cardLayout.show(mainCardPanel, VIEW_SECTIONS);
        });

        btnComputeFinal.addActionListener((ActionEvent e) -> computeFinalAndUpdateTable());
        btnSave.addActionListener((ActionEvent e) -> saveGradesToDB());
        btnExport.addActionListener((ActionEvent e) -> exportGradesCSV());
        btnViewStats.addActionListener(e -> showEnhancedStatsDialog());

        // Maintenance timer
        refreshMaintenanceBanner();
        maintenancePollTimer = new javax.swing.Timer(30_000, e -> refreshMaintenanceBanner());
        maintenancePollTimer.setInitialDelay(30_000);
        maintenancePollTimer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowActivated(java.awt.event.WindowEvent e) { refreshMaintenanceBanner(); }
        });
    }

    private JPanel createModernHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CARD_BG);
        header.setBorder(new EmptyBorder(20, 30, 20, 30));


        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightPanel.setOpaque(false);

        JPanel titleStack = new JPanel();
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.setOpaque(false);

        lblWelcome.setFont(TITLE_FONT);
        lblWelcome.setForeground(TEXT_PRIMARY);
        lblWelcome.setText("Welcome, " + username);
        lblWelcome.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Instructor Dashboard");
        subtitle.setFont(HEADER_FONT);
        subtitle.setForeground(TEXT_SECONDARY);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleStack.add(lblWelcome);
        titleStack.add(Box.createVerticalStrut(4));
        titleStack.add(subtitle);

        leftPanel.add(titleStack);

        lblMaintenance.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblMaintenance.setForeground(new Color(220, 38, 38));
        lblMaintenance.setBackground(new Color(254, 226, 226));
        lblMaintenance.setOpaque(true);
        lblMaintenance.setBorder(new EmptyBorder(6, 12, 6, 12));
        lblMaintenance.setVisible(false);
        rightPanel.add(lblMaintenance);

        lblDepartment.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblDepartment.setForeground(TEXT_PRIMARY);
        lblDepartment.setBackground(CARD_BG);
        lblDepartment.setOpaque(false);
        lblDepartment.setBorder(new EmptyBorder(0, 0, 0, 0));
        rightPanel.add(lblDepartment);

        header.add(leftPanel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);

        JPanel borderWrapper = new JPanel(new BorderLayout());
        borderWrapper.setBackground(CARD_BG);
        borderWrapper.add(header, BorderLayout.CENTER);

        JPanel bottomBorder = new JPanel();
        bottomBorder.setBackground(BORDER_COLOR);
        bottomBorder.setPreferredSize(new Dimension(0, 1));
        borderWrapper.add(bottomBorder, BorderLayout.SOUTH);

        return borderWrapper;
    }
    private JPanel createSectionsView() {
        JPanel container = new JPanel(new BorderLayout(0, 20));
        container.setOpaque(false);


        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));


        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setBackground(CARD_BG);
        cardHeader.setBorder(new EmptyBorder(20, 25, 15, 25));

        JLabel title = new JLabel("My Sections");
        title.setFont(CARD_TITLE_FONT);
        title.setForeground(TEXT_PRIMARY);
        cardHeader.add(title, BorderLayout.WEST);

        sectionsModel.setColumnIdentifiers(new String[]{
                "Section ID","Course Code","Title","Semester","Year","Day","Start","End","Room","Capacity"
        });
        tblSections.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblSections.setRowHeight(42);
        styleModernTable(tblSections);

        JScrollPane scrollPane = new JScrollPane(tblSections);
        scrollPane.getViewport().setBackground(CARD_BG);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 16));
        btnPanel.setBackground(CARD_BG);
        btnPanel.setBorder(new EmptyBorder(0, 10, 0, 0));

        btnRefreshSections.setPreferredSize(new Dimension(160, 40));
        btnLoadRoster.setPreferredSize(new Dimension(160, 40));

        btnPanel.add(btnRefreshSections);
        btnPanel.add(btnLoadRoster);

        card.add(cardHeader, BorderLayout.NORTH);
        card.add(scrollPane, BorderLayout.CENTER);
        card.add(btnPanel, BorderLayout.SOUTH);

        container.add(card, BorderLayout.CENTER);

        // Logout button
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFont(HEADER_FONT);
        logoutBtn.setForeground(new Color(220, 38, 38));
        logoutBtn.setBackground(Color.WHITE);
        logoutBtn.setBorder(new EmptyBorder(12, 0, 0, 0));
        logoutBtn.setFocusPainted(false);
        logoutBtn.setContentAreaFilled(false);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.setHorizontalAlignment(SwingConstants.LEFT);

        Color normalRed = new Color(220, 38, 38);
        Color hoverRed = new Color(185, 28, 28);
        logoutBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                logoutBtn.setForeground(hoverRed);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                logoutBtn.setForeground(normalRed);
            }
        });

        logoutBtn.addActionListener(e -> logout());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footer.setOpaque(false);
        footer.add(logoutBtn);

        container.add(footer, BorderLayout.SOUTH);

        return container;
    }
    private JPanel createGradebookView() {
        JPanel container = new JPanel(new BorderLayout(0, 20));
        container.setOpaque(false);

        JPanel mainContent = new JPanel(new BorderLayout(20, 0));
        mainContent.setOpaque(false);

        JPanel tableCard = new JPanel(new BorderLayout());
        tableCard.setBackground(CARD_BG);
        tableCard.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));

        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setBackground(CARD_BG);
        cardHeader.setBorder(new EmptyBorder(20, 25, 15, 25));

        lblGradebookTitle.setFont(CARD_TITLE_FONT);
        lblGradebookTitle.setForeground(TEXT_PRIMARY);
        cardHeader.add(lblGradebookTitle, BorderLayout.WEST);

        tblGrades.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblGrades.setRowHeight(40);
        styleModernTable(tblGrades);
        installNumericEditors();

        JScrollPane scrollPane = new JScrollPane(tblGrades);
        scrollPane.getViewport().setBackground(CARD_BG);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 16));
        btnPanel.setBackground(CARD_BG);
        btnPanel.setBorder(new EmptyBorder(0, 10, 0, 0));

        btnBack.setPreferredSize(new Dimension(100, 40));
        btnComputeFinal.setPreferredSize(new Dimension(180, 40));
        btnSave.setPreferredSize(new Dimension(120, 40));
        btnExport.setPreferredSize(new Dimension(120, 40));

        btnPanel.add(btnBack);
        btnPanel.add(Box.createHorizontalStrut(5));
        btnPanel.add(btnComputeFinal);
        btnPanel.add(btnSave);
        btnPanel.add(btnExport);

        tableCard.add(cardHeader, BorderLayout.NORTH);
        tableCard.add(scrollPane, BorderLayout.CENTER);
        tableCard.add(btnPanel, BorderLayout.SOUTH);

        JPanel statsPanel = createVisualStatsPanel();

        mainContent.add(tableCard, BorderLayout.CENTER);
        mainContent.add(statsPanel, BorderLayout.EAST);

        container.add(mainContent, BorderLayout.CENTER);

        return container;
    }

    private JPanel createVisualStatsPanel() {
        JPanel statsContainer = new JPanel();
        statsContainer.setLayout(new BoxLayout(statsContainer, BoxLayout.Y_AXIS));
        statsContainer.setOpaque(false);
        statsContainer.setPreferredSize(new Dimension(280, 0));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titlePanel.setMaximumSize(new Dimension(280, 50));

        JLabel statsTitle = new JLabel("Class Statistics");
        statsTitle.setFont(CARD_TITLE_FONT);
        statsTitle.setForeground(TEXT_PRIMARY);

        btnViewStats.setPreferredSize(new Dimension(140, 36));

        titlePanel.add(statsTitle, BorderLayout.WEST);
        titlePanel.add(btnViewStats, BorderLayout.EAST);

        statsContainer.add(titlePanel);
        statsContainer.add(Box.createVerticalStrut(15));

        statsContainer.add(createStatCard("Total Students", "0", new Color(59, 130, 246)));
        statsContainer.add(Box.createVerticalStrut(12));

        statsContainer.add(createStatCard("Average Score", "0.00", new Color(16, 185, 129)));
        statsContainer.add(Box.createVerticalStrut(12));

        statsContainer.add(createStatCard("Highest Score", "0.00", new Color(245, 158, 11)));
        statsContainer.add(Box.createVerticalStrut(12));

        statsContainer.add(createStatCard("Lowest Score", "0.00", new Color(239, 68, 68)));
        statsContainer.add(Box.createVerticalStrut(12));

        statsContainer.add(createStatCard("Pass Rate", "0.0%", ACCENT));

        statsContainer.add(Box.createVerticalGlue());

        return statsContainer;
    }

    private JPanel createStatCard(String label, String value, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(16, 18, 16, 18)
        ));
        card.setMaximumSize(new Dimension(280, 90));
        card.setPreferredSize(new Dimension(280, 90));

        JPanel accentBar = new JPanel();
        accentBar.setBackground(accentColor);
        accentBar.setPreferredSize(new Dimension(4, 0));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(0, 12, 0, 0));

        JLabel lblLabel = new JLabel(label);
        lblLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblLabel.setForeground(TEXT_SECONDARY);
        lblLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(STATS_VALUE_FONT);
        lblValue.setForeground(TEXT_PRIMARY);
        lblValue.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(lblLabel);
        content.add(Box.createVerticalStrut(6));
        content.add(lblValue);

        card.add(accentBar, BorderLayout.WEST);
        card.add(content, BorderLayout.CENTER);

        return card;
    }

    private void updateStatCards() {
        SwingUtilities.invokeLater(() -> {
            Component[] components = ((JPanel) ((JPanel) mainCardPanel.getComponent(1))
                    .getComponent(0)).getComponents();

            for (Component comp : components) {
                if (comp instanceof JPanel statsPanel && statsPanel.getLayout() instanceof BoxLayout) {
                    updateStatsInPanel(statsPanel);
                    break;
                }
            }
        });
    }

    private void updateStatsInPanel(JPanel statsPanel) {
        int cardIndex = 0;
        for (Component comp : statsPanel.getComponents()) {
            if (comp instanceof JPanel card && card.getBorder() instanceof javax.swing.border.CompoundBorder) {
                JPanel content = findContentPanel(card);
                if (content != null) {
                    Component[] contentComps = content.getComponents();
                    for (Component c : contentComps) {
                        if (c instanceof JLabel lbl && lbl.getFont().equals(STATS_VALUE_FONT)) {
                            switch (cardIndex) {
                                case 0 -> lbl.setText(String.valueOf(totalStudents));
                                case 1 -> lbl.setText(String.format("%.2f", avgScore));
                                case 2 -> lbl.setText(String.format("%.2f", maxScore));
                                case 3 -> lbl.setText(String.format("%.2f", minScore));
                                case 4 -> lbl.setText(String.format("%.1f%%", passRate));
                            }
                            break;
                        }
                    }
                }
                cardIndex++;
            }
        }
    }

    private JPanel findContentPanel(JPanel card) {
        for (Component c : card.getComponents()) {
            if (c instanceof JPanel p && p.getLayout() instanceof BoxLayout) {
                return p;
            }
        }
        return null;
    }
    private JLabel createStatLabel(String label, String value) {
        JLabel statLabel = new JLabel("<html><b>" + label + ":</b> " + value + "</html>");
        statLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        statLabel.setForeground(TEXT_PRIMARY);
        statLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0)); // Small vertical padding
        return statLabel;
    }

    private void showEnhancedStatsDialog() {
        JDialog statsDialog = new JDialog(this, "Detailed Statistics", true);
        statsDialog.setSize(550, 520);
        statsDialog.setLocationRelativeTo(this);
        statsDialog.setLayout(new BorderLayout());
        statsDialog.getContentPane().setBackground(BG);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 20));
        mainPanel.setBackground(BG);
        mainPanel.setBorder(new EmptyBorder(25, 30, 25, 30));

        // Title
        JLabel title = new JLabel("Class Performance Summary");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);

        JPanel statsContentPanel = new JPanel();
        statsContentPanel.setLayout(new BoxLayout(statsContentPanel, BoxLayout.Y_AXIS));
        statsContentPanel.setBackground(CARD_BG);
        statsContentPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        int failCount = totalStudents - (int)Math.round(totalStudents * passRate / 100.0);

        statsContentPanel.add(createStatLabel("Total Graded",
                totalStudents > 0 ? String.valueOf(totalStudents) + " students" : "0 students"));
        statsContentPanel.add(createStatLabel("Average Score",
                totalStudents > 0 ? String.format("%.2f out of 100", avgScore) : "0.00 out of 100"));
        statsContentPanel.add(createStatLabel("Highest Score",
                totalStudents > 0 ? String.format("%.2f maximum", maxScore) : "0.00 maximum"));
        statsContentPanel.add(createStatLabel("Lowest Score",
                totalStudents > 0 ? String.format("%.2f minimum", minScore) : "0.00 minimum"));
        statsContentPanel.add(createStatLabel("Pass Rate",
                totalStudents > 0 ? String.format("%.1f%% (score ≥ 50)", passRate) : "0.0%"));
        statsContentPanel.add(createStatLabel("Fail Count",
                totalStudents > 0 ? String.valueOf(failCount) + " students" : "0 students"));

        JPanel distributionPanel = new JPanel(new BorderLayout());
        distributionPanel.setBackground(CARD_BG);
        distributionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(15, 20, 15, 20)
        ));

        JLabel distTitle = new JLabel("Grade Distribution Information");
        distTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        distTitle.setForeground(TEXT_PRIMARY);

        JTextArea distText = new JTextArea();
        distText.setEditable(false);
        distText.setOpaque(false);
        distText.setFont(STATS_FONT);
        distText.setForeground(TEXT_SECONDARY);
        distText.setLineWrap(true);
        distText.setWrapStyleWord(true);
        distText.setText(String.format(
                "Grading Formula:\n" +
                        "• Quiz: 20%% (Weight: %.0f points)\n" +
                        "• Midterm: 30%% (Weight: %.0f points)\n" +
                        "• End Semester: 50%% (Weight: %.0f points)\n\n" +
                        "Passing Criteria: Final Score ≥ 50.0",
                W_QUIZ * 100, W_MID * 100, W_END * 100
        ));

        JPanel distContent = new JPanel(new BorderLayout(0, 10));
        distContent.setOpaque(false);
        distContent.add(distTitle, BorderLayout.NORTH);
        distContent.add(distText, BorderLayout.CENTER);

        distributionPanel.add(distContent);

        // Close button
        JButton closeBtn = new ModernButton("Close", true);
        closeBtn.setPreferredSize(new Dimension(120, 42));
        closeBtn.addActionListener(e -> statsDialog.dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(closeBtn);

        mainPanel.add(title, BorderLayout.NORTH);

        JPanel centerContent = new JPanel(new BorderLayout(0, 20));
        centerContent.setOpaque(false);
        centerContent.add(statsContentPanel, BorderLayout.NORTH);  // ADD THIS LINE
        centerContent.add(distributionPanel, BorderLayout.SOUTH);

        mainPanel.add(centerContent, BorderLayout.CENTER);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        statsDialog.add(mainPanel);
        statsDialog.setVisible(true);
    }

    private JPanel createDetailedStatCard(String label, String value, String subtitle, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(18, 20, 18, 20)
        ));

        JPanel colorBar = new JPanel();
        colorBar.setBackground(color);
        colorBar.setPreferredSize(new Dimension(5, 0));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(0, 15, 0, 0));

        JLabel lblLabel = new JLabel(label);
        lblLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblLabel.setForeground(TEXT_SECONDARY);
        lblLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblValue.setForeground(color);
        lblValue.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSubtitle = new JLabel(subtitle);
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSubtitle.setForeground(TEXT_SECONDARY);
        lblSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(lblLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(lblValue);
        content.add(Box.createVerticalStrut(4));
        content.add(lblSubtitle);

        card.add(colorBar, BorderLayout.WEST);
        card.add(content, BorderLayout.CENTER);

        return card;
    }

    private void showModernDialog(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }
    private void styleModernTable(JTable table) {
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(12, 0));
        table.setFillsViewportHeight(true);
        table.setFont(HEADER_FONT);

        table.setSelectionBackground(SELECTION_COLOR);
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setBackground(CARD_BG);

        JTableHeader header = table.getTableHeader();
        header.setBackground(CARD_BG);
        header.setForeground(TEXT_SECONDARY);
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 45));

        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = new JLabel(value == null ? "" : value.toString());
                label.setFont(new Font("Segoe UI", Font.BOLD, 12));
                label.setForeground(TEXT_SECONDARY);
                label.setBackground(CARD_BG);
                label.setOpaque(true);
                label.setBorder(new EmptyBorder(12, 12, 12, 12));
                return label;
            }
        });

        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (isSelected) {
                    c.setBackground(SELECTION_COLOR);
                    c.setForeground(TEXT_PRIMARY);
                } else {
                    c.setBackground(row % 2 == 0 ? CARD_BG : new Color(249, 250, 251));
                    c.setForeground(TEXT_PRIMARY);
                }

                ((JLabel) c).setBorder(new EmptyBorder(10, 12, 10, 12));
                return c;
            }
        };

        table.setDefaultRenderer(Object.class, cellRenderer);
        table.setDefaultRenderer(String.class, cellRenderer);
    }

    private void installNumericEditors() {
        DoubleEditor doubleEditor = new DoubleEditor();
        SwingUtilities.invokeLater(() -> {
            TableColumnModel cm = tblGrades.getColumnModel();
            for (int modelCol = 4; modelCol <= 6; modelCol++) {
                if (cm.getColumnCount() > modelCol) {
                    TableColumn col = cm.getColumn(modelCol);
                    col.setCellEditor(doubleEditor);
                    col.setCellRenderer(new RightAlignDoubleRenderer());
                }
            }
            if (cm.getColumnCount() > 7) {
                TableColumn finalCol = cm.getColumn(7);
                finalCol.setCellRenderer(new RightAlignDoubleRenderer());
            }
        });
    }
    private void logout() {
        if (maintenancePollTimer != null && maintenancePollTimer.isRunning()) {
            maintenancePollTimer.stop();
        }
        dispose();
        SwingUtilities.invokeLater(() -> MainApp.main(new String[0]));
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
                    lblDepartment.setText(dep == null ? "Department: -" : dep);
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
                    showModernDialog("Error loading sections: " + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
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
                lblMaintenance.setText("  MAINTENANCE MODE  ");
                lblMaintenance.setVisible(true);
            } else {
                lblMaintenance.setVisible(false);
            }
            btnSave.setEnabled(!on);
            btnComputeFinal.setEnabled(!on);
            btnLoadRoster.setEnabled(true);
            tblGrades.repaint();
        });
    }

    private void loadRosterForSection(String sectionId) {
        boolean found = false;
        for (int r = 0; r < sectionsModel.getRowCount(); r++) {
            if (sectionId.equals(sectionsModel.getValueAt(r, 0))) {
                found = true;
                break;
            }
        }

        if (!found) {
            showModernDialog("You do not own this section or it is not visible.",
                    "Permission Denied", JOptionPane.ERROR_MESSAGE);
            return;
        }

        gradeModel.setRowCount(0);
        new SwingWorker<List<RosterRow>, Void>() {
            @Override
            protected List<RosterRow> doInBackground() {
                return service.getRosterForSection(sectionId);
            }

            @Override
            protected void done() {
                try {
                    List<RosterRow> roster = get();
                    double sumFinal = 0;
                    double minFinal = Double.MAX_VALUE;
                    double maxFinal = Double.MIN_VALUE;
                    int countFinal = 0;
                    int pass = 0;

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
                            sumFinal += r.finalScore();
                            minFinal = Math.min(minFinal, r.finalScore());
                            maxFinal = Math.max(maxFinal, r.finalScore());
                            countFinal++;
                            if (r.finalScore() >= 50.0) pass++;
                        }
                    }

                    if (countFinal > 0) {
                        totalStudents = countFinal;
                        avgScore = sumFinal / countFinal;
                        maxScore = maxFinal;
                        minScore = minFinal;
                        passRate = pass * 100.0 / countFinal;

                        currentStatsText = String.format(
                                "Class Performance Summary:\n\n" +
                                        "Total Graded Students: %d\n" +
                                        "Average Score: %.2f\n" +
                                        "Highest Score: %.2f\n" +
                                        "Lowest Score: %.2f\n" +
                                        "Pass Rate: %.1f%% (Score >= 50.0)",
                                totalStudents, avgScore, maxScore, minScore, passRate
                        );
                    } else {
                        totalStudents = 0;
                        avgScore = 0.0;
                        maxScore = 0.0;
                        minScore = 0.0;
                        passRate = 0.0;
                        currentStatsText = "No final grades computed yet.";
                    }

                    updateStatCards();

                } catch (Exception ex) {
                    ex.printStackTrace();
                    showModernDialog("Error loading roster: " + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void computeFinalAndUpdateTable() {
        if (gradeModel.getRowCount() == 0) return;

        if (maintenanceOn) {
            showModernDialog("System is in maintenance mode. Cannot compute grades.",
                    "Maintenance Mode", JOptionPane.WARNING_MESSAGE);
            return;
        }

        for (int r = 0; r < gradeModel.getRowCount(); r++) {
            Double q = toDouble(gradeModel.getValueAt(r, 4));
            Double m = toDouble(gradeModel.getValueAt(r, 5));
            Double e = toDouble(gradeModel.getValueAt(r, 6));

            double finalScore = Math.round(((q == null ? 0.0 : q) * W_QUIZ
                    + (m == null ? 0.0 : m) * W_MID
                    + (e == null ? 0.0 : e) * W_END) * 100.0) / 100.0;

            gradeModel.setValueAt(Double.valueOf(finalScore), r, 7);
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
        } catch (Exception ex) {
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
            totalStudents = count;
            avgScore = sum / count;
            maxScore = max;
            minScore = min;
            passRate = pass * 100.0 / count;

            currentStatsText = String.format(
                    "Class Performance Summary:\n\n" +
                            "Total Graded Students: %d\n" +
                            "Average Score: %.2f\n" +
                            "Highest Score: %.2f\n" +
                            "Lowest Score: %.2f\n" +
                            "Pass Rate: %.1f%% (Score >= 50.0)",
                    totalStudents, avgScore, maxScore, minScore, passRate
            );
        } else {
            totalStudents = 0;
            avgScore = 0.0;
            maxScore = 0.0;
            minScore = 0.0;
            passRate = 0.0;
            currentStatsText = "No final grades computed yet.";
        }

        updateStatCards();
    }

    private void saveGradesToDB() {
        if (gradeModel.getRowCount() == 0) return;

        if (maintenanceOn) {
            showModernDialog("System is in maintenance mode. Cannot save grades.",
                    "Maintenance Mode", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<GradeRow> toSave = new ArrayList<>();
        for (int r = 0; r < gradeModel.getRowCount(); r++) {
            String enrollmentId = (String) gradeModel.getValueAt(r, 0);
            Double quiz = toDouble(gradeModel.getValueAt(r, 4));
            Double mid = toDouble(gradeModel.getValueAt(r, 5));
            Double end = toDouble(gradeModel.getValueAt(r, 6));
            Double finalScore = toDouble(gradeModel.getValueAt(r, 7));

            if (quiz != null)
                toSave.add(new GradeRow(enrollmentId, "QUIZ", quiz, (int)Math.round(W_QUIZ*100)));
            if (mid != null)
                toSave.add(new GradeRow(enrollmentId, "MIDTERM", mid, (int)Math.round(W_MID*100)));
            if (end != null)
                toSave.add(new GradeRow(enrollmentId, "ENDSEM", end, (int)Math.round(W_END*100)));
            if (finalScore != null)
                toSave.add(new GradeRow(enrollmentId, "FINAL", finalScore, 100));
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.saveGradesBatch(toSave);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    showModernDialog("Grades have been saved successfully.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    recalculateStatsFromTable();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showModernDialog("Error saving grades: " + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    //CSV Export

    private void exportGradesCSV() {
        if (gradeModel.getRowCount() == 0) {
            showModernDialog("No grades to export.", "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String defaultSection = "unknown";
        if (lblGradebookTitle.getText().contains("(")) {
            defaultSection = "grades";
        }

        String ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .format(java.time.LocalDateTime.now());
        String defaultName = String.format("%s_%s.csv", defaultSection, ts);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save grades CSV");
        chooser.setSelectedFile(new java.io.File(defaultName));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));

        int userChoice = chooser.showSaveDialog(this);
        if (userChoice != JFileChooser.APPROVE_OPTION) return;

        String path = chooser.getSelectedFile().getAbsolutePath();
        if (!path.toLowerCase().endsWith(".csv")) path += ".csv";

        java.util.function.Function<String,String> esc = s -> {
            if (s == null) return "";
            String st = s;
            if (st.contains("\"") || st.contains(",") || st.contains("\n") || st.contains("\r")) {
                st = st.replace("\"", "\"\"");
                return "\"" + st + "\"";
            } else return st;
        };

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (int c = 0; c < gradeModel.getColumnCount(); c++) {
                pw.print(esc.apply(gradeModel.getColumnName(c)));
                if (c < gradeModel.getColumnCount() - 1) pw.print(",");
            }
            pw.println();

            for (int r = 0; r < gradeModel.getRowCount(); r++) {
                for (int c = 0; c < gradeModel.getColumnCount(); c++) {
                    Object vobj = gradeModel.getValueAt(r, c);
                    String cell = vobj == null ? "" : vobj.toString();
                    pw.print(esc.apply(cell));
                    if (c < gradeModel.getColumnCount() - 1) pw.print(",");
                }
                pw.println();
            }
            pw.flush();
            showModernDialog("CSV exported successfully to:\n" + path,
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            showModernDialog("Error exporting CSV: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void dispose() {
        if (maintenancePollTimer != null && maintenancePollTimer.isRunning())
            maintenancePollTimer.stop();
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            InstructorDashboard dash = new InstructorDashboard("<instructor-user-id>", "inst1");
            dash.setVisible(true);
        });
    }

    private static class ModernButton extends JButton {
        private final boolean isPrimary;

        public ModernButton(String text, boolean isPrimary) {
            super(text);
            this.isPrimary = isPrimary;

            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            if (isPrimary) {
                setForeground(Color.WHITE);
                setBackground(ACCENT);
            } else {
                setForeground(ACCENT_DARK);
                setBackground(new Color(204, 251, 241));
            }

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        if (isPrimary) {
                            setBackground(ACCENT_HOVER);
                        } else {
                            setBackground(new Color(153, 246, 228));
                        }
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (isPrimary) {
                        setBackground(ACCENT);
                    } else {
                        setBackground(new Color(204, 251, 241));
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (!isEnabled()) {
                g2.setColor(new Color(203, 213, 225));
            } else if (getModel().isPressed()) {
                g2.setColor(getBackground().darker());
            } else {
                g2.setColor(getBackground());
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.dispose();

            super.paintComponent(g);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            if (enabled) {
                if (isPrimary) {
                    setForeground(Color.WHITE);
                } else {
                    setForeground(ACCENT_DARK);
                }
            } else {
                setForeground(new Color(148, 163, 184));
            }
        }
    }

    static class DoubleEditor extends DefaultCellEditor {
        private final JTextField fld;

        DoubleEditor() {
            super(new JTextField());
            fld = (JTextField) getComponent();
            fld.addActionListener(e -> stopCellEditing());
        }

        @Override
        public Object getCellEditorValue() {
            String t = fld.getText();
            if (t == null) return null;
            t = t.trim();
            if (t.isEmpty()) return null;
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    static class RightAlignDoubleRenderer extends DefaultTableCellRenderer {
        RightAlignDoubleRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setBorder(new EmptyBorder(10, 12, 10, 12));
        }

        @Override
        public void setValue(Object value) {
            if (value == null)
                setText("");
            else if (value instanceof Number)
                setText(String.format("%.2f", ((Number) value).doubleValue()));
            else
                setText(value.toString());
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (isSelected) {
                c.setBackground(SELECTION_COLOR);
                c.setForeground(TEXT_PRIMARY);
            } else {
                c.setBackground(row % 2 == 0 ? CARD_BG : new Color(249, 250, 251));
                c.setForeground(TEXT_PRIMARY);
            }

            return c;
        }
    }
}