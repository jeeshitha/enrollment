package com.schwab.registration.enrollment.controller;

import com.schwab.registration.enrollment.model.CourseOffering;
import com.schwab.registration.enrollment.model.OfferingStatus;
import com.schwab.registration.enrollment.service.CourseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public List<CourseOffering> courses() {
        return courseService.courses();
    }

    @GetMapping("/{courseId}")
    public OfferingStatus offeringStatus(@PathVariable String courseId) {
        return courseService.offeringStatus(courseId);
    }
}
