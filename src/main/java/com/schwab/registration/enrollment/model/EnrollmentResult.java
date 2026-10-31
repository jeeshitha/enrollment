package com.schwab.registration.enrollment.model;

public record EnrollmentResult(
        String courseId,
        String learnerId,
        EnrollmentStatus status,
        Integer waitlistPosition,
        String message) {
}
