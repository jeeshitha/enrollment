package com.schwab.registration.enrollment.service;

import com.schwab.registration.enrollment.exception.ApiException;
import com.schwab.registration.enrollment.entity.CourseEntity;
import com.schwab.registration.enrollment.model.CourseOffering;
import com.schwab.registration.enrollment.model.OfferingStatus;
import com.schwab.registration.enrollment.model.RegistrationState;
import com.schwab.registration.enrollment.repository.CourseRepository;
import com.schwab.registration.enrollment.repository.EnrollmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    public CourseService(CourseRepository courseRepository, EnrollmentRepository enrollmentRepository) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseOffering> courses() {
        return courseRepository.findAllByOfferingTrueOrderByIdAsc().stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseOffering course(String courseId) {
        return courseRepository.findWithPrerequisitesById(courseId)
                .map(this::toModel)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found: " + courseId));
    }

    @Transactional(propagation = Propagation.MANDATORY, timeout = 10)
    public CourseOffering lockCourse(String courseId) {
        return courseRepository.findByIdForUpdate(courseId)
                .map(this::toModel)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found: " + courseId));
    }

    @Transactional(readOnly = true)
    public OfferingStatus offeringStatus(String courseId) {
        CourseOffering course = course(courseId);
        List<String> enrolled = enrollmentRepository
                .findAllByCourse_IdAndStatusOrderByIdAsc(courseId, RegistrationState.ENROLLED)
                .stream()
                .map(registration -> registration.getLearner().getId())
                .toList();
        List<String> waitlisted = enrollmentRepository
                .findAllByCourse_IdAndStatusOrderByRegisteredAtAscIdAsc(courseId, RegistrationState.WAITLISTED)
                .stream()
                .map(registration -> registration.getLearner().getId())
                .toList();
        return new OfferingStatus(
                course,
                enrolled,
                waitlisted,
                course.capacity() - enrolled.size());
    }

    private CourseOffering toModel(CourseEntity course) {
        return new CourseOffering(
                course.getId(),
                course.getName(),
                course.getCapacity(),
                course.getPrerequisites().stream().map(CourseEntity::getId).collect(Collectors.toSet()));
    }
}
