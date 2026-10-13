package org.hermesprd.workbench.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hermesprd.workbench.hermes.HermesRunsClient;
import org.hermesprd.workbench.result.ResultEntity;
import org.hermesprd.workbench.result.ResultMapper;
import org.hermesprd.workbench.result.ResultValidator;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.Ids;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.hermesprd.workbench.telemetry.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RunService {
    private static final Logger log = LoggerFactory.getLogger(RunService.class);
    private static final Set<String> MODES = Set.of("REVIEW", "GENERATE", "DEMO", "POC", "ESTIMATE", "TEST_CASE", "TECH_SPEC");
    private static final String MODE_LIST = "REVIEW、GENERATE、DEMO、POC、ESTIMATE、TEST_CASE 或 TECH_SPEC";

    private final TaskService taskService;
    private final SourceMapper sourceMapper;
    private final RunMapper runMapper;
    private final ResultMapper resultMapper;
    private final ResultValidator resultValidator;
    private final HermesRunsClient hermesRunsClient;
    private final ObjectMapper objectMapper;
    private final WorkbenchProperties properties;
    private final TaskMutationGate mutationGate;
    private final TelemetryService telemetryService;
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            new java.util.concurrent.ThreadFactory() {
                private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "hermes-run-worker-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            }
    );

    public RunService(
            TaskService taskService,
            SourceMapper sourceMapper,
            RunMapper runMapper,
            ResultMapper resultMapper,
            ResultValidator resultValidator,
            HermesRunsClient hermesRunsClient,
            ObjectMapper objectMapper,
            WorkbenchProperties properties,
            TaskMutationGate mutationGate
    ) {
        this(taskService, sourceMapper, runMapper, resultMapper, resultValidator, hermesRunsClient, objectMapper, properties, mutationGate, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RunService(
            TaskService taskService,
            SourceMapper sourceMapper,
            RunMapper runMapper,
            ResultMapper resultMapper,
            ResultValidator resultValidator,
            HermesRunsClient hermesRunsClient,
            ObjectMapper objectMapper,
            WorkbenchProperties properties,
            TaskMutationGate mutationGate,
            TelemetryService telemetryService
    ) {
        this.taskService = taskService;
        this.sourceMapper = sourceMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.resultValidator = resultValidator;
        this.hermesRunsClient = hermesRunsClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.mutationGate = mutationGate;
        this.telemetryService = telemetryService;
    }

    public void failHungRunsOnStartup() {
        List<RunEntity> hung = runMapper.selectList(new LambdaQueryWrapper<RunEntity>()
                .in(RunEntity::getStatus, List.of("PENDING", "RUNNING")));
        for (RunEntity run : hung) {
            stopHermesQuietly(run.getHermesRunId());
        }
        Instant now = Instant.now();
        runMapper.update(null, new LambdaUpdateWrapper<RunEntity>()
                .in(RunEntity::getStatus, List.of("PENDING", "RUNNING"))
                .set(RunEntity::getStatus, "FAILED")
                .set(RunEntity::getErrorCode, "INTERRUPTED")
                .set(RunEntity::getErrorMessage, "进程重启，未完成的运行已标记失败")
                .set(RunEntity::getFinishedAt, now)
                .set(RunEntity::getUpdatedAt, now));
    }

    public RunEntity create(String userId, String taskId, ApiModels.CreateRunRequest request) {
        taskService.requireOwned(userId, taskId);
        String normalizedMode = request.mode() == null ? "" : request.mode().trim().toUpperCase();
        if (!MODES.contains(normalizedMode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "mode 只能是 " + MODE_LIST);
        }
        String completeness = QualityOptions.normalizeCompleteness(request.completeness());
        boolean lensesOmitted = request.lenses() == null;
        List<String> lenses = QualityOptions.normalizeLenses(normalizedMode, request.lenses(), lensesOmitted);
        return mutationGate.execute(taskId, () -> createLocked(
                userId, taskId, request, normalizedMode, completeness, lenses
        ));
    }

    private RunEntity createLocked(
            String userId,
            String taskId,
            ApiModels.CreateRunRequest request,
            String normalizedMode,
            String completeness,
            List<String> lenses
    ) {
        taskService.requireNoActiveRun(taskId);
        String parentRunId = resolveReviseParent(userId, taskId, normalizedMode, request.reviseFromRunId());
        List<SourceEntity> readySources = sourceMapper.selectList(new LambdaQueryWrapper<SourceEntity>()
                .eq(SourceEntity::getTaskId, taskId)
                .eq(SourceEntity::getStatus, "READY"));
        boolean hasDraft = readySources.stream()
                .anyMatch(source -> source.getOriginalName() != null
                        && source.getOriginalName().startsWith(SourceRoles.DRAFT_PREFIX));
        if (hasDraft && ("REVIEW".equals(normalizedMode) || "DEMO".equals(normalizedMode) || "POC".equals(normalizedMode))) {
            requireExactlyOneCurrentDraft(taskId, readySources);
        }
        Long ready = readySources
                .stream()
                .filter(source -> SourceRoles.visibleForRun(normalizedMode, source.getOriginalName(), parentRunId, hasDraft))
                .count();
        if (ready == 0) {
            String message = switch (normalizedMode) {
                case "REVIEW" -> "请先添加 PRD 材料或完成 PRD 生成后再进行评审";
                case "DEMO" -> "请先添加 PRD 材料或完成 PRD 生成后再进行 Demo 生成";
                case "POC" -> "请先添加 PRD 材料或完成 PRD 生成后再进行 POC 演示系统生成";
                case "ESTIMATE" -> "请先添加需求清单或完成 PRD 生成后再进行工作量评估";
                case "TEST_CASE" -> "请先添加需求材料或完成 PRD 生成后再进行测试用例生成";
                case "TECH_SPEC" -> "请先添加需求材料或完成 PRD 生成后再进行技术方案拆解";
                default -> parentRunId == null ? "请先添加可用材料" : "未找到可修订的生成稿，请先完成一次 PRD 生成";
            };
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
        }
        Instant now = Instant.now();
        RunEntity run = new RunEntity();
        run.setId(Ids.next("run_"));
        run.setTaskId(taskId);
        run.setParentRunId(parentRunId);
        run.setMode(normalizedMode);
        run.setInstruction(composeInstruction(normalizedMode, request.instruction(), request.skill()));
        run.setCompleteness(completeness);
        run.setLenses(QualityOptions.joinLenses(lenses));
        run.setStatus("PENDING");
        run.setStage("READING");
        run.setAccepted(false);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);
        if (telemetryService != null) {
            telemetryService.recordRun(run, "START_RUN", null);
        }
        executor.submit(() -> execute(run.getId()));
        return run;
    }

    private SourceEntity requireExactlyOneCurrentDraft(String taskId, List<SourceEntity> readySources) {
        List<SourceEntity> drafts = readySources.stream()
                .filter(source -> source.getOriginalName() != null
                        && source.getOriginalName().startsWith(SourceRoles.DRAFT_PREFIX))
                .toList();
        if (drafts.size() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "评审、Demo 和 POC 需要且只能有一份当前稿");
        }
        SourceEntity draft = drafts.getFirst();
        String generateRunId = SourceRoles.draftRunId(draft.getOriginalName());
        RunEntity generateRun = generateRunId == null ? null : runMapper.selectById(generateRunId);
        if (generateRun == null
                || !taskId.equals(generateRun.getTaskId())
                || !"GENERATE".equals(generateRun.getMode())
                || !"SUCCEEDED".equals(generateRun.getStatus())
                || !SourceRoles.draftName(generateRun.getId()).equals(draft.getOriginalName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "当前稿没有对应的成功生成记录");
        }
        return draft;
    }

    private String resolveReviseParent(String userId, String taskId, String mode, String reviseFromRunId) {
        if (reviseFromRunId == null || reviseFromRunId.isBlank()) {
            return null;
        }
        if (!"GENERATE".equals(mode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "只有生成模式支持基于当前稿修订");
        }
        RunEntity parent = requireOwned(userId, reviseFromRunId.trim());
        if (!taskId.equals(parent.getTaskId()) || !"GENERATE".equals(parent.getMode()) || !"SUCCEEDED".equals(parent.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "只能修订本需求中已成功的生成稿");
        }
        String draftName = SourceRoles.draftName(parent.getId());
        long draftCount = sourceMapper.selectList(new LambdaQueryWrapper<SourceEntity>()
                .eq(SourceEntity::getTaskId, taskId)
                .eq(SourceEntity::getStatus, "READY"))
                .stream()
                .filter(source -> draftName.equals(source.getOriginalName()))
                .count();
        if (draftCount != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "未找到可修订的生成稿，请先完成一次 PRD 生成");
        }
        return parent.getId();
    }

    public RunEntity requireRun(String runId) {
        RunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "运行不存在");
        }
        return run;
    }

    public RunEntity requireOwned(String userId, String runId) {
        RunEntity run = requireRun(runId);
        taskService.requireOwned(userId, run.getTaskId());
        return run;
    }

    public RunEntity confirm(String userId, String runId) {
        RunEntity run = requireOwned(userId, runId);
        if (!"SUCCEEDED".equals(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_ACTIVE", "只能确认已成功的结果");
        }
        run.setAccepted(true);
        run.setUpdatedAt(Instant.now());
        runMapper.updateById(run);
        return run;
    }

    public List<ApiModels.RunHistoryItem> listHistory(String userId, String mode, String taskId) {
        java.util.Map<String, String> titles = new java.util.LinkedHashMap<>();
        if (taskId != null && !taskId.isBlank()) {
            TaskEntity task = taskService.requireOwned(userId, taskId);
            titles.put(task.getId(), task.getTitle());
        } else {
            for (TaskEntity task : taskService.listByUser(userId)) {
                titles.put(task.getId(), task.getTitle());
            }
        }
        if (titles.isEmpty()) {
            return List.of();
        }
        String normalizedMode = mode == null || mode.isBlank() ? null : mode.trim().toUpperCase();
        if (normalizedMode != null && !MODES.contains(normalizedMode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "mode 只能是 " + MODE_LIST);
        }
        List<RunEntity> runs = runMapper.selectList(new LambdaQueryWrapper<RunEntity>()
                .in(RunEntity::getTaskId, titles.keySet())
                .eq(normalizedMode != null, RunEntity::getMode, normalizedMode)
                .orderByDesc(RunEntity::getCreatedAt)
                .last("LIMIT 100"));
        if (runs.isEmpty()) {
            return List.of();
        }
        List<String> runIds = runs.stream().map(RunEntity::getId).toList();
        java.util.Map<String, String> summaries = new java.util.HashMap<>();
        List<ResultEntity> results = resultMapper.selectList(new LambdaQueryWrapper<ResultEntity>()
                .select(ResultEntity::getRunId, ResultEntity::getSummary)
                .in(ResultEntity::getRunId, runIds));
        if (results != null) {
            for (ResultEntity r : results) {
                if (r.getRunId() != null) {
                    summaries.put(r.getRunId(), r.getSummary());
                }
            }
        }
        List<ApiModels.RunHistoryItem> items = new java.util.ArrayList<>(runs.size());
        for (RunEntity run : runs) {
            items.add(new ApiModels.RunHistoryItem(
                    run.getId(),
                    run.getTaskId(),
                    titles.getOrDefault(run.getTaskId(), "未命名任务"),
                    run.getMode(),
                    run.getStatus(),
                    summaries.get(run.getId()),
                    run.getErrorMessage(),
                    run.isAccepted(),
                    run.getCreatedAt(),
                    run.getFinishedAt()
            ));
        }
        return items;
    }

    public ResultEntity findResult(String runId) {
        return resultMapper.selectOne(new LambdaQueryWrapper<ResultEntity>().eq(ResultEntity::getRunId, runId));
    }

    public JsonNode resultPayload(ResultEntity result) {
        if (result == null || result.getPayload() == null) {
            return null;
        }
        try {
            return objectMapper.readTree(result.getPayload());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "结果无法解析");
        }
    }

    /**
     * 结果写入与 Run 状态更新必须同一事务：否则前者成功、后者失败时，
     * Run 会永久停在 RUNNING，被 {@link TaskService#requireNoActiveRun} 锁死整个需求。
     */
    @Transactional(rollbackFor = Exception.class)
    public void acceptResult(String runId, String mode, JsonNode payload) {
        acceptResult(runId, mode, payload, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean acceptResult(String runId, String mode, JsonNode payload, Integer partIndex, Integer totalParts, Boolean isFinal) {
        RunEntity run = requireRun(runId);
        if (!"RUNNING".equals(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_ACTIVE", "只能向运行中的任务提交结果");
        }
        if (!run.getMode().equals(mode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "提交 mode 与 Run 不一致");
        }

        int part = partIndex != null ? partIndex : (payload != null && payload.has("partIndex") && payload.get("partIndex").isNumber() ? payload.get("partIndex").asInt() : 1);
        int total = totalParts != null ? totalParts : (payload != null && payload.has("totalParts") && payload.get("totalParts").isNumber() ? payload.get("totalParts").asInt() : 1);
        boolean finalChunk = isFinal != null ? isFinal : (payload != null && payload.has("isFinal") && payload.get("isFinal").isBoolean() ? payload.get("isFinal").asBoolean() : (part >= total));
        if ("GENERATE".equals(mode) && (total > 1 || !finalChunk || part > 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "GENERATE 已改为沙箱文档包，请用 write_prd_file 写入后一次性 submit_result，不要分片提交");
        }
        if (("TEST_CASE".equals(mode) || "TECH_SPEC".equals(mode)) && (total > 1 || !finalChunk || part > 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    mode + " 已改为沙箱文档包，请用对应 write_*_file 写入后一次性 submit_result，不要分片提交");
        }
        if (!finalChunk) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "当前模式不支持分片提交");
        }

        JsonNode finalPayload = payload;

        if ("DEMO".equalsIgnoreCase(mode) && finalPayload != null && finalPayload.isObject()) {
            ObjectNode demoObj = (ObjectNode) finalPayload;
            String html = demoObj.has("html") && demoObj.get("html").isTextual() ? demoObj.get("html").asText() : "";
            String wsFile = demoObj.has("workspaceFile") && demoObj.get("workspaceFile").isTextual()
                    && !demoObj.get("workspaceFile").asText().isBlank()
                    ? demoObj.get("workspaceFile").asText()
                    : (demoObj.has("entryFile") && demoObj.get("entryFile").isTextual()
                    && !demoObj.get("entryFile").asText().isBlank() ? demoObj.get("entryFile").asText() : null);
            if (wsFile == null && html.isBlank()) {
                wsFile = "index.html";
            }
            if (wsFile != null) {
                Map<String, Object> workspaceFile = readWorkspaceFile(runId, wsFile);
                demoObj.put("html", String.valueOf(workspaceFile.get("content")));
                demoObj.put("workspaceFile", wsFile);
                demoObj.remove("entryFile");
            }
        }

        if ("POC".equalsIgnoreCase(mode) && finalPayload != null && finalPayload.isObject()) {
            ObjectNode pocObj = (ObjectNode) finalPayload;
            Path wsDir = properties.uploadDir().resolve("workspaces").resolve(runId).toAbsolutePath().normalize();
            List<String> requested = new ArrayList<>();
            if (pocObj.has("files") && pocObj.get("files").isArray()) {
                for (JsonNode node : pocObj.get("files")) {
                    if (node != null && node.isTextual() && !node.asText().isBlank()) {
                        requested.add(node.asText());
                    }
                }
            }
            Map<String, String> files = PocWorkspace.readAllowedFiles(wsDir, requested);
            ObjectNode fileNode = objectMapper.createObjectNode();
            ArrayNode fileList = objectMapper.createArrayNode();
            files.forEach((path, content) -> {
                fileNode.put(path, content);
                fileList.add(path);
            });
            pocObj.set("poc_files", fileNode);
            pocObj.set("files", fileList);
            pocObj.put("workspaceFile", PocWorkspace.INDEX);
            pocObj.put("html", PocWorkspace.inline(files.get(PocWorkspace.INDEX), files));
        }

        if ("GENERATE".equalsIgnoreCase(mode) && finalPayload != null && finalPayload.isObject()) {
            ObjectNode prdObj = (ObjectNode) finalPayload;
            Path wsDir = properties.uploadDir().resolve("workspaces").resolve(runId).toAbsolutePath().normalize();
            List<String> requested = new ArrayList<>();
            if (prdObj.has("files") && prdObj.get("files").isArray()) {
                for (JsonNode node : prdObj.get("files")) {
                    if (node != null && node.isTextual() && !node.asText().isBlank()) {
                        requested.add(node.asText());
                    }
                }
            }
            boolean hasWorkspace = Files.isDirectory(wsDir) && !PrdWorkspace.listRelativeFiles(wsDir).isEmpty();
            if (hasWorkspace || !requested.isEmpty() || (prdObj.has("workspaceFile") && prdObj.get("workspaceFile").isTextual()
                    && !prdObj.get("workspaceFile").asText().isBlank())) {
                Map<String, String> files = PrdWorkspace.readAllowedFiles(wsDir, requested);
                ObjectNode fileNode = objectMapper.createObjectNode();
                ArrayNode fileList = objectMapper.createArrayNode();
                files.forEach((path, content) -> {
                    fileNode.put(path, content);
                    fileList.add(path);
                });
                prdObj.set("prd_files", fileNode);
                prdObj.set("files", fileList);
                prdObj.put("workspaceFile", PrdWorkspace.README);
                prdObj.put("markdown", PrdWorkspace.assembleMarkdown(files));
            }
            prdObj.remove(List.of("partIndex", "totalParts", "isFinal"));
        }

        if ("TEST_CASE".equalsIgnoreCase(mode) && finalPayload != null && finalPayload.isObject()) {
            ObjectNode tcObj = (ObjectNode) finalPayload;
            Path wsDir = properties.uploadDir().resolve("workspaces").resolve(runId).toAbsolutePath().normalize();
            List<String> requested = new ArrayList<>();
            if (tcObj.has("files") && tcObj.get("files").isArray()) {
                for (JsonNode node : tcObj.get("files")) {
                    if (node != null && node.isTextual() && !node.asText().isBlank()) {
                        requested.add(node.asText());
                    }
                }
            }
            boolean hasWorkspace = Files.isDirectory(wsDir) && !TestCaseWorkspace.listRelativeFiles(wsDir).isEmpty();
            if (hasWorkspace || !requested.isEmpty() || (tcObj.has("workspaceFile") && tcObj.get("workspaceFile").isTextual()
                    && !tcObj.get("workspaceFile").asText().isBlank())) {
                Map<String, String> files = TestCaseWorkspace.readAllowedFiles(wsDir, requested);
                ObjectNode fileNode = objectMapper.createObjectNode();
                ArrayNode fileList = objectMapper.createArrayNode();
                files.forEach((path, content) -> {
                    fileNode.put(path, content);
                    fileList.add(path);
                });
                tcObj.set("test_case_files", fileNode);
                tcObj.set("files", fileList);
                tcObj.put("workspaceFile", TestCaseWorkspace.README);
                tcObj.set("items", TestCaseWorkspace.assembleItems(files));
            }
            tcObj.remove(List.of("partIndex", "totalParts", "isFinal"));
        }

        if ("TECH_SPEC".equalsIgnoreCase(mode) && finalPayload != null && finalPayload.isObject()) {
            ObjectNode tsObj = (ObjectNode) finalPayload;
            Path wsDir = properties.uploadDir().resolve("workspaces").resolve(runId).toAbsolutePath().normalize();
            List<String> requested = new ArrayList<>();
            if (tsObj.has("files") && tsObj.get("files").isArray()) {
                for (JsonNode node : tsObj.get("files")) {
                    if (node != null && node.isTextual() && !node.asText().isBlank()) {
                        requested.add(node.asText());
                    }
                }
            }
            boolean hasWorkspace = Files.isDirectory(wsDir) && !TechSpecWorkspace.listRelativeFiles(wsDir).isEmpty();
            if (hasWorkspace || !requested.isEmpty() || (tsObj.has("workspaceFile") && tsObj.get("workspaceFile").isTextual()
                    && !tsObj.get("workspaceFile").asText().isBlank())) {
                Map<String, String> files = TechSpecWorkspace.readAllowedFiles(wsDir, requested);
                ObjectNode fileNode = objectMapper.createObjectNode();
                ArrayNode fileList = objectMapper.createArrayNode();
                files.forEach((path, content) -> {
                    fileNode.put(path, content);
                    fileList.add(path);
                });
                tsObj.set("tech_spec_files", fileNode);
                tsObj.set("files", fileList);
                tsObj.put("workspaceFile", TechSpecWorkspace.README);
                tsObj.put("architectureMarkdown", TechSpecWorkspace.architectureMarkdown(files));
                tsObj.set("databaseTables", TechSpecWorkspace.assembleTables(files));
                tsObj.set("apiEndpoints", TechSpecWorkspace.assembleApis(files));
                ArrayNode assembledTasks = TechSpecWorkspace.assembleTasks(files);
                if (assembledTasks.isEmpty()) {
                    tsObj.remove("tasks");
                    tsObj.remove(List.of("totalTasks", "frontendTasks", "backendTasks", "middlewareTasks", "totalDevDays"));
                } else {
                    tsObj.set("tasks", assembledTasks);
                }
            }
            tsObj.remove(List.of("partIndex", "totalParts", "isFinal"));
        }

        resultValidator.validate(mode, finalPayload);
        Instant now = Instant.now();
        ResultEntity result = new ResultEntity();
        result.setId(Ids.next("res_"));
        result.setRunId(runId);
        result.setMode(mode);
        result.setSummary(resultValidator.summary(finalPayload));
        result.setPayload(finalPayload.toString());
        result.setCreatedAt(now);
        resultMapper.insert(result);
        run.setStatus("SUCCEEDED");
        run.setStage("SUBMITTING");
        run.setFinishedAt(now);
        run.setUpdatedAt(now);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        runMapper.update(null, new LambdaUpdateWrapper<RunEntity>()
                .eq(RunEntity::getId, runId)
                .eq(RunEntity::getStatus, "RUNNING")
                .set(RunEntity::getStatus, "SUCCEEDED")
                .set(RunEntity::getStage, "SUBMITTING")
                .set(RunEntity::getFinishedAt, now)
                .set(RunEntity::getUpdatedAt, now)
                .set(RunEntity::getErrorCode, null)
                .set(RunEntity::getErrorMessage, null));
        if (telemetryService != null) {
            long duration = run.getStartedAt() != null ? java.time.Duration.between(run.getStartedAt(), now).toMillis() : 0L;
            telemetryService.recordRun(run, "FINISH_RUN", duration);
        }
        return true;
    }

    public Map<String, Object> writeWorkspaceFile(String runId, String rawFilePath, String content) {
        RunEntity run = requireRun(runId);
        String filePath = PocWorkspace.normalizeRelativePath(rawFilePath);
        if ("DEMO".equals(run.getMode())) {
            if (!PocWorkspace.isDemoAllowedPath(filePath)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Demo 沙箱只允许写入 index.html");
            }
        } else if ("POC".equals(run.getMode())) {
            if (!PocWorkspace.isPocAllowedPath(filePath)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "POC 沙箱只允许 index.html、css/*.css、js/**/*.js、data/*.json");
            }
        } else if ("GENERATE".equals(run.getMode())) {
            if (!PrdWorkspace.isPrdAllowedPath(filePath)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "PRD 沙箱只允许 README.md、*.md、modules/*.md、data/*.json");
            }
        } else if ("TEST_CASE".equals(run.getMode())) {
            if (!TestCaseWorkspace.isAllowedPath(filePath)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "用例沙箱只允许 README.md、modules/*.json、data/*.json");
            }
        } else if ("TECH_SPEC".equals(run.getMode())) {
            if (!TechSpecWorkspace.isAllowedPath(filePath)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "技术方案沙箱只允许 README.md、architecture.md、*.md、modules/*、data/*.json");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "当前模式不允许写入工作区文件");
        }
        String body = content == null ? "" : content;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if ("POC".equals(run.getMode()) && bytes.length > PocWorkspace.MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE", "POC 单文件不能超过 100KB");
        }
        if ("GENERATE".equals(run.getMode()) && bytes.length > PrdWorkspace.MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE", "PRD 单文件不能超过 200KB");
        }
        if ("TEST_CASE".equals(run.getMode()) && bytes.length > TestCaseWorkspace.MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE", "用例单文件不能超过 200KB");
        }
        if ("TECH_SPEC".equals(run.getMode()) && bytes.length > TechSpecWorkspace.MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE", "技术方案单文件不能超过 200KB");
        }
        Path wsDir = properties.uploadDir().resolve("workspaces").resolve(runId).toAbsolutePath().normalize();
        Path target = wsDir.resolve(filePath).toAbsolutePath().normalize();
        if (!target.startsWith(wsDir)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SECURITY_ERROR", "非法文件路径");
        }
        boolean requiredLocked = PocWorkspace.REQUIRED_MODULES.equals(filePath)
                || TestCaseWorkspace.REQUIRED_MODULES.equals(filePath)
                || TechSpecWorkspace.REQUIRED_MODULES.equals(filePath);
        if (requiredLocked
                && ("POC".equals(run.getMode()) || "TEST_CASE".equals(run.getMode()) || "TECH_SPEC".equals(run.getMode()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_MODULES_LOCKED",
                    "data/required-modules.json 由控制面按当前稿 catalog 预置，禁止覆盖");
        }
        PocWorkspace.rejectViteSkeleton(filePath, body);
        if ("POC".equals(run.getMode())) {
            enforceTotalBytes(wsDir, PocWorkspace.listRelativeFiles(wsDir), filePath, bytes.length, PocWorkspace.MAX_TOTAL_BYTES, "POC");
        }
        if ("GENERATE".equals(run.getMode())) {
            enforceTotalBytes(wsDir, PrdWorkspace.listRelativeFiles(wsDir), filePath, bytes.length, PrdWorkspace.MAX_TOTAL_BYTES, "PRD");
        }
        if ("TEST_CASE".equals(run.getMode())) {
            enforceTotalBytes(wsDir, TestCaseWorkspace.listRelativeFiles(wsDir), filePath, bytes.length, TestCaseWorkspace.MAX_TOTAL_BYTES, "用例");
        }
        if ("TECH_SPEC".equals(run.getMode())) {
            enforceTotalBytes(wsDir, TechSpecWorkspace.listRelativeFiles(wsDir), filePath, bytes.length, TechSpecWorkspace.MAX_TOTAL_BYTES, "技术方案");
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, body, StandardCharsets.UTF_8);
            long size = Files.size(target);
            return Map.of("success", true, "filePath", filePath, "size", size);
        } catch (IOException e) {
            log.error("Failed to write workspace file for run {}", runId);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "写入工作区文件失败");
        }
    }

    private void enforceTotalBytes(Path wsDir, List<String> existingPaths, String filePath, long newBytes, long maxTotal, String label) {
        long existing = 0;
        if (Files.isDirectory(wsDir)) {
            for (String existingPath : existingPaths) {
                if (existingPath.equals(filePath)) {
                    continue;
                }
                Path existingFile = wsDir.resolve(existingPath);
                try {
                    existing += Files.size(existingFile);
                } catch (IOException ignored) {
                    // size is re-checked on submit
                }
            }
        }
        if (existing + newBytes > maxTotal) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE",
                    label + "工作区合计不能超过 " + (maxTotal / (1024 * 1024)) + "MB");
        }
    }

    public Map<String, Object> listWorkspaceFiles(String runId) {
        RunEntity run = requireRun(runId);
        Path wsDir = properties.uploadDir().resolve("workspaces").resolve(runId).toAbsolutePath().normalize();
        List<String> files;
        if ("POC".equals(run.getMode())) {
            files = PocWorkspace.listRelativeFiles(wsDir);
        } else if ("DEMO".equals(run.getMode())) {
            Path index = wsDir.resolve(PocWorkspace.INDEX);
            files = Files.isRegularFile(index) ? List.of(PocWorkspace.INDEX) : List.of();
        } else if ("GENERATE".equals(run.getMode())) {
            files = PrdWorkspace.listRelativeFiles(wsDir);
        } else if ("TEST_CASE".equals(run.getMode())) {
            files = TestCaseWorkspace.listRelativeFiles(wsDir);
        } else if ("TECH_SPEC".equals(run.getMode())) {
            files = TechSpecWorkspace.listRelativeFiles(wsDir);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "当前模式没有工作区文件");
        }
        return Map.of("success", true, "files", files);
    }

    public Map<String, Object> readWorkspaceFile(String runId, String rawFilePath) {
        RunEntity run = requireRun(runId);
        String filePath = PocWorkspace.normalizeRelativePath(rawFilePath);
        if ("DEMO".equals(run.getMode()) && !PocWorkspace.isDemoAllowedPath(filePath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Demo 沙箱只允许读取 index.html");
        }
        if ("POC".equals(run.getMode()) && !PocWorkspace.isPocAllowedPath(filePath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 沙箱不允许读取该路径");
        }
        if ("GENERATE".equals(run.getMode()) && !PrdWorkspace.isPrdAllowedPath(filePath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "PRD 沙箱不允许读取该路径");
        }
        if ("TEST_CASE".equals(run.getMode()) && !TestCaseWorkspace.isAllowedPath(filePath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "用例沙箱不允许读取该路径");
        }
        if ("TECH_SPEC".equals(run.getMode()) && !TechSpecWorkspace.isAllowedPath(filePath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "技术方案沙箱不允许读取该路径");
        }
        if (!Set.of("DEMO", "POC", "GENERATE", "TEST_CASE", "TECH_SPEC").contains(run.getMode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "当前模式不允许读取工作区文件");
        }
        Path wsDir = properties.uploadDir().resolve("workspaces").resolve(runId).toAbsolutePath().normalize();
        Path target = wsDir.resolve(filePath).toAbsolutePath().normalize();
        if (!target.startsWith(wsDir)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SECURITY_ERROR", "非法文件路径");
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "工作区文件不存在: " + rawFilePath);
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            return Map.of("success", true, "filePath", filePath, "content", content);
        } catch (IOException e) {
            log.error("Failed to read workspace file for run {}", runId);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "读取工作区文件失败");
        }
    }

    private void execute(String runId) {
        RunEntity run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        Instant started = Instant.now();
        int startedUpdated = runMapper.update(null, new LambdaUpdateWrapper<RunEntity>()
                .eq(RunEntity::getId, runId)
                .eq(RunEntity::getStatus, "PENDING")
                .set(RunEntity::getStatus, "RUNNING")
                .set(RunEntity::getStage, "READING")
                .set(RunEntity::getStartedAt, started)
                .set(RunEntity::getUpdatedAt, started));
        if (startedUpdated <= 0) {
            RunEntity current = runMapper.selectById(runId);
            if (current == null || !"RUNNING".equals(current.getStatus())) {
                return;
            }
        }
        run.setStatus("RUNNING");
        run.setStage("READING");
        run.setStartedAt(started);
        run.setUpdatedAt(started);
        String hermesRunId = null;
        try {
            if ("POC".equals(run.getMode()) || "TEST_CASE".equals(run.getMode()) || "TECH_SPEC".equals(run.getMode())) {
                seedRequiredModules(run);
            }
            String input = buildInput(run);
            hermesRunId = hermesRunsClient.createRun("prd-run-" + run.getId(), input);
            run.setHermesRunId(hermesRunId);
            run.setStage("ANALYZING");
            run.setUpdatedAt(Instant.now());
            int analyzingUpdated = runMapper.update(null, new LambdaUpdateWrapper<RunEntity>()
                    .eq(RunEntity::getId, runId)
                    .eq(RunEntity::getStatus, "RUNNING")
                    .set(RunEntity::getHermesRunId, hermesRunId)
                    .set(RunEntity::getStage, "ANALYZING")
                    .set(RunEntity::getUpdatedAt, Instant.now()));
            if (analyzingUpdated <= 0) {
                RunEntity current = runMapper.selectById(runId);
                if (current != null && ("SUCCEEDED".equals(current.getStatus()) || "FAILED".equals(current.getStatus()))) {
                    return;
                }
            }
            pollUntilDone(runId, hermesRunId, started, run);
        } catch (ApiException ex) {
            stopHermesQuietly(hermesRunId);
            fail(runId, ex.code(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("Run {} failed type={}", runId, ex.getClass().getSimpleName());
            stopHermesQuietly(hermesRunId);
            fail(runId, "HERMES_UNAVAILABLE", "分析服务运行失败");
        }
    }

    void pollUntilDone(String runId, String hermesRunId, Instant started, RunEntity run) throws InterruptedException {
        Duration timeout = QualityOptions.timeout(run.getCompleteness(), run.getMode(), properties);
        long deadline = started.toEpochMilli() + timeout.toMillis();
        while (Instant.now().toEpochMilli() < deadline) {
            RunEntity current = runMapper.selectById(runId);
            if (current == null || "SUCCEEDED".equals(current.getStatus()) || "FAILED".equals(current.getStatus())) {
                return;
            }
            JsonNode hermes = hermesRunsClient.getRun(hermesRunId);
            String status = hermes.path("status").asText("");
            if ("waiting_for_approval".equals(status)) {
                stopHermesQuietly(hermesRunId);
                fail(runId, "HERMES_UNAVAILABLE", "分析服务等待人工审批，当前不支持该交互");
                return;
            }
            if ("completed".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                String error = hermes.path("error").asText("");
                String mode = current != null ? current.getMode() : run.getMode();
                settleFinishedRun(runId, status, error, mode);
                return;
            }
            Thread.sleep(2000);
        }
        if (findResult(runId) == null) {
            log.warn("Run {} exceeded workbench timeout, stopping Hermes run {}", runId, hermesRunId);
            stopHermesQuietly(hermesRunId);
            fail(runId, "HERMES_TIMEOUT", "分析未在时限内完成");
        } else {
            markSucceeded(runId);
        }
    }

    /**
     * Hermes 侧已结束时收敛本地状态。结果已落库但 Run 仍非终态，说明
     * {@code acceptResult} 的状态更新丢失，此处补偿，否则 Run 会永久停在 RUNNING。
     */
    private void settleFinishedRun(String runId, String hermesStatus, String detailError, String mode) {
        if (findResult(runId) != null) {
            markSucceeded(runId);
            return;
        }

        String errorCode = switch (hermesStatus) {
            case "completed" -> "HERMES_NO_STRUCTURED_RESULT";
            case "failed" -> {
                if (detailError != null && (detailError.contains("truncated") || detailError.contains("output length limit"))) {
                    yield "HERMES_TOKEN_LIMIT_EXCEEDED";
                }
                yield "HERMES_FAILED";
            }
            case "cancelled" -> "HERMES_CANCELLED";
            default -> "HERMES_TERMINAL_WITHOUT_RESULT";
        };
        String errorMessage = switch (hermesStatus) {
            case "completed" -> "分析服务已完成，但未提交结构化结果";
            case "failed" -> {
                if ("HERMES_TOKEN_LIMIT_EXCEEDED".equals(errorCode)) {
                    if ("POC".equals(mode)) {
                        yield "分析服务单次输出触达模型 Token 上限，通常是某一个 POC 文件写得太大被截断。POC 不能用 PRD 分片提交。请手动重跑；按文件拆写（共享台账放 js/ui.js，每个视图只写 catalog 配置），不要把整份 POC 塞进一次工具调用，也不要砍菜单覆盖。";
                    }
                    if ("GENERATE".equals(mode)) {
                        yield "分析服务单次输出触达模型 Token Limit 上限，通常是某一个 PRD 文件写得太大被截断。GENERATE 不能用分片 submit_result。请手动重跑；先写 catalog，再一文件一次 write_prd_file，不要一次吐整本，也不要砍模块覆盖。";
                    }
                    yield "分析服务生成内容超出模型单次输出上限(Token Limit)，已自动拦截；请按文件拆写后手动重跑，不要一次吐整本";
                }
                yield "分析服务执行失败，且未提交结构化结果";
            }
            case "cancelled" -> "分析服务已取消，且未提交结构化结果";
            default -> "分析服务已结束，但未提交结构化结果";
        };
        fail(runId, errorCode, errorMessage);
    }

    /** 仅在 Run 仍为非终态时收敛为 SUCCEEDED；已是终态则不动，避免覆盖。 */
    private void markSucceeded(String runId) {
        RunEntity run = runMapper.selectById(runId);
        if (run == null || "SUCCEEDED".equals(run.getStatus()) || "FAILED".equals(run.getStatus())) {
            return;
        }
        Instant now = Instant.now();
        int updated = runMapper.update(null, new LambdaUpdateWrapper<RunEntity>()
                .eq(RunEntity::getId, runId)
                .in(RunEntity::getStatus, List.of("PENDING", "RUNNING"))
                .set(RunEntity::getStatus, "SUCCEEDED")
                .set(RunEntity::getStage, "SUBMITTING")
                .set(RunEntity::getFinishedAt, now)
                .set(RunEntity::getUpdatedAt, now)
                .set(RunEntity::getErrorCode, null)
                .set(RunEntity::getErrorMessage, null));
        if (updated <= 0) {
            return;
        }
        log.warn("Run {} had a persisted result but was still active; converged to SUCCEEDED", runId);
        if (telemetryService != null) {
            RunEntity settled = runMapper.selectById(runId);
            if (settled != null) {
                long duration = settled.getStartedAt() != null
                        ? Duration.between(settled.getStartedAt(), now).toMillis()
                        : 0L;
                telemetryService.recordRun(settled, "FINISH_RUN", duration);
            }
        }
    }

    void stopHermesQuietly(String hermesRunId) {
        if (hermesRunId == null || hermesRunId.isBlank()) {
            return;
        }
        try {
            hermesRunsClient.stopRun(hermesRunId);
        } catch (RuntimeException ex) {
            log.warn("Failed to stop Hermes run {} type={}", hermesRunId, ex.getClass().getSimpleName());
        }
    }

    private void fail(String runId, String code, String message) {
        RunEntity run = runMapper.selectById(runId);
        if (run == null || "SUCCEEDED".equals(run.getStatus()) || "FAILED".equals(run.getStatus())) {
            return;
        }
        Instant now = Instant.now();
        run.setStatus("FAILED");
        run.setErrorCode(code);
        run.setErrorMessage(message);
        run.setFinishedAt(now);
        run.setUpdatedAt(now);
        runMapper.update(null, new LambdaUpdateWrapper<RunEntity>()
                .eq(RunEntity::getId, runId)
                .in(RunEntity::getStatus, List.of("PENDING", "RUNNING"))
                .set(RunEntity::getStatus, "FAILED")
                .set(RunEntity::getErrorCode, code)
                .set(RunEntity::getErrorMessage, message)
                .set(RunEntity::getFinishedAt, now)
                .set(RunEntity::getUpdatedAt, now));
        if (telemetryService != null) {
            long duration = run.getStartedAt() != null ? java.time.Duration.between(run.getStartedAt(), now).toMillis() : 0L;
            telemetryService.recordRun(run, "FAIL_RUN", duration);
        }
    }

    String buildInput(RunEntity run) {
        String instruction = userInstruction(run.getInstruction());
        if (instruction.isBlank()) {
            instruction = "按材料自主完成任务。";
        }
        String quality = QualityOptions.promptBlock(
                run.getMode(),
                QualityOptions.normalizeCompleteness(run.getCompleteness()),
                QualityOptions.splitLenses(run.getLenses()),
                run.getParentRunId()
        );
        if ("REVIEW".equals(run.getMode())) {
            return """
                    这是一个 PRD 评审任务。
                    workbenchRunId=%s
                    mode=REVIEW
                    用户关注点：%s

                    %s

                    工具参数 runId 必须原样使用 workbenchRunId，不要使用 Hermes 自己的 run_id。
                    请先调用 list_sources(runId=workbenchRunId)，再对每个 sourceId 调用 read_source，直到 nextCursor 为空。
                    完成后调用 submit_result(runId=workbenchRunId, mode=REVIEW, payload)。
                    payload 必须包含 mode、title、summary、blockingIssues。
                    不要把完整结果只写在最终聊天回复里。
                    """.formatted(run.getId(), instruction, quality);
        }
        if ("DEMO".equals(run.getMode())) {
            String skillId = skillFromInstruction(run.getInstruction());
            String skillPrompt = DemoSkills.promptFor(skillId);
            String packageRules = "单文件 HTML 为交付物。package_files 可省略；如提交，只接受安全相对路径的 UTF-8 文本，不承诺工程可编译或宿主集成。";
            return """
                    这是一个 PRD Demo 生成任务。
                    workbenchRunId=%s
                    mode=DEMO
                    skill=%s
                    用户要求：%s

                    %s

                    必须遵循下面的 skill 做页面风格设计：
                    %s

                    %s

                    工具参数 runId 必须原样使用 workbenchRunId，不要使用 Hermes 自己的 run_id。
                    请先调用 list_sources(runId=workbenchRunId)，再对每个 sourceId 调用 read_source，直到 nextCursor 为空。
                    根据材料中的 PRD 生成一个可本地双击打开的单页 HTML 原型。
                    HTML 必须是完整文档：包含 <!DOCTYPE html>、<html>、<head>（charset=utf-8 与 viewport）、<body> 以及闭合的 </html>。
                    CSS 和必要脚本全部内联，不依赖后端接口；字体使用系统字体栈，关键内容离线可见。
                    【页面设计与交互范围】：视觉 Token、页面骨架和 CRUD 细节以当前 skill 为唯一规范，不在通用 Prompt 中重复注入第二套视觉说明。根据用户要求和材料选择能形成完整演示链路的核心与代表性模块，不要求覆盖 PRD 全目录，也不设置固定菜单数或视图数；凡出现在侧栏、Tab、表格操作列中的入口都必须真实可点击并产生可观察的界面变化。
                    【稳定性与防白屏关键规则】：
                    1. 【静态 DOM 优先保底】：所有页面骨架与核心数据表格必须直接在 <body> 内部的静态 <tbody> 标签中直接输出 3~5 行包含真实字段的 <tr> 数据行！严禁留空 <tbody id='tb'></tbody> 纯靠 JS 动态拼接，首屏离线必须直接可读、立即可见数据！
                    2. 【侧边栏菜单独立高亮（严禁菜单黏连与反查跳回）】：点击侧栏菜单时，必须且仅能给当前被点击的单个 .menu 元素添加 .on 类名（先移除全部 .menu 的 .on，再对当前点击的菜单添加 .on）！严禁使用 querySelectorAll('[data-v="..."]') 批量设置 classList.add('on')，否则所有次要菜单会同时高亮连在一起！严禁在切换函数中通过 document.querySelector('.menu[data-view="'+v+'"]') 进行反查加高亮！因为当多个次要菜单共享通用视图时，querySelector 永远只会返回第 1 个菜单，导致点击其他菜单时高亮被强行弹回第 1 项！标准做法是在点击事件中直接针对触发事件的当前 menu 元素添加 .on（如 clickedMenu.classList.add('on')）。点击后必须先给目标 .view 加上可见 class，再调用渲染函数；渲染失败不得让主区域停在旧页面。禁止对仍包含带 id 子节点的标题容器执行 innerHTML 整段覆盖后再 getElementById 取该子节点（例如先覆盖 #ledgerTitle 会销毁 #ledgerSub，随后读取 textContent 抛错，表现为点击菜单后数据不变）。改标题用 textContent。多个菜单共享同一 data-view 时必须读取 data-mod 并替换表头与行数据。
                    3. 【严禁未定义方法调用（如 r.html）】：若封装表格渲染函数 rows(arr, s, fn)，必须显式将行渲染函数作为参数传入并在循环中调用 fn(i)，严禁臆想数据对象上有方法（严禁写 r.html(i)！数据对象没有该方法，会导致整页脚本运行时崩溃、所有列表页面空白）！
                    4. 【模态框与新增交互必闭合】：主界面上的每一个【+ 新增】按钮必须在静态 HTML 中对应一个真实的模态框容器（<div class='dlg' id='dlgX'>）与表单字段，包含保存按钮与追加数据逻辑，严禁只弹 Toast 轻提示应付用户！
                    5. 【严禁手写复杂算法】：金额大写等业务字段直接写预置示例文本（如“人民币肆万伍仟元整”），严禁在内联脚本中手写复杂的转换算法，避免消耗 Token 造成截断或语法错误。
                    6. 必须包含完整闭合的 </html> 标签，submit_result 提交前确保 HTML 文档完全闭合且内联脚本无语法错误与运行时崩溃。
                    界面文案跟随材料语言。不要在工作台里执行这段 HTML，只提交 JSON。
                    只做主路径和必要导航，不要把全部模块、全部状态或 PRD 原文铺成巨型单页。
                    【Token 预算与防截断原则】：
                    1. 优先完成核心主路径，再按剩余预算补充代表性次要模块；不设置固定菜单、视图或字符数量，避免为了凑数牺牲交互完整性。
                    2. 核心单据做深做透，次要模块可复用统一紧凑台账与轻量模态框函数，避免重复编写大段 HTML/JS。
                    3. 严禁内联大型 SVG、Base64 或海量纯文本介绍；服务端仍执行统一结果大小上限，生成内容应为完成主路径所需的最小充分集合。
                    4. scriptMarkdown 建议只写核心演练步骤，服务端最多接受 30000 字符，缺少该字段不影响提交。
                    5. submit_result 的工具参数必须闭合可解析；禁止在 <script> 或 HTML 中书写会破坏 JSON 的裸反斜杠正则。
                    【侧边栏多模块切换与动态字段原则 - 少而完整，Schema 驱动】：
                    1. 侧边栏只列出本次能完整实现的菜单，且每项必须支持真实点击切换；未实现的模块直接省略，严禁输出 Toast 占位或不可点击的死菜单。
                    2. 代表性次要模块可复用一个通用台账容器，并在 <script> 中声明轻量元数据字典 MODULE_CONFIGS = { '模块名': { cols: [...], fields: [...], rows: [...] } }：
                       - cols: 对应 PRD 中该模块业务台账的真实列名（例如用章：印章名称、印章类型、保管人、在用状态、外带状态；宿舍：宿舍房号、房型类别、总床位、已住人数、状态；会议：会议室、容纳人数、预约时段、发起人；应急：项目名称、隐患等级、责任人、整改时限）；
                       - fields: 点击【+ 新增】时弹窗动态渲染的输入控件（包含 label、type: 'text'/'select'/'date'、opts、req 等）；
                       - rows: 2~3 条贴合该业务模块真实场景的示例数据。
                    3. 【动态切换与表单生成逻辑】：
                       - 当用户点击任一次要菜单时，直接高亮当前菜单项，先显示目标 .view，再按 data-mod 渲染通用紧凑台账；
                       - 动态更新当前模块标题时只改 textContent，不要 innerHTML 覆盖仍包含 id 的子节点；
                       - 动态将 MODULE_CONFIGS[title].cols 组装成 <th>，将 rows 组装成 <tr>，严禁所有模块展示同一份与业务无关的死数据！
                       - 点击【+ 新增】时，读取当前模块的 fields 动态填充弹窗的表单控件，严禁使用“名称/编号/负责人”一套写死字段敷衍所有业务模块！保存时将新记录动态插入表格首行并提示成功！
                    4. 【提交前动作闭环核对】：禁止保留 __SEED_*__、__TODO_*__、__PLACEHOLDER_*__ 等占位符；逐项枚举 HTML 中每个 data-act/data-action 值，确保底部 click 事件委托中存在同名处理分支且点击后会改变界面；逐项核对 oninput/onchange/onclick 引用的函数已经定义。
                    【内联脚本无语法错误铁律 - 确保全页面点击可用】：
                    提交前必须严格自检内联脚本语法（括号 ()、花括号 {}、中括号 [] 必须完全配对且成对闭合；分号位置正确；字符串引号闭合无误）。严禁出现 SyntaxError 或运行时 TypeError，任何错误都会导致浏览器直接终止执行整个脚本，导致所有菜单和按钮全部瘫痪无法点击！
                    【HTML与JS双引号绝对禁忌（极重要，严防 JSON 解析崩溃）】：
                    整个 HTML 是作为 submit_result 工具调用的 JSON 字符串输出的，任何未转义的双引号（"）都会导致整个 JSON 解析崩溃：
                    1. 【HTML属性统一使用单引号】：所有 HTML 标签属性一律使用单引号包裹，如 <div class='header' id='main'>、<input type='text'>、<button class='btn'>。
                    2. 【严禁行内写带双引号的 JS 传参】：严禁在 HTML 属性中写 onclick='foo("bar")' 或 getElementById("id")，行内代码若需传参必须使用单引号（onclick="foo('bar')"）或纯数字；
                    3. 【JS代码统一使用单引号或模板字符串】：底部 <script> 中定义字符串变量或选择器时，一律使用单引号或反引号（如 'str'、`str`），严禁在 JS 中写裸双引号；
                    4. 【事件绑定推荐】：推荐将所有交互逻辑写在底部 <script> 中，使用事件委托或 document.getElementById('id').addEventListener 绑定事件，避免行内写复杂 JS。
                    【JS对象键名与变量名严禁裸连字符(-)】：JS 中对象字面量键名若包含连字符（如 'view-xxx'、'data-id' 等），必须使用单/双引号包裹（如 {'view-xxx': ...}，严禁漏写引号导致 SyntaxError: Unexpected token '-'）；变量名/函数参数名严禁包含连字符（必须使用小驼峰或下划线）。
                    禁止在单引号拼接的 innerHTML 里写 onclick="fn('参数')" 或 onchange="fn('参数')"，这会截断 JS 字符串，整页脚本无法运行。
                    交互用 document 事件委托读取 data-*，或使用模板字符串后再 addEventListener；初始 HTML 就要包含可点击的主路径控件，不要把全部界面只靠一段会报错的 innerHTML 注入。
                    【全新自主闭环交互流程 - 沙箱文件写入与自检自愈（极重要）】：
                    1. 编写单页完整 HTML 原型后，先调用 write_demo_file(runId=workbenchRunId, filePath="index.html", content=...) 将完整自包含 HTML 写入独立工作区沙箱。
                       【一次写完、防截断】：content 必须在同一次调用内写完，并以 </html> 闭合。建议整页不超过 25000 字符。模块过多时删掉次要 MODULE_CONFIGS，只保留一条可点击主路径。若工具返回 truncated，不要原样重试同一大页，先缩小再写。
                       【红线禁令】：沙箱专属用于单文件原型 index.html 的运行与测试！严禁调用 write_demo_file 创建 package.json、vite.config.ts、tsconfig.json、main.ts 或 src/ 目录散件！严禁将 index.html 覆盖为空挂载点（如 <div id='app'></div><script type='module' src='/src/main.ts'></script>）！
                    2. 调用 verify_demo(runId=workbenchRunId, filePath="index.html") 执行有限自动化诊断，检查标签闭合、占位符、JavaScript 语法、已声明动作的事件绑定和部分运行时错误；它不构成所有业务按钮、状态流转或浏览器兼容性的完整行为证明。
                    3. 首次诊断失败后最多进行两次定向修复：每次只修 errors 指出的具体问题，重新 write_demo_file 后再 verify_demo。第三次诊断仍失败时停止修复和提交，让本次 Run 明确失败并由用户手动重跑，严禁继续循环。
                    4. 【诊断通过后极简交付（严防超时）】：一旦 verify_demo 返回 passed=true，在同一轮次中立即且仅调用 submit_result(runId=workbenchRunId, mode=DEMO, payload) 提交结果！
                       严禁在自检通过后再发起任何多余思考、额外轮次或多余文件写入！
                    payload 必须包含 mode、title、summary、workspaceFile="index.html"，建议带 skill 字段；不要同时提交旧的内联 html。
                    可选增加 scriptMarkdown 字段（最多 30000 字符，建议简述 3~5 步演练路径），缺少该字段不影响提交。
                    控制面会在提交阶段重新读取并校验 workspaceFile 指向的最新文件，再保存为 html；不要只把结果放在聊天回复里。
                    """.formatted(run.getId(), skillId, instruction, quality, skillPrompt, packageRules);
        }
        if ("POC".equals(run.getMode())) {
            String skillPrompt = PocSkills.prompt();
            return """
                    这是一个静态多文件 POC 演示系统生成任务，不是单文件 Demo，也不是 Vue 工程。
                    workbenchRunId=%s
                    mode=POC
                    用户要求：%s

                    %s

                    Skill 约束：
                    %s

                    工具参数 runId 必须原样使用 workbenchRunId。
                    请先调用 list_sources(runId=workbenchRunId)，再对每个 sourceId 调用 read_source，直到 nextCursor 为空。
                    只使用 write_poc_file / read_poc_file / list_poc_files / verify_poc / submit_result。不要调用 write_demo_file 或 verify_demo。
                    沙箱只允许 index.html、css/*.css、js/**/*.js、data/*.json。禁止 package.json、src/、.env、Vue、CDN、fetch、import 模块。
                    必须做成 hash 路由 SPA。若工作区已有 data/required-modules.json，先 read_poc_file 读取，按其中 key/title/core 写 data/catalog.json，禁止覆盖 required-modules.json，禁止另起 platforms/subjects 这类菜单。
                    先写 data/catalog.json 作为唯一事实源（列、字段、状态、动作只来自材料），再写 mock/store/router/ui，再按 catalog 写视图。
                    未列入 catalog 的模块禁止进侧栏；列入的必须可点。禁止万能列名（仅名称/编号/负责人）。
                    先切换视图再渲染标题；标题用 textContent，禁止 innerHTML 销毁子节点后再取 id。
                    切菜单前必须 view.innerHTML='' 再 Views[key].render(view)；禁止 if (!rootEl) appendChild 把页面叠在 #view 里。菜单 click 必须直接 render，不能只改 hash。
                    沙箱磁盘上限是单文件 100KB、工作区合计 5MB；模型单轮写入预算是 js/html/css 不超过 20000 字符。每次 write_poc_file 只写 1 个完整文件。
                    先写 js/ui.js 的 PocUI.ledger 工厂（中文 label、列表分页），每个 js/views/<key>.js 只写该模块 catalog 配置（必须带 labels），不要把整套 CRUD 复制进每个视图。
                    界面文案只用中文：fields 只作数据键，详情和表单必须用 labels，禁止把 id/period/status 等英文字段名画到页面上。每个列表必须分页（共 N 条 / 上一页）。
                    若 write_poc_file 返回 truncated，不要原样重试同一段 content，也不要从 catalog 删模块。POC 不能用 PRD 分片提交。
                    文件数量随 catalog 模块而定，不设 8～15 的固定个数。
                    全部文件写入完毕后，再调用 verify_poc 执行整体诊断与修复（最多定向修复两次）；严禁在文件尚未全部写完前提前调用 verify_poc，以免消耗修复额度。
                    自愈最多两次：verify_poc 失败后只修 errors 指出的问题。第三次仍失败则停止并让用户重跑。
                    诊断通过后立即 submit_result(runId=workbenchRunId, mode=POC, payload={"mode":"POC","title":"...","summary":"...","workspaceFile":"index.html","files":["index.html","css/app.css",...]})。
                    不要把文件正文放进 payload。可选 scriptMarkdown 按真实菜单写售前话术。
                    本任务可能需要 15～40 分钟；把时间用在写可点击页面上，不要反复空转。
                    """.formatted(run.getId(), instruction, quality, skillPrompt)
                    + pocCoveragePrompt(run);
        }
        if ("ESTIMATE".equals(run.getMode())) {
            return """
                    这是基于材料的工作量估算任务，不是经过认证的 FPA 测算或商业报价承诺。
                    workbenchRunId=%s
                    mode=ESTIMATE
                    用户关注点与要求：%s
                    %s
                    优先读取当前生成稿与估算补充材料。材料不足时说明假设和不确定性，不虚构团队效率或固定人天费率。
                    按材料真实模块拆解 PAGE、API 或 THIRD_PARTY_API，给出 devDays、testDays 和 description。
                    不限制条目总数，不套用固定硬件/行业价格。尊重用户提供的效率、范围和组织方式。
                    未指定范围时采用 development-self-test-v1 示例口径：只计算开发及研发自测，
                    不包含产品设计、运维、项目管理；在 summary 和 scopeNote 明确本次范围与排除项。
                    用户明确指定其他范围时遵循用户要求并记录口径。可以附加 assumptions 和 uncertainty 字段。
                    请先 list_sources，再 read_source 读取全部材料，最后 submit_result(runId=workbenchRunId, mode=ESTIMATE, payload)。
                    payload 包含 mode、title、summary、scopeNote、items。每个 item 包含 module、name、type、devDays、testDays。
                    可增加 subModule、detail、complexity、description、customHeaders、moduleSummaries；总数按明细求和，不输出私有推理。
                    """.formatted(run.getId(), instruction, quality);
        }
        if ("TEST_CASE".equals(run.getMode())) {
            return """
                    这是一个测试用例文档包生成任务，不是一次性塞进 submit_result 的压缩 JSON。
                    workbenchRunId=%s
                    mode=TEST_CASE
                    用户关注点与要求：%s

                    %s

                    工具参数 runId 必须原样使用 workbenchRunId。
                    请先调用 list_sources(runId=workbenchRunId)，再对每个 sourceId 调用 read_source，直到 nextCursor 为空。
                    只使用 write_test_case_file / read_test_case_file / list_test_case_files / verify_test_case / submit_result。
                    沙箱只允许 README.md、modules/<key>.json、data/*.json。禁止分片 submit_result。
                    若工作区已有 data/required-modules.json，先 read_test_case_file 读取，按其中 key/title/core 写 data/catalog.json，禁止覆盖 required-modules.json。
                    STANDARD/THOROUGH 必须覆盖清单全部一级模块；QUICK 可从清单挑 1 核心 + 最多 2 次要。
                    每个 catalog 模块一份 modules/<key>.json：{"module":"中文标题","items":[{caseId,module,subModule,title,type,priority,precondition,steps,testData,expectedResult,description},...]}。
                    覆盖五类：FUNCTION / BOUNDARY / EXCEPTION / PERMISSION / CONCURRENCY；priority 只用 P0/P1/P2。
                    不设 18～32 条硬顶。单文件 JSON ≤ 28000 字符；过大则拆模块或压缩 steps，不要砍模块。
                    有生成稿时必须以生成稿规格为准；无稿时按原始材料。
                    全部写完后 verify_test_case；通过后立即 submit_result(mode=TEST_CASE, payload={mode,title,summary,scopeNote,workspaceFile:"README.md",files:[...],totalCount,p0Count,p1Count,p2Count,...})。
                    不要把 items 正文塞进 payload，由控制面从 modules 组装。本任务可能需要 15～40 分钟。
                    """.formatted(run.getId(), instruction, quality)
                    + moduleCoveragePrompt(run);
        }
        if ("TECH_SPEC".equals(run.getMode())) {
            return """
                    这是一个可交付前后端与 DBA 的详细技术方案文档包，不是工单拆解，也不是一次性塞进 submit_result 的压缩 JSON。
                    workbenchRunId=%s
                    mode=TECH_SPEC
                    用户关注点与要求：%s

                    %s

                    工具参数 runId 必须原样使用 workbenchRunId。
                    请先调用 list_sources(runId=workbenchRunId)，再对每个 sourceId 调用 read_source，直到 nextCursor 为空。
                    只使用 write_tech_spec_file / read_tech_spec_file / list_tech_spec_files / verify_tech_spec / submit_result。
                    沙箱允许 README.md、architecture.md、根目录 *.md、modules/*、data/*.json。禁止分片 submit_result。
                    若工作区已有 data/required-modules.json，先读取并写 data/catalog.json 对齐模块 key，按其中模块写清架构/表/API，禁止覆盖 required-modules。
                    不要写 data/tasks.json，不要拆解研发工单、人天或排期。

                    交付完整性（缺一不可，须达到前后端可直接按方案实施的粒度）：
                    1) architecture.md：技术选型与分层职责、模块边界与依赖、关键单据/状态流转（可嵌闭合 mermaid）、前后端协作约定（鉴权、错误码、分页约定等）。禁止只有标题没有设计说明。
                    2) data/tables.json：覆盖范围内每个核心实体至少 1 张表；每张表必须含 tableName、tableComment、columns[{name,type,nullable,comment}]，关键表补 indexes；写清主键/唯一/外键意图。
                    3) data/apis.json：覆盖范围内每个核心写/读路径至少 1 个端点；每个端点必须含 method、path、summary、requestBody、responseBody、errorCodes（紧凑 JSON 字符串样例）。
                    4) 建议按模块补 modules/<key>.md：页面/接口对照、关键规则落地说明、异常与权限边界；超大内容拆 data/*-part*.json 或 modules/*.json，控制面会合并。

                    tables/apis 可以是数组，或 {"databaseTables"|"apiEndpoints":[...]}。
                    单文件 Markdown ≤ 18000 字符、JSON ≤ 28000 字符；过大则拆写，不要砍模块覆盖，也不要省略字段明细或契约样例。
                    有生成稿时必须基于生成稿模块与流程；无稿时按原始材料。
                    全部写完后 verify_tech_spec；通过后立即 submit_result(mode=TECH_SPEC, payload={mode,title,summary,workspaceFile:"README.md",files:[...]})。
                    不要把 architecture/tables/apis 正文塞进 payload，由控制面组装。本任务可能需要 15～40 分钟。
                    """.formatted(run.getId(), instruction, quality)
                    + moduleCoveragePrompt(run);
        }
        return """
                这是一个可开工 PRD 文档包生成任务，不是单篇压缩 Markdown，也不是技术方案或用例集。
                workbenchRunId=%s
                mode=GENERATE
                用户要求：%s

                %s

                Skill 约束：
                %s

                工具参数 runId 必须原样使用 workbenchRunId。
                请先调用 list_sources(runId=workbenchRunId)，再对每个 sourceId 调用 read_source，直到 nextCursor 为空。
                只使用 write_prd_file / read_prd_file / list_prd_files / verify_prd / submit_result。不要调用 write_demo_file、write_poc_file 或分片 submit_result。
                沙箱只允许 README.md、根目录 *.md、modules/*.md、data/*.json。
                必须先写 data/catalog.json 作为唯一事实源（modules[].key/title/core/pages/entities/states），再按章节和模块一文件一轮写入。
                固定章节：00-overview.md、01-ia.md、02-global.md、03-permissions.md、04-nfr.md、open-questions.md、README.md。
                每个 catalog 模块一份 modules/<key>.md，必须含页面清单、字段矩阵（| 字段名 | 控件类型 | 必填 | 校验 | 默认值/联动 |）、闭合 Mermaid、BR-、核心模块至少 3 条 AC-。
                完整度只缩小 catalog，不缩章节模板。禁止「保留模块」空章；做不完的写进本期不做并从 catalog 删除。
                沙箱磁盘上限是单文件 200KB、工作区合计 8MB；模型单轮写入预算是 Markdown 不超过 18000 字符。每次 write_prd_file 只写 1 个完整文件。
                若 write_prd_file 返回 truncated，不要原样重试同一段 content，也不要从 catalog 删模块。GENERATE 不能用 partIndex 分片提交。
                全部文件写入完毕后，再调用 verify_prd；严禁在文件尚未全部写完前提前调用 verify_prd。
                自愈最多两次：verify_prd 失败后只修 errors 指出的文件。第三次仍失败则停止并让用户重跑。
                诊断通过后立即 submit_result(runId=workbenchRunId, mode=GENERATE, payload={"mode":"GENERATE","title":"...","summary":"...","workspaceFile":"README.md","files":[...],"openQuestions":[...]})。
                不要把文件正文放进 payload。markdown 由控制面按 README 顺序组装。
                关键单据流转嵌入成对闭合的 ```mermaid 代码块（graph TD 或 stateDiagram）。
                本任务可能需要 20～60 分钟；把时间用在写可开工章节上，不要反复空转，也不要压成 15k 提纲。
                """.formatted(run.getId(), instruction, quality, PrdSkills.prompt());
    }

    private void seedRequiredModules(RunEntity run) {
        List<PrdModuleManifest.Module> modules = loadPrdModulesForTask(run.getTaskId());
        if (modules.isEmpty()) {
            return;
        }
        String coverage = QualityOptions.QUICK.equals(run.getCompleteness())
                ? PrdModuleManifest.COVERAGE_SUBSET
                : PrdModuleManifest.COVERAGE_ALL;
        String body = PrdModuleManifest.toJson(currentDraftRunId(run.getTaskId()), coverage, modules);
        Path wsDir = properties.uploadDir().resolve("workspaces").resolve(run.getId()).toAbsolutePath().normalize();
        Path target = wsDir.resolve(PocWorkspace.REQUIRED_MODULES).toAbsolutePath().normalize();
        if (!target.startsWith(wsDir)) {
            log.warn("{} {} refused seed required-modules outside workspace", run.getMode(), run.getId());
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, body, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("{} {} could not seed required-modules", run.getMode(), run.getId());
        }
    }

    private String moduleCoveragePrompt(RunEntity run) {
        return pocCoveragePrompt(run);
    }

    private String pocCoveragePrompt(RunEntity run) {
        List<PrdModuleManifest.Module> modules = loadPrdModulesForTask(run.getTaskId());
        if (modules.isEmpty()) {
            return """

                    【必须覆盖的一级模块】当前任务没有可用的当前稿 catalog。从材料自行抽出一级模块；STANDARD/THOROUGH 仍须覆盖材料中能闭环的全部一级菜单，禁止另起一套无关 IA。
                    """;
        }
        String coverage = QualityOptions.QUICK.equals(run.getCompleteness())
                ? PrdModuleManifest.COVERAGE_SUBSET
                : PrdModuleManifest.COVERAGE_ALL;
        return "\n" + PrdModuleManifest.promptBlock(modules, coverage);
    }

    private List<PrdModuleManifest.Module> loadPrdModulesForTask(String taskId) {
        String generateRunId = currentDraftRunId(taskId);
        if (generateRunId == null) {
            return List.of();
        }
        Path catalog = properties.uploadDir().resolve("workspaces").resolve(generateRunId)
                .resolve(PrdWorkspace.CATALOG).toAbsolutePath().normalize();
        if (Files.isRegularFile(catalog)) {
            try {
                List<PrdModuleManifest.Module> fromFile = PrdModuleManifest.parseCatalog(
                        Files.readString(catalog, StandardCharsets.UTF_8));
                if (!fromFile.isEmpty()) {
                    return fromFile;
                }
            } catch (IOException ignored) {
                // fall through to persisted payload
            }
        }
        ResultEntity result = findResult(generateRunId);
        if (result == null || result.getPayload() == null) {
            return List.of();
        }
        try {
            return PrdModuleManifest.parsePayload(objectMapper.readTree(result.getPayload()));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String currentDraftRunId(String taskId) {
        List<SourceEntity> ready = sourceMapper.selectList(new LambdaQueryWrapper<SourceEntity>()
                .eq(SourceEntity::getTaskId, taskId)
                .eq(SourceEntity::getStatus, "READY"));
        if (ready == null || ready.isEmpty()) {
            return null;
        }
        List<SourceEntity> drafts = ready.stream()
                .filter(source -> source.getOriginalName() != null
                        && source.getOriginalName().startsWith(SourceRoles.DRAFT_PREFIX))
                .toList();
        if (drafts.size() != 1) {
            return null;
        }
        return SourceRoles.draftRunId(drafts.getFirst().getOriginalName());
    }

    private String composeInstruction(String mode, String instruction, String skill) {
        String text = instruction == null ? "" : instruction.trim();
        if (!"DEMO".equals(mode)) {
            return text;
        }
        return "[skill:" + DemoSkills.normalize(skill) + "]\n" + text;
    }

    static String skillFromInstruction(String instruction) {
        if (instruction != null && instruction.startsWith("[skill:")) {
            int end = instruction.indexOf(']');
            if (end > 7) {
                return DemoSkills.normalize(instruction.substring(7, end));
            }
        }
        return DemoSkills.DEFAULT;
    }

    static String userInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return "";
        }
        if (instruction.startsWith("[skill:")) {
            int newline = instruction.indexOf('\n');
            return newline >= 0 ? instruction.substring(newline + 1).trim() : "";
        }
        return instruction.trim();
    }
}
