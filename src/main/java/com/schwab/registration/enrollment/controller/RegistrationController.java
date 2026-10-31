package com.schwab.registration.enrollment.controller;

import com.schwab.registration.enrollment.api.EnrollRequest;
import com.schwab.registration.enrollment.exception.ApiException;
import com.schwab.registration.enrollment.model.EnrollmentResult;
import com.schwab.registration.enrollment.model.EnrollmentStatus;
import com.schwab.registration.enrollment.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RegistrationController {
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/courses/{courseId}/enrollments")
    public ResponseEntity<EnrollmentResult> enroll(
            @PathVariable String courseId,
            @RequestBody EnrollRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enrollment request body is required.");
        }
        EnrollmentResult result = registrationService.enroll(courseId, request.learnerId());
        return ResponseEntity.status(result.status() == EnrollmentStatus.WAITLISTED
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK).body(result);
    }

    @DeleteMapping("/courses/{courseId}/enrollments/{learnerId}")
    public EnrollmentResult drop(
            @PathVariable String courseId,
            @PathVariable String learnerId) {
        return registrationService.drop(courseId, learnerId);
    }

    @DeleteMapping("/courses/{courseId}/waitlist/{learnerId}")
    public EnrollmentResult cancelWaitlist(
            @PathVariable String courseId,
            @PathVariable String learnerId) {
        return registrationService.cancelWaitlist(courseId, learnerId);
    }
}
