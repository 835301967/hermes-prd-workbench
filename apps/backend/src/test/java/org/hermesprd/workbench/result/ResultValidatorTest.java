package org.hermesprd.workbench.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultValidatorTest {
    private ResultValidator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        WorkbenchProperties properties = new WorkbenchProperties(
                Path.of("/tmp"),
                10_000,
                20_971_520,
                12000,
                Duration.ofSeconds(300),
                Duration.ofSeconds(900),
                Duration.ofSeconds(2400),
                Duration.ofSeconds(2400),
                Duration.ofSeconds(2400),
                Duration.ofSeconds(2400),
                new WorkbenchProperties.Hermes("http://127.0.0.1:8643", Path.of("/tmp/key"), "prd-workbench", "0.16.0", Duration.ofSeconds(3)),
                Path.of("/tmp/plugin")
        );
        validator = new ResultValidator(properties);
    }

    @Test
    void acceptsMinimalReview() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"REVIEW","title":"评审","summary":"需要补异常","blockingIssues":[]}
                """);
        assertDoesNotThrow(() -> validator.validate("REVIEW", payload));
        assertEquals("需要补异常", validator.summary(payload));
    }

    @Test
    void rejectsReviewWithoutIssuesArray() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"REVIEW","title":"评审","summary":"结论"}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("REVIEW", payload));
        assertEquals("INVALID_REQUEST", ex.code());
    }

    @Test
    void rejectsStringReviewEntriesAcrossAllSections() throws Exception {
        for (String field : new String[]{"blockingIssues", "otherSuggestions", "readyItems"}) {
            var payload = mapper.readTree("{\"mode\":\"REVIEW\",\"title\":\"评审\",\"summary\":\"结论\",\"blockingIssues\":[]}");
            ((com.fasterxml.jackson.databind.node.ObjectNode) payload).putArray(field).add("合成条目");
            assertThrows(ApiException.class, () -> validator.validate("REVIEW", payload));
        }
    }

    @Test
    void rejectsReviewEntryWithoutTextTitle() throws Exception {
        for (String item : new String[]{"{}", "{\"title\":23}", "{\"title\":\" \"}"}) {
            var payload = mapper.readTree("{\"mode\":\"REVIEW\",\"title\":\"评审\",\"summary\":\"结论\",\"blockingIssues\":[" + item + "]}");
            assertThrows(ApiException.class, () -> validator.validate("REVIEW", payload));
        }
    }

    @Test
    void acceptsStructuredReviewWithoutRewritingOriginalPayload() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"REVIEW","title":"评审","summary":"结论","blockingIssues":[],
                 "otherSuggestions":[{"title":"合成建议","detail":"保留原文"}],"readyItems":[{"title":"可沿用"}]}
                """);
        var original = payload.deepCopy();
        validator.validate("REVIEW", payload);
        assertEquals(original, payload);
    }

    @Test
    void rejectsGenerateWithoutMarkdown() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"GENERATE","title":"PRD","summary":"说明"}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("GENERATE", payload));
        assertEquals("INVALID_REQUEST", ex.code());
    }

    @Test
    void acceptsLegacyGenerateMarkdownWithoutPrdFiles() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"GENERATE","title":"PRD","summary":"说明","markdown":"# 旧稿"}
                """);
        assertDoesNotThrow(() -> validator.validate("GENERATE", payload));
    }

    @Test
    void acceptsGeneratePackAndRejectsIllegalPath() throws Exception {
        var ok = mapper.readTree("""
                {"mode":"GENERATE","title":"PRD","summary":"说明","markdown":"# 组装","prd_files":{"README.md":"# 顺序","00-overview.md":"# 概述"}}
                """);
        assertDoesNotThrow(() -> validator.validate("GENERATE", ok));

        var bad = mapper.readTree("""
                {"mode":"GENERATE","title":"PRD","summary":"说明","markdown":"# 组装","prd_files":{"README.md":"# 顺序","src/main.ts":"export {}"}}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("GENERATE", bad));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("非法文件路径"));
    }

    @Test
    void acceptsStandaloneDemoHtml() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"DEMO","title":"入驻 Demo","summary":"可本地打开","html":"<!DOCTYPE html><html><head><meta charset=utf-8></head><body><h1>广东安捷伦 ERP 系统</h1><p>企业业务管理工作台单据流转与审批</p></body></html>"}
                """);
        assertDoesNotThrow(() -> validator.validate("DEMO", payload));
    }

    @Test
    void acceptsOptionalDemoScriptAndRejectsInvalidShapeOrLength() throws Exception {
        var withScript = mapper.readTree("""
                {"mode":"DEMO","title":"入驻 Demo","summary":"可讲解","html":"<!DOCTYPE html><html><body><h1>广东安捷伦 ERP 系统</h1><p>企业业务管理工作台单据流转与审批</p></body></html>","scriptMarkdown":"# 开场\\n演示主流程"}
                """);
        assertDoesNotThrow(() -> validator.validate("DEMO", withScript));

        var invalidType = mapper.readTree("""
                {"mode":"DEMO","title":"入驻 Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>广东安捷伦 ERP 系统</h1><p>企业业务管理工作台单据流转与审批</p></body></html>","scriptMarkdown":[]}
                """);
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                () -> validator.validate("DEMO", invalidType)).code());

        var tooLong = mapper.createObjectNode()
                .put("mode", "DEMO")
                .put("title", "入驻 Demo")
                .put("summary", "说明")
                .put("html", "<!DOCTYPE html><html><body><h1>广东安捷伦 ERP 系统</h1><p>企业业务管理工作台单据流转与审批</p></body></html>")
                .put("scriptMarkdown", "讲".repeat(30_001));
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                () -> validator.validate("DEMO", tooLong)).code());
    }

    @Test
    void acceptsSafePackageFilesAlongsideStandaloneHtml() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode":"DEMO",
                  "title":"库存 Demo",
                  "summary":"同时交付预览与工程文件",
                  "html":"<!DOCTYPE html><html><body><h1>库存管理业务</h1><p>企业库存业务台账可离线预览</p></body></html>",
                  "skill":"generic-admin-html",
                  "package_files": {
                    "application.json":"{\\"pages\\":{}}",
                    "src/ui/pc/pages/inventory/index.ts":"export default {}",
                    "src/ui/pc/pages/inventory/views/list/index.vue":"<template><div>库存</div></template>"
                  }
                }
                """);
        assertDoesNotThrow(() -> validator.validate("DEMO", payload));
    }

    @Test
    void rejectsInvalidPackageFileShapePathAndContent() throws Exception {
        var nonObject = mapper.readTree("""
                {"mode":"DEMO","title":"Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>","package_files":[]}
                """);
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                () -> validator.validate("DEMO", nonObject)).code());

        var traversal = mapper.readTree("""
                {"mode":"DEMO","title":"Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>","package_files":{"../application.json":"{}"}}
                """);
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                () -> validator.validate("DEMO", traversal)).code());

        var envFile = mapper.readTree("""
                {"mode":"DEMO","title":"Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>","package_files":{".env.example":"PLACEHOLDER_ONLY"}}
                """);
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                () -> validator.validate("DEMO", envFile)).code());

        var nonText = mapper.readTree("""
                {"mode":"DEMO","title":"Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>","package_files":{"src/data.json":[]}}
                """);
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                () -> validator.validate("DEMO", nonText)).code());

        var credential = mapper.readTree("""
                {"mode":"DEMO","title":"Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>","package_files":{"src/config.ts":"const API_KEY = 'not-a-placeholder-secret';"}}
                """);
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                () -> validator.validate("DEMO", credential)).code());
    }

    @Test
    void rejectsCamelCasePackageFileAliasAndExpandedCredentialPatterns() throws Exception {
        var alias = mapper.readTree("""
                {"mode":"DEMO","title":"Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>","packageFiles":{"application.json":"{}"}}
                """);
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                () -> validator.validate("DEMO", alias)).code());

        for (String secret : java.util.List.of(
                "const TOKEN = '1234567890';",
                "const PASSWORD = '1234567890';",
                "const Authorization = 'Bearer 1234567890';",
                "-----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY-----"
        )) {
            var payload = mapper.createObjectNode()
                    .put("mode", "DEMO")
                    .put("title", "Demo")
                    .put("summary", "说明")
                    .put("html", "<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>");
            payload.putObject("package_files").put("src/config.ts", secret);
            assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                    () -> validator.validate("DEMO", payload)).code(), secret);
        }
    }

    @Test
    void rejectsStructuredCredentialValuesAndRuntimePackageDirectories() throws Exception {
        for (String secret : java.util.List.of(
                "{\"apiKey\":\"1234567890\"}",
                "password: 1234567890",
                "authorization: Bearer 1234567890"
        )) {
            var payload = mapper.createObjectNode()
                    .put("mode", "DEMO")
                    .put("title", "Demo")
                    .put("summary", "说明")
                    .put("html", "<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>");
            payload.putObject("package_files").put("src/config.ts", secret);
            assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                    () -> validator.validate("DEMO", payload)).code(), secret);
        }

        for (String path : java.util.List.of(
                "node_modules/pkg/index.js",
                "dist/index.js",
                ".git/config",
                ".DS_Store"
        )) {
            var payload = mapper.createObjectNode()
                    .put("mode", "DEMO")
                    .put("title", "Demo")
                    .put("summary", "说明")
                    .put("html", "<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>");
            payload.putObject("package_files").put(path, "safe text");
            assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                    () -> validator.validate("DEMO", payload)).code(), path);
        }
    }

    @Test
    void rejectsExplicitNullPackageFiles() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"DEMO","title":"Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>业务台账</h1></body></html>","package_files":null}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("DEMO", payload));
        assertEquals("INVALID_REQUEST", ex.code());
    }

    @Test
    void rejectsDemoWithStubOrInertScript() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode":"DEMO",
                  "title":"用车 Demo",
                  "summary":"测试",
                  "html":"<!DOCTYPE html><html><body><h1>广东安捷伦车辆管理系统业务工作台</h1><button class='menu'>用车申请管理</button><script>var CARS=[['A1']];</script></body></html>"
                }
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("DEMO", payload));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("存根脚本"));
    }

    @Test
    void enforcesTwentyMiBOnTheWholeDemoPayload() throws Exception {
        var payload = mapper.createObjectNode()
                .put("mode", "DEMO")
                .put("title", "大文件 Demo")
                .put("summary", "验证完整 payload 上限")
                .put("html", "<!DOCTYPE html><html><body><h1>业务台账与库存工作台</h1><p>用于验证完整结果大小边界的可运行单页</p></body></html>");
        var files = payload.putObject("package_files");
        files.put("src/generated.txt", "");
        int emptyPayloadBytes = payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int fillerBytes = 20_971_520 - emptyPayloadBytes;
        files.put("src/generated.txt", "x".repeat(fillerBytes));
        assertEquals(20_971_520, payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertDoesNotThrow(() -> validator.validate("DEMO", payload));

        files.put("src/generated.txt", "x".repeat(fillerBytes + 1));
        assertEquals("RESULT_TOO_LARGE", assertThrows(ApiException.class,
                () -> validator.validate("DEMO", payload)).code());
    }

    @Test
    void rejectsDemoWithoutHtmlDocument() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"DEMO","title":"入驻 Demo","summary":"说明","html":"not html"}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("DEMO", payload));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("完整的 HTML"));
    }

    @Test
    void rejectsDemoWithEmptyBodySkeleton() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"DEMO","title":"入驻 Demo","summary":"说明","html":"<!DOCTYPE html><html><body><aside id=sidebar></aside><main id=main></main></body></html>"}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("DEMO", payload));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("主体内容为空"));
    }

    @Test
    void rejectsDemoWithTruncatedScript() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"DEMO","title":"入驻 Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>广东安捷伦 ERP 系统</h1><p>企业业务管理工作台单据流转与审批</p><script>var x = 1 + ...;</script></body></html>"}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("DEMO", payload));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("脚本存在截断"));
    }

    @Test
    void rejectsDemoHtmlWithBrokenNestedEventHandlerQuotes() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"DEMO","title":"入驻 Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>广东安捷伦 ERP 系统</h1><p>企业业务管理工作台单据流转与审批</p><main id=main></main><script>$('main').innerHTML='<button onclick=\\"goMod('alert')\\">预警</button>';</script></body></html>"}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("DEMO", payload));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("引号嵌套错误"));
    }

    @Test
    void rejectsDemoHtmlWithUndefinedMethodCallHallucination() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"DEMO","title":"入驻 Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>广东安捷伦 ERP 系统</h1><p>企业业务管理工作台单据流转与审批</p><script>for(var i=0;i<arr.length;i++){h+=r.html(i);}</script></body></html>"}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("DEMO", payload));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("方法调用幻觉"));
    }

    @Test
    void rejectsDemoWithInertDataActions() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"DEMO","title":"订单 Demo","summary":"说明","html":"<!DOCTYPE html><html><body><h1>订单管理工作台</h1><button data-act='create'>新增订单</button><table><tbody><tr><td>SO-001</td></tr></tbody></table><script>var ORDERS=[];</script></body></html>"}
                """);

        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("DEMO", payload));

        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("没有 click 事件监听器"));
    }

    @Test
    void rejectsDemoWithUnresolvedGenerationPlaceholder() {
        var payload = mapper.createObjectNode()
                .put("mode", "DEMO")
                .put("title", "订单 Demo")
                .put("summary", "说明")
                .put("html", "<!DOCTYPE html><html><body><h1>订单管理工作台</h1><p>用于销售订单全生命周期流转与审批演示</p><table><tbody><tr><td>SO-001</td></tr></tbody></table><script>var ORDERS=[[\"__SEED_ORDER_ROWS__\"]];</script></body></html>");

        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("DEMO", payload));

        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("生成占位符"), ex.getMessage());
    }

    @Test
    void acceptsValidEstimate() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "ESTIMATE",
                  "title": "律所系统工作量评估",
                  "summary": "预估总计 15.2 人天",
                  "scopeNote": "本次报价仅包含开发和测试的工作量，不含产品设计、运维、项目管理的工作量。",
                  "totalDays": 15.2,
                  "devDays": 11.5,
                  "testDays": 3.7,
                  "items": [
                    {
                      "module": "案件管理",
                      "subModule": "立案审核",
                      "type": "PAGE",
                      "name": "案件详情页",
                      "detail": "包含案件基础信息与审批历史",
                      "complexity": "MEDIUM",
                      "devDays": 0.5,
                      "testDays": 0.2,
                      "totalDays": 0.7,
                      "description": "标准页面0.5天+自测0.2天"
                    },
                    {
                      "module": "案件管理",
                      "subModule": "立案审核",
                      "type": "API",
                      "name": "POST /api/cases",
                      "detail": "案件创建接口",
                      "complexity": "MEDIUM",
                      "devDays": 0.5,
                      "testDays": 0.1,
                      "totalDays": 0.6,
                      "description": "标准接口0.5天"
                    }
                  ]
                }
                """);
        assertDoesNotThrow(() -> validator.validate("ESTIMATE", payload));
        assertEquals("预估总计 15.2 人天", validator.summary(payload));
    }

    @Test
    void rejectsEstimateWithoutScopeNote() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "ESTIMATE",
                  "title": "评估",
                  "summary": "总计 10 天",
                  "totalDays": 10.0,
                  "items": [{"module":"A","type":"PAGE","name":"P","devDays":1,"testDays":0.2,"totalDays":1.2}]
                }
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("ESTIMATE", payload));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("缺少字段: scopeNote"));
    }

    @Test
    void rejectsEstimateWithEmptyItems() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "ESTIMATE",
                  "title": "评估",
                  "summary": "总计 0 天",
                  "scopeNote": "本次报价仅包含开发和测试的工作量...",
                  "totalDays": 0.0,
                  "items": []
                }
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("ESTIMATE", payload));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("非空 items 数组"));
    }

    @Test
    void rejectsEstimateWithInvalidTypeOrNegativeDays() throws Exception {
        var invalidType = mapper.readTree("""
                {
                  "mode": "ESTIMATE",
                  "title": "评估",
                  "summary": "总计 1 天",
                  "scopeNote": "本次报价仅包含开发和测试的工作量...",
                  "totalDays": 1.0,
                  "items": [{"module":"A","type":"UNKNOWN","name":"P","devDays":1,"testDays":0.2,"totalDays":1.2}]
                }
                """);
        ApiException ex1 = assertThrows(ApiException.class, () -> validator.validate("ESTIMATE", invalidType));
        assertEquals("INVALID_REQUEST", ex1.code());
        assertTrue(ex1.getMessage().contains("type 必须是 PAGE、API 或 THIRD_PARTY_API"));

        var negativeDays = mapper.readTree("""
                {
                  "mode": "ESTIMATE",
                  "title": "评估",
                  "summary": "总计 1 天",
                  "scopeNote": "本次报价仅包含开发和测试的工作量...",
                  "totalDays": 1.0,
                  "items": [{"module":"A","type":"PAGE","name":"P","devDays":-1,"testDays":0.2,"totalDays":1.2}]
                }
                """);
        ApiException ex2 = assertThrows(ApiException.class, () -> validator.validate("ESTIMATE", negativeDays));
        assertEquals("INVALID_REQUEST", ex2.code());
        assertTrue(ex2.getMessage().contains("devDays 必须是非负数值"));
    }

    @Test
    void acceptsEstimateAliasesWithoutMutatingPayload() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "ESTIMATE",
                  "title": "系统工作量评估",
                  "summary": "评估完成",
                  "scopeNote": "本次报价仅包含开发和测试的工作量，不含产品设计、运维、项目管理的工作量。",
                  "items": [
                    {
                      "module": "案件管理",
                      "type": "INTERNAL_API",
                      "feature": "案件状态流转接口组",
                      "complexity": "HIGH",
                      "devDays": "1.5",
                      "qaDays": "0.3",
                      "rationale": "包含复杂多状态审批流与驳回"
                    },
                    {
                      "module": "客户管理",
                      "type": "页面",
                      "name": "公海池台账",
                      "complexity": "SIMPLE",
                      "devDays": 0.5,
                      "testDays": 0.1,
                      "detail": "标准数据列表"
                    }
                  ]
                }
                """);
        String before = payload.toString();
        assertDoesNotThrow(() -> validator.validate("ESTIMATE", payload));
        assertEquals(before, payload.toString(), "control plane must preserve the Agent conclusion verbatim");

        var item0 = payload.get("items").get(0);
        assertEquals("INTERNAL_API", item0.get("type").asText());
        assertEquals("案件状态流转接口组", item0.get("feature").asText());
        assertEquals(1.5, item0.get("devDays").asDouble());
        assertEquals(0.3, item0.get("qaDays").asDouble());
        assertTrue(item0.get("totalDays") == null);
        assertEquals("HIGH", item0.get("complexity").asText());
        assertEquals("包含复杂多状态审批流与驳回", item0.get("rationale").asText());

        var item1 = payload.get("items").get(1);
        assertEquals("页面", item1.get("type").asText());
        assertEquals("SIMPLE", item1.get("complexity").asText());
        assertEquals(0.5, item1.get("devDays").asDouble());
        assertEquals(0.1, item1.get("testDays").asDouble());
    }

    @Test
    void acceptsValidTestCasePayload() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "TEST_CASE",
                  "title": "测试用例集",
                  "summary": "覆盖核心业务",
                  "scopeNote": "本次测试用例集覆盖系统核心业务功能流、边界值校验、逆向异常流与权限安全测试。",
                  "items": [
                    {
                      "caseId": "TC-001",
                      "module": "用户管理",
                      "title": "正确密码登录成功",
                      "type": "FUNCTION",
                      "priority": "P0",
                      "expectedResult": "成功进入主界面"
                    }
                  ]
                }
                """);
        String before = payload.toString();
        assertDoesNotThrow(() -> validator.validate("TEST_CASE", payload));
        assertEquals(before, payload.toString(), "control plane must preserve the Agent conclusion verbatim");
    }

    @Test
    void acceptsTestCaseAliasesWithoutMutatingPayload() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "TEST_CASE",
                  "title": "自动化测试用例集",
                  "summary": "测试用例集汇总说明",
                  "items": [
                    {
                      "case_id": "TC-CUSTOM-01",
                      "firstLevelModule": "案源渠道",
                      "secondLevelModule": "渠道配置",
                      "caseTitle": "新增渠道成功",
                      "caseType": "功能测试",
                      "level": "冒烟阻断",
                      "step": ["1. 打开配置页", "2. 点击新增", "3. 提交表单"],
                      "test_data": "渠道名=抖音",
                      "expected": "提示保存成功并生效"
                    },
                    {
                      "parentModule": "合同管理",
                      "name": "合同金额输入超大值校验",
                      "type": "边界值测试",
                      "priority": "P1",
                      "steps": "输入 999999999999999 点击保存",
                      "expected_result": "输入框提示超出最大限制"
                    },
                    {
                      "module": "系统安全",
                      "title": "普通员工越权访问财务设置",
                      "type": "权限越权",
                      "priority": "P2",
                      "steps": "直接访问 /finance/settings 路由",
                      "result": "拦截并重定向到 403 页面"
                    }
                  ]
                }
                """);
        String before = payload.toString();
        assertDoesNotThrow(() -> validator.validate("TEST_CASE", payload));
        assertEquals(before, payload.toString(), "aliases may be read for validation but must not be written back");
        assertTrue(payload.get("items").get(0).has("case_id"));
        assertTrue(payload.get("items").get(0).has("firstLevelModule"));
        assertTrue(payload.get("items").get(0).has("caseTitle"));
        assertTrue(payload.get("items").get(0).get("caseId") == null);
        assertTrue(payload.get("totalCount") == null);
    }

    @Test
    void acceptsTestCaseFilesPayloadWithoutMutating() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "TEST_CASE",
                  "title": "用例包",
                  "summary": "多文件",
                  "workspaceFile": "README.md",
                  "files": ["README.md", "data/catalog.json", "modules/gl.json"],
                  "test_case_files": {
                    "README.md": "# 用例",
                    "data/catalog.json": "{\\"modules\\":[{\\"key\\":\\"gl\\",\\"title\\":\\"总账\\"}]}",
                    "modules/gl.json": "[{\\"title\\":\\"登录成功\\",\\"expectedResult\\":\\"进入首页\\",\\"module\\":\\"总账\\"}]"
                  },
                  "items": [
                    {"module": "总账", "title": "登录成功", "expectedResult": "进入首页"}
                  ]
                }
                """);
        String before = payload.toString();
        assertDoesNotThrow(() -> validator.validate("TEST_CASE", payload));
        assertEquals(before, payload.toString());
    }

    @Test
    void acceptsTechSpecFilesPayloadWithoutMutating() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "TECH_SPEC",
                  "title": "方案包",
                  "summary": "多文件",
                  "architectureMarkdown": "## 架构",
                  "workspaceFile": "README.md",
                  "tech_spec_files": {
                    "README.md": "# 方案",
                    "architecture.md": "## 架构",
                    "data/tables.json": "[{\\"tableName\\":\\"t1\\",\\"columns\\":[{\\"name\\":\\"id\\",\\"type\\":\\"BIGINT\\"}]}]",
                    "data/apis.json": "[{\\"method\\":\\"GET\\",\\"path\\":\\"/a\\",\\"summary\\":\\"查\\"}]"
                  },
                  "databaseTables": [{"tableName": "t1", "columns": [{"name": "id", "type": "BIGINT"}]}],
                  "apiEndpoints": [{"method": "GET", "path": "/a", "summary": "查"}]
                }
                """);
        String before = payload.toString();
        assertDoesNotThrow(() -> validator.validate("TECH_SPEC", payload));
        assertEquals(before, payload.toString());
    }

    @Test
    void rejectsTestCaseWithoutItems() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"TEST_CASE","title":"用例集","summary":"说明"}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("TEST_CASE", payload));
        assertEquals("INVALID_REQUEST", ex.code());
    }

    @Test
    void acceptsTechSpecWithoutMutatingPayload() throws Exception {
        var payload = mapper.readTree("""
                {
                    "mode": "TECH_SPEC",
                    "title": "OA系统研发技术方案与任务拆解清单",
                    "summary": "基于 PRD 的系统分层架构设计与任务拆解",
                    "architectureMarkdown": "## 1. 系统分层架构\\n采用 Spring Boot + Vue 3 模块化单体架构。",
                    "tasks": [
                        {
                            "name": "审批流状态机与乐观锁实现",
                            "type": "后端接口",
                            "priority": "P0",
                            "devDays": 2.5,
                            "dependencies": ["DEV-DB-001"],
                            "description": "基于状态机流转与版本号并发控制"
                        },
                        {
                            "name": "动态审批流转时间轴组件",
                            "type": "前端页面",
                            "priority": "高优先",
                            "devDays": 2.0,
                            "dependencies": ["DEV-BE-001"],
                            "description": "Vue 3 响应式时间轴组件"
                        },
                        {
                            "name": "审批表模型创建与索引优化",
                            "type": "数据库",
                            "priority": "P2",
                            "devDays": 1.0,
                            "description": "DDL 脚本与联合索引"
                        }
                    ],
                    "databaseTables": [
                        {
                            "tableName": "oa_approval_instance",
                            "tableComment": "审批实例表",
                            "columns": [
                                {"name": "id", "type": "VARCHAR(64)", "nullable": false, "comment": "主键ID"},
                                {"name": "status", "type": "VARCHAR(32)", "nullable": false, "comment": "流转状态"}
                            ],
                            "indexes": ["PRIMARY KEY (id)"]
                        }
                    ],
                    "apiEndpoints": [
                        {
                            "method": "POST",
                            "path": "/api/v1/flow/approve",
                            "summary": "审批通过流转接口",
                            "requestBody": "{\\"flowId\\": \\"1\\"}",
                            "responseBody": "{\\"code\\": 200}",
                            "errorCodes": ["400: 参数缺失"]
                        }
                    ]
                }
                """);

        String before = payload.toString();
        assertDoesNotThrow(() -> validator.validate("TECH_SPEC", payload));
        assertEquals(before, payload.toString(), "control plane must preserve the Agent conclusion verbatim");
        assertTrue(payload.get("tasks").get(0).get("taskId") == null);
        assertTrue(payload.get("totalTasks") == null);
        assertTrue(payload.get("totalDevDays") == null);
    }

    @Test
    void rejectsTechSpecWithoutTablesOrApis() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"TECH_SPEC","title":"方案","summary":"说明","architectureMarkdown":"## 架构"}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("TECH_SPEC", payload));
        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("databaseTables") || ex.getMessage().contains("apiEndpoints"));
    }

    @Test
    void rejectsEstimateWithNaNOrInfinity() throws Exception {
        var nanPayload = mapper.readTree("""
                {"mode":"ESTIMATE","title":"报价","summary":"说明","scopeNote":"范围",
                 "items":[{"module":"M1","name":"N1","type":"PAGE","devDays":"NaN","testDays":1.0}]}
                """);
        ApiException ex1 = assertThrows(ApiException.class, () -> validator.validate("ESTIMATE", nanPayload));
        assertEquals("INVALID_REQUEST", ex1.code());

        var infPayload = mapper.readTree("""
                {"mode":"ESTIMATE","title":"报价","summary":"说明","scopeNote":"范围",
                 "items":[{"module":"M1","name":"N1","type":"PAGE","devDays":"Infinity","testDays":1.0}]}
                """);
        ApiException ex2 = assertThrows(ApiException.class, () -> validator.validate("ESTIMATE", infPayload));
        assertEquals("INVALID_REQUEST", ex2.code());
    }

    @Test
    void rejectsEstimateWithNegativeDevOrTestDays() throws Exception {
        var negativeDevPayload = mapper.readTree("""
                {"mode":"ESTIMATE","title":"报价","summary":"说明","scopeNote":"范围","devDays":-1.0,
                 "items":[{"module":"M1","name":"N1","type":"PAGE","devDays":2.0,"testDays":1.0}]}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("ESTIMATE", negativeDevPayload));
        assertEquals("INVALID_REQUEST", ex.code());
    }

    @Test
    void doesNotRewriteInconsistentEstimateTotals() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"ESTIMATE","title":"报价","summary":"说明","scopeNote":"范围","totalDays":999.0,
                 "items":[{"module":"M1","name":"N1","type":"PAGE","devDays":2.0,"testDays":1.0}]}
                """);
        assertDoesNotThrow(() -> validator.validate("ESTIMATE", payload));
        assertEquals(999.0, payload.get("totalDays").asDouble(), "control plane must not rewrite Agent totals");
    }

    @Test
    void preservesLongEstimateTexts() throws Exception {
        String longText = "x".repeat(5000);
        var estimatePayload = mapper.readTree(String.format("""
                {"mode":"ESTIMATE","title":"报价","summary":"说明","scopeNote":"%s",
                 "items":[{"module":"M1","name":"N1","type":"PAGE","devDays":2.0,"testDays":1.0,"detail":"%s"}]}
                """, longText, longText));
        assertDoesNotThrow(() -> validator.validate("ESTIMATE", estimatePayload));
        assertEquals(5000, estimatePayload.get("scopeNote").asText().length());
        assertEquals(5000, estimatePayload.get("items").get(0).get("detail").asText().length());
    }

    @Test
    void preservesEstimateRoundingAndSummaryAsSubmitted() throws Exception {
        // The control plane validates numeric shapes but does not recalculate Agent conclusions.
        var payload = mapper.readTree("""
                {
                  "mode": "ESTIMATE",
                  "title": "顺灏数智化项目V2.0工作量评估与报价表",
                  "summary": "总工作量105.5人天，其中开发86.5人天、研发自测19.0人天。一期覆盖业务模块...",
                  "scopeNote": "本次报价仅包含开发和测试的工作量，不含产品设计、运维、项目管理的工作量。",
                  "totalDays": 44.8,
                  "devDays": 35.0,
                  "testDays": 9.5,
                  "items": [
                    {"module":"销售市场","type":"API","name":"客户管理接口组","devDays":0.5,"testDays":0.15,"totalDays":0.7},
                    {"module":"销售市场","type":"API","name":"销售订单管理接口组","devDays":1.0,"testDays":0.25,"totalDays":1.3},
                    {"module":"质量管理","type":"PAGE","name":"原材料检验页面","devDays":0.5,"testDays":0.15,"totalDays":0.7}
                  ]
                }
                """);
        String before = payload.toString();
        assertDoesNotThrow(() -> validator.validate("ESTIMATE", payload));
        assertEquals(before, payload.toString());

        assertEquals(0.5, payload.get("items").get(0).get("devDays").asDouble());
        assertEquals(0.15, payload.get("items").get(0).get("testDays").asDouble());
        assertEquals(0.7, payload.get("items").get(0).get("totalDays").asDouble());

        assertEquals(1.0, payload.get("items").get(1).get("devDays").asDouble());
        assertEquals(0.25, payload.get("items").get(1).get("testDays").asDouble());
        assertEquals(1.3, payload.get("items").get(1).get("totalDays").asDouble());

        assertEquals(35.0, payload.get("devDays").asDouble());
        assertEquals(9.5, payload.get("testDays").asDouble());
        assertEquals(44.8, payload.get("totalDays").asDouble());
        assertEquals("总工作量105.5人天，其中开发86.5人天、研发自测19.0人天。一期覆盖业务模块...", payload.get("summary").asText());
    }

    @Test
    void acceptsInlinedPocFilesAndRejectsVueFetchAndPackageFiles() throws Exception {
        var files = mapper.createObjectNode()
                .put("index.html", "<!DOCTYPE html><html><head><link rel=\"stylesheet\" href=\"css/app.css\"></head><body><div class=\"app\">示例CRM工作台</div><script src=\"js/app.js\"></script></body></html>")
                .put("css/app.css", ".app{color:#0E67DA}")
                .put("js/app.js", "window.ready=true;");
        var payload = mapper.createObjectNode();
        payload.put("mode", "POC");
        payload.put("title", "示例 CRM POC");
        payload.put("summary", "核心客户档案可点");
        payload.put("html", "<!DOCTYPE html><html><head><style>.app{color:#0E67DA}</style></head><body><div class=\"app\">示例精细化工CRM客户档案管理工作台主路径演示</div><script>function esc(v){return String(v==null?'':v).replace(/&/g,'&amp;').replace(/\"/g,'&quot;');}</script></body></html>");
        payload.set("poc_files", files);
        assertDoesNotThrow(() -> validator.validate("POC", payload));

        var withPackage = payload.deepCopy();
        withPackage.set("package_files", mapper.createObjectNode().put("src/main.ts", "export {}"));
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class, () -> validator.validate("POC", withPackage)).code());

        var banned = files.deepCopy();
        banned.put("js/app.js", "fetch('/api/customers')");
        var bannedPayload = payload.deepCopy();
        bannedPayload.set("poc_files", banned);
        bannedPayload.put("html", "<!DOCTYPE html><html><body><div class=\"app\">示例CRM工作台</div><script>fetch('/api/customers')</script></body></html>");
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class, () -> validator.validate("POC", bannedPayload)).code());

        // Legitimate textual website URLs in HTML or JS do not get falsely banned
        var withTextUrl = files.deepCopy();
        withTextUrl.put("js/app.js", "window.company = { website: 'https://hengan.example.com' };");
        var withTextUrlPayload = payload.deepCopy();
        withTextUrlPayload.set("poc_files", withTextUrl);
        withTextUrlPayload.put("html", "<!DOCTYPE html><html><head><style>.app{color:#0E67DA}</style></head><body><div class=\"app\">示例官网：https://hengan.example.com 客户档案演示</div><script>window.company = { website: 'https://hengan.example.com' };</script></body></html>");
        assertDoesNotThrow(() -> validator.validate("POC", withTextUrlPayload));

        // External remote script loading is banned
        var withExternalScript = files.deepCopy();
        withExternalScript.put("index.html", "<!DOCTYPE html><html><head></head><body><script src=\"https://cdn.example.com/vue.js\"></script></body></html>");
        var withExternalScriptPayload = payload.deepCopy();
        withExternalScriptPayload.set("poc_files", withExternalScript);
        withExternalScriptPayload.put("html", "<!DOCTYPE html><html><head></head><body><div class=\"app\">POC</div><script src=\"https://cdn.example.com/vue.js\"></script></body></html>");
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class, () -> validator.validate("POC", withExternalScriptPayload)).code());
    }

    @Test
    void demoStillAcceptsSingleFileHtmlWithoutPocFiles() throws Exception {
        var payload = mapper.readTree("""
                {"mode":"DEMO","title":"入驻 Demo","summary":"可本地打开","html":"<!DOCTYPE html><html><head><meta charset=utf-8></head><body><h1>广东安捷伦 ERP 系统</h1><p>企业业务管理工作台单据流转与审批</p></body></html>"}
                """);
        assertDoesNotThrow(() -> validator.validate("DEMO", payload));
    }
}
