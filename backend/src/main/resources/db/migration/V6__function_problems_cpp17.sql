ALTER TABLE problem_version
    ADD COLUMN problem_type VARCHAR(16) NOT NULL DEFAULT 'PROGRAM',
    ADD COLUMN language VARCHAR(16) NOT NULL DEFAULT 'C17',
    ADD COLUMN function_signature TEXT NULL,
    ADD COLUMN starter_code MEDIUMTEXT NULL,
    ADD COLUMN judge_template MEDIUMTEXT NULL,
    ADD CONSTRAINT ck_problem_version_type CHECK (problem_type IN ('PROGRAM', 'FUNCTION')),
    ADD CONSTRAINT ck_problem_version_language CHECK (language IN ('C17', 'CPP17'));
