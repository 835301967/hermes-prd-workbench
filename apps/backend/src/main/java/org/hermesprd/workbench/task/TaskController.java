package org.hermesprd.workbench.task;

import org.hermesprd.workbench.estimate.EstimateExcelExporter;
import com.fasterxml.jackson.databind.JsonNode;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.hermes.HermesRunsClient;
import org.hermesprd.workbench.result.ResultEntity;
import org.hermesprd.workbench.task.ApiModels.CreateRunRequest;
import org.hermesprd.workbench.task.ApiModels.CreateTaskRequest;
import org.hermesprd.workbench.task.ApiModels.DraftSourceResponse;
import org.hermesprd.workbench.task.ApiModels.HealthResponse;
import org.hermesprd.workbench.task.ApiModels.RenameTaskRequest;
import org.hermesprd.workbench.task.ApiModels.ReviewFeedbackResponse;
import org.hermesprd.workbench.task.ApiModels.RunHistoryItem;
import org.hermesprd.workbench.task.ApiModels.RunResponse;
import org.hermesprd.workbench.task.ApiModels.SourceResponse;
import org.hermesprd.workbench.task.ApiModels.TaskResponse;
import org.hermesprd.workbench.task.ApiModels.TaskSummary;
import org.hermesprd.workbench.task.ApiModels.UpdateDraftSourceRequest;
import org.hermesprd.workbench.task.ApiModels.UpdateReviewFeedbackRequest;
import org.hermesprd.workbench.task.DemoSkills.SkillInfo;
import org.hermesprd.workbench.telemetry.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TaskController {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final TaskService taskService;
    private final RunService runService;
    private final ReviewFeedbackService reviewFeedbackService;
    private final HermesRunsClient hermesRunsClient;
    private final EstimateExcelExporter estimateExcelExporter;
    private final org.hermesprd.workbench.testcase.TestCaseExcelExporter testCaseExcelExporter;
    private final org.hermesprd.workbench.techspec.TechSpecMarkdownExporter techSpecMarkdownExporter;
    private final org.hermesprd.workbench.bundle.TaskBundleExporter taskBundleExporter;
    private final TelemetryService telemetryService;

    public TaskController(
            TaskService taskService,
            RunService runService,
            ReviewFeedbackService reviewFeedbackService,
            HermesRunsClient hermesRunsClient,
            EstimateExcelExporter estimateExcelExporter,
            org.hermesprd.workbench.testcase.TestCaseExcelExporter testCaseExcelExporter,
            org.hermesprd.workbench.techspec.TechSpecMarkdownExporter techSpecMarkdownExporter,
            org.hermesprd.workbench.bundle.TaskBundleExporter taskBundleExporter
    ) {
        this(taskService, runService, reviewFeedbackService, hermesRunsClient, estimateExcelExporter, testCaseExcelExporter, techSpecMarkdownExporter, taskBundleExporter, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TaskController(
            TaskService taskService,
            RunService runService,
            ReviewFeedbackService reviewFeedbackService,
            HermesRunsClient hermesRunsClient,
            EstimateExcelExporter estimateExcelExporter,
            org.hermesprd.workbench.testcase.TestCaseExcelExporter testCaseExcelExporter,
            org.hermesprd.workbench.techspec.TechSpecMarkdownExporter techSpecMarkdownExporter,
            org.hermesprd.workbench.bundle.TaskBundleExporter taskBundleExporter,
            TelemetryService telemetryService
    ) {
        this.taskService = taskService;
        this.runService = runService;
        this.reviewFeedbackService = reviewFeedbackService;
        this.hermesRunsClient = hermesRunsClient;
        this.estimateExcelExporter = estimateExcelExporter;
        this.testCaseExcelExporter = testCaseExcelExporter;
        this.techSpecMarkdownExporter = techSpecMarkdownExporter;
        this.taskBundleExporter = taskBundleExporter;
        this.telemetryService = telemetryService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        boolean hermes = hermesRunsClient.isAvailable();
        boolean database = false;
        try {
            database = Integer.valueOf(1).equals(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                    && "prd_workbench".equals(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
        } catch (Exception ignored) {
            // Health never exposes driver messages, credentials or topology.
        }
        return new HealthResponse(hermes && database ? "UP" : "DOWN", hermes ? "CONNECTED" : "UNAVAILABLE");
    }

    @GetMapping("/tasks")
    public List<TaskSummary> listTasks() {
        return taskService.listByUser(userId()).stream()
                .map(task -> new TaskSummary(task.getId(), task.getTitle(), task.getCreatedAt()))
                .toList();
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskEntity task = taskService.create(userId(), request.title());
        if (telemetryService != null) {
            telemetryService.recordEvent("TASK", "PROJECT", "CREATE_TASK", "SUCCESS", null, null, null, java.util.Map.of("taskId", task.getId()));
        }
        return toTask(task);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskResponse getTask(@PathVariable String taskId) {
        return toTask(taskService.requireOwned(userId(), taskId));
    }

    @PatchMapping("/tasks/{taskId}")
    public TaskResponse renameTask(@PathVariable String taskId, @Valid @RequestBody RenameTaskRequest request) {
        return toTask(taskService.rename(userId(), taskId, request.title()));
    }

    @GetMapping("/demo-skills")
    public List<SkillInfo> listDemoSkills() {
        return DemoSkills.catalog();
    }

    @GetMapping("/quality-options")
    public QualityOptions.Catalog listQualityOptions() {
        return QualityOptions.catalog();
    }

    @PostMapping("/tasks/{taskId}/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public SourceResponse upload(@PathVariable String taskId, @RequestPart("file") MultipartFile file) {
        SourceEntity source = taskService.upload(userId(), taskId, file);
        if (telemetryService != null) {
            telemetryService.recordEvent("SOURCE", "FILE", "UPLOAD_FILE", "SUCCESS", null, null, null,
                    java.util.Map.of("sourceId", source.getId(), "size", source.getSizeBytes(), "mediaType", source.getMediaType()));
        }
        return toSource(source);
    }

    @DeleteMapping("/tasks/{taskId}/sources/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSource(@PathVariable String taskId, @PathVariable String sourceId) {
        taskService.deleteSource(userId(), taskId, sourceId);
    }

    @GetMapping("/tasks/{taskId}/draft-sources/{sourceId}")
    public DraftSourceResponse getDraftSource(@PathVariable String taskId, @PathVariable String sourceId) {
        return taskService.getDraftSource(userId(), taskId, sourceId);
    }

    @PutMapping("/tasks/{taskId}/draft-sources/{sourceId}")
    public DraftSourceResponse updateDraftSource(
            @PathVariable String taskId,
            @PathVariable String sourceId,
            @Valid @RequestBody UpdateDraftSourceRequest request
    ) {
        return taskService.updateDraftSource(userId(), taskId, sourceId, request);
    }

    @PostMapping("/tasks/{taskId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunResponse createRun(@PathVariable String taskId, @Valid @RequestBody CreateRunRequest request) {
        return toRun(runService.create(userId(), taskId, request));
    }

    @GetMapping("/runs")
    public List<RunHistoryItem> listRuns(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String taskId
    ) {
        return runService.listHistory(userId(), mode, taskId);
    }

    @GetMapping("/runs/{runId}")
    public RunResponse getRun(@PathVariable String runId) {
        return toRun(runService.requireOwned(userId(), runId));
    }

    @GetMapping("/runs/{runId}/review-feedback")
    public ReviewFeedbackResponse getReviewFeedback(@PathVariable String runId) {
        return reviewFeedbackService.get(userId(), runId);
    }

    @PutMapping("/runs/{runId}/review-feedback")
    public ReviewFeedbackResponse updateReviewFeedback(
            @PathVariable String runId,
            @Valid @RequestBody UpdateReviewFeedbackRequest request
    ) {
        return reviewFeedbackService.update(userId(), runId, request);
    }

    @PostMapping("/runs/{runId}/confirm")
    public RunResponse confirm(@PathVariable String runId) {
        return toRun(runService.confirm(userId(), runId));
    }

    @PostMapping("/runs/{runId}/export-estimate")
    public org.springframework.http.ResponseEntity<byte[]> exportEstimate(
            @PathVariable String runId,
            @RequestBody(required = false) ApiModels.ExportEstimateRequest request
    ) {
        RunEntity run = runService.requireOwned(userId(), runId);
        if (!"ESTIMATE".equals(run.getMode())) {
            throw new org.hermesprd.workbench.shared.ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "只有工作量评估结果支持导出 Excel");
        }
        ResultEntity result = runService.findResult(run.getId());
        if (result == null || result.getPayload() == null) {
            throw new org.hermesprd.workbench.shared.ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "评估结果不存在");
        }
        JsonNode payload = runService.resultPayload(result);
        byte[] bytes = estimateExcelExporter.export(payload, request);
        if (telemetryService != null) {
            telemetryService.recordEvent("ESTIMATE", "EXPORT", "EXPORT_EXCEL", "SUCCESS", null, null, null, java.util.Map.of("runId", runId));
        }

        String title = (request != null && request.title() != null && !request.title().isBlank())
                ? request.title()
                : payload.path("title").asText("工作量评估与报价单");
        String filename = title.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + ".xlsx";
        String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/runs/{runId}/export-estimate")
    public org.springframework.http.ResponseEntity<byte[]> exportEstimateGet(
            @PathVariable String runId,
            @RequestParam(required = false) Double dailyRate,
            @RequestParam(required = false) String title
    ) {
        ApiModels.ExportEstimateRequest request = new ApiModels.ExportEstimateRequest(
                title, dailyRate, null, null, null
        );
        return exportEstimate(runId, request);
    }

    @PostMapping("/runs/{runId}/export-test-cases")
    public org.springframework.http.ResponseEntity<byte[]> exportTestCases(
            @PathVariable String runId,
            @RequestBody(required = false) ApiModels.ExportTestCaseRequest request
    ) {
        RunEntity run = runService.requireOwned(userId(), runId);
        if (!"TEST_CASE".equals(run.getMode())) {
            throw new org.hermesprd.workbench.shared.ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "只有测试用例结果支持导出 Excel");
        }
        ResultEntity result = runService.findResult(run.getId());
        if (result == null || result.getPayload() == null) {
            throw new org.hermesprd.workbench.shared.ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "测试用例结果不存在");
        }
        JsonNode payload = runService.resultPayload(result);
        byte[] bytes = testCaseExcelExporter.export(payload, request);
        if (telemetryService != null) {
            telemetryService.recordEvent("TEST_CASE", "EXPORT", "EXPORT_EXCEL", "SUCCESS", null, null, null, java.util.Map.of("runId", runId));
        }

        String title = (request != null && request.title() != null && !request.title().isBlank())
                ? request.title()
                : payload.path("title").asText("系统测试用例集");
        String filename = title.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + ".xlsx";
        String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/runs/{runId}/export-test-cases")
    public org.springframework.http.ResponseEntity<byte[]> exportTestCasesGet(
            @PathVariable String runId,
            @RequestParam(required = false) String title
    ) {
        ApiModels.ExportTestCaseRequest request = new ApiModels.ExportTestCaseRequest(
                title, null, null, null
        );
        return exportTestCases(runId, request);
    }

    @PostMapping("/runs/{runId}/export-tech-spec")
    public org.springframework.http.ResponseEntity<byte[]> exportTechSpec(
            @PathVariable String runId,
            @RequestBody(required = false) ApiModels.ExportTechSpecRequest request
    ) {
        RunEntity run = runService.requireOwned(userId(), runId);
        if (!"TECH_SPEC".equals(run.getMode())) {
            throw new org.hermesprd.workbench.shared.ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "只有技术方案结果支持导出 Markdown");
        }
        ResultEntity result = runService.findResult(run.getId());
        if (result == null || result.getPayload() == null) {
            throw new org.hermesprd.workbench.shared.ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "技术方案结果不存在");
        }
        JsonNode payload = runService.resultPayload(result);
        String markdown = techSpecMarkdownExporter.export(payload);
        if (telemetryService != null) {
            telemetryService.recordEvent("TECH_SPEC", "EXPORT", "EXPORT_MARKDOWN", "SUCCESS", null, null, null, java.util.Map.of("runId", runId));
        }

        String title = (request != null && request.title() != null && !request.title().isBlank())
                ? request.title()
                : payload.path("title").asText("研发技术方案与任务工单");
        String filename = title.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + ".md";
        String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        byte[] bytes = markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentType(new org.springframework.http.MediaType("text", "markdown", java.nio.charset.StandardCharsets.UTF_8))
                .body(bytes);
    }

    @GetMapping("/runs/{runId}/export-tech-spec")
    public org.springframework.http.ResponseEntity<byte[]> exportTechSpecGet(
            @PathVariable String runId,
            @RequestParam(required = false) String title
    ) {
        return exportTechSpec(runId, new ApiModels.ExportTechSpecRequest(title));
    }

    @GetMapping("/tasks/{taskId}/bundle")
    public org.springframework.http.ResponseEntity<byte[]> exportTaskBundle(@PathVariable String taskId) throws java.io.IOException {
        TaskEntity task = taskService.requireOwned(userId(), taskId);
        byte[] bytes = taskBundleExporter.exportBundle(userId(), taskId);
        if (telemetryService != null) {
            telemetryService.recordEvent("BUNDLE", "EXPORT", "DOWNLOAD_BUNDLE", "SUCCESS", null, null, null, java.util.Map.of("taskId", taskId));
        }
        String filename = task.getTitle().replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + "_交付大礼包.zip";
        String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .body(bytes);
    }

    private String userId() {
        UserEntity user = CurrentUser.require();
        return user.getId();
    }

    private TaskResponse toTask(TaskEntity task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                taskService.listSources(userId(), task.getId()).stream().map(this::toSource).toList(),
                task.getCreatedAt()
        );
    }

    private SourceResponse toSource(SourceEntity source) {
        return new SourceResponse(
                source.getId(),
                source.getTaskId(),
                source.getOriginalName(),
                source.getMediaType(),
                source.getSizeBytes(),
                source.getStatus(),
                source.getCreatedAt()
        );
    }

    private RunResponse toRun(RunEntity run) {
        ResultEntity result = runService.findResult(run.getId());
        return new RunResponse(
                run.getId(),
                run.getTaskId(),
                run.getParentRunId(),
                run.getMode(),
                run.getCompleteness() == null ? QualityOptions.STANDARD : run.getCompleteness(),
                QualityOptions.splitLenses(run.getLenses()),
                run.getStatus(),
                run.getStage(),
                runService.resultPayload(result),
                run.getErrorMessage(),
                run.isAccepted(),
                run.getCreatedAt(),
                run.getFinishedAt()
        );
    }
}
