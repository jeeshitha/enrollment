package com.schwab.registration.enrollment;

import com.schwab.registration.enrollment.exception.ApiException;
import com.schwab.registration.enrollment.entity.CourseEntity;
import com.schwab.registration.enrollment.entity.RegistrationEntity;
import com.schwab.registration.enrollment.model.EnrollmentResult;
import com.schwab.registration.enrollment.model.EnrollmentStatus;
import com.schwab.registration.enrollment.model.RegistrationState;
import com.schwab.registration.enrollment.messaging.WaitlistPromotionEvent;
import com.schwab.registration.enrollment.messaging.KafkaWaitlistPromotionPublisher;
import com.schwab.registration.enrollment.repository.CourseRepository;
import com.schwab.registration.enrollment.repository.EnrollmentRepository;
import com.schwab.registration.enrollment.repository.LearnerRepository;
import com.schwab.registration.enrollment.repository.ProcessedWaitlistEventRepository;
import com.schwab.registration.enrollment.repository.WaitlistPromotionOutboxRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import com.schwab.registration.enrollment.service.CourseService;
import com.schwab.registration.enrollment.service.LearnerService;
import com.schwab.registration.enrollment.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RegistrationServiceTests {
    private static final String COURSE_ID = "data-structures-and-algorithms";
    private static final String DATABASE_ID = "database-systems";
    private static final String DESIGN_ID = "design-systems";
    private static final String PROGRAMMING = "introduction-to-programming";
    private static final String DSA = "data-structures-and-algorithms";
    private static final String DATABASE = "database-systems";
    private static final Set<String> PREREQUISITES = Set.of(PROGRAMMING);

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private LearnerService learnerService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private LearnerRepository learnerRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ProcessedWaitlistEventRepository processedEventRepository;

    @Autowired
    private WaitlistPromotionOutboxRepository outboxRepository;

    @Autowired
    private KafkaWaitlistPromotionPublisher kafkaPromotionPublisher;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearRegistrationData() {
        enrollmentRepository.deleteAll();
        processedEventRepository.deleteAll();
        outboxRepository.deleteAll();
        learnerRepository.deleteAll();
        reset(kafkaTemplate);
    }

    @Test
    void rejectsUnknownLearnerAndBlankLearnerId() {
        ApiException missingLearner = assertThrows(
                ApiException.class,
                () -> registrationService.enroll(COURSE_ID, "not-created"));
        assertEquals(404, missingLearner.status().value());

        learnerService.saveLearner("learner-1", PREREQUISITES);
        ApiException blankLearner = assertThrows(
                ApiException.class,
                () -> registrationService.enroll(COURSE_ID, " "));
        assertEquals(400, blankLearner.status().value());
    }

    @Test
    void courseLockRequiresAnOuterTransactionAndJoinsRegistrationTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> courseService.lockCourse(COURSE_ID));
        createEligibleLearner("learner-1");

        assertEquals(EnrollmentStatus.ENROLLED,
                registrationService.enroll(COURSE_ID, "learner-1").status());
    }

    @Test
    void rejectsLearnersMissingAnyRequiredPrerequisite() {
        learnerService.saveLearner("learner-1", Set.of());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> registrationService.enroll(COURSE_ID, "learner-1"));

        assertEquals(422, exception.status().value());
        assertTrue(exception.getMessage().contains(PROGRAMMING));
        assertTrue(courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().isEmpty());
    }

    @Test
    void rejectsUnknownCompletedCourses() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> learnerService.saveLearner("learner-1", Set.of("not-a-course")));

        assertEquals(400, exception.status().value());
        assertFalse(learnerRepository.existsById("learner-1"));
    }

    @Test
    void designSystemsRequiresProgrammingAndBothOtherCourses() {
        learnerService.saveLearner("learner-missing-dsa", Set.of(PROGRAMMING, DATABASE));
        learnerService.saveLearner("learner-missing-db", Set.of(PROGRAMMING, DSA));
        learnerService.saveLearner("learner-ready", Set.of(PROGRAMMING, DSA, DATABASE));

        assertEquals(422, assertThrows(ApiException.class,
                () -> registrationService.enroll(DESIGN_ID, "learner-missing-dsa")).status().value());
        assertEquals(422, assertThrows(ApiException.class,
                () -> registrationService.enroll(DESIGN_ID, "learner-missing-db")).status().value());
        assertEquals(EnrollmentStatus.ENROLLED,
                registrationService.enroll(DESIGN_ID, "learner-ready").status());
    }

    @Test
    void enforcesCapacityAndOneRegistrationPerLearner() {
        for (int i = 1; i <= 6; i++) {
            createEligibleLearner("learner-" + i);
        }

        for (int i = 1; i <= 5; i++) {
            assertEquals(EnrollmentStatus.ENROLLED,
                    registrationService.enroll(COURSE_ID, "learner-" + i).status());
        }
        EnrollmentResult waitlisted = registrationService.enroll(COURSE_ID, "learner-6");
        assertEquals(EnrollmentStatus.WAITLISTED, waitlisted.status());
        assertEquals(1, waitlisted.waitlistPosition());

        ApiException duplicateEnrollment = assertThrows(
                ApiException.class,
                () -> registrationService.enroll(COURSE_ID, "learner-1"));
        ApiException duplicateWaitlist = assertThrows(
                ApiException.class,
                () -> registrationService.enroll(COURSE_ID, "learner-6"));
        assertEquals(409, duplicateEnrollment.status().value());
        assertEquals(409, duplicateWaitlist.status().value());
        assertEquals(5, courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().size());
        assertEquals(List.of("learner-6"), courseService.offeringStatus(COURSE_ID).waitlistedLearnerIds());
    }

    @Test
    void capacityIsIndependentForEachCourse() {
        for (int i = 1; i <= 5; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        createEligibleLearner("learner-6");

        assertEquals(EnrollmentStatus.ENROLLED,
                registrationService.enroll(DATABASE_ID, "learner-6").status());
        assertEquals(5, courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().size());
        assertEquals(1, courseService.offeringStatus(DATABASE_ID).enrolledLearnerIds().size());
    }

    @Test
    void ineligibleLearnerCannotJoinWaitlistForFullCourse() {
        for (int i = 1; i <= 5; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        learnerService.saveLearner("learner-ineligible", Set.of());

        assertEquals(422, assertThrows(ApiException.class,
                () -> registrationService.enroll(COURSE_ID, "learner-ineligible")).status().value());
        assertTrue(courseService.offeringStatus(COURSE_ID).waitlistedLearnerIds().isEmpty());
    }

    @Test
    void dropAndWaitlistCancellationReturnNotFoundWhenMembershipDoesNotExist() {
        ApiException notEnrolled = assertThrows(
                ApiException.class,
                () -> registrationService.drop(COURSE_ID, "learner-1"));
        ApiException notWaitlisted = assertThrows(
                ApiException.class,
                () -> registrationService.cancelWaitlist(COURSE_ID, "learner-1"));

        assertEquals(404, notEnrolled.status().value());
        assertEquals(404, notWaitlisted.status().value());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void dropPublishesPromotionOnlyWhenWaitlistExists() {
        createEligibleLearner("learner-enrolled");
        registrationService.enroll(COURSE_ID, "learner-enrolled");
        registrationService.drop(COURSE_ID, "learner-enrolled");
        verifyNoInteractions(kafkaTemplate);

        for (int i = 1; i <= 6; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        registrationService.drop(COURSE_ID, "learner-1");

        assertEquals(1, outboxRepository.count());
        verifyNoInteractions(kafkaTemplate);
        assertEquals(4, courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().size());
    }

    @Test
    void outboxPublisherSendsStableEventIdAndMarksOnlyAcknowledgedMessages() {
        for (int i = 1; i <= 6; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        registrationService.drop(COURSE_ID, "learner-1");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        kafkaPromotionPublisher.dispatchPendingEvents();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                eq("course-seat-available-test"),
                eq(COURSE_ID),
                payload.capture());
        WaitlistPromotionEvent sentEvent = WaitlistPromotionEvent.fromPayload(payload.getValue());
        assertEquals(COURSE_ID, sentEvent.courseId());
        assertNotNull(sentEvent.eventId());
        assertTrue(outboxRepository.findAll().getFirst().isPublished());
    }

    @Test
    void failedKafkaSendLeavesOutboxEventPendingForRetry() {
        for (int i = 1; i <= 6; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        registrationService.drop(COURSE_ID, "learner-1");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        kafkaPromotionPublisher.dispatchPendingEvents();

        assertFalse(outboxRepository.findAll().getFirst().isPublished());
    }

    @Test
    void outboxWriteRollsBackWithItsEnclosingTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            kafkaPromotionPublisher.publishSeatAvailable(COURSE_ID);
            throw new IllegalStateException("Force transaction rollback.");
        }));

        assertEquals(0, outboxRepository.count());
    }

    @Test
    void cancellationRemovesWaitlistMembershipAndAllowsLaterEnrollment() {
        for (int i = 1; i <= 5; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        createEligibleLearner("learner-waitlisted");
        createEligibleLearner("learner-new");
        assertEquals(EnrollmentStatus.WAITLISTED,
                registrationService.enroll(COURSE_ID, "learner-waitlisted").status());

        assertEquals(EnrollmentStatus.WAITLIST_CANCELLED,
                registrationService.cancelWaitlist(COURSE_ID, "learner-waitlisted").status());
        assertEquals(EnrollmentStatus.WAITLISTED,
                registrationService.enroll(COURSE_ID, "learner-new").status());
        assertEquals(List.of("learner-new"), courseService.offeringStatus(COURSE_ID).waitlistedLearnerIds());
    }

    @Test
    void promotesFifoLearnerAndSkipsLearnersWhoBecomeIneligible() {
        for (int i = 1; i <= 5; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        createEligibleLearner("learner-now-ineligible");
        createEligibleLearner("learner-next");
        registrationService.enroll(COURSE_ID, "learner-now-ineligible");
        registrationService.enroll(COURSE_ID, "learner-next");
        learnerService.saveLearner("learner-now-ineligible", Set.of());

        registrationService.drop(COURSE_ID, "learner-1");
        promote(COURSE_ID);

        assertTrue(courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().contains("learner-next"));
        assertFalse(courseService.offeringStatus(COURSE_ID).waitlistedLearnerIds()
                .contains("learner-now-ineligible"));
        assertEquals(5, courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().size());
    }

    @Test
    void promotesTheEarliestEligibleLearnerAndKeepsLaterLearnersQueued() {
        for (int i = 1; i <= 5; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        createEligibleLearner("waitlisted-first");
        createEligibleLearner("waitlisted-second");
        registrationService.enroll(COURSE_ID, "waitlisted-first");
        registrationService.enroll(COURSE_ID, "waitlisted-second");

        registrationService.drop(COURSE_ID, "learner-1");
        promote(COURSE_ID);

        assertTrue(courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().contains("waitlisted-first"));
        assertEquals(
                List.of("waitlisted-second"),
                courseService.offeringStatus(COURSE_ID).waitlistedLearnerIds());
    }

    @Test
    void promotionIsSafeWhenNoSeatIsAvailableOrWaitlistIsEmpty() {
        for (int i = 1; i <= 5; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        createEligibleLearner("learner-waitlisted");
        registrationService.enroll(COURSE_ID, "learner-waitlisted");

        promote(COURSE_ID);
        assertEquals(1, courseService.offeringStatus(COURSE_ID).waitlistedLearnerIds().size());

        registrationService.drop(COURSE_ID, "learner-1");
        promote(COURSE_ID);
        promote(COURSE_ID);
        assertTrue(courseService.offeringStatus(COURSE_ID).waitlistedLearnerIds().isEmpty());
        assertEquals(5, courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().size());
    }

    @Test
    void duplicateKafkaEventPromotesOnlyOnce() {
        for (int i = 1; i <= 5; i++) {
            createEligibleLearner("learner-" + i);
            registrationService.enroll(COURSE_ID, "learner-" + i);
        }
        createEligibleLearner("learner-waitlisted");
        registrationService.enroll(COURSE_ID, "learner-waitlisted");
        registrationService.drop(COURSE_ID, "learner-1");
        WaitlistPromotionEvent event = new WaitlistPromotionEvent(UUID.randomUUID(), COURSE_ID);

        registrationService.promoteWaitlistedLearner(event);
        registrationService.promoteWaitlistedLearner(event);

        assertEquals(5, courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().size());
        assertTrue(courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().contains("learner-waitlisted"));
        assertTrue(courseService.offeringStatus(COURSE_ID).waitlistedLearnerIds().isEmpty());
    }

    @Test
    void simultaneousDifferentLearnersCannotExceedCapacity() throws Exception {
        for (int i = 1; i <= 4; i++) {
            createEligibleLearner("existing-" + i);
            registrationService.enroll(COURSE_ID, "existing-" + i);
        }
        createEligibleLearner("racing-learner-a");
        createEligibleLearner("racing-learner-b");

        List<ConcurrentAttempt> attempts = concurrentlyEnroll(
                List.of("racing-learner-a", "racing-learner-b"));

        assertEquals(1, attempts.stream().filter(attempt -> attempt.status() == EnrollmentStatus.ENROLLED).count());
        assertEquals(1, attempts.stream().filter(attempt -> attempt.status() == EnrollmentStatus.WAITLISTED).count());
        assertEquals(5, courseService.offeringStatus(COURSE_ID).enrolledLearnerIds().size());
        assertEquals(1, courseService.offeringStatus(COURSE_ID).waitlistedLearnerIds().size());
        assertEquals(6, allRegistrationLearnerIds(COURSE_ID).size());
    }

    @Test
    void simultaneousDuplicateRequestsCreateOnlyOneRegistration() throws Exception {
        createEligibleLearner("same-learner");

        List<ConcurrentAttempt> attempts = concurrentlyEnroll(List.of("same-learner", "same-learner"));

        assertEquals(1, attempts.stream().filter(attempt -> attempt.status() == EnrollmentStatus.ENROLLED).count());
        assertEquals(1, attempts.stream()
                .filter(attempt -> Integer.valueOf(409).equals(attempt.errorStatus()))
                .count());
        assertEquals(1, allRegistrationLearnerIds(COURSE_ID).size());
    }

    @Test
    void databaseUniqueConstraintRejectsDuplicateCourseLearnerRows() {
        createEligibleLearner("constraint-learner");
        CourseEntity course = courseRepository.getReferenceById(COURSE_ID);
        var learner = learnerRepository.getReferenceById("constraint-learner");

        enrollmentRepository.saveAndFlush(new RegistrationEntity(course, learner, RegistrationState.ENROLLED));
        assertThrows(DataIntegrityViolationException.class,
                () -> enrollmentRepository.saveAndFlush(
                        new RegistrationEntity(course, learner, RegistrationState.WAITLISTED)));
    }

    @Test
    void offeringStatusUsesBoundedQueriesInsteadOfOneQueryPerLearner() {
        for (int i = 1; i <= 5; i++) {
            createEligibleLearner("n-plus-one-" + i);
            registrationService.enroll(COURSE_ID, "n-plus-one-" + i);
        }

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        courseService.offeringStatus(COURSE_ID);

        assertTrue(statistics.getPrepareStatementCount() <= 3,
                "Offering status should execute three bounded queries, not one query per registration.");
    }

    private void createEligibleLearner(String learnerId) {
        learnerService.saveLearner(learnerId, PREREQUISITES);
    }

    private void promote(String courseId) {
        registrationService.promoteWaitlistedLearner(new WaitlistPromotionEvent(UUID.randomUUID(), courseId));
    }

    private List<ConcurrentAttempt> concurrentlyEnroll(List<String> learnerIds) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(learnerIds.size());
        CountDownLatch ready = new CountDownLatch(learnerIds.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ConcurrentAttempt>> futures = new ArrayList<>();

        try {
            for (String learnerId : learnerIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test did not release its start gate.");
                    }
                    try {
                        EnrollmentResult result = registrationService.enroll(COURSE_ID, learnerId);
                        return new ConcurrentAttempt(result.status(), null);
                    } catch (ApiException exception) {
                        return new ConcurrentAttempt(null, exception.status().value());
                    }
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Workers did not reach the start gate.");
            start.countDown();
            List<ConcurrentAttempt> attempts = new ArrayList<>();
            for (Future<ConcurrentAttempt> future : futures) {
                attempts.add(future.get(20, TimeUnit.SECONDS));
            }
            return attempts;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Set<String> allRegistrationLearnerIds(String courseId) {
        var status = courseService.offeringStatus(courseId);
        Set<String> learnerIds = new HashSet<>(status.enrolledLearnerIds());
        learnerIds.addAll(status.waitlistedLearnerIds());
        return learnerIds;
    }

    private record ConcurrentAttempt(EnrollmentStatus status, Integer errorStatus) {
    }
}
