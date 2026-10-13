package org.hermesprd.workbench.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechSpecWorkspaceTest {
    @TempDir
    Path temp;

    @Test
    void assemblesArchitectureTablesAndApisWithoutTasks() throws Exception {
        Path ws = temp.resolve("ws");
        Files.createDirectories(ws.resolve("data"));
        Files.writeString(ws.resolve("architecture.md"), "# 架构\n分层说明");
        Files.writeString(ws.resolve("data/tables.json"), "[{\"tableName\":\"fin_voucher\",\"tableComment\":\"凭证\",\"columns\":[{\"name\":\"id\",\"type\":\"BIGINT\"}]}]");
        Files.writeString(ws.resolve("data/apis.json"), "[{\"method\":\"POST\",\"path\":\"/api/vouchers\",\"summary\":\"创建\"}]");
        Map<String, String> files = TechSpecWorkspace.readAllowedFiles(ws, null);
        assertTrue(TechSpecWorkspace.architectureMarkdown(files).contains("分层说明"));
        assertEquals(1, TechSpecWorkspace.assembleTables(files).size());
        assertEquals(1, TechSpecWorkspace.assembleApis(files).size());
        assertEquals(0, TechSpecWorkspace.assembleTasks(files).size());
    }

    @Test
    void mergesSplitTableAndApiJsonFiles() throws Exception {
        Path ws = temp.resolve("ws-merge");
        Files.createDirectories(ws.resolve("data"));
        Files.createDirectories(ws.resolve("modules"));
        Files.writeString(ws.resolve("architecture.md"), "# 架构\n分层");
        Files.writeString(ws.resolve("data/tables.json"), "{\"databaseTables\":[{\"tableName\":\"a\",\"columns\":[{\"name\":\"id\",\"type\":\"BIGINT\"}]}]}");
        Files.writeString(ws.resolve("modules/finance-tables.json"), "{\"tables\":[{\"tableName\":\"b\",\"columns\":[{\"name\":\"id\",\"type\":\"BIGINT\"}]}]}");
        Files.writeString(ws.resolve("data/apis.json"), "[{\"method\":\"GET\",\"path\":\"/a\",\"summary\":\"查\"}]");
        Files.writeString(ws.resolve("data/apis-part2.json"), "{\"apiEndpoints\":[{\"method\":\"POST\",\"path\":\"/b\",\"summary\":\"写\"}]}");

        Map<String, String> files = TechSpecWorkspace.readAllowedFiles(ws, null);
        assertEquals(2, TechSpecWorkspace.assembleTables(files).size());
        assertEquals(2, TechSpecWorkspace.assembleApis(files).size());
        assertEquals(0, TechSpecWorkspace.assembleTasks(files).size());
    }
}
