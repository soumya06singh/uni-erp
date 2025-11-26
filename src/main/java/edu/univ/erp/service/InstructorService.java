package edu.univ.erp.service;

import edu.univ.erp.data.DBConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * InstructorService
 *
 * Small service layer isolating DB access for instructor-related operations.
 * Returns simple DTO records used by the UI.
 */
public class InstructorService {

    public record SectionRow(
            String sectionId,
            String courseCode,
            String courseName,
            String semester,
            int year,
            String day,
            String startTime,
            String endTime,
            String room,
            int capacity
    ) {}

    public record RosterRow(
            String enrollmentId,
            String studentId,
            String rollNo,
            String studentName,
            Double quiz,
            Double midterm,
            Double endsem,
            Double finalScore
    ) {}

    public record GradeRow(
            String enrollmentId,
            String component, // QUIZ, MIDTERM, ENDSEM, FINAL
            Double score,
            int weight
    ) {}

    /**
     * Get department for instructor
     */
    public String getDepartment(String instructorUserId) {
        String sql = "SELECT department FROM instructors WHERE user_id = ?";
        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instructorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("department");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Get sections for the instructor
     */
    public List<SectionRow> getSectionsForInstructor(String instructorUserId) {
        List<SectionRow> out = new ArrayList<>();
        String sql = "SELECT s.section_id, c.course_code, c.course_name, s.semester, s.year, s.day, s.start_time, s.end_time, s.room, s.capacity " +
                "FROM sections s JOIN courses c ON s.course_id = c.course_id " +
                "WHERE s.instructor_id = ?";
        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instructorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SectionRow(
                            rs.getString("section_id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getString("semester"),
                            rs.getInt("year"),
                            rs.getString("day"),
                            rs.getString("start_time"),
                            rs.getString("end_time"),
                            rs.getString("room"),
                            rs.getInt("capacity")
                    ));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return out;
    }

    /**
     * Get roster for a section (enrolled students + existing grade components)
     */
    public List<RosterRow> getRosterForSection(String sectionId) {
        List<RosterRow> out = new ArrayList<>();
        String sql = "SELECT e.enrollment_id, st.user_id as student_id, st.roll_no, u.username as student_name " +
                "FROM enrollments e JOIN students st ON e.student_id = st.user_id " +
                "LEFT JOIN auth_db.users_auth u ON st.user_id = u.user_id " +
                "WHERE e.section_id = ? AND (e.status IS NULL OR e.status = 'ENROLLED')";
        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sectionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String enrollmentId = rs.getString("enrollment_id");
                    String studentId = rs.getString("student_id");
                    String rollNo = rs.getString("roll_no");
                    String studentName = rs.getString("student_name");

                    // fetch existing components
                    Double quiz = null, mid = null, end = null, fin = null;
                    String gsql = "SELECT component, score FROM grades WHERE enrollment_id = ?";
                    try (PreparedStatement gps = conn.prepareStatement(gsql)) {
                        gps.setString(1, enrollmentId);
                        try (ResultSet grs = gps.executeQuery()) {
                            while (grs.next()) {
                                String comp = grs.getString("component");
                                Object obj = grs.getObject("score");
                                Double sc = null;
                                if (obj instanceof Number) sc = ((Number) obj).doubleValue();
                                else if (obj != null) {
                                    try { sc = Double.parseDouble(obj.toString()); } catch (Exception ignored) {}
                                }
                                if ("QUIZ".equalsIgnoreCase(comp)) quiz = sc;
                                else if ("MIDTERM".equalsIgnoreCase(comp)) mid = sc;
                                else if ("ENDSEM".equalsIgnoreCase(comp)) end = sc;
                                else if ("FINAL".equalsIgnoreCase(comp)) fin = sc;
                            }
                        }
                    }

                    out.add(new RosterRow(enrollmentId, studentId, rollNo, studentName, quiz, mid, end, fin));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return out;
    }
    private Double parseDoubleOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            String s = o.toString().trim();
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Save a batch of grade rows (upsert)
     * Expects a list of GradeRow built from the UI.
     */
    /**
     * Save a batch of grade rows (upsert)
     * Expects a list of GradeRow built from the UI.
     */


       /**
     * Try common settings column variants to detect maintenance mode.
     */
    /**
     * Save a batch of grade rows (upsert)
     * Expects a list of GradeRow built from the UI.
     */
    /**
     * Save a batch of grade rows (upsert)
     * Expects a list of GradeRow built from the UI.
     */
    public void saveGradesBatch(List<GradeRow> grades) throws SQLException {
        if (grades == null || grades.isEmpty()) return;

        String upsertSql = """
INSERT INTO grades (enrollment_id, component, score, max_score)
VALUES (?, ?, ?, ?)
ON DUPLICATE KEY UPDATE score = VALUES(score), max_score = VALUES(max_score)
""";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            conn.setAutoCommit(false);
            try {
                for (GradeRow g : grades) {
                    ps.setString(1, g.enrollmentId());
                    ps.setString(2, g.component());
                    if (g.score() == null) ps.setNull(3, Types.DOUBLE);
                    else ps.setDouble(3, g.score());

                    // All scores are out of 100
                    ps.setInt(4, 100);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
    public boolean isMaintenanceMode() {
        String[][] combos = new String[][] {
                {"`key`", "`value`"},
                {"`key`", "setting_value"},
                {"setting_key", "`value`"},
                {"setting_key", "setting_value"},
                {"key", "value"},
                {"key", "setting_value"},
                {"setting_key", "value"},
                {"setting_key", "setting_value"}
        };

        try (Connection conn = DBConfig.getErpConnection()) {
            for (String[] comb : combos) {
                String keyCol = comb[0];
                String valueCol = comb[1];
                String sql = String.format("SELECT %s as v FROM settings WHERE %s = 'maintenance_mode' LIMIT 1", valueCol, keyCol);
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String s = rs.getString("v");
                        if (s != null && !s.isBlank()) return Boolean.parseBoolean(s.trim());
                    }
                } catch (SQLException ignored) {
                    // try next
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }
}
