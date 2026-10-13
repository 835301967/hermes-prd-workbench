package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DemoSkillsTest {
    @Test void defaultsToPortableHtml() {
        assertEquals("generic-admin-html", DemoSkills.normalize(null));
        assertEquals("generic-admin-html", DemoSkills.normalize(""));
        String prompt = DemoSkills.promptFor(null);
        assertTrue(prompt.contains("index.html"));
        assertTrue(prompt.contains("不承诺可编译"));
    }
    @Test void everyCatalogEntryHasAnAccessiblePrompt() {
        assertEquals(6, DemoSkills.catalog().size());
        for (var skill : DemoSkills.catalog()) assertFalse(DemoSkills.promptFor(skill.id()).isBlank());
    }
    @Test void unsupportedSkillFailsWithoutPrivateFallback() {
        assertThrows(ApiException.class, () -> DemoSkills.normalize("unknown-host-package"));
    }
}
