package edu.univ.erp.auth;

import org.mindrot.jbcrypt.BCrypt;

public class HashUtil {

    // produce a BCrypt hash for storage
    public static String hashPassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null.");
        }
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    // verify a plain password against stored BCrypt hash
    public static boolean checkPassword(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, storedHash);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid hash format: " + e.getMessage());
            return false;
        }
    }

    // optional: remove or keep only for quick manual tests (DO NOT use in production)
    public static void main(String[] args) {
        System.out.println("HashUtil test: remove hard-coded passwords from main usage.");
    }
}
