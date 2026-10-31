package com.schwab.registration.enrollment.repository;

import com.schwab.registration.enrollment.entity.LearnerEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LearnerRepository extends JpaRepository<LearnerEntity, String> {
    @EntityGraph(attributePaths = "completedCourses")
    Optional<LearnerEntity> findWithCompletedCoursesById(String id);

    @EntityGraph(attributePaths = "completedCourses")
    List<LearnerEntity> findAllWithCompletedCoursesByIdIn(Collection<String> ids);

}
