CREATE TABLE app_user (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_app_user_username (username),
    CONSTRAINT ck_app_user_role CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE classroom (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    teacher_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_classroom_teacher FOREIGN KEY (teacher_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_member (
    class_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (class_id, student_id),
    CONSTRAINT fk_class_member_class FOREIGN KEY (class_id) REFERENCES classroom(id),
    CONSTRAINT fk_class_member_student FOREIGN KEY (student_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE problem (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(300) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_problem_owner FOREIGN KEY (owner_id) REFERENCES app_user(id),
    CONSTRAINT ck_problem_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE problem_version (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    problem_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    title VARCHAR(300) NOT NULL,
    statement MEDIUMTEXT NOT NULL,
    input_description TEXT NOT NULL,
    output_description TEXT NOT NULL,
    reference_answer MEDIUMTEXT NULL,
    time_limit_ms BIGINT NULL,
    memory_limit_mb BIGINT NULL,
    code_limit_bytes BIGINT NULL,
    max_score DECIMAL(10,2) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_problem_version (problem_id, version_number),
    CONSTRAINT fk_problem_version_problem FOREIGN KEY (problem_id) REFERENCES problem(id),
    CONSTRAINT ck_problem_version_number CHECK (version_number > 0),
    CONSTRAINT ck_problem_time CHECK (time_limit_ms IS NULL OR time_limit_ms > 0),
    CONSTRAINT ck_problem_memory CHECK (memory_limit_mb IS NULL OR memory_limit_mb > 0),
    CONSTRAINT ck_problem_code CHECK (code_limit_bytes IS NULL OR code_limit_bytes > 0),
    CONSTRAINT ck_problem_score CHECK (max_score IS NULL OR max_score >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE problem_sample (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    problem_version_id VARCHAR(36) NOT NULL,
    sample_index INT NOT NULL,
    input_text MEDIUMTEXT NOT NULL,
    output_text MEDIUMTEXT NOT NULL,
    explanation TEXT NULL,
    UNIQUE KEY uk_problem_sample_index (problem_version_id, sample_index),
    CONSTRAINT fk_sample_version FOREIGN KEY (problem_version_id) REFERENCES problem_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE test_case (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    problem_version_id VARCHAR(36) NOT NULL,
    case_index INT NOT NULL,
    input_text MEDIUMTEXT NOT NULL,
    expected_output MEDIUMTEXT NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    comparison_mode VARCHAR(16) NOT NULL,
    UNIQUE KEY uk_test_case_index (problem_version_id, case_index),
    CONSTRAINT fk_case_version FOREIGN KEY (problem_version_id) REFERENCES problem_version(id),
    CONSTRAINT ck_case_visibility CHECK (visibility IN ('PUBLIC', 'HIDDEN')),
    CONSTRAINT ck_case_comparison CHECK (comparison_mode IN ('EXACT', 'TOKENS')),
    CONSTRAINT ck_case_score CHECK (score >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE problem_set (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(300) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_problem_set_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE problem_set_item (
    problem_set_id VARCHAR(36) NOT NULL,
    problem_version_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    PRIMARY KEY (problem_set_id, problem_version_id),
    UNIQUE KEY uk_problem_set_position (problem_set_id, position),
    CONSTRAINT fk_set_item_set FOREIGN KEY (problem_set_id) REFERENCES problem_set(id),
    CONSTRAINT fk_set_item_version FOREIGN KEY (problem_version_id) REFERENCES problem_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assignment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    problem_set_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(300) NOT NULL,
    opens_at DATETIME(6) NOT NULL,
    closes_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_assignment_set FOREIGN KEY (problem_set_id) REFERENCES problem_set(id),
    CONSTRAINT fk_assignment_owner FOREIGN KEY (owner_id) REFERENCES app_user(id),
    CONSTRAINT ck_assignment_times CHECK (closes_at > opens_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assignment_class (
    assignment_id VARCHAR(36) NOT NULL,
    class_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (assignment_id, class_id),
    CONSTRAINT fk_assignment_class_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id),
    CONSTRAINT fk_assignment_class_class FOREIGN KEY (class_id) REFERENCES classroom(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Copy problem_set_item at publication; later edits to the set do not change this snapshot.
CREATE TABLE assignment_problem (
    assignment_id VARCHAR(36) NOT NULL,
    problem_version_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    PRIMARY KEY (assignment_id, problem_version_id),
    UNIQUE KEY uk_assignment_problem_position (assignment_id, position),
    CONSTRAINT fk_assignment_problem_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id),
    CONSTRAINT fk_assignment_problem_version FOREIGN KEY (problem_version_id) REFERENCES problem_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
