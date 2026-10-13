package org.hermesprd.workbench.techspec;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a deliverable TECH_SPEC Markdown document from the assembled result payload.
 * Used by single-run export and the delivery bundle.
 */
@Component
public class TechSpecMarkdownExporter {

    public String export(JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            return "# 研发技术方案\n\n（无有效结果）\n";
        }

        StringBuilder sb = new StringBuilder();
        String title = text(payload, "title", "研发技术方案与任务工单拆解清单");
        String summary = text(payload, "summary", "");

        JsonNode tasks = payload.get("tasks");
        JsonNode tables = payload.get("databaseTables");
        JsonNode apis = payload.get("apiEndpoints");
        int taskCount = countArray(tasks);
        int tableCount = countArray(tables);
        int apiCount = countArray(apis);

        int totalTasks = payload.path("totalTasks").asInt(taskCount);
        if (totalTasks <= 0) {
            totalTasks = taskCount;
        }
        double totalDevDays = payload.path("totalDevDays").asDouble(sumDevDays(tasks));

        sb.append("# ").append(title).append("\n\n");
        if (!summary.isBlank()) {
            sb.append("> ").append(summary.trim()).append("\n\n");
        }

        sb.append("## 交付说明\n\n");
        sb.append("本文档由 PRD 工作台技术方案结果组装，供前后端、DBA 直接按方案实施。\n\n");
        sb.append("- **架构设计**：选型、分层、模块依赖、关键流转与协作约定（含 Mermaid）。\n");
        sb.append("- **数据模型**：表、字段、可空性、索引与约束。\n");
        sb.append("- **API 契约**：方法、路径、摘要、请求/响应样例、错误码。\n");
        sb.append("- **模块补充**：若结果含 `modules/*.md`，一并附在架构之后。\n\n");

        sb.append("## 方案指标概览\n\n");
        sb.append("| 指标 | 数值 |\n| :--- | :--- |\n");
        sb.append("| 数据表 | ").append(tableCount).append(" 张 |\n");
        sb.append("| API 端点 | ").append(apiCount).append(" 个 |\n");
        if (taskCount > 0) {
            sb.append("| （历史）研发工单 | ").append(totalTasks).append(" 项 |\n");
            sb.append("| （历史）预估人天 | ").append(formatDays(totalDevDays)).append(" 人天 |\n");
        }
        sb.append("\n---\n\n");

        String arch = text(payload, "architectureMarkdown", "");
        if (!arch.isBlank()) {
            String trimmedArch = arch.trim();
            if (!trimmedArch.startsWith("#")) {
                sb.append("## 系统分层架构与设计推演\n\n");
            }
            sb.append(trimmedArch).append("\n\n");
        }

        appendModuleMarkdownFiles(sb, payload.get("tech_spec_files"));

        if (tables != null && tables.isArray() && !tables.isEmpty()) {
            sb.append("## 数据模型设计（Database Tables）\n\n");
            sb.append("共 ").append(tableCount).append(" 张表。字段、索引与约束如下，可直接作为 DDL 设计依据。\n\n");
            for (JsonNode table : tables) {
                String tableName = text(table, "tableName", "table");
                String tableComment = text(table, "tableComment", "");
                sb.append("### `").append(tableName).append("`");
                if (!tableComment.isBlank()) {
                    sb.append(" — ").append(tableComment);
                }
                sb.append("\n\n");

                JsonNode columns = table.get("columns");
                if (columns != null && columns.isArray() && !columns.isEmpty()) {
                    sb.append("| 字段名 | 类型 | 可空 | 说明 |\n");
                    sb.append("| :--- | :--- | :--- | :--- |\n");
                    for (JsonNode col : columns) {
                        sb.append("| `").append(text(col, "name", "")).append("` | ")
                                .append(escapeCell(text(col, "type", ""))).append(" | ")
                                .append(col.path("nullable").asBoolean(true) ? "YES" : "NO").append(" | ")
                                .append(escapeCell(text(col, "comment", ""))).append(" |\n");
                    }
                    sb.append("\n");
                } else {
                    sb.append("_（该表未提供字段明细）_\n\n");
                }

                JsonNode indexes = table.get("indexes");
                if (indexes != null && indexes.isArray() && !indexes.isEmpty()) {
                    sb.append("**索引与约束**：\n\n");
                    for (JsonNode idx : indexes) {
                        sb.append("- `").append((idx.isTextual() ? idx.asText() : idx.toString())).append("`\n");
                    }
                    sb.append("\n");
                }
            }
        }

        if (apis != null && apis.isArray() && !apis.isEmpty()) {
            sb.append("## API 接口契约\n\n");
            sb.append("共 ").append(apiCount).append(" 个端点。请求/响应样例为紧凑 JSON 字符串原文。\n\n");
            int i = 1;
            for (JsonNode api : apis) {
                String method = text(api, "method", "POST").toUpperCase(Locale.ROOT);
                String path = text(api, "path", "/");
                String apiSummary = text(api, "summary", "");
                sb.append("### ").append(i++).append(". `").append(method).append(" ").append(path).append("`");
                if (!apiSummary.isBlank()) {
                    sb.append(" — ").append(apiSummary);
                }
                sb.append("\n\n");

                String req = text(api, "requestBody", "");
                if (!req.isBlank()) {
                    sb.append("**Request**\n\n```json\n").append(prettyJsonOrRaw(req)).append("\n```\n\n");
                }
                String resp = text(api, "responseBody", "");
                if (!resp.isBlank()) {
                    sb.append("**Response**\n\n```json\n").append(prettyJsonOrRaw(resp)).append("\n```\n\n");
                }
                JsonNode errors = api.get("errorCodes");
                if (errors != null && errors.isArray() && !errors.isEmpty()) {
                    sb.append("**Error codes**\n\n");
                    for (JsonNode err : errors) {
                        sb.append("- ").append((err.isTextual() ? err.asText() : err.toString())).append("\n");
                    }
                    sb.append("\n");
                } else if (errors != null && errors.isTextual() && !errors.asText().isBlank()) {
                    sb.append("**Error codes**\n\n").append(errors.asText()).append("\n\n");
                }
            }
        }

        if (tasks != null && tasks.isArray() && !tasks.isEmpty()) {
            sb.append("## （历史）研发任务工单清单\n\n");
            sb.append("> 以下内容来自历史结果；新生成的技术方案不再包含工单拆解。\n\n");
            sb.append("| 工单编号 | 模块 | 任务名称 | 端别 | 优先级 | 人天 | 前置依赖 | 实现要点 |\n");
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n");
            for (JsonNode t : tasks) {
                String taskId = firstText(t, "taskId", "id");
                String module = text(t, "module", "");
                String name = firstText(t, "name", "title");
                String type = text(t, "type", "");
                String priority = text(t, "priority", "");
                double days = t.has("devDays") ? t.path("devDays").asDouble(0) : t.path("days").asDouble(0);
                String deps = joinArray(t.get("dependencies"));
                String desc = text(t, "description", "").replace("|", "\\|").replace("\n", " ");
                sb.append("| `").append(taskId).append("` | ")
                        .append(escapeCell(module)).append(" | ")
                        .append(escapeCell(name)).append(" | `")
                        .append(type).append("` | `")
                        .append(priority).append("` | ")
                        .append(formatDays(days)).append(" | ")
                        .append(deps.isBlank() ? "-" : deps).append(" | ")
                        .append(desc.isBlank() ? "-" : desc).append(" |\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private void appendModuleMarkdownFiles(StringBuilder sb, JsonNode files) {
        if (files == null || !files.isObject() || files.isEmpty()) {
            return;
        }
        List<Map.Entry<String, JsonNode>> moduleMd = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = files.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String path = entry.getKey();
            if (path != null && path.startsWith("modules/") && path.endsWith(".md")
                    && entry.getValue() != null && entry.getValue().isTextual()
                    && !entry.getValue().asText().isBlank()) {
                moduleMd.add(entry);
            }
        }
        if (moduleMd.isEmpty()) {
            return;
        }
        moduleMd.sort(Comparator.comparing(Map.Entry::getKey));
        sb.append("## 分模块设计补充\n\n");
        for (Map.Entry<String, JsonNode> entry : moduleMd) {
            sb.append("### 文件 `").append(entry.getKey()).append("`\n\n");
            sb.append(entry.getValue().asText().trim()).append("\n\n");
        }
    }

    private static int countArray(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    private static double sumDevDays(JsonNode tasks) {
        if (tasks == null || !tasks.isArray()) {
            return 0;
        }
        double sum = 0;
        for (JsonNode t : tasks) {
            if (t.has("devDays")) {
                sum += t.path("devDays").asDouble(0);
            } else if (t.has("days")) {
                sum += t.path("days").asDouble(0);
            }
        }
        return sum;
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        String v = node.get(field).asText("");
        return v == null || v.isBlank() ? fallback : v;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String v = text(node, field, "");
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String joinArray(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : arr) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(item.asText(""));
        }
        return sb.toString();
    }

    private static String escapeCell(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }

    private static String nullToDash(String raw) {
        return raw == null || raw.isBlank() ? "-" : raw;
    }

    private static String formatDays(double days) {
        if (Math.abs(days - Math.rint(days)) < 1e-9) {
            return String.valueOf((long) Math.rint(days));
        }
        return String.format(Locale.ROOT, "%.1f", days);
    }

    private static String prettyJsonOrRaw(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode node = mapper.readTree(trimmed);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ex) {
            return trimmed;
        }
    }
}
