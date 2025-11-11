package edu.univ.erp.data;

import edu.univ.erp.auth.HashUtil;
import edu.univ.erp.domain.Auth.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Executes initial setup: seeding required test users into the Auth DB
 * and their corresponding profiles into the ERP DB.
 */
public class InitialDataSeeder {

    private static final String DEFAULT_PASSWORD = "password123";

    public static void main(String[] args) {
        seedUsers();
    }

    private static void seedUsers() {
        System.out.println("Starting data seeding...");

        seedUser("admin1", Role.ADMIN, "Admin User");
        seedUser("inst1", Role.INSTRUCTOR, "Instructor Profile");
        seedUser("stu1", Role.STUDENT, "Student A Profile");
        seedUser("stu2", Role.STUDENT, "Student B Profile");

        System.out.println("\n✅ Initial required user data seeded successfully! (Username/Password: user/" + DEFAULT_PASSWORD + ")");
    }

    private static void seedUser(String username, Role role, String erpProfileDetails) {
        String userId = UUID.randomUUID().toString();
        String hashedPassword = HashUtil.hashPassword(DEFAULT_PASSWORD);
        long currentTimestamp = System.currentTimeMillis();

        try (Connection authConn = DBConfig.getAuthConnection()) {
            // --- A. Insert into Auth DB ---
            String authSql = "INSERT INTO users_auth (user_id, username, role, password_hash, status, last_login) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement authStmt = authConn.prepareStatement(authSql)) {
                authStmt.setString(1, userId);
                authStmt.setString(2, username);
                authStmt.setString(3, role.name());
                authStmt.setString(4, hashedPassword);
                authStmt.setString(5, "ACTIVE");
                authStmt.setLong(6, currentTimestamp);
                authStmt.executeUpdate();
                System.out.println("   - Auth DB: Inserted user " + username + " (" + role.name() + ")");
            }

            // --- B. Insert into ERP DB (Profile) ---
            try (Connection erpConn = DBConfig.getErpConnection()) {
                if (role == Role.STUDENT) {
                    String erpSql = "INSERT INTO students (user_id, roll_no, program, year) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement erpStmt = erpConn.prepareStatement(erpSql)) {
                        erpStmt.setString(1, userId);
                        erpStmt.setString(2, "R-" + (int)(Math.random() * 9000 + 1000));
                        erpStmt.setString(3, "BTech CSE");
                        erpStmt.setInt(4, 2025);
                        erpStmt.executeUpdate();
                        System.out.println("     - ERP DB: Created Student profile for " + username);
                    }
                } else if (role == Role.INSTRUCTOR) {
                    String erpSql = "INSERT INTO instructors (user_id, department) VALUES (?, ?)"; // Simplified insert
                    try (PreparedStatement erpStmt = erpConn.prepareStatement(erpSql)) {
                        erpStmt.setString(1, userId);
                        erpStmt.setString(2, "CS");
                        erpStmt.executeUpdate();
                        System.out.println("     - ERP DB: Created Instructor profile for " + username);
                    }
                }
                if (role == Role.ADMIN) {
                    System.out.println("     - ERP DB: Admin user requires no specific profile table entry.");
                }
            }

        } catch (SQLException e) {
            System.err.println("!!! ERROR seeding user " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}