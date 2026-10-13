-- Separate engineering-code submissions from the standard HTML-only DEMO contract.

ALTER TABLE agent_run DROP CHECK chk_agent_run_mode;

ALTER TABLE agent_run
    ADD CONSTRAINT chk_agent_run_mode CHECK (mode IN ('REVIEW', 'GENERATE', 'DEMO', 'ESTIMATE', 'TEST_CASE', 'TECH_SPEC'));

ALTER TABLE agent_result DROP CHECK chk_agent_result_mode;

ALTER TABLE agent_result
    ADD CONSTRAINT chk_agent_result_mode CHECK (mode IN ('REVIEW', 'GENERATE', 'DEMO', 'ESTIMATE', 'TEST_CASE', 'TECH_SPEC'));
