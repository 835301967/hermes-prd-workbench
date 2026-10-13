package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {
    @Test
    void keepsUnicodeFilenameWithoutUsingHostPathCharset() {
        assertEquals("IOC 智慧运营中心平台 PRD（基于含税报价需求清单）.docx", TaskService.originalNameOf(
                "IOC 智慧运营中心平台 PRD（基于含税报价需求清单）.docx"
        ));
    }

    @Test
    void stripsUnixAndWindowsPathPrefixes() {
        assertEquals("需求说明.txt", TaskService.originalNameOf("/tmp/需求说明.txt"));
        assertEquals("需求说明.txt", TaskService.originalNameOf("C:\\tmp\\需求说明.txt"));
        assertEquals("untitled.txt", TaskService.originalNameOf(null));
    }

    @Test
    void readsOnlyDraftBackedBySucceededGenerateRun() {
        Fixture fixture = fixture();
        Instant updatedAt = Instant.parse("2026-08-26T02:00:00Z");
        SourceEntity source = draft("src_1", "task_1", "run_1", updatedAt, "# 当前稿");
        RunEntity run = generateRun("run_1", "task_1", "SUCCEEDED");
        when(fixture.sourceMapper.selectById("src_1")).thenReturn(source);
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);

        ApiModels.DraftSourceResponse response = fixture.service.getDraftSource("user_1", "task_1", "src_1");

        assertEquals("src_1", response.sourceId());
        assertEquals("run_1", response.runId());
        assertEquals("# 当前稿", response.content());
        assertEquals(updatedAt, response.updatedAt());
    }

    @Test
    void rejectsDraftNameWithoutMatchingSucceededGenerateRun() {
        Fixture fixture = fixture();
        SourceEntity source = draft("src_1", "task_1", "run_1", Instant.now(), "# 伪装稿");
        when(fixture.sourceMapper.selectById("src_1")).thenReturn(source);
        when(fixture.runMapper.selectById("run_1")).thenReturn(generateRun("run_1", "task_1", "FAILED"));

        ApiException error = assertThrows(ApiException.class,
                () -> fixture.service.getDraftSource("user_1", "task_1", "src_1"));

        assertEquals(404, error.status().value());
        assertEquals("RESOURCE_NOT_FOUND", error.code());
    }

    @Test
    void savesDraftVerbatimWithUtf8SizeAndOptimisticToken() {
        Fixture fixture = fixture();
        Instant expected = Instant.parse("2026-08-26T02:00:00Z");
        SourceEntity source = draft("src_1", "task_1", "run_1", expected, "old");
        when(fixture.sourceMapper.selectById("src_1")).thenReturn(source);
        when(fixture.runMapper.selectById("run_1")).thenReturn(generateRun("run_1", "task_1", "SUCCEEDED"));
        when(fixture.runMapper.selectCount(any())).thenReturn(0L);
        when(fixture.sourceMapper.updateDraftContent(
                eq("task_1"), eq("src_1"), eq("  新正文\n"), eq(12L), eq(expected), any()
        )).thenReturn(1);

        ApiModels.DraftSourceResponse response = fixture.service.updateDraftSource(
                "user_1",
                "task_1",
                "src_1",
                new ApiModels.UpdateDraftSourceRequest("  新正文\n", expected)
        );

        assertEquals("  新正文\n", response.content());
        ArgumentCaptor<Instant> nextToken = ArgumentCaptor.forClass(Instant.class);
        verify(fixture.sourceMapper).updateDraftContent(
                eq("task_1"), eq("src_1"), eq("  新正文\n"), eq(12L), eq(expected), nextToken.capture()
        );
        assertTrue(nextToken.getValue().isAfter(expected));
    }

    @Test
    void reportsSourceChangedWithoutRetryingOrOverwriting() {
        Fixture fixture = fixture();
        Instant expected = Instant.parse("2026-08-26T02:00:00Z");
        when(fixture.sourceMapper.selectById("src_1"))
                .thenReturn(draft("src_1", "task_1", "run_1", expected, "old"));
        when(fixture.runMapper.selectById("run_1")).thenReturn(generateRun("run_1", "task_1", "SUCCEEDED"));
        when(fixture.runMapper.selectCount(any())).thenReturn(0L);
        when(fixture.sourceMapper.updateDraftContent(any(), any(), any(), anyLong(), any(), any())).thenReturn(0);

        ApiException error = assertThrows(ApiException.class, () -> fixture.service.updateDraftSource(
                "user_1", "task_1", "src_1", new ApiModels.UpdateDraftSourceRequest("new", expected)
        ));

        assertEquals(409, error.status().value());
        assertEquals("SOURCE_CHANGED", error.code());
    }

    @Test
    void activeRunBlocksContextMutation() {
        Fixture fixture = fixture();
        when(fixture.runMapper.selectCount(any())).thenReturn(1L);

        ApiException error = assertThrows(ApiException.class,
                () -> fixture.service.requireNoActiveRun("task_1"));

        assertEquals(409, error.status().value());
        assertEquals("RUN_NOT_ACTIVE", error.code());
    }

    @Test
    void activeRunBlocksUploadBeforeReadingOrStoringFile() {
        Fixture fixture = fixture();
        when(fixture.runMapper.selectCount(any())).thenReturn(1L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "补充.txt", "text/plain", "不能写入".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ApiException error = assertThrows(ApiException.class,
                () -> fixture.service.upload("user_1", "task_1", file));

        assertEquals("RUN_NOT_ACTIVE", error.code());
        verify(fixture.sourceMapper, never()).insert(any(SourceEntity.class));
    }

    @Test
    void rejectsGeneratedDraftPrefixWithoutMatchingSucceededRun() {
        Fixture fixture = fixture();
        when(fixture.runMapper.selectCount(any())).thenReturn(0L);
        when(fixture.runMapper.selectById("run_missing")).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "生成稿-run_missing.md",
                "text/markdown",
                "# 伪装稿".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ApiException error = assertThrows(ApiException.class,
                () -> fixture.service.upload("user_1", "task_1", file));

        assertEquals(400, error.status().value());
        assertTrue(error.getMessage().contains("保留的生成稿名称"));
        verify(fixture.sourceMapper, never()).insert(any(SourceEntity.class));
    }

    @Test
    void rejectsDuplicateReadyDraftForTheSameGenerateRun() {
        Fixture fixture = fixture();
        when(fixture.runMapper.selectCount(any())).thenReturn(0L);
        when(fixture.runMapper.selectById("run_1")).thenReturn(generateRun("run_1", "task_1", "SUCCEEDED"));
        when(fixture.sourceMapper.selectCount(any())).thenReturn(1L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "生成稿-run_1.md",
                "text/markdown",
                "# 重复稿".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ApiException error = assertThrows(ApiException.class,
                () -> fixture.service.upload("user_1", "task_1", file));

        assertEquals(409, error.status().value());
        assertEquals("SOURCE_CHANGED", error.code());
        verify(fixture.sourceMapper, never()).insert(any(SourceEntity.class));
    }

    private static Fixture fixture() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        SourceMapper sourceMapper = mock(SourceMapper.class);
        RunMapper runMapper = mock(RunMapper.class);
        TaskEntity task = new TaskEntity();
        task.setId("task_1");
        task.setUserId("user_1");
        when(taskMapper.selectById("task_1")).thenReturn(task);
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
        return new Fixture(
                new TaskService(taskMapper, sourceMapper, runMapper, properties, new TaskMutationGate()),
                sourceMapper,
                runMapper
        );
    }

    private static SourceEntity draft(String sourceId, String taskId, String runId, Instant updatedAt, String content) {
        SourceEntity source = new SourceEntity();
        source.setId(sourceId);
        source.setTaskId(taskId);
        source.setOriginalName(SourceRoles.draftName(runId));
        source.setStatus("READY");
        source.setUpdatedAt(updatedAt);
        source.setContent(content);
        return source;
    }

    private static RunEntity generateRun(String runId, String taskId, String status) {
        RunEntity run = new RunEntity();
        run.setId(runId);
        run.setTaskId(taskId);
        run.setMode("GENERATE");
        run.setStatus(status);
        return run;
    }

    private record Fixture(TaskService service, SourceMapper sourceMapper, RunMapper runMapper) {
    }
}
