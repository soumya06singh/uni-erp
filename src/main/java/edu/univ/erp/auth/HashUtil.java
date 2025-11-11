package edu.univ.erp.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility class for handling password hashing and verification using BCrypt.
 */
public class HashUtil {

    private static final int LOG_ROUNDS = 12;

    public static String hashPassword(String plaintextPassword) {
        return BCrypt.hashpw(plaintextPassword, BCrypt.gensalt(LOG_ROUNDS));
    }

    public static boolean checkPassword(String plaintextPassword, String storedHash) {
        try {
            return BCrypt.checkpw(plaintextPassword, storedHash);
        } catch (IllegalArgumentException e) {
            System.err.println("Error checking hash: " + e.getMessage());
            return false;
        }
    }
}