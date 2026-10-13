-- Persist per-run output completeness and optional focus lenses.
-- parent_run_id is reused for GENERATE revisions of a previous draft.

ALTER TABLE agent_run
    ADD COLUMN completeness VARCHAR(20) NOT NULL DEFAULT 'STANDARD' AFTER instruction,
    ADD COLUMN lenses VARCHAR(500) NULL AFTER completeness;

ALTER TABLE agent_run
    ADD CONSTRAINT chk_agent_run_completeness CHECK (completeness IN ('QUICK', 'STANDARD', 'THOROUGH'));
