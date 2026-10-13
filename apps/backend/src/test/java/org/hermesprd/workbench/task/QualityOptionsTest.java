package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityOptionsTest {
    @Test
    void defaultsCompletenessAndModeLensesWhenOmitted() {
        assertEquals("STANDARD", QualityOptions.normalizeCompleteness(null));
        assertEquals(List.of("module-rules"), QualityOptions.normalizeLenses("GENERATE", null, true));
        assertEquals(List.of("can-start"), QualityOptions.normalizeLenses("REVIEW", null, true));
        assertEquals(List.of("main-path", "more-states"), QualityOptions.normalizeLenses("POC", null, true));
        assertEquals(List.of(), QualityOptions.normalizeLenses("GENERATE", List.of(), false));
    }

    @Test
    void rejectsUnknownCompletenessAndLens() {
        assertThrows(ApiException.class, () -> QualityOptions.normalizeCompleteness("MAX"));
        assertThrows(ApiException.class, () -> QualityOptions.normalizeLenses("REVIEW", List.of("unknown"), false));
    }

    @Test
    void thoroughGenerateSkipsDuplicateModuleLens() {
        String prompt = QualityOptions.promptBlock("GENERATE", "THOROUGH", List.of("module-rules", "by-role"), null);
        assertTrue(prompt.contains("详尽可开工文档包") || prompt.contains("可开发详尽稿"));
        assertTrue(prompt.contains("按角色拆分"));
        assertEquals(0, prompt.split("按功能模块拆分", -1).length - 1);
    }

    @Test
    void revisePromptKeepsDraftName() {
        String prompt = QualityOptions.promptBlock("GENERATE", "STANDARD", List.of(), "run_1");
        assertTrue(prompt.contains("生成稿-run_1.md"));
        assertTrue(prompt.contains("不是从零重写"));
    }

    @Test
    void timeoutScalesWithCompleteness() {
        WorkbenchProperties properties = new WorkbenchProperties(
                Path.of("/tmp"),
                1,
                1,
                100,
                Duration.ofSeconds(600),
                Duration.ofSeconds(900),
                Duration.ofSeconds(2400),
                Duration.ofSeconds(2400),
                Duration.ofSeconds(2400),
                Duration.ofSeconds(2400),
                new WorkbenchProperties.Hermes("http://127.0.0.1:8643", Path.of("/tmp/key"), "prd-workbench", "0.16.0", Duration.ofSeconds(3)),
                Path.of("/tmp/plugin")
        );
        assertEquals(Duration.ofSeconds(900), QualityOptions.timeout("QUICK", "GENERATE", properties));
        assertEquals(Duration.ofSeconds(600), QualityOptions.timeout("STANDARD", "REVIEW", properties));
        assertEquals(Duration.ofSeconds(2400), QualityOptions.timeout("STANDARD", "GENERATE", properties));
        assertEquals(Duration.ofSeconds(3000), QualityOptions.timeout("THOROUGH", "GENERATE", properties));
        assertEquals(Duration.ofSeconds(1200), QualityOptions.timeout("THOROUGH", "DEMO", properties));
        assertEquals(Duration.ofSeconds(900), QualityOptions.timeout("QUICK", "POC", properties));
        assertEquals(Duration.ofSeconds(2400), QualityOptions.timeout("STANDARD", "POC", properties));
        assertEquals(Duration.ofSeconds(2700), QualityOptions.timeout("THOROUGH", "POC", properties));
        assertEquals(Duration.ofSeconds(900), QualityOptions.timeout("QUICK", "TEST_CASE", properties));
        assertEquals(Duration.ofSeconds(2400), QualityOptions.timeout("STANDARD", "TEST_CASE", properties));
        assertEquals(Duration.ofSeconds(2700), QualityOptions.timeout("THOROUGH", "TEST_CASE", properties));
        assertEquals(Duration.ofSeconds(2400), QualityOptions.timeout("STANDARD", "TECH_SPEC", properties));
        assertEquals(Duration.ofSeconds(2700), QualityOptions.timeout("THOROUGH", "TECH_SPEC", properties));
    }

    @Test
    void catalogHasThreeCompletenessLevels() {
        assertEquals(3, QualityOptions.catalog().completeness().size());
        assertFalse(QualityOptions.catalog().lenses().get("REVIEW").isEmpty());
        assertFalse(QualityOptions.catalog().lenses().get("TECH_SPEC").isEmpty());
        assertFalse(QualityOptions.catalog().lenses().get("POC").isEmpty());
    }

    @Test
    void techSpecLensesAndPrompts() {
        assertEquals(List.of("arch-focus"), QualityOptions.normalizeLenses("TECH_SPEC", null, true));
        String prompt = QualityOptions.promptBlock("TECH_SPEC", "STANDARD", List.of("arch-focus", "api-first", "module-deep"), null);
        assertTrue(prompt.contains("标准技术方案"));
        assertTrue(prompt.contains("架构与模型重点"));
        assertTrue(prompt.contains("接口契约重点"));
        assertTrue(prompt.contains("分模块设计补充"));
        assertFalse(prompt.contains("工单排期重点"));
    }

    @Test
    void thoroughDemoAvoidsFixedSizeAndViewCountConstraints() {
        String prompt = QualityOptions.promptBlock("DEMO", "THOROUGH", List.of(), null);

        assertTrue(prompt.contains("不设置固定菜单数、视图数或字符数"));
        assertFalse(prompt.contains("20000"));
        assertFalse(prompt.contains("25000"));
        assertFalse(prompt.contains("3~5 个核心视图"));
    }

    @Test
    void pocCompletenessUsesCatalogInsteadOfFileCaps() {
        String quick = QualityOptions.promptBlock("POC", "QUICK", List.of(), null);
        assertTrue(quick.contains("1 个核心域"));
        assertTrue(quick.contains("最多 2 个次要台账"));
        assertFalse(quick.contains("8 个左右"));

        String standard = QualityOptions.promptBlock("POC", "STANDARD", List.of("main-path", "more-states"), null);
        assertTrue(standard.contains("catalog 作为唯一事实源"));
        assertTrue(standard.contains("全部一级菜单"));
        assertTrue(standard.contains("按 catalog 把核心业务域打通"));
        assertTrue(standard.contains("禁止空菜单"));
        assertFalse(standard.contains("6～10"));

        String thorough = QualityOptions.promptBlock("POC", "THOROUGH", List.of(), null);
        assertTrue(thorough.contains("全部一级菜单"));
        assertTrue(thorough.contains("2～3 个核心域"));
        assertFalse(thorough.contains("15 个以内"));
    }

    @Test
    void pocRevisePromptDoesNotUseDraftMarkdown() {
        String prompt = QualityOptions.promptBlock("POC", "STANDARD", List.of(), "run_1");
        assertFalse(prompt.contains("生成稿-run_1.md"));
        assertTrue(prompt.contains("加深或修订"));
        assertTrue(prompt.contains("抽出 catalog"));
    }

    @Test
    void generateCompletenessUsesCatalogInsteadOfCharCaps() {
        String quick = QualityOptions.promptBlock("GENERATE", "QUICK", List.of(), null);
        assertTrue(quick.contains("1 个核心域"));
        assertTrue(quick.contains("禁止空章"));
        assertFalse(quick.contains("一页纸"));

        String standard = QualityOptions.promptBlock("GENERATE", "STANDARD", List.of(), null);
        assertTrue(standard.contains("catalog 作为唯一事实源"));
        assertFalse(standard.contains("15000"));

        String thorough = QualityOptions.promptBlock("GENERATE", "THOROUGH", List.of(), null);
        assertTrue(thorough.contains("全部一级模块"));
        assertTrue(thorough.contains("禁止「保留模块」空章") || thorough.contains("禁止「保留模块」"));
        assertFalse(thorough.contains("22000"));
    }
}
