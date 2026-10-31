package com.schwab.registration.enrollment.messaging;

import com.schwab.registration.enrollment.service.RegistrationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WaitlistPromotionListener {
    private final RegistrationService registrationService;

    public WaitlistPromotionListener(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @KafkaListener(
            topics = "${app.kafka.waitlist-promotion-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${app.kafka.listener.concurrency:3}")
    public void onSeatAvailable(String payload) {
        registrationService.promoteWaitlistedLearner(WaitlistPromotionEvent.fromPayload(payload));
    }
}
