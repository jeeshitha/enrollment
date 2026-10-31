package com.schwab.registration.enrollment.model;

import java.util.Set;

public record CourseOffering(String id, String name, int capacity, Set<String> prerequisiteCourseIds) {
    public CourseOffering {
        prerequisiteCourseIds = Set.copyOf(prerequisiteCourseIds);
    }
}
