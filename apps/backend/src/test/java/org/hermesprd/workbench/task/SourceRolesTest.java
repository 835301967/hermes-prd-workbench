package org.hermesprd.workbench.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceRolesTest {
    @Test
    void generateRunIgnoresDraftAndReviewExtras() {
        assertTrue(SourceRoles.visibleForMode("GENERATE", "IOC需求清单.txt"));
        assertFalse(SourceRoles.visibleForMode("GENERATE", "生成稿-run_1.md"));
        assertFalse(SourceRoles.visibleForMode("GENERATE", "评审补充-补充.txt"));
    }

    @Test
    void reviewAndDemoIsolateOwnExtras() {
        // When no draft exists: raw uploaded PRD materials are visible
        assertTrue(SourceRoles.visibleForMode("REVIEW", "IOC需求清单.txt", false));
        assertTrue(SourceRoles.visibleForMode("REVIEW", "广州示例精细化工有限公司 CRM 业务模块产品需求文档（PRD）V1.1.docx", false));
        assertTrue(SourceRoles.visibleForMode("REVIEW", "评审补充-补充.txt", false));
        assertFalse(SourceRoles.visibleForMode("REVIEW", "演示补充-风格.txt", false));

        assertTrue(SourceRoles.visibleForMode("DEMO", "IOC需求清单.txt", false));
        assertTrue(SourceRoles.visibleForMode("DEMO", "广州示例精细化工有限公司 CRM 业务模块产品需求文档（PRD）V1.1.docx", false));
        assertTrue(SourceRoles.visibleForMode("DEMO", "演示补充-补充.txt", false));
        assertFalse(SourceRoles.visibleForMode("DEMO", "评审补充-补充.txt", false));
        assertTrue(SourceRoles.visibleForMode("POC", "IOC需求清单.txt", false));
        assertTrue(SourceRoles.visibleForMode("POC", "POC补充-范围.txt", false));
        assertFalse(SourceRoles.visibleForMode("POC", "演示补充-风格.txt", false));

        // When draft exists: raw materials are hidden, only draft and respective extras are visible
        assertFalse(SourceRoles.visibleForMode("REVIEW", "IOC需求清单.txt", true));
        assertTrue(SourceRoles.visibleForMode("REVIEW", "生成稿-run_1.md", true));
        assertTrue(SourceRoles.visibleForMode("REVIEW", "评审补充-补充.txt", true));
        assertFalse(SourceRoles.visibleForMode("REVIEW", "演示补充-风格.txt", true));

        assertFalse(SourceRoles.visibleForMode("DEMO", "IOC需求清单.txt", true));
        assertTrue(SourceRoles.visibleForMode("DEMO", "生成稿-run_1.md", true));
        assertTrue(SourceRoles.visibleForMode("DEMO", "演示补充-补充.txt", true));
        assertFalse(SourceRoles.visibleForMode("DEMO", "评审补充-补充.txt", true));
        assertFalse(SourceRoles.visibleForMode("POC", "IOC需求清单.txt", true));
        assertTrue(SourceRoles.visibleForMode("POC", "生成稿-run_1.md", true));
        assertTrue(SourceRoles.visibleForMode("POC", "POC补充-范围.txt", true));
        assertFalse(SourceRoles.visibleForMode("POC", "演示补充-风格.txt", true));

        assertEquals("生成稿-run_1.md", SourceRoles.draftName("run_1"));
        assertTrue(SourceRoles.visibleForRun("GENERATE", "生成稿-run_1.md", "run_1"));
        assertFalse(SourceRoles.visibleForRun("GENERATE", "生成稿-run_2.md", "run_1"));
        assertFalse(SourceRoles.visibleForMode("GENERATE", "生成稿-run_1.md"));
        assertEquals("run_1", SourceRoles.draftRunId("生成稿-run_1.md"));
        assertEquals(null, SourceRoles.draftRunId("生成稿-.md"));
        assertEquals(null, SourceRoles.draftRunId("生成稿-run_1.txt"));
    }

    @Test
    void estimateRunSeesRawMaterialsWhenNoDraftAndDraftWhenHasDraft() {
        // When no draft exists: raw materials are visible
        assertTrue(SourceRoles.visibleForMode("ESTIMATE", "IOC需求清单.txt", false));
        assertFalse(SourceRoles.visibleForMode("ESTIMATE", "评审补充-补充.txt", false));
        assertFalse(SourceRoles.visibleForMode("ESTIMATE", "演示补充-风格.txt", false));
        assertTrue(SourceRoles.visibleForMode("ESTIMATE", "评估补充-单价.txt", false));

        // When draft exists: only draft and estimate extras are visible, raw materials are hidden
        assertTrue(SourceRoles.visibleForMode("ESTIMATE", "生成稿-run_1.md", true));
        assertFalse(SourceRoles.visibleForMode("ESTIMATE", "IOC需求清单.txt", true));
        assertTrue(SourceRoles.visibleForMode("ESTIMATE", "评估补充-单价.txt", true));
        assertFalse(SourceRoles.visibleForMode("ESTIMATE", "评审补充-补充.txt", true));
        assertFalse(SourceRoles.visibleForMode("ESTIMATE", "演示补充-风格.txt", true));
    }

    @Test
    void testCaseRunSeesRawMaterialsWhenNoDraftAndDraftWhenHasDraft() {
        // When no draft exists: raw materials are visible
        assertTrue(SourceRoles.visibleForMode("TEST_CASE", "IOC需求清单.txt", false));
        assertFalse(SourceRoles.visibleForMode("TEST_CASE", "评审补充-补充.txt", false));
        assertFalse(SourceRoles.visibleForMode("TEST_CASE", "演示补充-风格.txt", false));
        assertTrue(SourceRoles.visibleForMode("TEST_CASE", "用例补充-规则.txt", false));

        // When draft exists: only draft and test case extras are visible, raw materials are hidden
        assertTrue(SourceRoles.visibleForMode("TEST_CASE", "生成稿-run_1.md", true));
        assertFalse(SourceRoles.visibleForMode("TEST_CASE", "IOC需求清单.txt", true));
        assertTrue(SourceRoles.visibleForMode("TEST_CASE", "用例补充-规则.txt", true));
        assertFalse(SourceRoles.visibleForMode("TEST_CASE", "评审补充-补充.txt", true));
        assertFalse(SourceRoles.visibleForMode("TEST_CASE", "演示补充-风格.txt", true));
    }

    @Test
    void techSpecRunSeesRawMaterialsWhenNoDraftAndDraftWhenHasDraft() {
        // When no draft exists: raw materials are visible
        assertTrue(SourceRoles.visibleForMode("TECH_SPEC", "IOC需求清单.txt", false));
        assertFalse(SourceRoles.visibleForMode("TECH_SPEC", "评审补充-补充.txt", false));
        assertFalse(SourceRoles.visibleForMode("TECH_SPEC", "演示补充-风格.txt", false));
        assertTrue(SourceRoles.visibleForMode("TECH_SPEC", "方案补充-架构.txt", false));

        // When draft exists: only draft and tech spec extras are visible, raw materials are hidden
        assertTrue(SourceRoles.visibleForMode("TECH_SPEC", "生成稿-run_1.md", true));
        assertFalse(SourceRoles.visibleForMode("TECH_SPEC", "IOC需求清单.txt", true));
        assertTrue(SourceRoles.visibleForMode("TECH_SPEC", "方案补充-架构.txt", true));
        assertFalse(SourceRoles.visibleForMode("TECH_SPEC", "评审补充-补充.txt", true));
        assertFalse(SourceRoles.visibleForMode("TECH_SPEC", "演示补充-风格.txt", true));
    }
}
