-- 接入应用单独启用角色码映射，既有应用默认继续接收角色 ID。
ALTER TABLE sys_application
    ADD COLUMN role_codes_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE sys_application_role_code (
    application_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (application_id, role_id, role_code),
    INDEX idx_application_role_code_role (role_id),
    CONSTRAINT chk_application_role_code CHECK (CHAR_LENGTH(TRIM(role_code)) > 0),
    CONSTRAINT fk_application_role_code_app
        FOREIGN KEY (application_id) REFERENCES sys_application (id),
    CONSTRAINT fk_application_role_code_role
        FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
