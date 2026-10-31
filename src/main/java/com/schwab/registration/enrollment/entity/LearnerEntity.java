package com.schwab.registration.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.UniqueConstraint;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "learners")
public class LearnerEntity {
    @Id
    @Column(length = 100, nullable = false, updatable = false)
    private String id;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "learner_course_completions",
            joinColumns = @JoinColumn(name = "learner_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_learner_completed_course",
                    columnNames = {"learner_id", "course_id"}),
            indexes = @Index(name = "idx_completed_course_reverse", columnList = "course_id"))
    private Set<CourseEntity> completedCourses = new LinkedHashSet<>();

    protected LearnerEntity() {
    }

    public LearnerEntity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Set<CourseEntity> getCompletedCourses() {
        return completedCourses;
    }

    public void setCompletedCourses(Set<CourseEntity> completedCourses) {
        this.completedCourses = new LinkedHashSet<>(completedCourses);
    }
}
