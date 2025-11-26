package edu.univ.erp.service;

import edu.univ.erp.data.DBConfig;
import edu.univ.erp.domain.ServiceResult;
import java.time.LocalDate;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for Student operations
 * Handles all business logic for student enrollment, registration, and grade viewing
 */
public class StudentService {

    /**
     * Simple DTO for enrollment display with course details
     */
    public record EnrollmentView(
            String sectionId,
            String courseCode,
            String courseName,
            int credits,
            String instructorId,
            String semester,
            String room,
            String status
    ) {}

    /**
     * Simple DTO for course catalog with availability
     */
    public record CourseCatalogView(
            String sectionId,
            String courseCode,
            String courseName,
            int credits,
            String instructorId,
            String semester,
            String room,
            int capacity,
            int enrolled,
            int available
    ) {}

    /**
     * Simple DTO for timetable entries
     */
    public record TimetableView(
            String day,
            String time,
            String course,
            String sectionId,
            String room,
            String instructorId
    ) {}

    /**
     * Simple DTO for grade display
     */
    public record GradeView(
            String courseCode,
            String courseName,
            String sectionId,
            String component,
            Double score,
            String finalGrade
    ) {}

    /**
     * Simple DTO for transcript entries
     */
    public record TranscriptView(
            String courseCode,
            String courseName,
            int credits,
            String semester,
            int year,
            String finalGrade
    ) {}

    /**
     * Register a student for a section
     * Checks: maintenance mode, capacity, duplicate enrollment
     */
    public ServiceResult<String> registerForSection(String studentId, String sectionId) {
        // Check maintenance mode
        if (isMaintenanceMode()) {
            return ServiceResult.error("System is in maintenance mode. Registration is disabled.");
        }

        try (Connection conn = DBConfig.getErpConnection()) {

            // 1) Check section capacity (only ENROLLED count)
            String capacityCheck = """
                SELECT s.capacity, COALESCE(COUNT(e.enrollment_id), 0) AS enrolled
                FROM sections s
                LEFT JOIN enrollments e
                       ON s.section_id = e.section_id
                      AND e.status = 'ENROLLED'
                WHERE s.section_id = ?
                GROUP BY s.section_id, s.capacity
                """;

            try (PreparedStatement ps = conn.prepareStatement(capacityCheck)) {
                ps.setString(1, sectionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return ServiceResult.error("Section not found.");
                    }
                    int capacity = rs.getInt("capacity");
                    int enrolled = rs.getInt("enrolled");
                    if (enrolled >= capacity) {
                        return ServiceResult.error("Section is full. No seats available.");
                    }
                }
            }

            // 2) See if an enrollment row already exists for this student+section
            String existingSql = """
                SELECT enrollment_id, status
                FROM enrollments
                WHERE student_id = ? AND section_id = ?
                """;

            Integer existingEnrollmentId = null;
            String existingStatus = null;

            try (PreparedStatement ps = conn.prepareStatement(existingSql)) {
                ps.setString(1, studentId);
                ps.setString(2, sectionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        existingEnrollmentId = rs.getInt("enrollment_id");
                        existingStatus = rs.getString("status");
                    }
                }
            }

            // 3) Decide what to do based on existing row (if any)
            if (existingEnrollmentId != null) {
                if ("ENROLLED".equalsIgnoreCase(existingStatus)) {
                    // already enrolled
                    return ServiceResult.error("You are already enrolled in this section.");
                } else if ("COMPLETED".equalsIgnoreCase(existingStatus)) {
                    // you can choose the policy here; I'll block re-enroll for now
                    return ServiceResult.error("You have already completed this course; cannot re-register.");
                } else if ("DROPPED".equalsIgnoreCase(existingStatus)) {
                    // Re-activate the SAME row instead of inserting a new one
                    String reactivateSql = """
                        UPDATE enrollments
                        SET status = 'ENROLLED',
                            enrollment_date = NOW(),
                            drop_date = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE enrollment_id = ?
                        """;
                    try (PreparedStatement ps = conn.prepareStatement(reactivateSql)) {
                        ps.setInt(1, existingEnrollmentId);
                        int rows = ps.executeUpdate();
                        if (rows > 0) {
                            return ServiceResult.success(
                                    "Re-registered for section " + sectionId,
                                    sectionId
                            );
                        } else {
                            return ServiceResult.error("Failed to re-register. Please try again.");
                        }
                    }
                }
            }

            // 4) No existing row → insert a brand new enrollment
            String insertSql = """
                INSERT INTO enrollments (student_id, section_id, status, enrollment_date)
                VALUES (?, ?, 'ENROLLED', NOW())
                """;

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, studentId);
                ps.setString(2, sectionId);
                int rows = ps.executeUpdate();

                if (rows > 0) {
                    return ServiceResult.success("Successfully registered for section " + sectionId, sectionId);
                } else {
                    return ServiceResult.error("Failed to register. Please try again.");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Database error: " + e.getMessage());
        }
    }

    /**
     * Drop a section for a student
     */
    public ServiceResult<String> dropSection(String studentId, String sectionId) {
        // Check maintenance mode
        if (isMaintenanceMode()) {
            return ServiceResult.error("System is in maintenance mode. Cannot drop sections.");
        }

        try (Connection conn = DBConfig.getErpConnection()) {
            if (isPastDropDeadline(conn)) {
                return ServiceResult.error("Drop deadline has passed. You can no longer drop sections.");
            }

            String sql = "UPDATE enrollments SET status = 'DROPPED', drop_date = NOW() " +
                    "WHERE student_id = ? AND section_id = ? AND status = 'ENROLLED'";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, studentId);
                ps.setString(2, sectionId);
                int rows = ps.executeUpdate();

                if (rows > 0) {
                    return ServiceResult.success("Successfully dropped section " + sectionId, sectionId);
                } else {
                    return ServiceResult.error("Enrollment not found or already dropped.");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Database error: " + e.getMessage());
        }
    }

    /**
     * Get all enrollments for a student
     */
    public List<EnrollmentView> getStudentEnrollments(String studentId) {
        List<EnrollmentView> enrollments = new ArrayList<>();

        String sql = "SELECT sec.section_id, c.course_code, c.course_name, c.credits, " +
                "COALESCE(instr.user_id, 'TBA') AS instructor_id, " +
                "sec.semester, sec.room, e.status " +
                "FROM enrollments e " +
                "JOIN sections sec ON e.section_id = sec.section_id " +
                "JOIN courses c ON sec.course_id = c.course_id " +
                "LEFT JOIN instructors instr ON sec.instructor_id = instr.user_id " +
                "WHERE e.student_id = ? " +
                "ORDER BY sec.semester, sec.year, c.course_code";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    enrollments.add(new EnrollmentView(
                            rs.getString("section_id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getInt("credits"),
                            rs.getString("instructor_id"),
                            rs.getString("semester"),
                            rs.getString("room"),
                            rs.getString("status")
                    ));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return enrollments;
    }

    /**
     * Get course catalog with availability
     */
    public List<CourseCatalogView> getCourseCatalog(String keyword, String semester) {
        List<CourseCatalogView> sections = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT sec.section_id, c.course_code, c.course_name, c.credits, " +
                        "COALESCE(instr.user_id, 'TBA') AS instructor_id, " +
                        "sec.semester, sec.room, sec.capacity, " +
                        "COALESCE(COUNT(e.enrollment_id), 0) AS enrolled " +
                        "FROM sections sec " +
                        "JOIN courses c ON sec.course_id = c.course_id " +
                        "LEFT JOIN instructors instr ON sec.instructor_id = instr.user_id " +
                        "LEFT JOIN enrollments e ON sec.section_id = e.section_id AND e.status = 'ENROLLED' " +
                        "WHERE 1=1 "
        );

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND (c.course_code LIKE ? OR c.course_name LIKE ?) ");
        }
        if (semester != null && !semester.trim().isEmpty()) {
            sql.append("AND sec.semester = ? ");
        }

        sql.append("GROUP BY sec.section_id, c.course_code, c.course_name, c.credits, " +
                "instr.user_id, sec.semester, sec.room, sec.capacity " +
                "ORDER BY c.course_code, sec.section_id");

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (keyword != null && !keyword.trim().isEmpty()) {
                String search = "%" + keyword + "%";
                ps.setString(paramIndex++, search);
                ps.setString(paramIndex++, search);
            }
            if (semester != null && !semester.trim().isEmpty()) {
                ps.setString(paramIndex++, semester);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int capacity = rs.getInt("capacity");
                    int enrolled = rs.getInt("enrolled");
                    int available = capacity - enrolled;

                    sections.add(new CourseCatalogView(
                            rs.getString("section_id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getInt("credits"),
                            rs.getString("instructor_id"),
                            rs.getString("semester"),
                            rs.getString("room"),
                            capacity,
                            enrolled,
                            available
                    ));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return sections;
    }

    /**
     * Get student timetable
     */
    public List<TimetableView> getStudentTimetable(String studentId) {
        List<TimetableView> timetable = new ArrayList<>();

        String sql = "SELECT sec.day, CONCAT(sec.start_time, ' - ', sec.end_time) AS time, " +
                "CONCAT(c.course_code, ' - ', c.course_name) AS course, " +
                "sec.section_id, sec.room, COALESCE(instr.user_id, 'TBA') AS instructor_id " +
                "FROM enrollments e " +
                "JOIN sections sec ON e.section_id = sec.section_id " +
                "JOIN courses c ON sec.course_id = c.course_id " +
                "LEFT JOIN instructors instr ON sec.instructor_id = instr.user_id " +
                "WHERE e.student_id = ? AND e.status = 'ENROLLED' " +
                "ORDER BY FIELD(sec.day, 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'), " +
                "sec.start_time";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    timetable.add(new TimetableView(
                            rs.getString("day"),
                            rs.getString("time"),
                            rs.getString("course"),
                            rs.getString("section_id"),
                            rs.getString("room"),
                            rs.getString("instructor_id")
                    ));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return timetable;
    }

    /**
     * Get student grades
     */
        public List<GradeView> getStudentGrades(String studentId) {
            List<GradeView> grades = new ArrayList<>();

            String sql = "SELECT c.course_code, c.course_name, sec.section_id, " +
                    "g.component, g.score, g.final_grade " +
                    "FROM enrollments e " +
                    "JOIN sections sec ON e.section_id = sec.section_id " +
                    "JOIN courses c ON sec.course_id = c.course_id " +
                    "LEFT JOIN grades g ON g.enrollment_id = e.enrollment_id " +
                    "WHERE e.student_id = ? AND e.status <> 'DROPPED' " +
                    "ORDER BY c.course_code, sec.section_id, g.component";

            try (Connection conn = DBConfig.getErpConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Object scoreObj = rs.getObject("score");
                        Double score = scoreObj != null ? rs.getDouble("score") : null;

                        grades.add(new GradeView(
                                rs.getString("course_code"),
                                rs.getString("course_name"),
                                rs.getString("section_id"),
                                rs.getString("component"),
                                score,
                                rs.getString("final_grade")
                        ));
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

            return grades;
        }

        /**
         * Get transcript data for CSV export
         */
    public List<TranscriptView> getTranscript(String studentId) {
        List<TranscriptView> transcript = new ArrayList<>();

        String sql = "SELECT c.course_code, c.course_name, c.credits, " +
                "sec.semester, sec.year, g.final_grade " +
                "FROM enrollments e " +
                "JOIN sections sec ON e.section_id = sec.section_id " +
                "JOIN courses c ON sec.course_id = c.course_id " +
                "LEFT JOIN grades g ON g.enrollment_id = e.enrollment_id AND g.component IS NULL " +
                "WHERE e.student_id = ? AND e.status = 'ENROLLED' " +
                "ORDER BY sec.year, sec.semester, c.course_code";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transcript.add(new TranscriptView(
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getInt("credits"),
                            rs.getString("semester"),
                            rs.getInt("year"),
                            rs.getString("final_grade")
                    ));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return transcript;
    }

    /**
     * Check if maintenance mode is enabled
     */
    public boolean isMaintenanceMode() {
        String sql = "SELECT value FROM settings WHERE `key` = 'maintenance_mode'";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                String value = rs.getString("value");
                return "true".equalsIgnoreCase(value);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
    private boolean isPastDropDeadline(Connection conn) throws SQLException {
        String sql = "SELECT `value` FROM settings WHERE `key` = 'drop_deadline'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                // No deadline configured → allow drop
                return false;
            }

            String value = rs.getString("value");
            if (value == null || value.isBlank()) {
                return false;
            }

            // Expecting YYYY-MM-DD
            LocalDate deadline = LocalDate.parse(value.trim());
            LocalDate today = LocalDate.now();

            return today.isAfter(deadline);
        }
    }

}