package edu.univ.erp.service;

import edu.univ.erp.data.DBConfig;
import edu.univ.erp.domain.ServiceResult;
import edu.univ.erp.auth.HashUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for Admin operations
 * Handles user management, course management, section management, and system settings
 */
public class AdminService {

    // ==================== VIEW DTOs ====================

    public record UserView(
            String userId,
            String username,
            String role,
            String status,
            String specificId  // roll_no for students, department for instructors
    ) {}

    public record StudentView(
            String userId,
            String rollNo,
            String program,
            int yearOfStudy,
            String enrollmentDate
    ) {}

    public record InstructorView(
            String userId,
            String department,
            String designation,
            String officeRoom
    ) {}

    public record CourseView(
            int courseId,
            String courseCode,
            String courseName,
            int credits,
            String description
    ) {}

    public record SectionView(
            String sectionId,
            int courseId,
            String courseCode,
            String courseName,
            String instructorId,
            String semester,
            int year,
            String day,
            String startTime,
            String endTime,
            String room,
            int capacity,
            int enrolled
    ) {}

    // ==================== USER MANAGEMENT ====================

    /**
     * Add a new student (creates entry in both auth_db and erp_db)
     */
    public ServiceResult<String> addStudent(String username, String password, String rollNo,
                                            String program, int yearOfStudy) {
        String userId = UUID.randomUUID().toString();

        String passwordHash = HashUtil.hashPassword(password);

        try {
            // 1. Insert into auth_db
            try (Connection authConn = DBConfig.getAuthConnection()) {
                String authSql = "INSERT INTO users_auth (user_id, username, role, password_hash, status, last_login) " +
                        "VALUES (?, ?, 'STUDENT', ?, 'ACTIVE', NULL)";


                try (PreparedStatement ps = authConn.prepareStatement(authSql)) {
                    ps.setString(1, userId);
                    ps.setString(2, username);
                    ps.setString(3, passwordHash);
                    ps.executeUpdate();
                }
            }

            // 2. Insert into erp_db students table
            try (Connection erpConn = DBConfig.getErpConnection()) {
                String erpSql = "INSERT INTO students (user_id, roll_no, program, year_of_study, enrollment_date) " +
                        "VALUES (?, ?, ?, ?, CURDATE())";

                try (PreparedStatement ps = erpConn.prepareStatement(erpSql)) {
                    ps.setString(1, userId);
                    ps.setString(2, rollNo);
                    ps.setString(3, program);
                    ps.setInt(4, yearOfStudy);
                    ps.executeUpdate();
                }
            }

            return ServiceResult.success("Student added successfully!", userId);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to add student: " + e.getMessage());
        }
    }

    /**
     * Add a new instructor
     */
    public ServiceResult<String> addInstructor(String username, String password, String department,
                                               String designation, String officeRoom) {
        String userId = "inst" + String.format("%03d", (int)(Math.random() * 1000));
        String passwordHash = HashUtil.hashPassword(password);

        try {
            // 1. Insert into auth_db
            try (Connection authConn = DBConfig.getAuthConnection()) {
                String authSql = "INSERT INTO users_auth (user_id, username, role, password_hash, status, last_login) " +
                        "VALUES (?, ?, 'ADMIN', ?, 'ACTIVE', NULL)";


                try (PreparedStatement ps = authConn.prepareStatement(authSql)) {
                    ps.setString(1, userId);
                    ps.setString(2, username);
                    ps.setString(3, passwordHash);
                    ps.executeUpdate();
                }
            }

            // 2. Insert into erp_db instructors table
            try (Connection erpConn = DBConfig.getErpConnection()) {
                String erpSql = "INSERT INTO instructors (user_id, department, designation, office_room) " +
                        "VALUES (?, ?, ?, ?)";

                try (PreparedStatement ps = erpConn.prepareStatement(erpSql)) {
                    ps.setString(1, userId);
                    ps.setString(2, department);
                    ps.setString(3, designation);
                    ps.setString(4, officeRoom);
                    ps.executeUpdate();
                }
            }

            return ServiceResult.success("Instructor added successfully!", userId);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to add instructor: " + e.getMessage());
        }
    }

    /**
     * Add a new admin
     */
    public ServiceResult<String> addAdmin(String username, String password) {
        String userId = "admin" + String.format("%03d", (int)(Math.random() * 1000));
        String passwordHash = HashUtil.hashPassword(password);

        try (Connection authConn = DBConfig.getAuthConnection()) {
            String sql = "INSERT INTO users_auth (user_id, username, role, password_hash, status, last_login) " +
                    "VALUES (?, ?, 'ADMIN', ?, 'ACTIVE', NULL)";

            try (PreparedStatement ps = authConn.prepareStatement(sql)) {
                ps.setString(1, userId);
                ps.setString(2, username);
                ps.setString(3, passwordHash);
                ps.executeUpdate();
            }

            return ServiceResult.success("Admin added successfully!", userId);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to add admin: " + e.getMessage());
        }
    }

    /**
     * Get all users with their roles
     */
    public List<UserView> getAllUsers() {
        List<UserView> users = new ArrayList<>();

        String sql = "SELECT user_id, username, role, status FROM users_auth ORDER BY role, username";

        try (Connection conn = DBConfig.getAuthConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String userId = rs.getString("user_id");
                String role = rs.getString("role");
                String specificId = getSpecificIdForUser(userId, role);

                users.add(new UserView(
                        userId,
                        rs.getString("username"),
                        role,
                        rs.getString("status"),
                        specificId
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return users;
    }

    private String getSpecificIdForUser(String userId, String role) {
        if ("STUDENT".equals(role)) {
            try (Connection conn = DBConfig.getErpConnection()) {
                String sql = "SELECT roll_no FROM students WHERE user_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getString("roll_no");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else if ("INSTRUCTOR".equals(role)) {
            try (Connection conn = DBConfig.getErpConnection()) {
                String sql = "SELECT department FROM instructors WHERE user_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getString("department");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return "N/A";
    }

    /**
     * Delete a user (removes from both databases)
     */
    public ServiceResult<String> deleteUser(String userId, String role) {
        try {
            // Delete from ERP DB first (if student/instructor)
            if ("STUDENT".equals(role)) {
                try (Connection conn = DBConfig.getErpConnection()) {
                    String sql = "DELETE FROM students WHERE user_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, userId);
                        ps.executeUpdate();
                    }
                }
            } else if ("INSTRUCTOR".equals(role)) {
                try (Connection conn = DBConfig.getErpConnection()) {
                    String sql = "DELETE FROM instructors WHERE user_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, userId);
                        ps.executeUpdate();
                    }
                }
            }

            // Delete from Auth DB
            try (Connection conn = DBConfig.getAuthConnection()) {
                String sql = "DELETE FROM users_auth WHERE user_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    ps.executeUpdate();
                }
            }

            return ServiceResult.success("User deleted successfully!");

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to delete user: " + e.getMessage());
        }
    }

    // ==================== COURSE MANAGEMENT ====================

    /**
     * Add a new course
     */
    public ServiceResult<Integer> addCourse(String courseCode, String courseName, int credits, String description) {
        String sql = "INSERT INTO courses (course_code, course_name, credits, description) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, courseCode);
            ps.setString(2, courseName);
            ps.setInt(3, credits);
            ps.setString(4, description);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return ServiceResult.success("Course added successfully!", rs.getInt(1));
                }
            }

            return ServiceResult.error("Failed to get course ID");

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to add course: " + e.getMessage());
        }
    }

    /**
     * Get all courses
     */
    public List<CourseView> getAllCourses() {
        List<CourseView> courses = new ArrayList<>();

        String sql = "SELECT course_id, course_code, course_name, credits, description FROM courses ORDER BY course_code";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                courses.add(new CourseView(
                        rs.getInt("course_id"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getInt("credits"),
                        rs.getString("description")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return courses;
    }

    /**
     * Update a course
     */
    public ServiceResult<String> updateCourse(int courseId, String courseCode, String courseName,
                                              int credits, String description) {
        String sql = "UPDATE courses SET course_code = ?, course_name = ?, credits = ?, description = ? WHERE course_id = ?";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, courseCode);
            ps.setString(2, courseName);
            ps.setInt(3, credits);
            ps.setString(4, description);
            ps.setInt(5, courseId);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                return ServiceResult.success("Course updated successfully!");
            } else {
                return ServiceResult.error("Course not found");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to update course: " + e.getMessage());
        }
    }

    /**
     * Delete a course
     */
    public ServiceResult<String> deleteCourse(int courseId) {
        String sql = "DELETE FROM courses WHERE course_id = ?";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, courseId);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                return ServiceResult.success("Course deleted successfully!");
            } else {
                return ServiceResult.error("Course not found");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to delete course: " + e.getMessage());
        }
    }

    // ==================== SECTION MANAGEMENT ====================

    /**
     * Add a new section
     */
    public ServiceResult<String> addSection(int courseId, String instructorId, String semester, int year,
                                            String day, String startTime, String endTime, String room, int capacity) {
        // Generate section_id (e.g., CS101-F24-01)
        String sectionId = generateSectionId(courseId, semester, year);

        String sql = "INSERT INTO sections (section_id, course_id, instructor_id, semester, year, day, start_time, end_time, room, capacity) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sectionId);
            ps.setInt(2, courseId);
            ps.setString(3, instructorId);
            ps.setString(4, semester);
            ps.setInt(5, year);
            ps.setString(6, day);
            ps.setString(7, startTime);
            ps.setString(8, endTime);
            ps.setString(9, room);
            ps.setInt(10, capacity);
            ps.executeUpdate();

            return ServiceResult.success("Section created successfully!", sectionId);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to create section: " + e.getMessage());
        }
    }

    private String generateSectionId(int courseId, String semester, int year) {
        try (Connection conn = DBConfig.getErpConnection()) {
            String sql = "SELECT course_code FROM courses WHERE course_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String courseCode = rs.getString("course_code");
                        String semCode = semester.substring(0, 1); // F, S, or Su
                        String yearCode = String.valueOf(year % 100); // 24, 25, etc.

                        // Count existing sections for this course/semester/year
                        String countSql = "SELECT COUNT(*) FROM sections WHERE course_id = ? AND semester = ? AND year = ?";
                        try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                            countPs.setInt(1, courseId);
                            countPs.setString(2, semester);
                            countPs.setInt(3, year);
                            try (ResultSet countRs = countPs.executeQuery()) {
                                if (countRs.next()) {
                                    int count = countRs.getInt(1) + 1;
                                    return String.format("%s-%s%s-%02d", courseCode, semCode, yearCode, count);
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "SEC-" + System.currentTimeMillis();
    }

    /**
     * Get all sections with enrollment count
     */
    public List<SectionView> getAllSections() {
        List<SectionView> sections = new ArrayList<>();

        String sql = "SELECT s.section_id, s.course_id, c.course_code, c.course_name, " +
                "s.instructor_id, s.semester, s.year, s.day, s.start_time, s.end_time, " +
                "s.room, s.capacity, COUNT(e.enrollment_id) as enrolled " +
                "FROM sections s " +
                "JOIN courses c ON s.course_id = c.course_id " +
                "LEFT JOIN enrollments e ON s.section_id = e.section_id AND e.status = 'ENROLLED' " +
                "GROUP BY s.section_id " +
                "ORDER BY s.year DESC, s.semester, c.course_code";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                sections.add(new SectionView(
                        rs.getString("section_id"),
                        rs.getInt("course_id"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getString("instructor_id"),
                        rs.getString("semester"),
                        rs.getInt("year"),
                        rs.getString("day"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        rs.getString("room"),
                        rs.getInt("capacity"),
                        rs.getInt("enrolled")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return sections;
    }

    /**
     * Assign instructor to section
     */
    public ServiceResult<String> assignInstructor(String sectionId, String instructorId) {
        String sql = "UPDATE sections SET instructor_id = ? WHERE section_id = ?";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, instructorId);
            ps.setString(2, sectionId);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                return ServiceResult.success("Instructor assigned successfully!");
            } else {
                return ServiceResult.error("Section not found");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to assign instructor: " + e.getMessage());
        }
    }

    /**
     * Delete a section
     */
    public ServiceResult<String> deleteSection(String sectionId) {
        String sql = "DELETE FROM sections WHERE section_id = ?";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sectionId);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                return ServiceResult.success("Section deleted successfully!");
            } else {
                return ServiceResult.error("Section not found");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to delete section: " + e.getMessage());
        }
    }

    // ==================== SYSTEM SETTINGS ====================

    /**
     * Toggle maintenance mode
     */
    public ServiceResult<Boolean> toggleMaintenanceMode(boolean enable) {
        String sql = "UPDATE settings SET `value` = ? WHERE `key` = 'maintenance_mode'";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, enable ? "true" : "false");
            ps.executeUpdate();

            return ServiceResult.success(
                    "Maintenance mode " + (enable ? "enabled" : "disabled"),
                    enable
            );

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.error("Failed to toggle maintenance mode: " + e.getMessage());
        }
    }

    /**
     * Get current maintenance mode status
     */
    public boolean getMaintenanceMode() {
        String sql = "SELECT `value` FROM settings WHERE `key` = 'maintenance_mode'";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return "true".equalsIgnoreCase(rs.getString("value"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Get all instructors for dropdown
     */
    public List<InstructorView> getAllInstructors() {
        List<InstructorView> instructors = new ArrayList<>();

        String sql = "SELECT user_id, department, designation, office_room FROM instructors ORDER BY user_id";

        try (Connection conn = DBConfig.getErpConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                instructors.add(new InstructorView(
                        rs.getString("user_id"),
                        rs.getString("department"),
                        rs.getString("designation"),
                        rs.getString("office_room")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return instructors;
    }
}