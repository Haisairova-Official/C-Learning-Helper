CREATE TABLE submission (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 student_id VARCHAR(36) NOT NULL,
 assignment_id VARCHAR(36) NOT NULL,
 problem_id VARCHAR(36) NOT NULL,
 problem_version_id VARCHAR(36) NOT NULL,
 source_code MEDIUMTEXT NOT NULL,
 source_hash VARCHAR(64) NOT NULL,
 language VARCHAR(16) NOT NULL,
 status VARCHAR(32) NOT NULL,
 verdict VARCHAR(40) NULL,
 score DECIMAL(10,2) NULL,
 max_score DECIMAL(10,2) NOT NULL,
 compile_message TEXT NULL,
 idempotency_key VARCHAR(128) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 submitted_at DATETIME(6) NOT NULL,
 finished_at DATETIME(6) NULL,
 UNIQUE KEY uk_submission_idempotency (student_id, idempotency_key),
 KEY ix_submission_student_assignment (student_id, assignment_id, submitted_at),
 KEY ix_submission_assignment_problem (assignment_id, problem_id),
 CONSTRAINT fk_submission_student FOREIGN KEY (student_id) REFERENCES app_user(id),
 CONSTRAINT fk_submission_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id),
 CONSTRAINT fk_submission_problem FOREIGN KEY (problem_id) REFERENCES problem(id),
 CONSTRAINT fk_submission_version FOREIGN KEY (problem_version_id) REFERENCES problem_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE judge_task (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 submission_id VARCHAR(36) NOT NULL,
 state VARCHAR(32) NOT NULL,
 attempt INT NOT NULL DEFAULT 0,
 max_attempts INT NOT NULL DEFAULT 3,
 available_at DATETIME(6) NOT NULL,
 lease_owner VARCHAR(100) NULL,
 lease_token VARCHAR(64) NULL,
 lease_until DATETIME(6) NULL,
 last_error_code VARCHAR(80) NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_judge_task_submission (submission_id),
 KEY ix_judge_task_available (state, available_at, lease_until),
 CONSTRAINT fk_judge_task_submission FOREIGN KEY (submission_id) REFERENCES submission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE case_result (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 submission_id VARCHAR(36) NOT NULL,
 case_index INT NOT NULL,
 test_case_id VARCHAR(36) NOT NULL,
 verdict VARCHAR(40) NOT NULL,
 score DECIMAL(10,2) NOT NULL,
 cpu_time_ns BIGINT NOT NULL,
 wall_time_ns BIGINT NOT NULL,
 memory_bytes BIGINT NOT NULL,
 UNIQUE KEY uk_case_result_index (submission_id, case_index),
 CONSTRAINT fk_case_result_submission FOREIGN KEY (submission_id) REFERENCES submission(id),
 CONSTRAINT fk_case_result_test FOREIGN KEY (test_case_id) REFERENCES test_case(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
