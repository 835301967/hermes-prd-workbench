-- Allow ESTIMATE runs and results alongside REVIEW, GENERATE, and DEMO.

ALTER TABLE agent_run DROP CHECK chk_agent_run_mode;

ALTER TABLE agent_run
    ADD CONSTRAINT chk_agent_run_mode CHECK (mode IN ('REVIEW', 'GENERATE', 'DEMO', 'ESTIMATE'));

ALTER TABLE agent_result DROP CHECK chk_agent_result_mode;

ALTER TABLE agent_result
    ADD CONSTRAINT chk_agent_result_mode CHECK (mode IN ('REVIEW', 'GENERATE', 'DEMO', 'ESTIMATE'));
