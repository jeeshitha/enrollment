package com.schwab.registration.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

@Entity
@Table(name = "processed_waitlist_events")
public class ProcessedWaitlistEventEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false, length = 100)
    private String courseId;

    @CreationTimestamp(source = SourceType.DB)
    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedWaitlistEventEntity() {
    }

    public ProcessedWaitlistEventEntity(UUID eventId, String courseId) {
        this.eventId = eventId;
        this.courseId = courseId;
    }
}
