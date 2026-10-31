package com.schwab.registration.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

@Entity
@Table(
        name = "waitlist_promotion_outbox",
        indexes = @Index(name = "idx_waitlist_outbox_pending", columnList = "published_at, created_at"))
public class WaitlistPromotionEventEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_promotion_outbox_course"))
    private CourseEntity course;

    @CreationTimestamp(source = SourceType.DB)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant publishedAt;

    protected WaitlistPromotionEventEntity() {
    }

    public WaitlistPromotionEventEntity(UUID id, CourseEntity course) {
        this.id = id;
        this.course = course;
    }

    public UUID getId() {
        return id;
    }

    public String getCourseId() {
        return course.getId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void markPublished() {
        publishedAt = Instant.now();
    }
}
