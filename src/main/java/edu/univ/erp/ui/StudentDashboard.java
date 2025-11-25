package edu.univ.erp.ui;

import edu.univ.erp.data.DBConfig;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Vector;

public class StudentDashboard extends JFrame {

    private final String userId;
    private final String username;
    private JTable enrollTable;
    private DefaultTableModel model;

    public StudentDashboard(String userId, String username) {
        super("Student Dashboard - " + username);
        this.userId = userId;
        this.username = username;

        initUI();
        loadEnrollments();
    }

    private void initUI() {
        setSize(900, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JLabel title = new JLabel("Welcome, " + username);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        model = new DefaultTableModel();
        model.setColumnIdentifiers(new Object[] {
                "Section ID", "Course Code", "Course Title", "Instructor", "Semester", "Year", "Room", "Day", "Time"
        });

        enrollTable = new JTable(model);
        JScrollPane scroll = new JScrollPane(enrollTable);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadEnrollments());

        JButton downloadBtn = new JButton("Download Transcript (CSV)");
        downloadBtn.addActionListener(e -> downloadTranscript());

        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.WEST);

        JPanel bottom = new JPanel();
        bottom.add(refreshBtn);
        bottom.add(downloadBtn);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);
    }

    private void loadEnrollments() {
        model.setRowCount(0);

        /*
         * Important: users_auth lives in the auth_db database while this connection
         * uses the ERP DB (erp_db). Previously the code tried to LEFT JOIN users_auth
         * without qualifying the database which caused MySQL to look for it in erp_db
         * and fail. To avoid cross-db joins here we join the instructors table (in erp_db)
         * and display the instructor user_id. If you want to show the username (not id)
         * you can do a separate lookup against auth_db using DBConfig.getAuthConnection().
         */

        String sql =
                "SELECT sec.section_id, c.course_code, c.course_name, " +
                        "       COALESCE(instr.user_id, 'TBA') AS instructor_id, " +
                        "       sec.semester, sec.year, sec.room, sec.day, CONCAT(sec.start_time, ' - ', sec.end_time) AS time " +
                        "FROM enrollments e " +
                        "JOIN sections sec ON e.section_id = sec.section_id " +
                        "JOIN courses c ON sec.course_id = c.course_id " +
                        "LEFT JOIN instructors instr ON sec.instructor_id = instr.user_id " +
                        "WHERE e.student_id = ? AND e.status = 'ENROLLED'";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("section_id"));
                    row.add(rs.getString("course_code"));
                    row.add(rs.getString("course_name"));
                    row.add(rs.getString("instructor_id"));
                    row.add(rs.getString("semester"));
                    row.add(rs.getInt("year"));
                    row.add(rs.getString("room"));
                    row.add(rs.getString("day"));
                    row.add(rs.getString("time"));
                    model.addRow(row);
                }
                if (!any) {
                    // Optionally show a friendly message but don't spam on refresh
                    System.out.println("No enrollments found for " + username);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load enrollments: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void downloadTranscript() {
        try (Connection conn = DBConfig.getErpConnection()) {
            String sql = "SELECT c.course_code, c.course_name, g.final_grade " +
                    "FROM enrollments e " +
                    "JOIN sections s ON e.section_id = s.section_id " +
                    "JOIN courses c ON s.course_id = c.course_id " +
                    "LEFT JOIN grades g ON g.enrollment_id = e.enrollment_id " +
                    "WHERE e.student_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {

                    String file = "transcript_" + username + ".csv";
                    try (FileWriter fw = new FileWriter(file)) {
                        fw.write("CourseCode,CourseName,FinalGrade\n");
                        while (rs.next()) {
                            String code = rs.getString("course_code");
                            String name = rs.getString("course_name");
                            String grade = rs.getString("final_grade");
                            fw.write(String.format("%s,%s,%s\n", escapeCsv(code), escapeCsv(name), grade == null ? "" : escapeCsv(grade)));
                        }
                    }
                    JOptionPane.showMessageDialog(this, "Transcript saved as: " + file);

                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to download transcript: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // small CSV escaper for commas/newlines
    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\n") || s.contains("\r") || s.contains("\"") ) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    // For quick manual testing
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Replace the userId below with an actual user_id value from your students table
            StudentDashboard d = new StudentDashboard("33ea148a-c4c8-11f0-bc78-00090faa0001", "stu1001");
            d.setVisible(true);
        });
    }
}
