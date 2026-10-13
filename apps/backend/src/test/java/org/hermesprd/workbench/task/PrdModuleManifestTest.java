package org.hermesprd.workbench.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrdModuleManifestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseCatalogKeepsKeyOrderAndCoreFlag() throws Exception {
        List<PrdModuleManifest.Module> modules = PrdModuleManifest.parseCatalog("""
                {
                  "modules": [
                    {"key":"base-setup","title":"基础设置","core":true},
                    {"key":"gl","title":"总账","core":false}
                  ]
                }
                """);

        assertEquals(List.of(
                new PrdModuleManifest.Module("base-setup", "基础设置", true),
                new PrdModuleManifest.Module("gl", "总账", false)
        ), modules);
    }

    @Test
    void parsePayloadReadsPrdFilesCatalogString() throws Exception {
        var payload = objectMapper.readTree("""
                {
                  "mode": "GENERATE",
                  "prd_files": {
                    "data/catalog.json": "{\\"modules\\":[{\\"key\\":\\"ar\\",\\"title\\":\\"应收\\",\\"core\\":true}]}"
                  }
                }
                """);

        assertEquals(
                List.of(new PrdModuleManifest.Module("ar", "应收", true)),
                PrdModuleManifest.parsePayload(payload)
        );
    }

    @Test
    void promptBlockRequiresExactKeys() {
        String prompt = PrdModuleManifest.promptBlock(List.of(
                new PrdModuleManifest.Module("base-setup", "基础设置", true),
                new PrdModuleManifest.Module("ap", "应付", false)
        ), "all");

        assertTrue(prompt.contains("必须覆盖的一级模块"));
        assertTrue(prompt.contains("`base-setup`"));
        assertTrue(prompt.contains("`ap`"));
        assertTrue(prompt.contains("禁止改成 platforms"));
        assertTrue(prompt.contains("必须全部写入 catalog"));
    }
}
