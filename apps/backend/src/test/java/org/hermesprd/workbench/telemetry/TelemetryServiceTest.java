package org.hermesprd.workbench.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.auth.UserMapper;
import org.hermesprd.workbench.task.RunEntity;
import org.hermesprd.workbench.task.RunMapper;
import org.hermesprd.workbench.task.TaskEntity;
import org.hermesprd.workbench.task.TaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {
    @Mock
    private TelemetryEventMapper eventMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private RunMapper runMapper;

    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryService = new TelemetryService(
                eventMapper, userMapper, taskMapper, runMapper, new ObjectMapper()
        );
        ReflectionTestUtils.setField(telemetryService, "enabled", true);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    void disabledCollectionDoesNotWriteOrDelete() {
        ReflectionTestUtils.setField(telemetryService, "enabled", false);
        telemetryService.recordEvent("USER_ACTION", "PRD", "START_RUN", "SUCCEEDED", null, null, "private", Map.of("prompt", "private"));
        telemetryService.purgeExpiredEvents();
        verifyNoInteractions(eventMapper);
    }

    @Test
    void metadataDropsArbitraryTextAndMalformedIds() {
        var safe = TelemetryService.safeMetadata(Map.of("prompt", "private", "token", "private", "taskId", "Bearer credential", "runId", "run_safe", "sourceCount", 2));
        assertEquals(Map.of("runId", "run_safe", "sourceCount", 2L), safe);
    }

    @Test
    void recordEventPopulatesCurrentUserAndInserts() {
        UserEntity user = new UserEntity();
        user.setId("usr_123");
        user.setUsername("alice");
        CurrentUser.set(user);

        telemetryService.recordEvent(
                "FEATURE_USE", "TEST_CASE", "EXPORT_EXCEL", "SUCCESS",
                120L, null, null, Map.of("runId", "run_abc")
        );

        ArgumentCaptor<TelemetryEventEntity> captor = ArgumentCaptor.forClass(TelemetryEventEntity.class);
        verify(eventMapper).insert(captor.capture());
        TelemetryEventEntity evt = captor.getValue();

        assertEquals("usr_123", evt.getUserId());
        assertEquals("alice", evt.getUsername());
        assertEquals("FEATURE_USE", evt.getEventType());
        assertEquals("TEST_CASE", evt.getModule());
        assertEquals("EXPORT_EXCEL", evt.getAction());
        assertEquals("SUCCESS", evt.getStatus());
        assertEquals(120L, evt.getDurationMs());
        assertNotNull(evt.getMetadataJson());
        assertTrue(evt.getMetadataJson().contains("run_abc"));
    }

    @Test
    void recordRunPopulatesRunDetails() {
        RunEntity run = new RunEntity();
        run.setId("run_1");
        run.setTaskId("task_1");
        run.setMode("TECH_SPEC");
        run.setStatus("SUCCEEDED");
        run.setCompleteness("STANDARD");
        run.setLenses("arch-focus");

        telemetryService.recordRun(run, "FINISH_RUN", 4500L);

        ArgumentCaptor<TelemetryEventEntity> captor = ArgumentCaptor.forClass(TelemetryEventEntity.class);
        verify(eventMapper).insert(captor.capture());
        TelemetryEventEntity evt = captor.getValue();

        assertEquals("RUN_EXECUTION", evt.getEventType());
        assertEquals("TECH_SPEC", evt.getModule());
        assertEquals("FINISH_RUN", evt.getAction());
        assertEquals("SUCCEEDED", evt.getStatus());
        assertEquals(4500L, evt.getDurationMs());
    }

    @Test
    void recordSystemErrorCapturesErrorCodeAndMessage() {
        telemetryService.recordSystemError("SOURCE", "UPLOAD_TOO_LARGE", "FILE_TOO_LARGE", "文件过大");

        ArgumentCaptor<TelemetryEventEntity> captor = ArgumentCaptor.forClass(TelemetryEventEntity.class);
        verify(eventMapper).insert(captor.capture());
        TelemetryEventEntity evt = captor.getValue();

        assertEquals("SYSTEM_ERROR", evt.getEventType());
        assertEquals("SOURCE", evt.getModule());
        assertEquals("UPLOAD_TOO_LARGE", evt.getAction());
        assertEquals("FAILURE", evt.getStatus());
        assertEquals("FILE_TOO_LARGE", evt.getErrorCode());
        assertNull(evt.getErrorMessage());
    }

    @Test
    void exceptionDuringRecordingIsSilenced() {
        doThrow(new RuntimeException("DB down")).when(eventMapper).insert(any(TelemetryEventEntity.class));
        assertDoesNotThrow(() -> telemetryService.recordEvent(
                "FEATURE_USE", "DEMO", "PREVIEW", "SUCCESS", null, null, null, null
        ));
    }

    @Test
    void recentFailuresComeFromFailedRunsWithReasonAndTask() {
        RunEntity failed = new RunEntity();
        failed.setId("run_poc");
        failed.setTaskId("task_1");
        failed.setMode("POC");
        failed.setStatus("FAILED");
        failed.setErrorCode("HERMES_TOKEN_LIMIT_EXCEEDED");
        failed.setErrorMessage("单次输出超限");
        failed.setFinishedAt(Instant.parse("2026-09-23T01:00:00Z"));
        TaskEntity task = new TaskEntity();
        task.setId("task_1");
        task.setTitle("客户原型");
        task.setUserId("usr_1");
        UserEntity user = new UserEntity();
        user.setUsername("alice");
        when(runMapper.selectList(any())).thenReturn(List.of(failed));
        when(taskMapper.selectById("task_1")).thenReturn(task);
        when(userMapper.selectById("usr_1")).thenReturn(user);

        var records = telemetryService.getRecentErrors(20);

        assertEquals(1, records.size());
        assertEquals("run_poc", records.getFirst().id());
        assertEquals("客户原型", records.getFirst().taskTitle());
        assertEquals("alice", records.getFirst().username());
        assertEquals("单次输出超限", records.getFirst().errorMessage());
        verify(eventMapper, never()).selectList(any());
    }

    @Test
    void failureStatisticsOnlyUseMonitoredRuns() {
        when(runMapper.aggregateMonitoredFailures(any())).thenReturn(List.of(Map.of(
                "module", "GENERATE", "errorCode", "HERMES_FAILED", "count", 2L,
                "lastOccurredAt", java.sql.Timestamp.from(Instant.parse("2026-09-23T01:00:00Z"))
        )));
        var stats = telemetryService.getErrorStats(30);
        assertEquals(1, stats.size());
        assertEquals("GENERATE", stats.getFirst().module());
        assertEquals(2, stats.getFirst().count());
        verify(eventMapper, never()).aggregateErrors(any());
    }
}
