package org.hermesprd.workbench.hermes;

import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.hermesprd.workbench.task.ApiModels.SourcePageResponse;
import org.hermesprd.workbench.task.ApiModels.ToolSourceSummary;
import org.hermesprd.workbench.task.ReviewFeedbackService;
import org.hermesprd.workbench.task.RunEntity;
import org.hermesprd.workbench.task.RunMapper;
import org.hermesprd.workbench.task.SourceEntity;
import org.hermesprd.workbench.task.SourceMapper;
import org.hermesprd.workbench.task.SourceRoles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ToolService {
    private final RunMapper runMapper;
    private final SourceMapper sourceMapper;
    private final WorkbenchProperties properties;
    private final ReviewFeedbackService reviewFeedbackService;

    public ToolService(
            RunMapper runMapper,
            SourceMapper sourceMapper,
            WorkbenchProperties properties,
            ReviewFeedbackService reviewFeedbackService
    ) {
        this.runMapper = runMapper;
        this.sourceMapper = sourceMapper;
        this.properties = properties;
        this.reviewFeedbackService = reviewFeedbackService;
    }

    public RunEntity requireActiveRun(String runId) {
        RunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "运行不存在");
        }
        if (!"RUNNING".equals(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_ACTIVE", "Run 不在运行中");
        }
        return run;
    }

    public List<ToolSourceSummary> listSources(String runId) {
        RunEntity run = requireActiveRun(runId);
        List<SourceEntity> allReady = sourceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SourceEntity>()
                        .eq(SourceEntity::getTaskId, run.getTaskId())
                        .eq(SourceEntity::getStatus, "READY")
                        .orderByAsc(SourceEntity::getCreatedAt));
        boolean hasDraft = allReady.stream()
                .anyMatch(s -> s.getOriginalName() != null
                        && s.getOriginalName().startsWith(SourceRoles.DRAFT_PREFIX));
        List<ToolSourceSummary> sources = new ArrayList<>(allReady.stream()
                .filter(source -> SourceRoles.visibleForRun(run.getMode(), source.getOriginalName(), run.getParentRunId(), hasDraft))
                .map(source -> new ToolSourceSummary(source.getId(), source.getOriginalName(), source.getSizeBytes()))
                .toList());
        String history = reviewFeedbackService.historyContext(run);
        if (history != null) {
            sources.add(new ToolSourceSummary(
                    ReviewFeedbackService.HISTORY_SOURCE_ID,
                    ReviewFeedbackService.HISTORY_SOURCE_NAME,
                    history.getBytes(StandardCharsets.UTF_8).length
            ));
        }
        return List.copyOf(sources);
    }

    public SourcePageResponse readSource(String runId, String sourceId, String cursor) {
        RunEntity run = requireActiveRun(runId);
        if (ReviewFeedbackService.HISTORY_SOURCE_ID.equals(sourceId)) {
            String history = reviewFeedbackService.historyContext(run);
            if (history == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "材料不存在");
            }
            return pageSource(sourceId, ReviewFeedbackService.HISTORY_SOURCE_NAME, history, cursor);
        }
        SourceEntity source = sourceMapper.selectById(sourceId);
        boolean hasDraft = sourceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SourceEntity>()
                        .eq(SourceEntity::getTaskId, run.getTaskId())
                        .eq(SourceEntity::getStatus, "READY")
                        .likeRight(SourceEntity::getOriginalName, SourceRoles.DRAFT_PREFIX)) > 0;
        if (source == null || !run.getTaskId().equals(source.getTaskId())
                || !"READY".equals(source.getStatus())
                || !SourceRoles.visibleForRun(run.getMode(), source.getOriginalName(), run.getParentRunId(), hasDraft)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "材料不存在");
        }
        String content = source.getContent() == null ? "" : source.getContent();
        return pageSource(source.getId(), source.getOriginalName(), content, cursor);
    }

    private SourcePageResponse pageSource(String sourceId, String name, String content, String cursor) {
        int start = parseCursor(cursor);
        if (start < 0 || start > content.length()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "cursor 无效");
        }
        int pageSize = properties.sourcePageChars();
        int end = Math.min(content.length(), start + pageSize);
        String page = content.substring(start, end);
        String next = end < content.length() ? String.valueOf(end) : null;
        return new SourcePageResponse(sourceId, name, page, next);
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(cursor);
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "cursor 无效");
        }
    }
}
