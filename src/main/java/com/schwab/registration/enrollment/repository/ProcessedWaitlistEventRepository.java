package com.schwab.registration.enrollment.repository;

import com.schwab.registration.enrollment.entity.ProcessedWaitlistEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedWaitlistEventRepository extends JpaRepository<ProcessedWaitlistEventEntity, UUID> {
}
