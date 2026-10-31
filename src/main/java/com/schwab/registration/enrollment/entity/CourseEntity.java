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
import org.hibernate.annotations.Check;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "courses")
@Check(constraints = "capacity >= 0 AND (offering = false OR capacity > 0)")
public class CourseEntity {
    @Id
    @Column(length = 100, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean offering;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "course_prerequisites",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_course_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_course_prerequisite",
                    columnNames = {"course_id", "prerequisite_course_id"}),
            indexes = @Index(name = "idx_course_prerequisite_reverse", columnList = "prerequisite_course_id"))
    private Set<CourseEntity> prerequisites = new LinkedHashSet<>();

    protected CourseEntity() {
    }

    public CourseEntity(String id, String name, int capacity, boolean offering) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.offering = offering;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isOffering() {
        return offering;
    }

    public Set<CourseEntity> getPrerequisites() {
        return prerequisites;
    }

    public void setConfiguration(String name, int capacity, boolean offering) {
        this.name = name;
        this.capacity = capacity;
        this.offering = offering;
    }

    public void setPrerequisites(Set<CourseEntity> prerequisites) {
        this.prerequisites = new LinkedHashSet<>(prerequisites);
    }
}
