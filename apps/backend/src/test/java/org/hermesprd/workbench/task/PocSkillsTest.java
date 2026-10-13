package org.hermesprd.workbench.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocSkillsTest {
    @Test
    void loadsStaticAdminShellAndStaticRules() {
        String prompt = PocSkills.prompt();
        assertTrue(prompt.contains("static-admin-poc"));
        assertTrue(prompt.contains("write_poc_file"));
        assertTrue(prompt.contains("#0E67DA"));
        assertTrue(prompt.contains("height:68px"));
        assertTrue(prompt.contains("width:240px"));
        assertTrue(prompt.contains("Vue SFC"));
        assertTrue(prompt.contains("hash"));
        assertTrue(prompt.contains("data/catalog.json"));
        assertTrue(prompt.contains("事实源"));
        assertTrue(prompt.contains("核心域做深"));
        assertTrue(prompt.contains("textContent"));
        assertTrue(prompt.contains("innerHTML = ''") || prompt.contains("view.innerHTML"));
        assertTrue(prompt.contains("appendChild"));
        assertTrue(prompt.contains("PocUI.ledger"));
        assertTrue(prompt.contains("js/ui.js"));
        assertTrue(prompt.contains("labels"));
        assertTrue(prompt.contains("上一页"));
        assertTrue(prompt.contains("required-modules.json"));
        assertTrue(prompt.contains("platforms"));
        assertTrue(prompt.contains(".split('\"').join('&quot;')"));
        assertTrue(prompt.contains("20000"));
        assertTrue(prompt.contains("truncated"));
        assertFalse(prompt.contains("总共 8～15"));
    }
}
