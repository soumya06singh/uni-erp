package edu.univ.erp.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages database connection configuration for both Auth DB and ERP DB.
 */
public class DBConfig {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String HOST = "localhost";
    private static final int PORT = 3306;

    private static final String DB_USER = "root";
    private static final String DB_PASS = "bhavya123";

    private static final String AUTH_DB_NAME = "auth_db";
    private static final String ERP_DB_NAME = "erp_db";

    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found. Check libraries.");
            e.printStackTrace();
        }
    }

    // --------------------------
    // MAIN CONNECTION CREATOR
    // --------------------------
    public static Connection getConnection(String dbName) throws SQLException {
        String url = String.format(
                "jdbc:mysql://%s:%d/%s?serverTimezone=UTC&useSSL=false&useUnicode=true&characterEncoding=utf8mb4",
                HOST, PORT, dbName
        );

        Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
        applyUtf8mb4Session(conn);
        return conn;
    }

    // --------------------------
    // AUTH + ERP CONNECTIONS
    // --------------------------
    public static Connection getAuthConnection() throws SQLException {
        return getConnection(AUTH_DB_NAME);
    }

    public static Connection getErpConnection() throws SQLException {
        return getConnection(ERP_DB_NAME);
    }

    // --------------------------
    // FIX: Set session collation to utf8mb4_unicode_ci
    // --------------------------
    private static void applyUtf8mb4Session(Connection conn) {
        try (Statement st = conn.createStatement()) {

            // Makes communication use utf8mb4
            st.execute("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");

            // Ensures SQL comparisons use the same collation
            st.execute("SET collation_connection = utf8mb4_unicode_ci");

        } catch (SQLException ex) {
            System.err.println("Warning: Failed to apply utf8mb4 session settings: " + ex.getMessage());
        }
    }
}
