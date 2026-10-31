package com.schwab.registration.enrollment.repository;

import com.schwab.registration.enrollment.entity.RegistrationEntity;
import com.schwab.registration.enrollment.model.RegistrationState;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<RegistrationEntity, Long> {
    boolean existsByCourse_IdAndLearner_Id(String courseId, String learnerId);

    long countByCourse_IdAndStatus(String courseId, RegistrationState status);

    @EntityGraph(attributePaths = "learner")
    List<RegistrationEntity> findAllByCourse_IdAndStatusOrderByRegisteredAtAscIdAsc(
            String courseId,
            RegistrationState status);

    @EntityGraph(attributePaths = "learner")
    List<RegistrationEntity> findAllByCourse_IdAndStatusOrderByIdAsc(String courseId, RegistrationState status);

    @EntityGraph(attributePaths = "learner")
    Optional<RegistrationEntity> findByCourse_IdAndLearner_IdAndStatus(
            String courseId,
            String learnerId,
            RegistrationState status);
}
