package com.schwab.registration.enrollment.controller;

import com.schwab.registration.enrollment.api.CreateLearnerRequest;
import com.schwab.registration.enrollment.exception.ApiException;
import com.schwab.registration.enrollment.model.Learner;
import com.schwab.registration.enrollment.service.LearnerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learners")
public class LearnerController {
    private final LearnerService learnerService;

    public LearnerController(LearnerService learnerService) {
        this.learnerService = learnerService;
    }

    @PutMapping("/{learnerId}")
    public Learner saveLearner(
            @PathVariable String learnerId,
            @RequestBody CreateLearnerRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Learner request body is required.");
        }
        return learnerService.saveLearner(learnerId, request.completedCourseIds());
    }

    @GetMapping("/{learnerId}")
    public Learner learner(@PathVariable String learnerId) {
        return learnerService.learner(learnerId);
    }
}
