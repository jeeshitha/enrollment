package com.schwab.registration.enrollment.config;

import com.schwab.registration.enrollment.entity.CourseEntity;
import com.schwab.registration.enrollment.repository.CourseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CourseDataInitializer implements CommandLineRunner {
    private static final String PROGRAMMING = "introduction-to-programming";

    private final CourseRepository courseRepository;

    public CourseDataInitializer(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<CourseEntity> courses = List.of(
                new CourseEntity(PROGRAMMING, "Introduction to Programming", 0, false),
                new CourseEntity("data-structures-and-algorithms", "Data Structures and Algorithms", 5, true),
                new CourseEntity("database-systems", "Database Systems", 5, true),
                new CourseEntity("design-systems", "Design Systems", 5, true));

        for (CourseEntity configured : courses) {
            CourseEntity course = courseRepository.findById(configured.getId()).orElse(configured);
            course.setConfiguration(configured.getName(), configured.getCapacity(), configured.isOffering());
            courseRepository.save(course);
        }

        Map<String, CourseEntity> coursesById = courseRepository.findAll().stream()
                .collect(Collectors.toMap(CourseEntity::getId, Function.identity()));
        coursesById.get("data-structures-and-algorithms")
                .setPrerequisites(Set.of(coursesById.get(PROGRAMMING)));
        coursesById.get("database-systems")
                .setPrerequisites(Set.of(coursesById.get(PROGRAMMING)));
        coursesById.get("design-systems").setPrerequisites(Set.of(
                coursesById.get(PROGRAMMING),
                coursesById.get("data-structures-and-algorithms"),
                coursesById.get("database-systems")));
        courseRepository.saveAll(coursesById.values());
    }
}
