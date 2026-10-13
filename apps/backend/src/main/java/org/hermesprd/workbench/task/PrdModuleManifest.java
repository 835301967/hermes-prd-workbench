package org.hermesprd.workbench.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从 GENERATE catalog 抽出一级模块清单，供 POC 对齐覆盖，不改写 Agent 业务结论。
 */
public final class PrdModuleManifest {
    public static final String COVERAGE_ALL = "all";
    public static final String COVERAGE_SUBSET = "subset";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Module(String key, String title, boolean core) {
    }

    private PrdModuleManifest() {
    }

    public static List<Module> parseCatalog(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return parseCatalogNode(MAPPER.readTree(json));
        } catch (Exception ex) {
            return List.of();
        }
    }

    public static List<Module> parseCatalogNode(JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            return List.of();
        }
        JsonNode modules = root.get("modules");
        if (modules == null || !modules.isArray()) {
            return List.of();
        }
        List<Module> result = new ArrayList<>();
        for (JsonNode item : modules) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String key = text(item, "key");
            if (key.isBlank()) {
                continue;
            }
            String title = text(item, "title");
            if (title.isBlank()) {
                title = key;
            }
            boolean core = item.path("core").asBoolean(false);
            result.add(new Module(key, title, core));
        }
        return List.copyOf(result);
    }

    public static List<Module> parsePayload(JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            return List.of();
        }
        JsonNode files = payload.get("prd_files");
        if (files != null && files.isObject() && files.has(PrdWorkspace.CATALOG)) {
            List<Module> fromFile = parseCatalogValue(files.get(PrdWorkspace.CATALOG));
            if (!fromFile.isEmpty()) {
                return fromFile;
            }
        }
        if (payload.has("catalog")) {
            return parseCatalogValue(payload.get("catalog"));
        }
        return List.of();
    }

    public static String toJson(String sourceRunId, String coverage, List<Module> modules) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("sourceRunId", sourceRunId == null ? "" : sourceRunId);
        root.put("coverage", normalizeCoverage(coverage));
        ArrayNode array = root.putArray("modules");
        for (Module module : modules == null ? List.<Module>of() : modules) {
            ObjectNode item = array.addObject();
            item.put("key", module.key());
            item.put("title", module.title());
            item.put("core", module.core());
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception ex) {
            return "{\"sourceRunId\":\"\",\"coverage\":\"all\",\"modules\":[]}";
        }
    }

    public static String promptBlock(List<Module> modules, String coverage) {
        if (modules == null || modules.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("【必须覆盖的一级模块】控制面已根据当前稿 catalog 预置 data/required-modules.json，禁止覆盖该文件。");
        builder.append("catalog.modules[].key / title / core 必须按下列 key 原样使用，禁止改成 platforms、subjects、vouchers 等另一套菜单。\n");
        for (Module module : modules) {
            builder.append("- `").append(module.key()).append("` ").append(module.title());
            if (module.core()) {
                builder.append(" (core)");
            }
            builder.append('\n');
        }
        builder.append("共 ").append(modules.size()).append(" 个。");
        if (COVERAGE_SUBSET.equals(normalizeCoverage(coverage))) {
            builder.append("快速完整度可从该清单挑选 1 个核心域和最多 2 个次要台账，但 key 必须来自清单。\n");
        } else {
            builder.append("STANDARD/THOROUGH 必须全部写入 catalog 并出现在侧栏；允许额外增加二级台账，但不得漏掉或替换清单里的 key。\n");
        }
        return builder.toString();
    }

    public static String normalizeCoverage(String coverage) {
        if (coverage != null && COVERAGE_SUBSET.equals(coverage.trim().toLowerCase(Locale.ROOT))) {
            return COVERAGE_SUBSET;
        }
        return COVERAGE_ALL;
    }

    private static List<Module> parseCatalogValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            return parseCatalog(node.asText());
        }
        if (node.isObject()) {
            return parseCatalogNode(node);
        }
        return List.of();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return "";
        }
        return value.asText().trim();
    }
}
