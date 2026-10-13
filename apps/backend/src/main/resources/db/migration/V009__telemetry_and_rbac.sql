-- Telemetry events and RBAC role for administration

ALTER TABLE prd_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER display_name,
    ADD KEY idx_prd_user_role (role);


CREATE TABLE prd_telemetry_event (
    id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NULL,
    username VARCHAR(32) NULL,
    event_type VARCHAR(32) NOT NULL,
    module VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    duration_ms BIGINT UNSIGNED NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    metadata_json JSON NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(256) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_telemetry_created (created_at),
    KEY idx_telemetry_user (user_id, created_at),
    KEY idx_telemetry_module_action (module, action, created_at),
    KEY idx_telemetry_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
