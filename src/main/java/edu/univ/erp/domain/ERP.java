package edu.univ.erp.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public class ERP {

    public record Student(
            String userId,
            String rollNo,
            String program,
            int year
    ) {}

    public record Instructor(
            String userId,
            String department
    ) {}

    public record Course(
            String courseId,
            String code,
            String title,
            int credits
    ) {}

    public record Section(
            String sectionId,
            String courseId,
            String instructorId,
            DayOfWeek day,
            LocalTime startTime,
            LocalTime endTime,
            String room,
            int capacity,
            String semester,
            int year
    ) {}

    public record Enrollment(
            String enrollmentId,
            String studentId,
            String sectionId,
            String status
    ) {}

    public record Grade(
            String enrollmentId,
            List<ComponentScore> componentScores,
            Double finalGrade
    ) {}

    public record ComponentScore(
            String componentName,
            double score,
            int weight
    ) {}

    public record Setting(
            String key,
            String value
    ) {}
}