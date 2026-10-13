-- Allow DEMO results alongside REVIEW and GENERATE.

ALTER TABLE agent_result DROP CHECK chk_agent_result_mode;

ALTER TABLE agent_result
    ADD CONSTRAINT chk_agent_result_mode CHECK (mode IN ('REVIEW', 'GENERATE', 'DEMO'));
