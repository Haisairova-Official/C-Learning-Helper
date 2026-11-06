CREATE TABLE import_job (
 id VARCHAR(64) NOT NULL PRIMARY KEY,
 owner_id VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL,
 payload_json LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 KEY ix_import_job_owner (owner_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_analysis (
 id VARCHAR(64) NOT NULL PRIMARY KEY,
 owner_id VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL,
 payload_json LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 KEY ix_ai_analysis_owner (owner_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE draft_import_receipt (
 import_job_id VARCHAR(64) NOT NULL PRIMARY KEY,
 owner_id VARCHAR(36) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 problem_set_id VARCHAR(36) NOT NULL,
 problem_ids_json LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_import_receipt_job FOREIGN KEY (import_job_id) REFERENCES import_job(id),
 CONSTRAINT fk_import_receipt_set FOREIGN KEY (problem_set_id) REFERENCES problem_set(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
