package com.schwab.registration.enrollment.repository;

import com.schwab.registration.enrollment.entity.WaitlistPromotionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface WaitlistPromotionOutboxRepository extends JpaRepository<WaitlistPromotionEventEntity, UUID> {
    @Query("""
            select event.id as eventId, event.course.id as courseId
            from WaitlistPromotionEventEntity event
            where event.publishedAt is null
            order by event.createdAt asc, event.id asc
            """)
    List<OutboxEventView> findNextEvents(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Transactional
    @Query("update WaitlistPromotionEventEntity event set event.publishedAt = :publishedAt " +
            "where event.id = :eventId and event.publishedAt is null")
    int markPublished(@Param("eventId") UUID eventId, @Param("publishedAt") Instant publishedAt);

    interface OutboxEventView {
        UUID getEventId();

        String getCourseId();
    }
}
