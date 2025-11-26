package edu.univ.erp.domain;
public class Auth {

    public enum Role {
        STUDENT,
        INSTRUCTOR,
        ADMIN;
    }

    public record UserAuth(
            String userId,
            String username,
            Role role,
            String passwordHash,
            String status,
            long lastLoginTimestamp
    ) {}

    public record Session(
            String userId,
            Role role,
            long lastLoginTimestamp
    ) {}
}