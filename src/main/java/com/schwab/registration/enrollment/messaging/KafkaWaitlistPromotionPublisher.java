package com.schwab.registration.enrollment.messaging;

import com.schwab.registration.enrollment.entity.WaitlistPromotionEventEntity;
import com.schwab.registration.enrollment.repository.CourseRepository;
import com.schwab.registration.enrollment.repository.WaitlistPromotionOutboxRepository;
import com.schwab.registration.enrollment.service.WaitlistPromotionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;
import java.time.Instant;

@Component
public class KafkaWaitlistPromotionPublisher implements WaitlistPromotionPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaWaitlistPromotionPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final WaitlistPromotionOutboxRepository outboxRepository;
    private final CourseRepository courseRepository;
    private final String topic;
    private final long publishTimeoutMillis;

    public KafkaWaitlistPromotionPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            WaitlistPromotionOutboxRepository outboxRepository,
            CourseRepository courseRepository,
            @Value("${app.kafka.waitlist-promotion-topic}") String topic,
            @Value("${app.kafka.publish-timeout:5s}") java.time.Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxRepository = outboxRepository;
        this.courseRepository = courseRepository;
        this.topic = topic;
        this.publishTimeoutMillis = publishTimeout.toMillis();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
    public void publishSeatAvailable(String courseId) {
        UUID eventId = UUID.randomUUID();
        WaitlistPromotionEventEntity event = new WaitlistPromotionEventEntity(
                eventId, courseRepository.getReferenceById(courseId));
        outboxRepository.save(event);
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-interval:1000}")
    public void dispatchPendingEvents() {
        var pendingEvents = outboxRepository.findNextEvents(PageRequest.of(0, 20));
        for (var pending : pendingEvents) {
            try {
                WaitlistPromotionEvent event = new WaitlistPromotionEvent(
                        pending.getEventId(), pending.getCourseId());
                kafkaTemplate.send(topic, event.courseId(), event.toPayload())
                        .get(publishTimeoutMillis, TimeUnit.MILLISECONDS);
                outboxRepository.markPublished(event.eventId(), Instant.now());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted publishing waitlist event {}", pending.getEventId(), exception);
                return;
            } catch (ExecutionException | TimeoutException exception) {
                LOGGER.warn("Waitlist event {} remains pending for retry", pending.getEventId(), exception);
                return;
            } catch (RuntimeException exception) {
                LOGGER.warn("Waitlist event {} remains pending after a publish failure", pending.getEventId(),
                        exception);
                return;
            }
        }
    }
}
