package org.hermesprd.workbench.result;

import com.fasterxml.jackson.databind.JsonNode;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.hermesprd.workbench.task.PocWorkspace;
import org.hermesprd.workbench.task.PrdWorkspace;
import org.hermesprd.workbench.task.TechSpecWorkspace;
import org.hermesprd.workbench.task.TestCaseWorkspace;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ResultValidator {
    private static final java.util.regex.Pattern SECRET_ASSIGNMENT = java.util.regex.Pattern.compile(
            "(?i)[\\\"']?(api[_-]?key|access[_-]?token|auth(?:orization)?|bearer|client[_-]?secret|database[_-]?(?:password|url)|(?:db|mysql|redis)[_-]?password|password|token)[\\\"']?\\s*[:=]\\s*(?:[\\\"'`]\\s*(?!\\$\\{|YOUR_|CHANGE_ME|REPLACE_|<)[^\\\"'`\\r\\n]{8,}[\\\"'`]|(?!\\$\\{|YOUR_|CHANGE_ME|REPLACE_|<)(?:Bearer\\s+)?[^\\s,#\\r\\n]{8,})"
    );
    private static final java.util.regex.Pattern SECRET_MARKER = java.util.regex.Pattern.compile(
            "(?i)-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----"
    );
    private final WorkbenchProperties properties;

    public ResultValidator(WorkbenchProperties properties) {
        this.properties = properties;
    }

    public void validate(String expectedMode, JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "结果必须是 JSON 对象");
        }
        long size = payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (size > properties.maxResultBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE", "结果超过大小限制");
        }
        String mode = text(payload, "mode");
        if (!expectedMode.equals(mode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "mode 必须与当前 Run 一致");
        }
        requireText(payload, "title");
        requireText(payload, "summary");
        if ("REVIEW".equals(expectedMode)) {
            validateReviewItems(payload, "blockingIssues", true);
            validateReviewItems(payload, "otherSuggestions", false);
            validateReviewItems(payload, "readyItems", false);
        }
        if ("GENERATE".equals(expectedMode)) {
            if (isBlank(payload, "markdown")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "生成结果需要 markdown");
            }
            JsonNode files = payload.get("prd_files");
            if (files != null && !files.isNull()) {
                if (!files.isObject() || files.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "GENERATE prd_files 必须是非空文件映射");
                }
                if (!files.has(PrdWorkspace.README) || !files.get(PrdWorkspace.README).isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "GENERATE prd_files 必须包含 README.md");
                }
                java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = files.fields();
                while (fields.hasNext()) {
                    java.util.Map.Entry<String, JsonNode> entry = fields.next();
                    if (!PrdWorkspace.isPrdAllowedPath(entry.getKey())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "GENERATE 含非法文件路径: " + entry.getKey());
                    }
                    if (entry.getValue() == null || !entry.getValue().isTextual()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "GENERATE 文件内容必须是文本");
                    }
                    String text = entry.getValue().textValue();
                    if (text.indexOf('\u0000') >= 0
                            || SECRET_ASSIGNMENT.matcher(text).find()
                            || SECRET_MARKER.matcher(text).find()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "GENERATE 文件不得包含二进制内容或认证信息");
                    }
                }
            }
        }
        if ("DEMO".equals(expectedMode)) {
            if (payload.has("packageFiles")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Demo 结果只允许使用 package_files 字段");
            }
            if (payload.has("package_files")) {
                validatePackageFiles(payload.get("package_files"));
            }
            String html = text(payload, "html");
            if (!HtmlPayload.looksLikeHtmlDocument(html)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Demo 结果需要闭合且完整的 HTML 文档");
            }
            if (!HtmlPayload.hasSubstantiveBodyContent(html)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST",
                        "Demo 结果主体内容为空。请遵循静态 DOM 优先原则，直接在 <body> 内输出业务单据或工作台的静态骨架与表格"
                );
            }
            if (HtmlPayload.hasTruncatedScriptOrEllipsis(html)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST",
                        "Demo 脚本存在截断或未闭合大括号，请确保生成完整可运行的脚本"
                );
            }
            if (HtmlPayload.hasBrokenNestedEventHandlerQuotes(html)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST",
                        "Demo 脚本存在引号嵌套错误，页面无法点击。不要在单引号 innerHTML 字符串里写 onclick=\"fn('参数')\"，改用事件委托、data-* 或模板字符串"
                );
            }
            if (HtmlPayload.hasUndefinedMethodCallHallucination(html)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST",
                        "Demo 脚本存在方法调用幻觉（如 r.html(i)）。原生 JavaScript 数据对象没有 .html() 方法，请正确传递并调用行渲染回调函数（如 rows(arr, s, fn)）"
                );
            }
            if (HtmlPayload.hasInteractiveControlsWithoutClickWiring(html)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST",
                        "Demo 声明了 data-act/data-view 等交互控件，但没有 click 事件监听器或 onclick 处理器，页面无法点击"
                );
            }
            if (HtmlPayload.hasGenerationPlaceholders(html)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST",
                        "Demo 仍包含 __SEED_*__、__TODO_*__ 等生成占位符，请提交完成填充的可运行 HTML"
                );
            }
            if (HtmlPayload.hasStubOrInertScript(html)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST",
                        "Demo 包含未绑定的交互控件或仅有空变量定义的存根脚本，页面无法点击。请补充完整可运行的事件监听与视图切换交互逻辑"
                );
            }
            JsonNode script = payload.get("scriptMarkdown");
            if (script != null && !script.isNull()
                    && (!script.isTextual() || script.textValue().codePointCount(0, script.textValue().length()) > 30_000)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Demo 讲解词必须是最多 30000 字符的文本");
            }
        }
        if ("POC".equals(expectedMode)) {
            if (payload.has("package_files") || payload.has("packageFiles")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 不接收 package_files；请使用沙箱多文件 + poc_files");
            }
            String html = text(payload, "html");
            if (!HtmlPayload.looksLikeHtmlDocument(html)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 结果需要闭合且完整的 HTML 文档");
            }
            if (!HtmlPayload.hasSubstantiveBodyContent(html)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 预览 HTML 主体内容为空");
            }
            if (HtmlPayload.hasGenerationPlaceholders(html)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 仍包含生成占位符");
            }
            if (HtmlPayload.hasTruncatedScriptOrEllipsis(html)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 脚本存在截断或未闭合大括号");
            }
            JsonNode files = payload.get("poc_files");
            if (files == null || !files.isObject() || files.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 结果需要 poc_files 文件映射");
            }
            if (!files.has("index.html") || !files.get("index.html").isTextual()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC poc_files 必须包含 index.html");
            }
            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = files.fields();
            while (fields.hasNext()) {
                java.util.Map.Entry<String, JsonNode> entry = fields.next();
                if (!PocWorkspace.isPocAllowedPath(entry.getKey())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 含非法文件路径: " + entry.getKey());
                }
                if (entry.getValue() == null || !entry.getValue().isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 文件内容必须是文本");
                }
                String text = entry.getValue().textValue();
                if (text.indexOf('\u0000') >= 0
                        || SECRET_ASSIGNMENT.matcher(text).find()
                        || SECRET_MARKER.matcher(text).find()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 文件不得包含二进制内容或认证信息");
                }
                String path = entry.getKey();
                if ((path.endsWith(".js") || path.endsWith(".html") || path.endsWith(".css")) && containsBannedPocApi(text)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 禁止 fetch、HTTP 外链、Vue 和 ES module import: " + path);
                }
            }
            if (containsBannedPocApi(html)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 预览 HTML 禁止 fetch、HTTP 外链、Vue 和 ES module import");
            }
            JsonNode script = payload.get("scriptMarkdown");
            if (script != null && !script.isNull()
                    && (!script.isTextual() || script.textValue().codePointCount(0, script.textValue().length()) > 30_000)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 讲解词必须是最多 30000 字符的文本");
            }
        }
        if ("ESTIMATE".equals(expectedMode)) {
            requireText(payload, "scopeNote");
            if (!payload.has("items") || !payload.get("items").isArray() || payload.get("items").isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "工作量评估结果需要非空 items 数组");
            }
            for (JsonNode rawItem : payload.get("items")) {
                if (!rawItem.isObject()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "items 每一项必须是对象");
                }

                requireText(rawItem, "module");

                // Accept the documented name aliases for compatibility, but never rewrite
                // the Agent conclusion in the control plane.
                String name = text(rawItem, "name");
                if (name == null || name.isBlank()) {
                    name = text(rawItem, "feature");
                    if (name == null || name.isBlank()) {
                        name = text(rawItem, "title");
                    }
                    if (name == null || name.isBlank()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "缺少字段: name");
                    }
                }

                requireText(rawItem, "type");
                String rawType = text(rawItem, "type").trim();
                String upperType = rawType.toUpperCase();
                if ("PAGE".equals(upperType) || upperType.contains("PAGE") || upperType.contains("页面")) {
                } else if ("THIRD_PARTY_API".equals(upperType) || upperType.contains("THIRD") || upperType.contains("第三方")) {
                } else if ("API".equals(upperType) || "INTERNAL_API".equals(upperType) || upperType.contains("API") || upperType.contains("接口")) {
                } else {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "评估项目 type 必须是 PAGE、API 或 THIRD_PARTY_API");
                }

                parseNonNegativeDouble(rawItem, "devDays", "评估项目 devDays 必须是非负数值");

                // testDays may still be supplied as qaDays by older Agent prompts.
                if (rawItem.has("testDays")) {
                    parseNonNegativeDouble(rawItem, "testDays", "评估项目 testDays 必须是非负数值");
                } else if (rawItem.has("qaDays")) {
                    parseNonNegativeDouble(rawItem, "qaDays", "评估项目 testDays 必须是非负数值");
                } else {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "评估项目 testDays 必须是非负数值");
                }

                if (rawItem.has("totalDays")) {
                    parseNonNegativeDouble(rawItem, "totalDays", "评估项目 totalDays 必须是非负数值");
                }
            }

            if (payload.has("totalDays")) {
                parseNonNegativeDouble(payload, "totalDays", "工作量评估结果需要非负 totalDays 数值");
            }
            if (payload.has("devDays")) {
                parseNonNegativeDouble(payload, "devDays", "工作量评估结果 devDays 必须是非负数值");
            }
            if (payload.has("testDays")) {
                parseNonNegativeDouble(payload, "testDays", "工作量评估结果 testDays 必须是非负数值");
            }
        }
        if ("TEST_CASE".equals(expectedMode)) {
            JsonNode files = payload.get("test_case_files");
            if (files != null && !files.isNull()) {
                if (!files.isObject() || files.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TEST_CASE test_case_files 必须是非空文件映射");
                }
                if (!files.has(TestCaseWorkspace.README) || !files.get(TestCaseWorkspace.README).isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TEST_CASE test_case_files 必须包含 README.md");
                }
                if (!files.has(TestCaseWorkspace.CATALOG) || !files.get(TestCaseWorkspace.CATALOG).isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TEST_CASE test_case_files 必须包含 data/catalog.json");
                }
                java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = files.fields();
                while (fields.hasNext()) {
                    java.util.Map.Entry<String, JsonNode> entry = fields.next();
                    if (!TestCaseWorkspace.isAllowedPath(entry.getKey())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TEST_CASE 含非法文件路径: " + entry.getKey());
                    }
                    JsonNode content = entry.getValue();
                    if (content == null || !content.isTextual()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TEST_CASE 文件内容必须是文本");
                    }
                    String text = content.textValue();
                    if (text.indexOf('\u0000') >= 0
                            || SECRET_ASSIGNMENT.matcher(text).find()
                            || SECRET_MARKER.matcher(text).find()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TEST_CASE 文件不得包含二进制内容或认证信息");
                    }
                }
            }
            if (!payload.has("items") || !payload.get("items").isArray() || payload.get("items").isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "测试用例结果需要非空 items 数组");
            }
            for (JsonNode rawItem : payload.get("items")) {
                if (!rawItem.isObject()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "items 每一项必须是对象");
                }

                // Compatibility aliases are read for validation only. The submitted
                // Agent payload is never normalized or enriched by the control plane.
                String module = firstText(rawItem, "module", "firstLevelModule", "parentModule");
                if (module == null || module.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "缺少字段: module");
                }
                String itemTitle = firstText(rawItem, "title", "name", "caseTitle", "caseName");
                if (itemTitle == null || itemTitle.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "缺少字段: title");
                }
                String expected = firstText(rawItem, "expectedResult", "expected", "expected_result", "result");
                if (expected == null || expected.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "缺少字段: expectedResult");
                }

                JsonNode stepsNode = firstNode(rawItem, "steps", "step", "testSteps");
                if (stepsNode != null && !stepsNode.isNull() && !stepsNode.isArray() && !stepsNode.isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "测试步骤必须是文本或数组");
                }
                JsonNode testDataNode = firstNode(rawItem, "testData", "test_data", "data");
                if (testDataNode != null && !testDataNode.isNull() && !testDataNode.isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "测试数据必须是文本");
                }
            }
        }
        if ("TECH_SPEC".equals(expectedMode)) {
            JsonNode files = payload.get("tech_spec_files");
            if (files != null && !files.isNull()) {
                if (!files.isObject() || files.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TECH_SPEC tech_spec_files 必须是非空文件映射");
                }
                if (!files.has(TechSpecWorkspace.README) || !files.get(TechSpecWorkspace.README).isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TECH_SPEC tech_spec_files 必须包含 README.md");
                }
                java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = files.fields();
                while (fields.hasNext()) {
                    java.util.Map.Entry<String, JsonNode> entry = fields.next();
                    if (!TechSpecWorkspace.isAllowedPath(entry.getKey())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TECH_SPEC 含非法文件路径: " + entry.getKey());
                    }
                    JsonNode content = entry.getValue();
                    if (content == null || !content.isTextual()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TECH_SPEC 文件内容必须是文本");
                    }
                    String text = content.textValue();
                    if (text.indexOf('\u0000') >= 0
                            || SECRET_ASSIGNMENT.matcher(text).find()
                            || SECRET_MARKER.matcher(text).find()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "TECH_SPEC 文件不得包含二进制内容或认证信息");
                    }
                }
            }
            requireText(payload, "architectureMarkdown");
            if (!payload.has("databaseTables") || !payload.get("databaseTables").isArray() || payload.get("databaseTables").isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "技术方案结果需要非空 databaseTables 数组");
            }
            for (JsonNode table : payload.get("databaseTables")) {
                if (table == null || !table.isObject()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "databaseTables 每一项必须是对象");
                }
                String tableName = firstText(table, "tableName", "name");
                if (tableName == null || tableName.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "缺少字段: tableName");
                }
                if (!table.has("columns") || !table.get("columns").isArray() || table.get("columns").isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "每张表需要非空 columns");
                }
            }
            if (!payload.has("apiEndpoints") || !payload.get("apiEndpoints").isArray() || payload.get("apiEndpoints").isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "技术方案结果需要非空 apiEndpoints 数组");
            }
            for (JsonNode api : payload.get("apiEndpoints")) {
                if (api == null || !api.isObject()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "apiEndpoints 每一项必须是对象");
                }
                requireText(api, "method");
                requireText(api, "path");
                String apiSummary = firstText(api, "summary", "title");
                if (apiSummary == null || apiSummary.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "缺少字段: summary");
                }
            }
            // Legacy work orders are optional; validate shape only when present.
            if (payload.has("tasks")) {
                if (!payload.get("tasks").isArray()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "tasks 必须是数组");
                }
                for (JsonNode rawTask : payload.get("tasks")) {
                    if (!rawTask.isObject()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "tasks 每一项必须是对象");
                    }
                    String taskName = firstText(rawTask, "name", "title");
                    if (taskName == null || taskName.isBlank()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "缺少字段: name");
                    }
                    if (rawTask.has("devDays")) {
                        parseNonNegativeDouble(rawTask, "devDays", "devDays 必须是非负数字");
                    } else if (rawTask.has("days")) {
                        parseNonNegativeDouble(rawTask, "days", "days 必须是非负数字");
                    }
                    if (rawTask.has("dependencies") && !rawTask.get("dependencies").isArray()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "dependencies 必须是数组");
                    }
                }
            }
        }
    }

    private void validatePackageFiles(JsonNode packageFiles) {
        if (packageFiles == null) {
            return;
        }
        if (packageFiles.isNull()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "package_files 不能为 null；没有文件时请省略该字段");
        }
        if (!packageFiles.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "package_files 必须是文件路径到文本内容的对象");
        }
        java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = packageFiles.fields();
        while (fields.hasNext()) {
            java.util.Map.Entry<String, JsonNode> entry = fields.next();
            String path = entry.getKey();
            if (!isSafePackagePath(path)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "package_files 含非法或敏感文件路径");
            }
            JsonNode content = entry.getValue();
            if (content == null || !content.isTextual()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "package_files 的文件内容必须是文本字符串");
            }
            String text = content.textValue();
            if (text.indexOf('\u0000') >= 0
                    || SECRET_ASSIGNMENT.matcher(text).find()
                    || SECRET_MARKER.matcher(text).find()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "package_files 不得包含二进制内容或认证信息");
            }
        }
    }

    private boolean isSafePackagePath(String path) {
        if (path == null || path.isBlank() || path.length() > 512
                || path.startsWith("/") || path.contains("\\") || path.matches("^[A-Za-z]:.*")) {
            return false;
        }
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                return false;
            }
            String lower = part.toLowerCase(java.util.Locale.ROOT);
            if (lower.equals(".env") || lower.startsWith(".env.")
                    || lower.equals(".npmrc") || lower.startsWith("credentials")
                    || lower.startsWith("secret") || lower.startsWith("id_rsa")
                    || lower.equals("node_modules") || lower.equals("dist") || lower.equals("build")
                    || lower.equals("coverage") || lower.equals(".git") || lower.equals(".ds_store")
                    || lower.equals(".vite") || lower.equals("target") || lower.equals("__pycache__")
                    || lower.endsWith(".pem") || lower.endsWith(".key") || lower.endsWith(".map")) {
                return false;
            }
        }
        return true;
    }

    private double parseNonNegativeDouble(JsonNode node, String field, String errorMsg) {
        if (!node.has(field)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", errorMsg);
        }
        JsonNode val = node.get(field);
        double d;
        if (val.isNumber()) {
            d = val.asDouble();
        } else if (val.isTextual()) {
            try {
                d = Double.parseDouble(val.asText().trim());
            } catch (NumberFormatException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", errorMsg);
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", errorMsg);
        }
        if (!Double.isFinite(d) || d < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", errorMsg);
        }
        return d;
    }

    private static final java.util.regex.Pattern BANNED_POC_EXTERNAL_RESOURCE = java.util.regex.Pattern.compile(
            "(?i)(?:<(?:script|iframe)\\b[^>]*src\\s*=\\s*['\"]https?://|<link\\b[^>]*href\\s*=\\s*['\"]https?://|@import\\s+['\"]?https?://|url\\s*\\(\\s*['\"]?https?://|['\"](?:https?:)?//cdn\\.[^'\"]+['\"])"
    );
    private static final java.util.regex.Pattern BANNED_POC_IMPORT = java.util.regex.Pattern.compile(
            "(?m)^\\s*import\\s"
    );

    static boolean containsBannedPocApi(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("fetch(")
                || lower.contains("xmlhttprequest")
                || lower.contains("axios")
                || lower.contains("from 'vue'")
                || lower.contains("from \"vue\"")
                || lower.contains("createapp(")
                || BANNED_POC_IMPORT.matcher(text).find()
                || BANNED_POC_EXTERNAL_RESOURCE.matcher(text).find();
    }

    public String summary(JsonNode payload) {
        String summary = text(payload, "summary");
        return summary == null ? "" : (summary.length() <= 2000 ? summary : summary.substring(0, 2000));
    }

    private void requireText(JsonNode payload, String field) {
        if (isBlank(payload, field)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "缺少字段: " + field);
        }
    }

    private boolean isBlank(JsonNode payload, String field) {
        String value = text(payload, field);
        return value == null || value.isBlank();
    }

    private void validateReviewItems(JsonNode payload, String field, boolean required) {
        JsonNode items = payload.get(field);
        if (items == null && !required) {
            return;
        }
        if (items == null || !items.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "评审结果的 " + field + " 必须是数组");
        }
        for (JsonNode item : items) {
            JsonNode title = item.isObject() ? item.get("title") : null;
            if (title == null || !title.isTextual() || title.textValue().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "评审条目必须是含非空 title 文本的对象");
            }
        }
    }

    private String text(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private String firstText(JsonNode payload, String... fields) {
        for (String field : fields) {
            String value = text(payload, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode firstNode(JsonNode payload, String... fields) {
        for (String field : fields) {
            JsonNode value = payload.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }
}
