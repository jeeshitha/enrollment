package com.schwab.registration.enrollment.service;

import com.schwab.registration.enrollment.entity.CourseEntity;
import com.schwab.registration.enrollment.entity.LearnerEntity;
import com.schwab.registration.enrollment.entity.RegistrationEntity;
import com.schwab.registration.enrollment.exception.ApiException;
import com.schwab.registration.enrollment.model.CourseOffering;
import com.schwab.registration.enrollment.model.EnrollmentResult;
import com.schwab.registration.enrollment.model.EnrollmentStatus;
import com.schwab.registration.enrollment.model.Learner;
import com.schwab.registration.enrollment.model.RegistrationState;
import com.schwab.registration.enrollment.repository.CourseRepository;
import com.schwab.registration.enrollment.repository.EnrollmentRepository;
import com.schwab.registration.enrollment.repository.LearnerRepository;
import com.schwab.registration.enrollment.repository.ProcessedWaitlistEventRepository;
import com.schwab.registration.enrollment.entity.ProcessedWaitlistEventEntity;
import com.schwab.registration.enrollment.messaging.WaitlistPromotionEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RegistrationService {
    private final CourseService courseService;
    private final LearnerService learnerService;
    private final CourseRepository courseRepository;
    private final LearnerRepository learnerRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProcessedWaitlistEventRepository processedEventRepository;
    private final WaitlistPromotionPublisher promotionPublisher;

    public RegistrationService(
            CourseService courseService,
            LearnerService learnerService,
            CourseRepository courseRepository,
            LearnerRepository learnerRepository,
            EnrollmentRepository enrollmentRepository,
            ProcessedWaitlistEventRepository processedEventRepository,
            WaitlistPromotionPublisher promotionPublisher) {
        this.courseService = courseService;
        this.learnerService = learnerService;
        this.courseRepository = courseRepository;
        this.learnerRepository = learnerRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.processedEventRepository = processedEventRepository;
        this.promotionPublisher = promotionPublisher;
    }

    @Transactional(timeout = 10)
    public EnrollmentResult enroll(String courseId, String learnerId) {
        CourseOffering course = courseService.lockCourse(courseId);
        requireText(learnerId, "Learner ID");
        Learner learner = learnerService.learner(learnerId);
        requireEligibility(course, learner);

        if (enrollmentRepository.existsByCourse_IdAndLearner_Id(courseId, learnerId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Learner is already enrolled or waitlisted for this course.");
        }

        RegistrationState state = enrollmentRepository.countByCourse_IdAndStatus(
                courseId, RegistrationState.ENROLLED) < course.capacity()
                ? RegistrationState.ENROLLED
                : RegistrationState.WAITLISTED;
        CourseEntity courseEntity = courseRepository.getReferenceById(courseId);
        LearnerEntity learnerEntity = learnerRepository.getReferenceById(learnerId);
        enrollmentRepository.save(new RegistrationEntity(courseEntity, learnerEntity, state));

        if (state == RegistrationState.ENROLLED) {
            return new EnrollmentResult(courseId, learnerId, EnrollmentStatus.ENROLLED, null,
                    "Learner enrolled successfully.");
        }
        List<RegistrationEntity> waitlist = waitlist(courseId);
        int position = waitlist.size();
        return new EnrollmentResult(courseId, learnerId, EnrollmentStatus.WAITLISTED, position,
                "Course is full; learner added to the waitlist.");
    }

    @Transactional(timeout = 10)
    public EnrollmentResult drop(String courseId, String learnerId) {
        courseService.lockCourse(courseId);
        RegistrationEntity registration = enrollmentRepository
                .findByCourse_IdAndLearner_IdAndStatus(courseId, learnerId, RegistrationState.ENROLLED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Learner is not enrolled in this course."));
        enrollmentRepository.delete(registration);

        if (!waitlist(courseId).isEmpty()) {
            promotionPublisher.publishSeatAvailable(courseId);
        }

        return new EnrollmentResult(courseId, learnerId, EnrollmentStatus.DROPPED, null,
                "Learner dropped the course.");
    }

    @Transactional(timeout = 10)
    public EnrollmentResult cancelWaitlist(String courseId, String learnerId) {
        courseService.lockCourse(courseId);
        RegistrationEntity registration = enrollmentRepository
                .findByCourse_IdAndLearner_IdAndStatus(courseId, learnerId, RegistrationState.WAITLISTED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Learner is not on this course's waitlist."));
        enrollmentRepository.delete(registration);
        return new EnrollmentResult(courseId, learnerId, EnrollmentStatus.WAITLIST_CANCELLED, null,
                "Learner removed from the waitlist.");
    }

    @Transactional(timeout = 10)
    public void promoteWaitlistedLearner(WaitlistPromotionEvent event) {
        CourseOffering course = courseService.lockCourse(event.courseId());
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        processedEventRepository.save(new ProcessedWaitlistEventEntity(event.eventId(), event.courseId()));

        String courseId = event.courseId();
        if (enrollmentRepository.countByCourse_IdAndStatus(courseId, RegistrationState.ENROLLED)
                >= course.capacity()) {
            return;
        }

        List<RegistrationEntity> waitlisted = waitlist(courseId);
        var learners = learnerService.learners(
                waitlisted.stream().map(registration -> registration.getLearner().getId()).toList());
        for (RegistrationEntity registration : waitlisted) {
            String learnerId = registration.getLearner().getId();
            Learner learner = learners.get(learnerId);
            if (learner != null && isEligible(course, learner)) {
                registration.setStatus(RegistrationState.ENROLLED);
                enrollmentRepository.save(registration);
                return;
            }
            enrollmentRepository.delete(registration);
        }
    }

    private List<RegistrationEntity> waitlist(String courseId) {
        return enrollmentRepository.findAllByCourse_IdAndStatusOrderByRegisteredAtAscIdAsc(
                courseId, RegistrationState.WAITLISTED);
    }

    private void requireEligibility(CourseOffering course, Learner learner) {
        Set<String> missing = course.prerequisiteCourseIds().stream()
                .filter(prerequisite -> !learner.completedCourseIds().contains(prerequisite))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Learner has not completed required prerequisites: " + String.join(", ", missing));
        }
    }

    private boolean isEligible(CourseOffering course, Learner learner) {
        return learner.completedCourseIds().containsAll(course.prerequisiteCourseIds());
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must not be blank.");
        }
    }
}
