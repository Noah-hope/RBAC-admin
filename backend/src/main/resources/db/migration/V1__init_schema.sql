CREATE TABLE sys_department (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    name VARCHAR(24) NOT NULL,
    INDEX idx_department_parent (parent_id),
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES sys_department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    department_id BIGINT,
    username VARCHAR(32) NOT NULL,
    name VARCHAR(24) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uq_user_username (username),
    INDEX idx_user_department (department_id),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES sys_department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_session (
    token_hash BINARY(32) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    INDEX idx_session_user (user_id),
    INDEX idx_session_expires_at (expires_at),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_application (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(24) NOT NULL,
    token_hash BINARY(32),
    UNIQUE KEY uq_application_name (name),
    UNIQUE KEY uq_application_token (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    parent_id BIGINT,
    name VARCHAR(24) NOT NULL,
    code VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    type VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uq_permission_application_code (application_id, code),
    INDEX idx_permission_parent (parent_id),
    CONSTRAINT chk_permission_parent CHECK (
        (type = 'MENU' AND parent_id IS NULL) OR (type = 'BUTTON' AND parent_id IS NOT NULL)
    ),
    CONSTRAINT fk_permission_application FOREIGN KEY (application_id) REFERENCES sys_application (id),
    CONSTRAINT fk_permission_parent FOREIGN KEY (parent_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(24) NOT NULL,
    description VARCHAR(48) NOT NULL DEFAULT '',
    role_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uq_role_name (name),
    CONSTRAINT chk_role_type CHECK (role_type IN ('BUSINESS', 'TOTAL_ADMIN', 'DEPARTMENT_ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    INDEX idx_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    INDEX idx_role_permission_permission (permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    occurred_at DATETIME(6) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_name VARCHAR(24) NOT NULL,
    action VARCHAR(64) NOT NULL,
    scope VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    detail TEXT NOT NULL,
    result VARCHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    INDEX idx_operation_log_time (occurred_at, id),
    CONSTRAINT chk_operation_log_scope CHECK (scope IN ('GLOBAL', 'USER_SCOPE')),
    CONSTRAINT chk_operation_log_result CHECK (result IN ('SUCCESS', 'FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_operation_log_target (
    operation_log_id BIGINT NOT NULL,
    target_no INT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT,
    target_name VARCHAR(128) NOT NULL,
    department_id_at_event BIGINT,
    PRIMARY KEY (operation_log_id, target_no),
    INDEX idx_operation_target_department (department_id_at_event, operation_log_id),
    CONSTRAINT fk_operation_target_log FOREIGN KEY (operation_log_id) REFERENCES sys_operation_log (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_role (name, role_type) VALUES
    ('总管理员', 'TOTAL_ADMIN'),
    ('部门管理员', 'DEPARTMENT_ADMIN');
