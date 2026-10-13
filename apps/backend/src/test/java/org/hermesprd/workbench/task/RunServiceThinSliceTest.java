package org.hermesprd.workbench.task;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.hermes.HermesRunsClient;
import org.hermesprd.workbench.result.ResultMapper;
import org.hermesprd.workbench.result.ResultValidator;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.hermesprd.workbench.result.ResultEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunServiceThinSliceTest {
    static {
        // LambdaUpdateWrapper.getTargetSql() 需要表元信息缓存，纯单元测试里手动初始化。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                RunEntity.class
        );
    }

    @Test
    void genericRunUpdatesCannotOverwriteFeedbackRevision() throws Exception {
        TableField feedback = RunEntity.class.getDeclaredField("reviewFeedback").getAnnotation(TableField.class);
        TableField revision = RunEntity.class.getDeclaredField("reviewFeedbackRevision").getAnnotation(TableField.class);

        assertEquals(FieldStrategy.NEVER, feedback.updateStrategy());
        assertEquals(FieldStrategy.NEVER, revision.updateStrategy());
    }

    @Test
    void demoPromptKeepsScriptMarkdownOptionalAndDefinesUsefulNarrative() {
        Fixture fixture = fixture();
        RunEntity run = new RunEntity();
        run.setId("run_demo");
        run.setTaskId("task_1");
        run.setMode("DEMO");
        run.setInstruction("[skill:frontend-design]\n客户名：甲公司");
        run.setCompleteness("STANDARD");

        String prompt = fixture.service.buildInput(run);

        assertTrue(prompt.contains("可选增加 scriptMarkdown"));
        assertTrue(prompt.contains("最多 30000 字符"));
        assertTrue(prompt.contains("缺少该字段不影响提交"));
        assertTrue(prompt.contains("payload 必须包含 mode、title、summary、workspaceFile=\"index.html\""));
        assertTrue(prompt.contains("不设置固定菜单、视图或字符数量"));
        assertTrue(prompt.contains("不设置固定菜单数或视图数"));
        assertTrue(prompt.contains("最多进行两次定向修复"));
        assertTrue(prompt.contains("不构成所有业务按钮、状态流转或浏览器兼容性的完整行为证明"));
        assertTrue(prompt.contains("控制面会在提交阶段重新读取并校验 workspaceFile 指向的最新文件"));
        assertTrue(prompt.contains("submit_result 的工具参数必须闭合可解析"));
        assertTrue(prompt.contains("不要把全部模块"));
        assertTrue(prompt.contains("onclick=\"fn('参数')\""));
        assertTrue(prompt.contains("一次写完、防截断"));
        assertTrue(prompt.contains("若工具返回 truncated"));
        assertTrue(prompt.contains("先给目标 .view 加上可见 class"));
        assertTrue(prompt.contains("不要 innerHTML 覆盖仍包含 id 的子节点"));
    }

    @Test
    void demoRunBuildInputInjectsDomainSpecificEnterpriseSkillPrompts() {
        Fixture fixture = fixture();

        RunEntity defaultRun = new RunEntity();
        defaultRun.setId("run_default");
        defaultRun.setTaskId("task_1");
        defaultRun.setMode("DEMO");
        defaultRun.setInstruction("库存模板管理");
        defaultRun.setCompleteness("STANDARD");
        String defaultPrompt = fixture.service.buildInput(defaultRun);
        assertTrue(defaultPrompt.contains("skill=generic-admin-html"));
        assertTrue(defaultPrompt.contains("package_files 可省略"));
        assertTrue(defaultPrompt.contains("不承诺工程可编译"));
        assertTrue(defaultPrompt.contains("index.html"));
        assertFalse(defaultPrompt.contains("pages.entry"));

        // 1. ERP Enterprise
        RunEntity erpRun = new RunEntity();
        erpRun.setId("run_erp");
        erpRun.setTaskId("task_1");
        erpRun.setMode("DEMO");
        erpRun.setInstruction("[skill:erp-enterprise]\n采购入库单审核");
        erpRun.setCompleteness("STANDARD");
        String erpPrompt = fixture.service.buildInput(erpRun);
        assertTrue(erpPrompt.contains("skill=erp-enterprise"));
        assertTrue(erpPrompt.contains("标准主子表结构"));
        assertTrue(erpPrompt.contains("高密度物料/条目网格"));
        assertTrue(erpPrompt.contains("单据状态生命周期条"));
        assertTrue(erpPrompt.contains("tabular-nums"));

        // 2. CRM Pipeline
        RunEntity crmRun = new RunEntity();
        crmRun.setId("run_crm");
        crmRun.setTaskId("task_1");
        crmRun.setMode("DEMO");
        crmRun.setInstruction("[skill:crm-pipeline]\nKA客户商机跟进");
        crmRun.setCompleteness("STANDARD");
        String crmPrompt = fixture.service.buildInput(crmRun);
        assertTrue(crmPrompt.contains("skill=crm-pipeline"));
        assertTrue(crmPrompt.contains("销售商机管道看板"));
        assertTrue(crmPrompt.contains("客户 360° 全景视图"));
        assertTrue(crmPrompt.contains("跟进动态流"));

        // 3. Finance Ledger
        RunEntity finRun = new RunEntity();
        finRun.setId("run_fin");
        finRun.setTaskId("task_1");
        finRun.setMode("DEMO");
        finRun.setInstruction("[skill:finance-ledger]\n应付核销与记账凭证");
        finRun.setCompleteness("STANDARD");
        String finPrompt = fixture.service.buildInput(finRun);
        assertTrue(finPrompt.contains("skill=finance-ledger"));
        assertTrue(finPrompt.contains("标准会计记账凭证"));
        assertTrue(finPrompt.contains("借贷平衡实时校验器"));
        assertTrue(finPrompt.contains("两栏勾稽视图"));
    }

    @Test
    void failHungRunsOnStartupStopsHermesThenMarksFailed() {
        Fixture fixture = fixture();
        RunEntity hung = running("run_1");
        when(fixture.runMapper.selectList(any())).thenReturn(List.of(hung));

        fixture.service.failHungRunsOnStartup();

        verify(fixture.hermesRunsClient).stopRun("hermes_1");
        verify(fixture.runMapper).update(isNull(), any());
    }

    @Test
    void controlPlaneTimeoutStopsHermesRun() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_1");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);

        fixture.service.pollUntilDone("run_1", "hermes_1", java.time.Instant.EPOCH, run);

        verify(fixture.hermesRunsClient).stopRun("hermes_1");
        verify(fixture.hermesRunsClient, never()).getRun(any());
        assertEquals("FAILED", run.getStatus());
        assertEquals("HERMES_TIMEOUT", run.getErrorCode());
        assertEquals("分析未在时限内完成", run.getErrorMessage());
    }

    @Test
    void timeoutStillFailsWhenHermesStopThrows() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_1");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "HERMES_UNAVAILABLE", "无法连接分析服务"))
                .when(fixture.hermesRunsClient).stopRun("hermes_1");

        fixture.service.pollUntilDone("run_1", "hermes_1", java.time.Instant.EPOCH, run);

        assertEquals("FAILED", run.getStatus());
        assertEquals("HERMES_TIMEOUT", run.getErrorCode());
    }

    @Test
    void completedWithoutSubmitDoesNotStopHermesRun() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_1");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.hermesRunsClient.getRun("hermes_1"))
                .thenReturn(new ObjectMapper().readTree("{\"status\":\"completed\"}"));

        fixture.service.pollUntilDone("run_1", "hermes_1", java.time.Instant.now(), run);

        verify(fixture.hermesRunsClient, never()).stopRun(any());
        assertEquals("FAILED", run.getStatus());
        assertEquals("HERMES_NO_STRUCTURED_RESULT", run.getErrorCode());
        assertEquals("分析服务已完成，但未提交结构化结果", run.getErrorMessage());
    }

    @Test
    void failedOrCancelledHermesRunUsesTerminalFailureCodeInsteadOfTimeout() throws Exception {
        for (String hermesStatus : List.of("failed", "cancelled")) {
            Fixture fixture = fixture();
            RunEntity run = running("run_1");
            when(fixture.runMapper.selectById("run_1")).thenReturn(run);
            when(fixture.hermesRunsClient.getRun("hermes_1"))
                    .thenReturn(new ObjectMapper().readTree("{\"status\":\"" + hermesStatus + "\"}"));

            fixture.service.pollUntilDone("run_1", "hermes_1", java.time.Instant.now(), run);

            assertEquals("FAILED", run.getStatus());
            assertEquals("failed".equals(hermesStatus) ? "HERMES_FAILED" : "HERMES_CANCELLED", run.getErrorCode());
            assertTrue(!"HERMES_TIMEOUT".equals(run.getErrorCode()));
        }
    }

    @Test
    void acceptResultRunsInOneTransactionSoResultAndStatusCannotDiverge() throws Exception {
        Transactional annotation = RunService.class
                .getMethod("acceptResult", String.class, String.class, com.fasterxml.jackson.databind.JsonNode.class)
                .getAnnotation(Transactional.class);

        assertTrue(annotation != null, "acceptResult 必须带事务，否则结果落库后状态更新失败会让 Run 卡在 RUNNING");
        assertArrayEquals(new Class<?>[]{Exception.class}, annotation.rollbackFor());
    }

    @Test
    void persistedResultOnFinishedHermesRunConvergesInsteadOfStayingActive() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_1");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.hermesRunsClient.getRun("hermes_1"))
                .thenReturn(new ObjectMapper().readTree("{\"status\":\"completed\"}"));
        // 结果已落库，但 Run 仍是 RUNNING（acceptResult 的状态更新丢失）
        when(fixture.resultMapper.selectOne(any())).thenReturn(new ResultEntity());
        when(fixture.runMapper.update(any(), any())).thenReturn(1);

        fixture.service.pollUntilDone("run_1", "hermes_1", java.time.Instant.now(), run);

        ArgumentCaptor<LambdaUpdateWrapper<RunEntity>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(fixture.runMapper).update(any(), captor.capture());
        // getTargetSql 返回 WHERE 子句：补偿只能命中仍活跃的 Run，不覆盖已有终态
        String where = captor.getValue().getTargetSql();
        assertTrue(where.contains("id = ?"), "补偿更新应按 runId 定位: " + where);
        assertTrue(where.contains("status IN"), "补偿更新应限定前置状态: " + where);
        verify(fixture.hermesRunsClient, never()).stopRun(any());
        assertEquals("RUNNING", run.getStatus(), "不应改写传入实体，避免陈旧回写");
    }

    @Test
    void timeoutWithPersistedResultConvergesRatherThanFailing() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_1");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.resultMapper.selectOne(any())).thenReturn(new ResultEntity());
        when(fixture.runMapper.update(any(), any())).thenReturn(1);

        // 已超时（started=EPOCH），但结果其实已经落库
        fixture.service.pollUntilDone("run_1", "hermes_1", java.time.Instant.EPOCH, run);

        verify(fixture.runMapper).update(any(), any());
        verify(fixture.hermesRunsClient, never()).stopRun(any());
        assertEquals("RUNNING", run.getStatus());
        assertEquals(null, run.getErrorCode(), "有结果时不应标记为超时失败");
    }

    @Test
    void alreadyTerminalRunIsNotConvergedAgain() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_1");
        run.setStatus("FAILED");
        run.setErrorCode("HERMES_TIMEOUT");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.resultMapper.selectOne(any())).thenReturn(new ResultEntity());

        fixture.service.pollUntilDone("run_1", "hermes_1", java.time.Instant.EPOCH, run);

        verify(fixture.runMapper, never()).update(any(), any());
        assertEquals("FAILED", run.getStatus());
    }

    @Test
    void createRunUsesSharedActiveRunGuardBeforeReadingSources() {
        Fixture fixture = fixture();
        doThrow(new ApiException(HttpStatus.CONFLICT, "RUN_NOT_ACTIVE", "已有运行"))
                .when(fixture.taskService).requireNoActiveRun("task_1");

        ApiException error = assertThrows(ApiException.class, () -> fixture.service.create(
                "user_1",
                "task_1",
                new ApiModels.CreateRunRequest("REVIEW", "", null, "STANDARD", List.of(), null)
        ));

        assertTrue(error.code().equals("RUN_NOT_ACTIVE"));
        verify(fixture.sourceMapper, never()).selectList(any());
    }

    @Test
    void reviewRequiresExactlyOneDraftBackedBySucceededGenerateRun() {
        Fixture fixture = fixture();
        SourceEntity first = draft("src_1", "run_generate_1");
        SourceEntity second = draft("src_2", "run_generate_2");
        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of(first, second));

        ApiException duplicate = assertThrows(ApiException.class, () -> fixture.service.create(
                "user_1", "task_1", new ApiModels.CreateRunRequest("REVIEW", "", null, "STANDARD", List.of(), null)
        ));
        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of(first));
        when(fixture.runMapper.selectById("run_generate_1")).thenReturn(null);
        ApiException orphan = assertThrows(ApiException.class, () -> fixture.service.create(
                "user_1", "task_1", new ApiModels.CreateRunRequest("DEMO", "", null, "STANDARD", List.of(), null)
        ));

        assertEquals(400, duplicate.status().value());
        assertTrue(duplicate.getMessage().contains("只能有一份"));
        assertEquals(400, orphan.status().value());
        assertTrue(orphan.getMessage().contains("成功生成记录"));
        verify(fixture.runMapper, never()).insert(any(RunEntity.class));
    }

    @Test
    void reviewAcceptsOneValidCurrentDraft() {
        Fixture fixture = fixture();
        SourceEntity draft = draft("src_1", "run_generate_1");
        RunEntity generate = generateRun("run_generate_1");
        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of(draft));
        when(fixture.runMapper.selectById("run_generate_1")).thenReturn(generate);

        RunEntity created = fixture.service.create(
                "user_1", "task_1", new ApiModels.CreateRunRequest("REVIEW", "", null, "STANDARD", List.of(), null)
        );

        assertEquals("PENDING", created.getStatus());
        assertEquals("REVIEW", created.getMode());
        verify(fixture.runMapper).insert(created);
    }

    @Test
    void reviewAndDemoAcceptRawUploadedPrdWhenNoDraftExists() {
        Fixture fixture = fixture();
        SourceEntity rawPrd = new SourceEntity();
        rawPrd.setId("src_raw_1");
        rawPrd.setTaskId("task_1");
        rawPrd.setOriginalName("广州示例精细化工有限公司 CRM 业务模块产品需求文档（PRD）V1.1.docx");
        rawPrd.setStatus("READY");
        rawPrd.setContent("示例精细化工 CRM PRD 正文...");
        rawPrd.setSizeBytes(1024L);

        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of(rawPrd));

        RunEntity reviewRun = fixture.service.create(
                "user_1", "task_1", new ApiModels.CreateRunRequest("REVIEW", "", null, "STANDARD", List.of(), null)
        );
        assertEquals("PENDING", reviewRun.getStatus());
        assertEquals("REVIEW", reviewRun.getMode());

        RunEntity demoRun = fixture.service.create(
                "user_1", "task_1", new ApiModels.CreateRunRequest("DEMO", "", null, "STANDARD", List.of(), null)
        );
        assertEquals("PENDING", demoRun.getStatus());
        assertEquals("DEMO", demoRun.getMode());

        verify(fixture.runMapper).insert(reviewRun);
        verify(fixture.runMapper).insert(demoRun);
    }

    @Test
    void reviseRequiresExactlyOneDraftForItsParentRun() {
        Fixture fixture = fixture();
        RunEntity parent = generateRun("run_generate_1");
        when(fixture.runMapper.selectById("run_generate_1")).thenReturn(parent);
        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of(
                draft("src_1", "run_generate_1"),
                draft("src_2", "run_generate_1")
        ));

        ApiException error = assertThrows(ApiException.class, () -> fixture.service.create(
                "user_1",
                "task_1",
                new ApiModels.CreateRunRequest("GENERATE", "", null, "STANDARD", List.of(), "run_generate_1")
        ));

        assertEquals(400, error.status().value());
        assertTrue(error.getMessage().contains("未找到可修订的生成稿"));
        verify(fixture.runMapper, never()).insert(any(RunEntity.class));
    }

    private Fixture fixture() {
        TaskService taskService = mock(TaskService.class);
        SourceMapper sourceMapper = mock(SourceMapper.class);
        RunMapper runMapper = mock(RunMapper.class);
        WorkbenchProperties properties = new WorkbenchProperties(
                Path.of("target/test-uploads"),
                10_000_000,
                2_000_000,
                8_000,
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                Duration.ofMinutes(40),
                Duration.ofMinutes(40),
                Duration.ofMinutes(40),
                Duration.ofMinutes(40),
                new WorkbenchProperties.Hermes("http://127.0.0.1", Path.of("key"), "profile", "v0.16.0", Duration.ofSeconds(3)),
                Path.of("plugin-key")
        );
        HermesRunsClient hermesRunsClient = mock(HermesRunsClient.class);
        ResultMapper resultMapper = mock(ResultMapper.class);
        RunService service = new RunService(
                taskService,
                sourceMapper,
                runMapper,
                resultMapper,
                mock(ResultValidator.class),
                hermesRunsClient,
                new ObjectMapper(),
                properties,
                new TaskMutationGate()
        );
        return new Fixture(service, taskService, sourceMapper, runMapper, hermesRunsClient, resultMapper);
    }

    @Test
    void estimateRunBuildInputUsesExplicitScopeAndUncertainty() {
        Fixture fixture = fixture();
        RunEntity run = new RunEntity();
        run.setId("run_est");
        run.setTaskId("task_1");
        run.setMode("ESTIMATE");
        run.setInstruction("重点评估对接接口");
        run.setCompleteness("STANDARD");

        String prompt = fixture.service.buildInput(run);

        assertTrue(prompt.contains("development-self-test-v1"));
        assertTrue(prompt.contains("scopeNote"));
        assertTrue(prompt.contains("不套用固定"));
        assertTrue(prompt.contains("不限制条目总数"));
        assertFalse(prompt.contains("18~35"));
        assertTrue(prompt.contains("假设和不确定性"));
    }

    @Test
    void estimateRunCanBeCreatedWithOnlyRawRequirementsWithoutDraft() {
        Fixture fixture = fixture();
        SourceEntity rawSource = new SourceEntity();
        rawSource.setId("src_1");
        rawSource.setTaskId("task_1");
        rawSource.setOriginalName("客户需求清单.docx");
        rawSource.setStatus("READY");

        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of(rawSource));

        RunEntity created = fixture.service.create(
                "user_1",
                "task_1",
                new ApiModels.CreateRunRequest("ESTIMATE", "请快速估算", null, "STANDARD", null, null)
        );

        assertEquals("ESTIMATE", created.getMode());
        assertEquals("PENDING", created.getStatus());
        verify(fixture.runMapper).insert(any(RunEntity.class));
    }

    @Test
    void estimateRunThrowsIfNoMaterialsAtAll() {
        Fixture fixture = fixture();
        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> fixture.service.create(
                "user_1",
                "task_1",
                new ApiModels.CreateRunRequest("ESTIMATE", "请估算", null, "STANDARD", null, null)
        ));

        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("请先添加需求清单或完成 PRD 生成"));
    }

    @Test
    void testCasePromptBuildInputIncludesRequiredConstraintsAndStructure() {
        Fixture fixture = fixture();
        RunEntity run = new RunEntity();
        run.setId("run_tc_1");
        run.setTaskId("task_1");
        run.setMode("TEST_CASE");
        run.setInstruction("覆盖全部主要流程和异常流");
        run.setCompleteness("STANDARD");

        String prompt = fixture.service.buildInput(run);

        assertTrue(prompt.contains("mode=TEST_CASE"));
        assertTrue(prompt.contains("覆盖全部主要流程和异常流"));
        assertTrue(prompt.contains("write_test_case_file"));
        assertTrue(prompt.contains("verify_test_case"));
        assertTrue(prompt.contains("modules/<key>.json"));
        assertTrue(prompt.contains("FUNCTION"));
        assertTrue(prompt.contains("BOUNDARY"));
        assertTrue(prompt.contains("EXCEPTION"));
        assertTrue(prompt.contains("PERMISSION"));
        assertTrue(prompt.contains("CONCURRENCY"));
        assertTrue(prompt.contains("不设 18～32 条硬顶"));
        assertTrue(prompt.contains("required-modules.json"));
        assertFalse(prompt.contains("items 条目总数严格控制在 18 ~ 32 条"));
        assertFalse(prompt.contains("思考预算极简控制"));
    }

    @Test
    void generatePromptIncludesMermaidDiagramGuidance() {
        Fixture fixture = fixture();
        RunEntity run = new RunEntity();
        run.setId("run_gen_1");
        run.setTaskId("task_1");
        run.setMode("GENERATE");
        run.setInstruction("生成法务系统PRD");
        run.setCompleteness("STANDARD");

        String prompt = fixture.service.buildInput(run);

        assertTrue(prompt.contains("mode=GENERATE"));
        assertTrue(prompt.contains("Mermaid"));
        assertTrue(prompt.contains("write_prd_file"));
        assertTrue(prompt.contains("graph TD") || prompt.contains("```mermaid"));
    }

    @Test
    void generatePromptUsesSandboxPackInsteadOfCharCapsAndChunking() {
        Fixture fixture = fixture();
        RunEntity run = new RunEntity();
        run.setId("run_gen_1");
        run.setTaskId("task_1");
        run.setMode("GENERATE");
        run.setInstruction("生成行政管理PRD");
        run.setCompleteness("STANDARD");

        String prompt = fixture.service.buildInput(run);

        assertTrue(prompt.contains("mode=GENERATE"));
        assertTrue(prompt.contains("write_prd_file"));
        assertTrue(prompt.contains("verify_prd"));
        assertTrue(prompt.contains("data/catalog.json"));
        assertTrue(prompt.contains("| 字段名 | 控件类型 | 必填 | 校验 | 默认值/联动 |"));
        assertTrue(prompt.contains("workspaceFile"));
        assertFalse(prompt.contains("15000"));
        assertFalse(prompt.contains("22000"));
        assertFalse(prompt.contains("方案 B：超长需求分片"));
        assertFalse(prompt.contains("partIndex=1, totalParts=2, isFinal=false"));
        assertFalse(prompt.contains("95%"));
    }

    @Test
    void failedHermesRunWithTruncationDetailMapsToTokenLimitExceeded() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_1");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.hermesRunsClient.getRun("hermes_1"))
                .thenReturn(new ObjectMapper().readTree("""
                    {"status":"failed","error":"Response truncated due to output length limit"}
                    """));

        fixture.service.pollUntilDone("run_1", "hermes_1", java.time.Instant.now(), run);

        assertEquals("FAILED", run.getStatus());
        assertEquals("HERMES_TOKEN_LIMIT_EXCEEDED", run.getErrorCode());
        assertTrue(run.getErrorMessage().contains("Token Limit"));
        assertTrue(run.getErrorMessage().contains("write_prd_file") || run.getErrorMessage().contains("按文件拆写"));
        assertFalse(run.getErrorMessage().contains("开启分片提交"));
    }

    @Test
    void failedPocHermesRunWithTruncationDoesNotSuggestPrdChunking() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_poc_token");
        run.setMode("POC");
        when(fixture.runMapper.selectById("run_poc_token")).thenReturn(run);
        when(fixture.hermesRunsClient.getRun("hermes_1"))
                .thenReturn(new ObjectMapper().readTree("""
                    {"status":"failed","error":"Response truncated due to output length limit"}
                    """));

        fixture.service.pollUntilDone("run_poc_token", "hermes_1", java.time.Instant.now(), run);

        assertEquals("FAILED", run.getStatus());
        assertEquals("HERMES_TOKEN_LIMIT_EXCEEDED", run.getErrorCode());
        assertTrue(run.getErrorMessage().contains("POC 不能用 PRD 分片提交"));
        assertTrue(run.getErrorMessage().contains("js/ui.js"));
    }

    @Test
    void generateChunkedAcceptResultIsRejected() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_chunk_1");
        when(fixture.runMapper.selectById("run_chunk_1")).thenReturn(run);

        ObjectMapper om = new ObjectMapper();
        var part1 = om.readTree("""
            {
                "title": "示例行政管理PRD",
                "summary": "行政管理需求",
                "markdown": "# 模块1: 资产管理\\n资产明细说明"
            }
            """);
        ApiException error = assertThrows(ApiException.class,
                () -> fixture.service.acceptResult("run_chunk_1", "GENERATE", part1, 1, 2, false));
        assertEquals("INVALID_REQUEST", error.code());
        assertTrue(error.getMessage().contains("沙箱文档包"));
        verify(fixture.resultMapper, never()).insert(any(ResultEntity.class));
    }

    @Test
    void generateAcceptResultAssemblesPrdFilesAndMarkdown() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_prd_pack");
        when(fixture.runMapper.selectById("run_prd_pack")).thenReturn(run);

        fixture.service.writeWorkspaceFile("run_prd_pack", "README.md", "# 顺序\n1. `00-overview.md`\n2. `modules/voucher.md`\n");
        fixture.service.writeWorkspaceFile("run_prd_pack", "00-overview.md", "# 概述\n目标：记账。");
        fixture.service.writeWorkspaceFile("run_prd_pack", "modules/voucher.md", "# 凭证\n| 字段名 | 控件类型 | 必填 | 校验 | 默认值/联动 |");
        fixture.service.writeWorkspaceFile("run_prd_pack", "data/catalog.json", "{\"modules\":[{\"key\":\"voucher\",\"title\":\"凭证\",\"core\":true}]}");

        ObjectMapper om = new ObjectMapper();
        var payload = om.readTree("""
            {
                "mode": "GENERATE",
                "title": "示例财务PRD",
                "summary": "可开工文档包",
                "workspaceFile": "README.md",
                "files": ["README.md", "00-overview.md", "modules/voucher.md", "data/catalog.json"]
            }
            """);
        assertTrue(fixture.service.acceptResult("run_prd_pack", "GENERATE", payload, 1, 1, true));

        ArgumentCaptor<ResultEntity> resultCaptor = ArgumentCaptor.forClass(ResultEntity.class);
        verify(fixture.resultMapper).insert(resultCaptor.capture());
        var saved = om.readTree(resultCaptor.getValue().getPayload());
        assertEquals("README.md", saved.get("workspaceFile").asText());
        assertTrue(saved.get("prd_files").has("modules/voucher.md"));
        assertTrue(saved.get("markdown").asText().contains("目标：记账"));
        assertTrue(saved.get("markdown").asText().contains("凭证"));
    }

    @Test
    void testCaseCreateSucceedsWithRawSource() {
        Fixture fixture = fixture();
        SourceEntity source = new SourceEntity();
        source.setId("source_1");
        source.setTaskId("task_1");
        source.setOriginalName("需求清单.md");
        source.setStatus("READY");
        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of(source));

        RunEntity created = fixture.service.create(
                "user_1",
                "task_1",
                new ApiModels.CreateRunRequest("TEST_CASE", "生成测试用例", null, "STANDARD", null, null)
        );

        assertEquals("TEST_CASE", created.getMode());
        assertEquals("PENDING", created.getStatus());
        verify(fixture.runMapper).insert(any(RunEntity.class));
    }

    @Test
    void testCaseCreateThrowsIfNoMaterialsAtAll() {
        Fixture fixture = fixture();
        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> fixture.service.create(
                "user_1",
                "task_1",
                new ApiModels.CreateRunRequest("TEST_CASE", "生成测试用例", null, "STANDARD", null, null)
        ));

        assertEquals("INVALID_REQUEST", ex.code());
        assertTrue(ex.getMessage().contains("请先添加需求材料或完成 PRD 生成"));
    }

    @Test
    void techSpecPromptUsesSandboxPackInsteadOfHardCaps() {
        Fixture fixture = fixture();
        RunEntity run = new RunEntity();
        run.setId("run_tech_spec");
        run.setTaskId("task_1");
        run.setMode("TECH_SPEC");
        run.setInstruction("重点拆解订单结算与业财一体化");
        run.setCompleteness("STANDARD");

        String prompt = fixture.service.buildInput(run);

        assertTrue(prompt.contains("mode=TECH_SPEC"));
        assertTrue(prompt.contains("write_tech_spec_file"));
        assertTrue(prompt.contains("verify_tech_spec"));
        assertTrue(prompt.contains("architecture.md"));
        assertTrue(prompt.contains("data/tables.json"));
        assertTrue(prompt.contains("data/apis.json"));
        assertTrue(prompt.contains("交付完整性"));
        assertTrue(prompt.contains("requestBody"));
        assertTrue(prompt.contains("不要写 data/tasks.json"));
        assertTrue(prompt.contains("required-modules.json"));
        assertFalse(prompt.contains("思考预算极简控制"));
        assertFalse(prompt.contains("tasks 任务工单总数严格控制在 12 ~ 18 项"));
        assertFalse(prompt.contains("data/tasks.json：前后端"));
    }

    @Test
    void pocRunBuildInputUsesPerFileWriteBudgetInsteadOfPrdChunking() {
        Fixture fixture = fixture();
        RunEntity run = new RunEntity();
        run.setId("run_poc_budget");
        run.setTaskId("task_1");
        run.setMode("POC");
        run.setInstruction("示例财务演示系统");
        run.setCompleteness("THOROUGH");

        String prompt = fixture.service.buildInput(run);

        assertTrue(prompt.contains("mode=POC"));
        assertTrue(prompt.contains("20000"));
        assertTrue(prompt.contains("PocUI.ledger"));
        assertTrue(prompt.contains("truncated"));
        assertTrue(prompt.contains("POC 不能用 PRD 分片提交"));
        assertTrue(prompt.contains("不要从 catalog 删模块"));
        assertFalse(prompt.contains("总共 8～15"));
        assertTrue(prompt.contains("required-modules.json"));
        assertTrue(prompt.contains("必须覆盖的一级模块"));
    }

    @Test
    void pocBuildInputInjectsCurrentDraftModuleKeys() {
        Fixture fixture = fixture();
        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of(draft("src_cov", "run_gen_cov")));
        ResultEntity result = new ResultEntity();
        result.setPayload("""
                {
                  "mode": "GENERATE",
                  "prd_files": {
                    "data/catalog.json": "{\\"modules\\":[{\\"key\\":\\"base-setup\\",\\"title\\":\\"基础设置\\",\\"core\\":true},{\\"key\\":\\"gl\\",\\"title\\":\\"总账\\",\\"core\\":true}]}"
                  }
                }
                """);
        when(fixture.resultMapper.selectOne(any())).thenReturn(result);

        RunEntity run = new RunEntity();
        run.setId("run_poc_cov");
        run.setTaskId("task_1");
        run.setMode("POC");
        run.setInstruction("示例财务演示系统");
        run.setCompleteness("STANDARD");

        String prompt = fixture.service.buildInput(run);

        assertTrue(prompt.contains("必须覆盖的一级模块"));
        assertTrue(prompt.contains("`base-setup`"));
        assertTrue(prompt.contains("`gl`"));
        assertTrue(prompt.contains("禁止改成 platforms"));
    }

    @Test
    void pocRequiredModulesFileCannotBeOverwritten() {
        Fixture fixture = fixture();
        RunEntity run = running("run_ws_req");
        run.setMode("POC");
        when(fixture.runMapper.selectById("run_ws_req")).thenReturn(run);

        ApiException locked = assertThrows(ApiException.class, () ->
                fixture.service.writeWorkspaceFile(
                        "run_ws_req",
                        "data/required-modules.json",
                        "{\"coverage\":\"all\",\"modules\":[{\"key\":\"gl\",\"title\":\"总账\",\"core\":true}]}"
                ));
        assertEquals("REQUIRED_MODULES_LOCKED", locked.code());
    }

    @Test
    void writeAndReadWorkspaceFileSucceedsAndBlocksPathTraversal() {
        Fixture fixture = fixture();
        RunEntity run = running("run_ws_1");
        run.setMode("DEMO");
        when(fixture.runMapper.selectById("run_ws_1")).thenReturn(run);

        var writeRes = fixture.service.writeWorkspaceFile("run_ws_1", "index.html", "<!DOCTYPE html><html><body>Test</body></html>");
        assertEquals(true, writeRes.get("success"));
        assertEquals("index.html", writeRes.get("filePath"));

        var readRes = fixture.service.readWorkspaceFile("run_ws_1", "index.html");
        assertEquals(true, readRes.get("success"));
        assertEquals("<!DOCTYPE html><html><body>Test</body></html>", readRes.get("content"));

        ApiException secEx = assertThrows(ApiException.class, () ->
                fixture.service.writeWorkspaceFile("run_ws_1", "../evil.html", "malicious"));
        assertEquals("SECURITY_ERROR", secEx.code());

        ApiException secEx2 = assertThrows(ApiException.class, () ->
                fixture.service.readWorkspaceFile("run_ws_1", "../../etc/passwd"));
        assertEquals("SECURITY_ERROR", secEx2.code());

        ApiException demoCss = assertThrows(ApiException.class, () ->
                fixture.service.writeWorkspaceFile("run_ws_1", "css/app.css", ".app{}"));
        assertEquals("INVALID_REQUEST", demoCss.code());

        run.setMode("POC");
        var pocCss = fixture.service.writeWorkspaceFile("run_ws_1", "css/app.css", ".app{color:red}");
        assertEquals(true, pocCss.get("success"));
        ApiException pocPkg = assertThrows(ApiException.class, () ->
                fixture.service.writeWorkspaceFile("run_ws_1", "package.json", "{}"));
        assertEquals("INVALID_REQUEST", pocPkg.code());
    }

    @Test
    void demoAcceptResultLoadsWorkspaceFileWhenHtmlIsOmitted() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_ws_demo");
        run.setMode("DEMO");
        when(fixture.runMapper.selectById("run_ws_demo")).thenReturn(run);

        fixture.service.writeWorkspaceFile("run_ws_demo", "index.html",
                "<!DOCTYPE html><html><body><h1>Demo Loaded</h1></body></html>");

        ObjectMapper om = new ObjectMapper();
        var payload = om.readTree("""
                {
                  "mode": "DEMO",
                  "title": "测试 Demo",
                  "summary": "自动读取沙箱文件",
                  "workspaceFile": "index.html"
                }
                """);

        boolean completed = fixture.service.acceptResult("run_ws_demo", "DEMO", payload, 1, 1, true);
        assertTrue(completed);

        ArgumentCaptor<ResultEntity> resultCaptor = ArgumentCaptor.forClass(ResultEntity.class);
        verify(fixture.resultMapper).insert(resultCaptor.capture());
        ResultEntity savedResult = resultCaptor.getValue();
        assertEquals("DEMO", savedResult.getMode());

        var resultJson = om.readTree(savedResult.getPayload());
        assertEquals("<!DOCTYPE html><html><body><h1>Demo Loaded</h1></body></html>", resultJson.get("html").asText());
        assertEquals("index.html", resultJson.get("workspaceFile").asText());
    }

    @Test
    void demoAcceptResultUsesLatestWorkspaceFileInsteadOfStaleInlineHtml() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_ws_latest");
        run.setMode("DEMO");
        when(fixture.runMapper.selectById("run_ws_latest")).thenReturn(run);

        String latestHtml = "<!DOCTYPE html><html><body><h1>Latest Workspace Demo</h1></body></html>";
        fixture.service.writeWorkspaceFile("run_ws_latest", "index.html", latestHtml);

        ObjectMapper om = new ObjectMapper();
        var payload = om.readTree("""
                {
                  "mode": "DEMO",
                  "title": "测试 Demo",
                  "summary": "以沙箱最新文件为准",
                  "workspaceFile": "index.html",
                  "html": "<!DOCTYPE html><html><body><h1>Stale Inline Demo</h1></body></html>"
                }
                """);

        assertTrue(fixture.service.acceptResult("run_ws_latest", "DEMO", payload, 1, 1, true));

        ArgumentCaptor<ResultEntity> resultCaptor = ArgumentCaptor.forClass(ResultEntity.class);
        verify(fixture.resultMapper).insert(resultCaptor.capture());
        var resultJson = om.readTree(resultCaptor.getValue().getPayload());
        assertEquals(latestHtml, resultJson.get("html").asText());
        assertEquals("index.html", resultJson.get("workspaceFile").asText());
    }

    @Test
    void demoAcceptResultRejectsMissingWorkspaceFileEvenWhenInlineHtmlExists() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = running("run_ws_missing");
        run.setMode("DEMO");
        when(fixture.runMapper.selectById("run_ws_missing")).thenReturn(run);

        ObjectMapper om = new ObjectMapper();
        var payload = om.readTree("""
                {
                  "mode": "DEMO",
                  "title": "测试 Demo",
                  "summary": "不得回退旧内联 HTML",
                  "workspaceFile": "index.html",
                  "html": "<!DOCTYPE html><html><body><h1>Stale Inline Demo</h1></body></html>"
                }
                """);

        ApiException error = assertThrows(ApiException.class,
                () -> fixture.service.acceptResult("run_ws_missing", "DEMO", payload, 1, 1, true));
        assertEquals("FILE_NOT_FOUND", error.code());
        verify(fixture.resultMapper, never()).insert(any(ResultEntity.class));
    }

    private RunEntity running(String runId) {
        RunEntity run = new RunEntity();
        run.setId(runId);
        run.setTaskId("task_1");
        run.setMode("GENERATE");
        run.setStatus("RUNNING");
        run.setCompleteness("STANDARD");
        run.setHermesRunId("hermes_1");
        return run;
    }

    private SourceEntity draft(String sourceId, String runId) {
        SourceEntity source = new SourceEntity();
        source.setId(sourceId);
        source.setTaskId("task_1");
        source.setOriginalName(SourceRoles.draftName(runId));
        source.setStatus("READY");
        return source;
    }

    private RunEntity generateRun(String runId) {
        RunEntity run = new RunEntity();
        run.setId(runId);
        run.setTaskId("task_1");
        run.setMode("GENERATE");
        run.setStatus("SUCCEEDED");
        return run;
    }

    private record Fixture(
            RunService service,
            TaskService taskService,
            SourceMapper sourceMapper,
            RunMapper runMapper,
            HermesRunsClient hermesRunsClient,
            ResultMapper resultMapper
    ) {
    }
}
