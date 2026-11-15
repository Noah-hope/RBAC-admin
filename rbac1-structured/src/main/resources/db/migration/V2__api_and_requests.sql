ALTER TABLE sys_application ADD COLUMN code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin;
UPDATE sys_application SET code = CONCAT('app-', id);
ALTER TABLE sys_application MODIFY code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD UNIQUE KEY uq_application_code (code);

CREATE TABLE sys_api (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    operation_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    http_method VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    path VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    UNIQUE KEY uq_api_operation (application_id, operation_id),
    UNIQUE KEY uq_api_route (application_id, http_method, path),
    FOREIGN KEY (application_id) REFERENCES sys_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_api_permission (
    permission_id BIGINT NOT NULL,
    api_id BIGINT NOT NULL,
    PRIMARY KEY (permission_id, api_id),
    INDEX idx_api_permission_api (api_id),
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id),
    FOREIGN KEY (api_id) REFERENCES sys_api(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reviewer_user_id BIGINT,
    approved_role_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6),
    FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id),
    FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id),
    FOREIGN KEY (approved_role_id) REFERENCES sys_role(id) ON DELETE SET NULL,
    CHECK (status IN ('PENDING','APPROVED','REJECTED','SATISFIED','WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_create_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_user_id BIGINT NOT NULL,
    role_name VARCHAR(24) NOT NULL,
    role_description VARCHAR(48) NOT NULL DEFAULT '',
    status VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reviewer_user_id BIGINT,
    created_role_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6),
    FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id),
    FOREIGN KEY (created_role_id) REFERENCES sys_role(id) ON DELETE SET NULL,
    CHECK (status IN ('PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_create_request_permission (
    request_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (request_id, permission_id),
    INDEX idx_role_request_permission (permission_id),
    FOREIGN KEY (request_id) REFERENCES sys_role_create_request(id),
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
