package edu.univ.erp.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages database connection configuration for both Auth DB and ERP DB.
 */
public class DBConfig {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String HOST = "localhost";
    private static final int PORT = 3306;

    // CHANGE THESE to match your local MySQL configuration!
    private static final String DB_USER = "root";
    private static final String DB_PASS = "soumya2006"; // <--- **CHANGE THIS!**

    private static final String AUTH_DB_NAME = "auth_db";
    private static final String ERP_DB_NAME = "erp_db";

    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found. Check pom.xml and libraries.");
            e.printStackTrace();
        }
    }

    public static Connection getConnection(String dbName) throws SQLException {
        String url = String.format("jdbc:mysql://%s:%d/%s?serverTimezone=UTC&useSSL=false",
                HOST, PORT, dbName);
        return DriverManager.getConnection(url, DB_USER, DB_PASS);
    }

    public static Connection getAuthConnection() throws SQLException {
        return getConnection(AUTH_DB_NAME);
    }

    public static Connection getErpConnection() throws SQLException {
        return getConnection(ERP_DB_NAME);
    }
}