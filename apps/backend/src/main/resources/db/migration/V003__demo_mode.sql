-- Allow DEMO runs alongside REVIEW and GENERATE.

ALTER TABLE agent_run DROP CHECK chk_agent_run_mode;

ALTER TABLE agent_run
    ADD CONSTRAINT chk_agent_run_mode CHECK (mode IN ('REVIEW', 'GENERATE', 'DEMO'));
