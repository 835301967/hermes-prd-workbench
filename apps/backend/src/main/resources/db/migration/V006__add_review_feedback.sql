ALTER TABLE agent_run
    ADD COLUMN review_feedback JSON NULL AFTER accepted,
    ADD COLUMN review_feedback_revision INT UNSIGNED NOT NULL DEFAULT 0 AFTER review_feedback;
