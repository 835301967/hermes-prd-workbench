-- Hermes PRD Workbench Spike schema for MySQL 8.0.27+.
-- Initial public schema. Later versioned migrations add local accounts and supporting tables.

CREATE TABLE prd_task (
    id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_source (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    original_name VARCHAR(300) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'READY',
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_prd_source_task_created (task_id, created_at),
    CONSTRAINT fk_prd_source_task FOREIGN KEY (task_id) REFERENCES prd_task (id) ON DELETE CASCADE,
    CONSTRAINT chk_prd_source_status CHECK (status IN ('READY', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_run (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    parent_run_id VARCHAR(64) NULL,
    mode VARCHAR(20) NOT NULL,
    instruction TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    stage VARCHAR(20) NULL,
    hermes_run_id VARCHAR(128) NULL,
    error_code VARCHAR(100) NULL,
    error_message VARCHAR(1000) NULL,
    accepted TINYINT(1) NOT NULL DEFAULT 0,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_agent_run_task_created (task_id, created_at),
    KEY idx_agent_run_status_updated (status, updated_at),
    CONSTRAINT fk_agent_run_task FOREIGN KEY (task_id) REFERENCES prd_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_run_parent FOREIGN KEY (parent_run_id) REFERENCES agent_run (id) ON DELETE SET NULL,
    CONSTRAINT chk_agent_run_mode CHECK (mode IN ('REVIEW', 'GENERATE')),
    CONSTRAINT chk_agent_run_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_agent_run_stage CHECK (stage IS NULL OR stage IN ('READING', 'ANALYZING', 'SUBMITTING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_result (
    id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_result_run (run_id),
    CONSTRAINT fk_agent_result_run FOREIGN KEY (run_id) REFERENCES agent_run (id) ON DELETE CASCADE,
    CONSTRAINT chk_agent_result_mode CHECK (mode IN ('REVIEW', 'GENERATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
