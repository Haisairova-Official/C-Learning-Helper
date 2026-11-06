CREATE TABLE problem_chat_message (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 assignment_id VARCHAR(36) NOT NULL,
 problem_version_id VARCHAR(36) NOT NULL,
 author_id VARCHAR(36) NOT NULL,
 client_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 body VARCHAR(1000) NOT NULL,
 retracted_by VARCHAR(36) NULL,
 retracted_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_chat_author_key(author_id,client_key),
 KEY idx_chat_room(assignment_id,problem_version_id,id),
 KEY idx_chat_author_time(author_id,created_at),
 CONSTRAINT fk_chat_assignment FOREIGN KEY(assignment_id) REFERENCES assignment(id),
 CONSTRAINT fk_chat_version FOREIGN KEY(problem_version_id) REFERENCES problem_version(id),
 CONSTRAINT fk_chat_author FOREIGN KEY(author_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assignment_chat_mute (
 assignment_id VARCHAR(36) NOT NULL,
 student_id VARCHAR(36) NOT NULL,
 muted_by VARCHAR(36) NOT NULL,
 muted_at DATETIME(6) NOT NULL,
 PRIMARY KEY(assignment_id,student_id),
 CONSTRAINT fk_mute_assignment FOREIGN KEY(assignment_id) REFERENCES assignment(id),
 CONSTRAINT fk_mute_student FOREIGN KEY(student_id) REFERENCES app_user(id),
 CONSTRAINT fk_mute_manager FOREIGN KEY(muted_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
