package com.schwab.registration.enrollment.model;

import java.util.List;

public record OfferingStatus(
        CourseOffering course,
        List<String> enrolledLearnerIds,
        List<String> waitlistedLearnerIds,
        int availableSeats) {
}
