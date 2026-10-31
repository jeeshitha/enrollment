package com.schwab.registration.enrollment.repository;

import com.schwab.registration.enrollment.entity.CourseEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<CourseEntity, String> {
    @EntityGraph(attributePaths = "prerequisites")
    List<CourseEntity> findAllByOfferingTrueOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select course from CourseEntity course where course.id = :courseId")
    Optional<CourseEntity> findByIdForUpdate(@Param("courseId") String courseId);

    @EntityGraph(attributePaths = "prerequisites")
    Optional<CourseEntity> findWithPrerequisitesById(String id);
}
