package edu.univ.erp.ui;

import edu.univ.erp.data.DBConfig;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

/**
 * InstructorDashboard
 * <p>
 * Simple Swing dashboard for instructors. Features:
 * - Shows instructor department info
 * - Lists sections taught by the instructor (from erp_db.sections + erp_db.courses)
 * - Allows viewing roster (students enrolled) for a selected section
 *
 * Usage:
 *   new InstructorDashboard(userId, "inst1").setVisible(true);
 *
 * Notes:
 * - Uses DBConfig.getErpConnection() to read ERP tables and DBConfig.getAuthConnection() if needed
 * - Uses SwingWorker to avoid blocking the Event Dispatch Thread
 */
public class InstructorDashboard extends JFrame {

    private final String instructorUserId;
    private final String username;

    private final JLabel lblWelcome = new JLabel();
    private final JLabel lblDepartment = new JLabel();
    private final JTable tblSections = new JTable();
    private final DefaultTableModel sectionsModel = new DefaultTableModel();
    private final JButton btnRefresh = new JButton("Refresh");
    private final JButton btnViewRoster = new JButton("View Roster");

    public InstructorDashboard(String instructorUserId, String username) {
        super("Instructor Dashboard - " + username);
        this.instructorUserId = instructorUserId;
        this.username = username;
        initUI();
        loadInstructorInfo();
        loadSections();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        lblWelcome.setFont(lblWelcome.getFont().deriveFont(Font.BOLD, 16f));
        lblWelcome.setText("Welcome, " + username);
        topPanel.add(lblWelcome, BorderLayout.WEST);
        topPanel.add(lblDepartment, BorderLayout.EAST);

        // Sections table
        sectionsModel.setColumnIdentifiers(new String[]{"Section ID", "Course Code", "Title", "Semester", "Year", "Day", "Start", "End", "Room", "Capacity"});
        tblSections.setModel(sectionsModel);
        tblSections.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane tableScroll = new JScrollPane(tblSections);

        // Buttons
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(btnRefresh);
        controls.add(btnViewRoster);

        btnRefresh.addActionListener((ActionEvent e) -> loadSections());

        btnViewRoster.addActionListener((ActionEvent e) -> {
            int sel = tblSections.getSelectedRow();
            if (sel < 0) {
                JOptionPane.showMessageDialog(this, "Please select a section first.", "No selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String sectionId = (String) sectionsModel.getValueAt(sel, 0);
            showRosterDialog(sectionId);
        });

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(topPanel, BorderLayout.NORTH);
        getContentPane().add(tableScroll, BorderLayout.CENTER);
        getContentPane().add(controls, BorderLayout.SOUTH);
    }

    private void loadInstructorInfo() {
        // Read department from erp_db.instructors
        new SwingWorker<Void, Void>() {
            private String department = "";

            @Override
            protected Void doInBackground() throws Exception {
                String sql = "SELECT department FROM instructors WHERE user_id = ?";
                try (Connection conn = DBConfig.getErpConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, instructorUserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            department = rs.getString("department");
                        }
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                lblDepartment.setText(department == null || department.isEmpty() ? "Department: -" : "Department: " + department);
            }
        }.execute();
    }

    private void loadSections() {
        // Clear model
        sectionsModel.setRowCount(0);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                String sql = "SELECT s.section_id, c.code, c.title, s.semester, s.year, s.day, s.startTime, s.endTime, s.room, s.capacity " +
                        "FROM sections s JOIN courses c ON s.course_id = c.course_id " +
                        "WHERE s.instructor_id = ?";

                try (Connection conn = DBConfig.getErpConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, instructorUserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Vector<Object> row = new Vector<>();
                            row.add(rs.getString("section_id"));
                            row.add(rs.getString("code"));
                            row.add(rs.getString("title"));
                            row.add(rs.getString("semester"));
                            row.add(rs.getInt("year"));
                            row.add(rs.getString("day"));
                            row.add(rs.getString("startTime"));
                            row.add(rs.getString("endTime"));
                            row.add(rs.getString("room"));
                            row.add(rs.getInt("capacity"));
                            sectionsModel.addRow(row);
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

    private void showRosterDialog(String sectionId) {
        JDialog dlg = new JDialog(this, "Roster: " + sectionId, true);
        dlg.setSize(700, 400);
        dlg.setLocationRelativeTo(this);

        DefaultTableModel rosterModel = new DefaultTableModel();
        rosterModel.setColumnIdentifiers(new String[]{"Student ID", "Roll No", "Program", "Year"});
        JTable tblRoster = new JTable(rosterModel);
        JScrollPane sp = new JScrollPane(tblRoster);

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.add(sp, BorderLayout.CENTER);

        // Load roster in background
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                String sql = "SELECT st.user_id, st.roll_no, st.program, st.year FROM enrollments e " +
                        "JOIN students st ON e.student_id = st.user_id " +
                        "WHERE e.section_id = ? AND (e.status IS NULL OR e.status = 'ENROLLED')";
                try (Connection conn = DBConfig.getErpConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, sectionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Vector<Object> r = new Vector<>();
                            r.add(rs.getString("user_id"));
                            r.add(rs.getString("roll_no"));
                            r.add(rs.getString("program"));
                            r.add(rs.getInt("year"));
                            rosterModel.addRow(r);
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
                // nothing additional
            }
        }.execute();

        dlg.getContentPane().add(p);
        dlg.setVisible(true);
    }

    // Convenience test main - not required for production
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // If you want to test locally, replace the uuid below with a real instructor user_id from your DB
            InstructorDashboard d = new InstructorDashboard("<instructor-user-id>", "inst1");
            d.setVisible(true);
        });
    }
}

