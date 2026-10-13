package org.hermesprd.workbench.techspec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechSpecMarkdownExporterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TechSpecMarkdownExporter exporter = new TechSpecMarkdownExporter();

    @Test
    void exportsArchitectureTablesAndApis() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("title", "财务系统技术方案");
        payload.put("summary", "模块化单体可实施详细方案");
        payload.put("architectureMarkdown", "## 分层\n\nController → Service → Mapper\n");

        ArrayNode tables = payload.putArray("databaseTables");
        ObjectNode table = tables.addObject();
        table.put("tableName", "fin_voucher");
        table.put("tableComment", "凭证主表");
        ArrayNode columns = table.putArray("columns");
        ObjectNode col = columns.addObject();
        col.put("name", "id");
        col.put("type", "BIGINT");
        col.put("nullable", false);
        col.put("comment", "主键");
        table.putArray("indexes").add("PRIMARY KEY (id)");

        ArrayNode apis = payload.putArray("apiEndpoints");
        ObjectNode api = apis.addObject();
        api.put("method", "POST");
        api.put("path", "/api/v1/vouchers");
        api.put("summary", "创建凭证");
        api.put("requestBody", "{\"title\":\"差旅\"}");
        api.put("responseBody", "{\"id\":\"v1\"}");
        api.putArray("errorCodes").add("VOUCHER_INVALID");

        ObjectNode files = payload.putObject("tech_spec_files");
        files.put("modules/voucher.md", "### 凭证模块补充\n\n审批流走状态机。\n");

        String md = exporter.export(payload);

        assertTrue(md.contains("# 财务系统技术方案"));
        assertTrue(md.contains("交付说明"));
        assertTrue(md.contains("Controller → Service → Mapper"));
        assertTrue(md.contains("分模块设计补充"));
        assertTrue(md.contains("modules/voucher.md"));
        assertTrue(md.contains("`fin_voucher`"));
        assertTrue(md.contains("| `id` |"));
        assertTrue(md.contains("POST /api/v1/vouchers"));
        assertTrue(md.contains("VOUCHER_INVALID"));
        assertFalse(md.contains("总开发工单"));
        assertFalse(md.contains("工单实施说明"));
    }
    @Test
    void exportsStructuredIndexesAndLocalErrorTextWithoutChangingPayload() throws Exception {
        var payload = mapper.readTree("""
                {"title":"Local app","architectureMarkdown":"No server database",
                 "databaseTables":[{"tableName":"note","columns":[{"name":"id","type":"string"}],
                   "indexes":[{"name":"note_order","columns":["createdAt","id"],"intent":"local sorting"}]}],
                 "apiEndpoints":[{"method":"LOCAL","path":"Store.save","summary":"Save",
                   "errorCodes":"STORAGE_UNAVAILABLE: memory fallback"}]}
                """);
        var original = payload.toString();
        var markdown = exporter.export(payload);
        assertTrue(markdown.contains("note_order"));
        assertTrue(markdown.contains("createdAt"));
        assertTrue(markdown.contains("LOCAL Store.save"));
        assertTrue(markdown.contains("STORAGE_UNAVAILABLE: memory fallback"));
        org.junit.jupiter.api.Assertions.assertEquals(original, payload.toString());
    }

}
