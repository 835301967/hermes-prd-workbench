package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrdWorkspaceTest {
    @Test
    void allowlistAcceptsPackFilesAndRejectsEngineeringPaths() {
        assertTrue(PrdWorkspace.isPrdAllowedPath("README.md"));
        assertTrue(PrdWorkspace.isPrdAllowedPath("00-overview.md"));
        assertTrue(PrdWorkspace.isPrdAllowedPath("modules/voucher.md"));
        assertTrue(PrdWorkspace.isPrdAllowedPath("data/catalog.json"));
        assertTrue(PrdWorkspace.isPrdAllowedPath("open-questions.md"));
        assertFalse(PrdWorkspace.isPrdAllowedPath("index.html"));
        assertFalse(PrdWorkspace.isPrdAllowedPath("package.json"));
        assertFalse(PrdWorkspace.isPrdAllowedPath("src/main.ts"));
        assertFalse(PrdWorkspace.isPrdAllowedPath("modules/a/b.md"));
        assertFalse(PrdWorkspace.isPrdAllowedPath("../secret.md"));
    }

    @Test
    void assemblesMarkdownInReadmeOrder(@TempDir Path dir) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("README.md", "# 顺序\n1. `00-overview.md`\n2. `modules/voucher.md`\n3. `open-questions.md`\n");
        files.put("00-overview.md", "# 概述\n## 目标\n### 范围\n#### 细则\n目标：记账。");
        files.put("modules/voucher.md", "# 1. 凭证\n## 1.1 字段矩阵\n| 字段名 | 控件类型 | 必填 | 校验 | 默认值/联动 |\n");
        files.put("open-questions.md", "# 待确认\n- 汇率来源？");
        files.put("data/catalog.json", "{\"modules\":[{\"key\":\"voucher\",\"title\":\"凭证\",\"core\":true}]}");
        Files.createDirectories(dir.resolve("modules"));
        Files.createDirectories(dir.resolve("data"));
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Files.writeString(dir.resolve(entry.getKey()), entry.getValue());
        }
        Map<String, String> loaded = PrdWorkspace.readAllowedFiles(dir, List.of());
        String markdown = PrdWorkspace.assembleMarkdown(loaded);
        assertTrue(markdown.contains("<!-- file: 00-overview.md -->"));
        assertTrue(markdown.indexOf("概述") < markdown.indexOf("凭证"));
        assertTrue(markdown.contains("汇率来源"));
        assertFalse(markdown.contains("catalog.json"));
        assertEquals(List.of("00-overview.md", "modules/voucher.md", "open-questions.md"),
                PrdWorkspace.assemblyOrder(loaded));
        assertTrue(markdown.contains("# 1 概述"));
        assertTrue(markdown.contains("## 1.1 目标"));
        assertTrue(markdown.contains("### 1.1.1 范围"));
        assertTrue(markdown.contains("#### 1.1.1.1 细则"));
        assertTrue(markdown.contains("# 2 凭证"));
        assertTrue(markdown.contains("## 2.1 字段矩阵"));
        assertTrue(markdown.contains("# 3 待确认"));
        assertFalse(markdown.contains("# 1. 凭证"));
    }

    @Test
    void numberHeadingsSkipsFencedCodeAndStripsExistingNumbers() {
        String raw = """
                # 已有编号
                ```mermaid
                # 不是标题
                ```
                ## 1.2 旧二级
                ### 细节
                ~~~
                ## 代码里也不编号
                ~~~
                # 下一章
                """;
        String numbered = PrdWorkspace.numberHeadings(raw);
        assertTrue(numbered.contains("# 1 已有编号"));
        assertTrue(numbered.contains("## 1.1 旧二级"));
        assertTrue(numbered.contains("### 1.1.1 细节"));
        assertTrue(numbered.contains("# 2 下一章"));
        assertTrue(numbered.contains("```mermaid\n# 不是标题\n```"));
        assertTrue(numbered.contains("~~~\n## 代码里也不编号\n~~~"));
    }

    @Test
    void stripLeadingSectionNumberRemovesArabicPrefixOnly() {
        assertEquals("概述", PrdWorkspace.stripLeadingSectionNumber("1 概述"));
        assertEquals("概述", PrdWorkspace.stripLeadingSectionNumber("1. 概述"));
        assertEquals("字段矩阵", PrdWorkspace.stripLeadingSectionNumber("1.1.1 字段矩阵"));
        assertEquals("待确认", PrdWorkspace.stripLeadingSectionNumber("待确认"));
    }

    @Test
    void readRequiresReadme(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("00-overview.md"), "# 概述");
        ApiException ex = assertThrows(ApiException.class, () -> PrdWorkspace.readAllowedFiles(dir, List.of()));
        assertEquals("INVALID_REQUEST", ex.code());
    }

    @Test
    void normalizeRejectsTraversal() {
        assertThrows(ApiException.class, () -> PrdWorkspace.normalizeRelativePath("../secret.md"));
    }
}
