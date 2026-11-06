ALTER TABLE assignment
 ADD COLUMN selection_mode VARCHAR(24) NOT NULL DEFAULT 'FULL',
 ADD COLUMN draw_count INT NULL,
 ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 ADD COLUMN chat_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 ADD CONSTRAINT ck_assignment_selection CHECK(selection_mode IN ('FULL','SHARED_RANDOM','PER_STUDENT_RANDOM'));
CREATE TABLE assignment_student_problem (
 assignment_id VARCHAR(36) NOT NULL,
 student_id VARCHAR(36) NOT NULL,
 problem_version_id VARCHAR(36) NOT NULL,
 position INT NOT NULL,
 PRIMARY KEY(assignment_id,student_id,problem_version_id),
 UNIQUE KEY uk_student_problem_position(assignment_id,student_id,position),
 CONSTRAINT fk_student_assignment FOREIGN KEY(assignment_id) REFERENCES assignment(id),
 CONSTRAINT fk_assignment_student FOREIGN KEY(student_id) REFERENCES app_user(id),
 CONSTRAINT fk_student_problem_version FOREIGN KEY(problem_version_id) REFERENCES problem_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
