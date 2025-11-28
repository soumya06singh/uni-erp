package edu.univ.erp.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


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

    public static Connection getConnection(String dbName) throws SQLException {

        String url = String.format(
                "jdbc:mysql://%s:%d/%s?serverTimezone=UTC&useSSL=false&useUnicode=true&characterEncoding=UTF-8",
                HOST, PORT, dbName
        );

        Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
        applyUtf8mb4Session(conn);
        return conn;
    }

    public static Connection getAuthConnection() throws SQLException {
        return getConnection(AUTH_DB_NAME);
    }

    public static Connection getErpConnection() throws SQLException {
        return getConnection(ERP_DB_NAME);
    }

    private static void applyUtf8mb4Session(Connection conn) {
        try (Statement st = conn.createStatement()) {
            // tell server we want utf8mb4 results and utf8mb4 collation for comparisons
            st.execute("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
            st.execute("SET collation_connection = utf8mb4_unicode_ci");
        } catch (SQLException ex) {
            System.err.println("Warning: failed to apply utf8mb4 session settings: " + ex.getMessage());
        }
    }
}
