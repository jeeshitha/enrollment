package com.schwab.registration.enrollment.messaging;

import java.util.UUID;

public record WaitlistPromotionEvent(UUID eventId, String courseId) {
    public String toPayload() {
        return eventId + "|" + courseId;
    }

    public static WaitlistPromotionEvent fromPayload(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Waitlist promotion event must not be null.");
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid waitlist promotion event.");
        }
        try {
            return new WaitlistPromotionEvent(UUID.fromString(parts[0]), parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid waitlist promotion event ID.", exception);
        }
    }
}
