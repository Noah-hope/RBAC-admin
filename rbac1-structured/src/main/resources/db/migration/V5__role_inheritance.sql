CREATE TABLE sys_role_inheritance (
    role_id BIGINT NOT NULL,
    inherited_role_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, inherited_role_id),
    INDEX idx_role_inheritance_parent (inherited_role_id),
    FOREIGN KEY (role_id) REFERENCES sys_role(id),
    FOREIGN KEY (inherited_role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_create_request_inheritance (
    request_id BIGINT NOT NULL,
    inherited_role_id BIGINT NOT NULL,
    inherited_role_name VARCHAR(24) NOT NULL,
    PRIMARY KEY (request_id, inherited_role_id),
    FOREIGN KEY (request_id) REFERENCES sys_role_create_request(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
