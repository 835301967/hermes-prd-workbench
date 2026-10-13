package org.hermesprd.workbench.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseWorkspaceTest {
    @TempDir
    Path temp;

    @Test
    void allowsModuleJsonAndRejectsSrc() {
        assertTrue(TestCaseWorkspace.isAllowedPath("README.md"));
        assertTrue(TestCaseWorkspace.isAllowedPath("data/catalog.json"));
        assertTrue(TestCaseWorkspace.isAllowedPath("modules/gl.json"));
        assertFalse(TestCaseWorkspace.isAllowedPath("src/main.ts"));
        assertFalse(TestCaseWorkspace.isAllowedPath("../evil.json"));
    }

    @Test
    void assemblesItemsFromModuleFiles() throws Exception {
        Path ws = temp.resolve("ws");
        Files.createDirectories(ws.resolve("modules"));
        Files.createDirectories(ws.resolve("data"));
        Files.writeString(ws.resolve("data/catalog.json"), "{\"modules\":[{\"key\":\"gl\",\"title\":\"总账\",\"core\":true}]}");
        Files.writeString(ws.resolve("modules/gl.json"), """
                {
                  "module": "总账",
                  "items": [
                    {"title":"凭证过账成功","expectedResult":"状态变为已过账","type":"FUNCTION","priority":"P0"}
                  ]
                }
                """);
        Map<String, String> files = TestCaseWorkspace.readAllowedFiles(ws, null);
        var items = TestCaseWorkspace.assembleItems(files);
        assertEquals(1, items.size());
        assertEquals("总账", items.get(0).path("module").asText());
        assertEquals("gl", items.get(0).path("moduleKey").asText());
    }
}
