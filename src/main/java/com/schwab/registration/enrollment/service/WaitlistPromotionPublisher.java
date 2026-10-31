package com.schwab.registration.enrollment.service;

public interface WaitlistPromotionPublisher {
    void publishSeatAvailable(String courseId);
}
