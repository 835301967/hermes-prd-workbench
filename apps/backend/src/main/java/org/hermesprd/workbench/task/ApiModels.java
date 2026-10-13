package org.hermesprd.workbench.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record CreateTaskRequest(@NotBlank @Size(max = 200) String title) {
    }

    public record RenameTaskRequest(@NotBlank @Size(max = 200) String title) {
    }

    public record CreateRunRequest(
            @NotBlank String mode,
            @Size(max = 4000) String instruction,
            @Size(max = 64) String skill,
            @Size(max = 20) String completeness,
            List<@Size(max = 64) String> lenses,
            @Size(max = 64) String reviseFromRunId
    ) {
    }

    public record HealthResponse(String status, String hermes) {
    }

    public record SourceResponse(
            String id,
            String taskId,
            String name,
            String mediaType,
            long size,
            String status,
            Instant createdAt
    ) {
    }

    public record DraftSourceResponse(
            String sourceId,
            String runId,
            String content,
            Instant updatedAt
    ) {
    }

    public record UpdateDraftSourceRequest(
            @NotNull String content,
            @NotNull Instant expectedUpdatedAt
    ) {
    }

    public record ReviewFeedbackItem(
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "^(blockingIssues|otherSuggestions)/(0|[1-9][0-9]*)$") String itemPath,
            @NotBlank String action,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Size(min = 1, max = 2000) String note
    ) {
    }

    public record UpdateReviewFeedbackRequest(
            @NotNull @PositiveOrZero Integer expectedRevision,
            @NotNull @Size(max = 150) List<@Valid ReviewFeedbackItem> items
    ) {
    }

    public record ReviewFeedbackResponse(
            String runId,
            int revision,
            List<ReviewFeedbackItem> items
    ) {
    }

    public record TaskResponse(String id, String title, List<SourceResponse> sources, Instant createdAt) {
    }

    public record TaskSummary(String id, String title, Instant createdAt) {
    }

    public record RunHistoryItem(
            String id,
            String taskId,
            String taskTitle,
            String mode,
            String status,
            String summary,
            String error,
            boolean accepted,
            Instant createdAt,
            Instant finishedAt
    ) {
    }

    public record RunResponse(
            String id,
            String taskId,
            String parentRunId,
            String mode,
            String completeness,
            List<String> lenses,
            String status,
            String stage,
            JsonNode result,
            String error,
            boolean accepted,
            Instant createdAt,
            Instant finishedAt
    ) {
    }

    public record ToolSourceSummary(String sourceId, String name, long size) {
    }

    public record ToolSourceListResponse(String runId, List<ToolSourceSummary> sources) {
    }

    public record SourcePageResponse(String sourceId, String name, String content, String nextCursor) {
    }

    public record SubmitResultRequest(
            @NotBlank String mode,
            JsonNode payload,
            Integer partIndex,
            Integer totalParts,
            Boolean isFinal
    ) {
        public SubmitResultRequest(@NotBlank String mode, JsonNode payload) {
            this(mode, payload, null, null, null);
        }
    }

    public record ExportEstimateColumn(String key, String title, Boolean visible) {
    }

    public record ExportEstimateRequest(
            String title,
            Double dailyRate,
            List<ExportEstimateColumn> columns,
            List<String> customHeaders,
            JsonNode extraFieldValues
    ) {
    }

    public record ExportTestCaseColumn(String key, String title, Boolean visible) {
    }

    public record ExportTestCaseRequest(
            String title,
            List<ExportTestCaseColumn> columns,
            List<String> customHeaders,
            JsonNode extraFieldValues
    ) {
    }

    public record ExportTechSpecRequest(String title) {
    }
}
