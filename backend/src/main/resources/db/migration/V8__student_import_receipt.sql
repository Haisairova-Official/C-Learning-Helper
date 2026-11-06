CREATE UNIQUE INDEX uk_app_user_student_number ON app_user(student_number);

CREATE TABLE student_import_receipt (
    owner_id VARCHAR(36) NOT NULL,
    operation_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    class_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    initial_password_hash VARCHAR(255) NOT NULL,
    result_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY(owner_id, operation_key),
    CONSTRAINT fk_student_import_owner FOREIGN KEY(owner_id) REFERENCES app_user(id),
    CONSTRAINT fk_student_import_class FOREIGN KEY(class_id) REFERENCES classroom(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
