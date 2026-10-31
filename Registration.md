Act as senior software engineer to create this course registration Sytstem.

Implement enrollment, dropping, and FIFO waitlist promotion for one course offering in the provided starter
project.
A learner may enroll only once. 
When the course is full, eligible learners join the waitlist.
If a seat opens, promote the earliest eligible learner.

The course offering has a maximum capacity of 5 learners.
Let's take there are 3 courses in the system, each with a maximum capacity of 5 learners. The courses are:

1. Data Structures and Algorithms
2. Database Systems
3. Design Systems

Introduction to Programming is a prerequisite for all three courses. A learner must complete this course before enrolling in any of the three courses.
Design system course needs 1 and 2 as prerequisites. A learner must complete both Data Structures and Algorithms and Database Systems before enrolling in Design Systems.

Prerequisites:

The course offering requires completion of the course "Introduction to Programming". 
A learner is eligible to enroll or join the waitlist only if this prerequisite has been completed.
Prerequisite completion information is available through the provided model.

Prerequisite Assumptions
A course offering may define a set of prerequisite courses.
A learner is considered eligible only if all prerequisites have been completed before enrollment.
Prerequisite completion is provided by the existing data model (or may be mocked in-memory if not available).
Learners who do not satisfy prerequisites cannot be enrolled in the course.

A learner is eligible if:

The learner exists.
The learner has completed all required prerequisites.
The learner is not already enrolled.
The learner is not already on the waitlist.


waitlist assumptions:
The waitlist follows FIFO (First-In, First-Out) ordering.
Only learners who satisfy the course prerequisites may join the waitlist.
A learner may appear only once, either as enrolled or waitlisted, but not both.
When an enrolled learner drops the course, the system attempts to fill the vacant seat by promoting learners from the waitlist in FIFO order.


ineligile learners:
If a learner does not meet the prerequisites, they cannot enroll or join the waitlist. The
system should provide appropriate feedback indicating the reason for ineligibility.


