-- Local login and per-user task ownership. Not a full RBAC/tenant model.

CREATE TABLE prd_user (
    id VARCHAR(64) NOT NULL,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_prd_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_session (
    id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_prd_session_token_hash (token_hash),
    KEY idx_prd_session_user_expires (user_id, expires_at),
    CONSTRAINT fk_prd_session_user FOREIGN KEY (user_id) REFERENCES prd_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE prd_task
    ADD COLUMN user_id VARCHAR(64) NULL AFTER title,
    ADD KEY idx_prd_task_user_created (user_id, created_at),
    ADD CONSTRAINT fk_prd_task_user FOREIGN KEY (user_id) REFERENCES prd_user (id) ON DELETE CASCADE;
