-- Run only against the selected local project instance with a controlled administrator connection.
CREATE DATABASE IF NOT EXISTS prd_workbench
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- User provisioning is environment-specific. Do not embed passwords or reset root here.
