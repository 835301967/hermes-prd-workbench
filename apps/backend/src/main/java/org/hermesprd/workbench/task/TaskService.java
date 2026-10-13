package org.hermesprd.workbench.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.Ids;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class TaskService {
    private static final int MAX_DRAFT_CHARACTERS = 500_000;
    private final TaskMapper taskMapper;
    private final SourceMapper sourceMapper;
    private final RunMapper runMapper;
    private final WorkbenchProperties properties;
    private final TaskMutationGate mutationGate;

    public TaskService(
            TaskMapper taskMapper,
            SourceMapper sourceMapper,
            RunMapper runMapper,
            WorkbenchProperties properties,
            TaskMutationGate mutationGate
    ) {
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
        this.runMapper = runMapper;
        this.properties = properties;
        this.mutationGate = mutationGate;
    }

    public TaskEntity create(String userId, String title) {
        TaskEntity task = new TaskEntity();
        Instant now = Instant.now();
        task.setId(Ids.next("task_"));
        task.setTitle(title.trim());
        task.setUserId(userId);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return task;
    }

    public TaskEntity rename(String userId, String taskId, String title) {
        TaskEntity task = requireOwned(userId, taskId);
        String next = title == null ? "" : title.trim();
        if (next.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "需求名称不能为空");
        }
        task.setTitle(next);
        task.setUpdatedAt(Instant.now());
        taskMapper.updateById(task);
        return task;
    }

    public List<TaskEntity> listByUser(String userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getUserId, userId)
                .orderByDesc(TaskEntity::getUpdatedAt)
                .last("LIMIT 50"));
    }

    public TaskEntity requireOwned(String userId, String taskId) {
        TaskEntity task = requireTask(taskId);
        if (task.getUserId() == null || !userId.equals(task.getUserId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
        }
        return task;
    }

    public TaskEntity requireTask(String taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
        }
        return task;
    }

    public List<SourceEntity> listSources(String userId, String taskId) {
        requireOwned(userId, taskId);
        return sourceMapper.selectList(new LambdaQueryWrapper<SourceEntity>()
                .eq(SourceEntity::getTaskId, taskId)
                .orderByAsc(SourceEntity::getCreatedAt));
    }

    public SourceEntity upload(String userId, String taskId, MultipartFile file) {
        requireOwned(userId, taskId);
        return mutationGate.execute(taskId, () -> uploadLocked(taskId, file));
    }

    private SourceEntity uploadLocked(String taskId, MultipartFile file) {
        requireNoActiveRun(taskId);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请选择文件");
        }
        if (file.getSize() > properties.maxSourceBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件超过 10MB 限制");
        }
        String originalName = originalNameOf(file.getOriginalFilename());
        validateGeneratedDraftUpload(taskId, originalName);
        String extension = extensionOf(originalName);
        if (!SourceTextExtractor.ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", SourceTextExtractor.unsupportedTypeMessage());
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "无法读取上传文件");
        }
        String content = SourceTextExtractor.extract(bytes, extension);
        if (content.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "空文件不能用于运行");
        }
        Path stored = store(taskId, extension, file);
        Instant now = Instant.now();
        SourceEntity source = new SourceEntity();
        source.setId(Ids.next("src_"));
        source.setTaskId(taskId);
        source.setOriginalName(originalName);
        source.setMediaType(SourceTextExtractor.mediaTypeFor(extension));
        source.setSizeBytes(file.getSize());
        source.setStoragePath(stored.toString());
        source.setContent(content);
        source.setStatus("READY");
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        sourceMapper.insert(source);
        return source;
    }

    private void validateGeneratedDraftUpload(String taskId, String originalName) {
        if (!originalName.startsWith(SourceRoles.DRAFT_PREFIX)) {
            return;
        }
        // Current drafts still enter through the upload API; only the exact succeeded GENERATE name is reserved for that path.
        String runId = SourceRoles.draftRunId(originalName);
        RunEntity run = runId == null ? null : runMapper.selectById(runId);
        if (run == null
                || !taskId.equals(run.getTaskId())
                || !"GENERATE".equals(run.getMode())
                || !"SUCCEEDED".equals(run.getStatus())
                || !SourceRoles.draftName(run.getId()).equals(originalName)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "保留的生成稿名称不能用于普通材料");
        }
        Long duplicates = sourceMapper.selectCount(new LambdaQueryWrapper<SourceEntity>()
                .eq(SourceEntity::getTaskId, taskId)
                .eq(SourceEntity::getOriginalName, originalName)
                .eq(SourceEntity::getStatus, "READY"));
        if (duplicates != null && duplicates > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED", "该生成稿已是当前材料，请刷新后重试");
        }
    }

    public void deleteSource(String userId, String taskId, String sourceId) {
        requireOwned(userId, taskId);
        mutationGate.execute(taskId, () -> deleteSourceLocked(taskId, sourceId));
    }

    private void deleteSourceLocked(String taskId, String sourceId) {
        SourceEntity source = sourceMapper.selectById(sourceId);
        if (source == null || !taskId.equals(source.getTaskId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "材料不存在");
        }
        requireNoActiveRun(taskId);
        sourceMapper.deleteById(sourceId);
        try {
            Files.deleteIfExists(Path.of(source.getStoragePath()));
        } catch (IOException ignored) {
            // Spike: missing file after DB delete is acceptable.
        }
    }

    public ApiModels.DraftSourceResponse getDraftSource(String userId, String taskId, String sourceId) {
        DraftSource draft = requireDraftSource(userId, taskId, sourceId);
        return toDraftResponse(draft.source(), draft.run().getId());
    }

    public ApiModels.DraftSourceResponse updateDraftSource(
            String userId,
            String taskId,
            String sourceId,
            ApiModels.UpdateDraftSourceRequest request
    ) {
        return mutationGate.execute(taskId, () -> updateDraftSourceLocked(userId, taskId, sourceId, request));
    }

    private ApiModels.DraftSourceResponse updateDraftSourceLocked(
            String userId,
            String taskId,
            String sourceId,
            ApiModels.UpdateDraftSourceRequest request
    ) {
        DraftSource draft = requireDraftSource(userId, taskId, sourceId);
        requireNoActiveRun(taskId);
        String content = request.content();
        if (content == null || content.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "当前稿正文不能为空");
        }
        if (content.codePointCount(0, content.length()) > MAX_DRAFT_CHARACTERS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "当前稿正文不能超过 500000 字符");
        }
        Instant expectedUpdatedAt = request.expectedUpdatedAt();
        if (expectedUpdatedAt == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "缺少并发令牌");
        }
        Instant updatedAt = nextUpdateToken(draft.source().getUpdatedAt(), expectedUpdatedAt);
        int updated = sourceMapper.updateDraftContent(
                taskId,
                sourceId,
                content,
                content.getBytes(StandardCharsets.UTF_8).length,
                expectedUpdatedAt,
                updatedAt
        );
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED", "当前稿已被修改，请重新加载");
        }
        return new ApiModels.DraftSourceResponse(sourceId, draft.run().getId(), content, updatedAt);
    }

    public void requireNoActiveRun(String taskId) {
        Long active = runMapper.selectCount(new LambdaQueryWrapper<RunEntity>()
                .eq(RunEntity::getTaskId, taskId)
                .in(RunEntity::getStatus, List.of("PENDING", "RUNNING")));
        if (active != null && active > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_ACTIVE", "运行期间不能修改材料或评审回复");
        }
    }

    private DraftSource requireDraftSource(String userId, String taskId, String sourceId) {
        requireOwned(userId, taskId);
        SourceEntity source = sourceMapper.selectById(sourceId);
        if (source == null || !taskId.equals(source.getTaskId()) || !"READY".equals(source.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "当前稿不存在");
        }
        String runId = SourceRoles.draftRunId(source.getOriginalName());
        if (runId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "当前稿不存在");
        }
        RunEntity run = runMapper.selectById(runId);
        if (run == null
                || !taskId.equals(run.getTaskId())
                || !"GENERATE".equals(run.getMode())
                || !"SUCCEEDED".equals(run.getStatus())
                || !SourceRoles.draftName(run.getId()).equals(source.getOriginalName())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "当前稿不存在");
        }
        return new DraftSource(source, run);
    }

    private ApiModels.DraftSourceResponse toDraftResponse(SourceEntity source, String runId) {
        return new ApiModels.DraftSourceResponse(
                source.getId(),
                runId,
                source.getContent(),
                source.getUpdatedAt()
        );
    }

    private Instant nextUpdateToken(Instant storedUpdatedAt, Instant expectedUpdatedAt) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant baseline = storedUpdatedAt == null ? expectedUpdatedAt : storedUpdatedAt;
        Instant minimum = baseline.truncatedTo(ChronoUnit.MILLIS).plusMillis(1);
        return now.isAfter(minimum) ? now : minimum;
    }

    private record DraftSource(SourceEntity source, RunEntity run) {
    }

    private Path store(String taskId, String extension, MultipartFile file) {
        try {
            Path dir = properties.uploadDir().resolve(taskId).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(Ids.next("file_") + "." + extension);
            file.transferTo(target);
            return target;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "无法保存上传文件");
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String originalNameOf(String filename) {
        if (filename == null || filename.isBlank()) {
            return "untitled.txt";
        }
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = separator >= 0 ? filename.substring(separator + 1) : filename;
        return name.isBlank() ? "untitled.txt" : name;
    }
}
