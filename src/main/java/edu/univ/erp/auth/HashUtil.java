package edu.univ.erp.auth;

import org.mindrot.jbcrypt.BCrypt;

public class HashUtil{

    public static String hashPassword(String Password){
        if(Password==null){
            throw new IllegalArgumentException("Password cannot be null.");
        }
        return BCrypt.hashpw(Password, BCrypt.gensalt());
    }

    public static boolean checkPassword(String Password, String storedHash){
        if(Password==null||storedHash==null){
            return false;  // cannot match
        }
        try{
            return BCrypt.checkpw(Password,storedHash);
        }catch(IllegalArgumentException e){
            System.err.println("Invalid hash format: " + e.getMessage());
            return false;
        }
    }
}