package org.hermesprd.workbench.telemetry;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TelemetryModels {
    private TelemetryModels() {
    }

    public record ClientEventRequest(
            @Pattern(regexp = "USER_ACTION|CLIENT_ERROR") String eventType,
            @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z_]+") String module,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z_]+") String action,
            @Pattern(regexp = "SUCCEEDED|FAILED|PENDING|RUNNING") String status,
            @Min(0) @Max(86400000) Long durationMs,
            @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String errorCode,
            @Size(max = 500) String errorMessage,
            @Size(max = 10) Map<String, Object> metadata
    ) {
    }

    public record OverviewResponse(
            long totalUsers,
            long activeUsersToday,
            long totalTasks,
            long totalRuns,
            long totalRunsSucceeded,
            long totalRunsFailed,
            double successRate,
            long totalErrors
    ) {
    }

    public record UserProfileDto(
            String userId,
            String username,
            String displayName,
            String role,
            Instant registeredAt,
            Instant lastActiveAt,
            long taskCount,
            long runCount,
            long errorCount
    ) {
    }

    public record FeatureUsageDto(
            String module,
            String action,
            long count,
            long avgDurationMs,
            long successCount,
            long failCount,
            double successRate
    ) {
    }

    public record ErrorStatDto(
            String module,
            String errorCode,
            long count,
            Instant lastOccurredAt
    ) {
    }

    public record RecentErrorEventDto(
            String id,
            String taskId,
            String taskTitle,
            String userId,
            String username,
            String module,
            String action,
            String errorCode,
            String errorMessage,
            Instant createdAt
    ) {
    }

    public record AdminDashboardResponse(
            OverviewResponse overview,
            List<UserProfileDto> users,
            List<FeatureUsageDto> features,
            List<ErrorStatDto> errorStats,
            List<RecentErrorEventDto> recentErrors
    ) {
    }
}
