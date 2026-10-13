package org.hermesprd.workbench.telemetry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.auth.UserMapper;
import org.hermesprd.workbench.shared.Ids;
import org.hermesprd.workbench.task.RunEntity;
import org.hermesprd.workbench.task.RunMapper;
import org.hermesprd.workbench.task.TaskEntity;
import org.hermesprd.workbench.task.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TelemetryService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);
    private static final List<String> MONITORED_MODES = List.of("GENERATE", "POC", "TEST_CASE", "TECH_SPEC", "REVIEW");

    @Value("${telemetry.enabled:false}")
    private boolean enabled;
    @Value("${telemetry.retention-days:30}")
    private int retentionDays = 30;

    private final TelemetryEventMapper eventMapper;
    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final RunMapper runMapper;
    private final ObjectMapper objectMapper;

    public TelemetryService(
            TelemetryEventMapper eventMapper,
            UserMapper userMapper,
            TaskMapper taskMapper,
            RunMapper runMapper,
            ObjectMapper objectMapper
    ) {
        this.eventMapper = eventMapper;
        this.userMapper = userMapper;
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.objectMapper = objectMapper;
    }

    public void recordEvent(
            String eventType,
            String module,
            String action,
            String status,
            Long durationMs,
            String errorCode,
            String errorMessage,
            Map<String, Object> metadata
    ) {
        if (!enabled) return;
        try {
            UserEntity user = CurrentUser.get();
            String userId = user != null ? user.getId() : null;
            String username = user != null ? user.getUsername() : null;

            TelemetryEventEntity event = new TelemetryEventEntity();
            event.setId(Ids.next("evt_"));
            event.setUserId(userId);
            event.setUsername(username);
            event.setEventType(eventType != null ? eventType : "GENERAL");
            event.setModule(module != null ? module : "SYSTEM");
            event.setAction(action != null ? action : "UNKNOWN");
            event.setStatus(status != null ? status : "SUCCESS");
            event.setDurationMs(durationMs != null ? Math.max(0L, durationMs) : null);
            event.setErrorCode(errorCode != null && errorCode.matches("[A-Z][A-Z0-9_]{0,63}") ? errorCode : null);
            event.setErrorMessage(null); // Raw browser/exception messages may contain private material or credentials.

            if (metadata != null && !metadata.isEmpty()) {
                try {
                    event.setMetadataJson(objectMapper.writeValueAsString(safeMetadata(metadata)));
                } catch (JsonProcessingException e) {
                    event.setMetadataJson(null);
                }
            }

            event.setCreatedAt(Instant.now());
            eventMapper.insert(event);
        } catch (Exception t) {
            log.warn("Telemetry recording failed type={}", t.getClass().getSimpleName());
        }
    }

    static Map<String, Object> safeMetadata(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("runId", "taskId")) {
            Object value = input.get(key);
            if (value instanceof String text && text.matches("(?:run|task)_[A-Za-z0-9]{1,64}")) result.put(key, text);
        }
        Object completeness = input.get("completeness");
        if (completeness instanceof String text && Set.of("QUICK", "STANDARD", "THOROUGH").contains(text)) result.put("completeness", text);
        for (String key : List.of("sourceCount", "fileCount")) {
            Object value = input.get(key);
            if (value instanceof Number number && number.longValue() >= 0 && number.longValue() <= 10000) result.put(key, number.longValue());
        }
        return result;
    }

    @Scheduled(fixedDelay = 86400000)
    public void purgeExpiredEvents() {
        if (!enabled) return;
        eventMapper.delete(new LambdaQueryWrapper<TelemetryEventEntity>().lt(TelemetryEventEntity::getCreatedAt,
                Instant.now().minus(Math.clamp(retentionDays, 1, 365), ChronoUnit.DAYS)));
    }

    public void recordRun(RunEntity run, String action, Long durationMs) {
        if (run == null) return;
        Map<String, Object> meta = new HashMap<>();
        meta.put("runId", run.getId());
        meta.put("taskId", run.getTaskId());
        meta.put("completeness", run.getCompleteness());
        meta.put("lenses", run.getLenses());

        recordEvent(
                "RUN_EXECUTION",
                run.getMode(),
                action,
                run.getStatus(),
                durationMs,
                run.getErrorCode(),
                run.getErrorMessage(),
                meta
        );
    }

    public void recordSystemError(String module, String action, String errorCode, String errorMessage) {
        recordEvent(
                "SYSTEM_ERROR",
                module != null ? module : "SYSTEM",
                action != null ? action : "API_REQUEST",
                "FAILURE",
                null,
                errorCode,
                errorMessage,
                null
        );
    }

    public TelemetryModels.AdminDashboardResponse getDashboard() {
        return new TelemetryModels.AdminDashboardResponse(
                getOverview(),
                getUserProfiles(),
                getFeatureUsage(30),
                getErrorStats(30),
                getRecentErrors(50)
        );
    }

    public TelemetryModels.OverviewResponse getOverview() {
        long totalUsers = userMapper.selectCount(null);

        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Long activeUsersTodayCount = eventMapper.selectCount(new LambdaQueryWrapper<TelemetryEventEntity>()
                .ge(TelemetryEventEntity::getCreatedAt, todayStart)
                .isNotNull(TelemetryEventEntity::getUserId));

        long totalTasks = taskMapper.selectCount(null);
        long totalRuns = runMapper.selectCount(null);

        long succeededRuns = runMapper.selectCount(new LambdaQueryWrapper<RunEntity>()
                .eq(RunEntity::getStatus, "SUCCEEDED"));
        long failedRuns = runMapper.selectCount(new LambdaQueryWrapper<RunEntity>()
                .eq(RunEntity::getStatus, "FAILED"));

        double successRate = totalRuns > 0 ? (double) succeededRuns / totalRuns * 100.0 : 100.0;

        Long totalErrors = runMapper.selectCount(new LambdaQueryWrapper<RunEntity>()
                .eq(RunEntity::getStatus, "FAILED")
                .in(RunEntity::getMode, MONITORED_MODES));

        return new TelemetryModels.OverviewResponse(
                totalUsers,
                activeUsersTodayCount != null ? activeUsersTodayCount : 0,
                totalTasks,
                totalRuns,
                succeededRuns,
                failedRuns,
                Math.round(successRate * 10.0) / 10.0,
                totalErrors != null ? totalErrors : 0
        );
    }

    public List<TelemetryModels.UserProfileDto> getUserProfiles() {
        List<Map<String, Object>> rows = eventMapper.aggregateUserProfiles();
        List<TelemetryModels.UserProfileDto> list = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                list.add(new TelemetryModels.UserProfileDto(
                        (String) r.get("userId"),
                        (String) r.get("username"),
                        (String) r.get("displayName"),
                        (String) r.get("role"),
                        toInstant(r.get("registeredAt")),
                        toInstant(r.get("lastActiveAt")),
                        toLong(r.get("taskCount")),
                        toLong(r.get("runCount")),
                        toLong(r.get("errorCount"))
                ));
            }
        }
        return list;
    }

    public List<TelemetryModels.FeatureUsageDto> getFeatureUsage(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Map<String, Object>> rows = eventMapper.aggregateFeatureUsage(since);
        List<TelemetryModels.FeatureUsageDto> list = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                long count = toLong(r.get("count"));
                long successCount = toLong(r.get("successCount"));
                long failCount = toLong(r.get("failCount"));
                long avgDuration = Math.round(toDouble(r.get("avgDuration")));
                double rate = count > 0 ? (double) successCount / count * 100.0 : 100.0;
                list.add(new TelemetryModels.FeatureUsageDto(
                        (String) r.get("module"),
                        (String) r.get("action"),
                        count,
                        avgDuration,
                        successCount,
                        failCount,
                        Math.round(rate * 10.0) / 10.0
                ));
            }
        }
        return list;
    }

    public List<TelemetryModels.ErrorStatDto> getErrorStats(int days) {
        Instant since = Instant.now().minus(Math.clamp(days, 1, 365), ChronoUnit.DAYS);
        List<Map<String, Object>> rows = runMapper.aggregateMonitoredFailures(since);
        List<TelemetryModels.ErrorStatDto> list = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                list.add(new TelemetryModels.ErrorStatDto(
                        (String) r.get("module"),
                        (String) r.get("errorCode"),
                        toLong(r.get("count")),
                        toInstant(r.get("lastOccurredAt"))
                ));
            }
        }
        return list;
    }

    public List<TelemetryModels.RecentErrorEventDto> getRecentErrors(int limit) {
        List<RunEntity> runs = runMapper.selectList(new LambdaQueryWrapper<RunEntity>()
                .eq(RunEntity::getStatus, "FAILED")
                .in(RunEntity::getMode, MONITORED_MODES)
                .orderByDesc(RunEntity::getFinishedAt)
                .last("LIMIT " + Math.clamp(limit, 1, 100)));

        List<TelemetryModels.RecentErrorEventDto> list = new ArrayList<>();
        for (RunEntity run : runs) {
            TaskEntity task = taskMapper.selectById(run.getTaskId());
            UserEntity user = task != null && task.getUserId() != null ? userMapper.selectById(task.getUserId()) : null;
            list.add(new TelemetryModels.RecentErrorEventDto(
                    run.getId(),
                    run.getTaskId(),
                    task != null ? task.getTitle() : null,
                    task != null ? task.getUserId() : null,
                    user != null ? user.getUsername() : null,
                    run.getMode(),
                    "RUN_FAILED",
                    run.getErrorCode(),
                    run.getErrorMessage(),
                    run.getFinishedAt() != null ? run.getFinishedAt() : run.getUpdatedAt()
            ));
        }
        return list;
    }

    private Instant toInstant(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Instant i) return i;
        if (obj instanceof java.time.LocalDateTime ldt) return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
        if (obj instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (obj instanceof java.util.Date d) return d.toInstant();
        return null;
    }

    private long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double toDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
