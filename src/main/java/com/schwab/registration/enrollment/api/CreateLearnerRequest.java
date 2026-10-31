package com.schwab.registration.enrollment.api;

import java.util.Set;

public record CreateLearnerRequest(Set<String> completedCourseIds) {
}
