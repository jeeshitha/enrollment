package com.schwab.registration.enrollment.entity;

import com.schwab.registration.enrollment.model.RegistrationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

@Entity
@Table(
        name = "course_registrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_registration_learner",
                columnNames = {"course_id", "learner_id"}),
        indexes = {
                @Index(name = "idx_course_registration_fifo", columnList = "course_id, status, registered_at, id"),
                @Index(name = "idx_course_registration_learner", columnList = "learner_id")
        })
@Check(constraints = "status in ('ENROLLED', 'WAITLISTED')")
public class RegistrationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_registration_course"))
    private CourseEntity course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_registration_learner"))
    private LearnerEntity learner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationState status;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    protected RegistrationEntity() {
    }

    public RegistrationEntity(CourseEntity course, LearnerEntity learner, RegistrationState status) {
        this.course = course;
        this.learner = learner;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public CourseEntity getCourse() {
        return course;
    }

    public LearnerEntity getLearner() {
        return learner;
    }

    public RegistrationState getStatus() {
        return status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setStatus(RegistrationState status) {
        this.status = status;
    }
}
