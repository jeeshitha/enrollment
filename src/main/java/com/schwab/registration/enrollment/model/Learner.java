package com.schwab.registration.enrollment.model;

import java.util.Set;

public record Learner(String id, Set<String> completedCourseIds) {
    public Learner {
        completedCourseIds = Set.copyOf(completedCourseIds);
    }
}
