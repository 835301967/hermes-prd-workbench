package org.hermesprd.workbench.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrdSkillsTest {
    @Test
    void loadsEnterprisePackRules() {
        String prompt = PrdSkills.prompt();
        assertTrue(prompt.contains("enterprise-prd"));
        assertTrue(prompt.contains("write_prd_file"));
        assertTrue(prompt.contains("verify_prd"));
        assertTrue(prompt.contains("data/catalog.json"));
        assertTrue(prompt.contains("03-permissions.md"));
        assertTrue(prompt.contains("| 字段名 | 控件类型 | 必填 | 校验 | 默认值/联动 |"));
        assertTrue(prompt.contains("AC-"));
        assertTrue(prompt.contains("BR-"));
        assertTrue(prompt.contains("workspaceFile"));
        assertTrue(prompt.contains("README.md"));
        assertFalse(prompt.contains("15000"));
        assertFalse(prompt.contains("22000"));
        assertFalse(prompt.contains("partIndex"));
        assertFalse(prompt.contains("单次能完成"));
    }
}
