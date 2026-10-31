package com.schwab.registration.enrollment.service;

import com.schwab.registration.enrollment.exception.ApiException;
import com.schwab.registration.enrollment.entity.CourseEntity;
import com.schwab.registration.enrollment.entity.LearnerEntity;
import com.schwab.registration.enrollment.model.Learner;
import com.schwab.registration.enrollment.repository.LearnerRepository;
import com.schwab.registration.enrollment.repository.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LearnerService {
    private final LearnerRepository learnerRepository;
    private final CourseRepository courseRepository;

    public LearnerService(LearnerRepository learnerRepository, CourseRepository courseRepository) {
        this.learnerRepository = learnerRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(timeout = 10)
    public Learner saveLearner(String learnerId, Set<String> completedCourseIds) {
        requireText(learnerId, "Learner ID");
        if (completedCourseIds == null || completedCourseIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Completed course IDs must not contain blank values.");
        }
        Set<CourseEntity> courses = courseRepository.findAllById(completedCourseIds).stream()
                .collect(Collectors.toSet());
        if (courses.size() != completedCourseIds.size()) {
            Set<String> missing = new java.util.HashSet<>(completedCourseIds);
            courses.stream().map(CourseEntity::getId).forEach(missing::remove);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown completed course IDs: " + String.join(", ", missing));
        }

        LearnerEntity learner = learnerRepository.findById(learnerId)
                .orElseGet(() -> new LearnerEntity(learnerId));
        learner.setCompletedCourses(courses);
        learnerRepository.save(learner);
        return toModel(learner);
    }

    @Transactional(readOnly = true)
    public Learner learner(String learnerId) {
        return learnerRepository.findWithCompletedCoursesById(learnerId)
                .map(this::toModel)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Learner not found: " + learnerId));
    }

    @Transactional(readOnly = true)
    public Learner findLearnerOrNull(String learnerId) {
        return learnerRepository.findWithCompletedCoursesById(learnerId).map(this::toModel).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<String, Learner> learners(Collection<String> learnerIds) {
        return learnerRepository.findAllWithCompletedCoursesByIdIn(learnerIds).stream()
                .map(this::toModel)
                .collect(Collectors.toMap(Learner::id, learner -> learner));
    }

    private Learner toModel(LearnerEntity learner) {
        return new Learner(
                learner.getId(),
                learner.getCompletedCourses().stream().map(CourseEntity::getId).collect(Collectors.toSet()));
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must not be blank.");
        }
    }
}
