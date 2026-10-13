package org.hermesprd.workbench.hermes;

import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.hermesprd.workbench.task.ApiModels;
import org.hermesprd.workbench.task.ReviewFeedbackService;
import org.hermesprd.workbench.task.RunEntity;
import org.hermesprd.workbench.task.RunMapper;
import org.hermesprd.workbench.task.SourceEntity;
import org.hermesprd.workbench.task.SourceMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolServiceTest {
    @Test
    void listReturnsContractSummariesAndReviewHistoryVirtualSource() {
        Fixture fixture = fixture();
        RunEntity run = running("run_1", "REVIEW");
        SourceEntity source = source("src_1", "生成稿-run_generate.md", 123);
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.sourceMapper.selectList(any())).thenReturn(List.of(source));
        when(fixture.reviewFeedbackService.historyContext(run)).thenReturn("历史决定");

        List<ApiModels.ToolSourceSummary> sources = fixture.service.listSources("run_1");

        assertEquals(2, sources.size());
        assertEquals(new ApiModels.ToolSourceSummary("src_1", "生成稿-run_generate.md", 123), sources.get(0));
        assertEquals(ReviewFeedbackService.HISTORY_SOURCE_ID, sources.get(1).sourceId());
        assertEquals("历史决定".getBytes(java.nio.charset.StandardCharsets.UTF_8).length, sources.get(1).size());
    }

    @Test
    void readsVirtualSourceWithSamePaginationContract() {
        Fixture fixture = fixture();
        RunEntity run = running("run_1", "REVIEW");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.reviewFeedbackService.historyContext(run)).thenReturn("0123456789");

        ApiModels.SourcePageResponse first = fixture.service.readSource(
                "run_1", ReviewFeedbackService.HISTORY_SOURCE_ID, null
        );
        ApiModels.SourcePageResponse second = fixture.service.readSource(
                "run_1", ReviewFeedbackService.HISTORY_SOURCE_ID, first.nextCursor()
        );

        assertEquals("01234567", first.content());
        assertEquals("8", first.nextCursor());
        assertEquals("89", second.content());
        assertEquals(null, second.nextCursor());
        verify(fixture.sourceMapper, never()).selectById(any());
    }

    @Test
    void guessedVirtualIdIsNotVisibleOutsideCurrentReviewContext() {
        Fixture fixture = fixture();
        RunEntity run = running("run_1", "DEMO");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.reviewFeedbackService.historyContext(run)).thenReturn(null);

        ApiException error = assertThrows(ApiException.class, () -> fixture.service.readSource(
                "run_1", ReviewFeedbackService.HISTORY_SOURCE_ID, null
        ));

        assertEquals(404, error.status().value());
        assertEquals("RESOURCE_NOT_FOUND", error.code());
        verify(fixture.sourceMapper, never()).selectById(any());
    }

    @Test
    void failedDatabaseSourceCannotBeReadEvenWhenItsNameMatchesMode() {
        Fixture fixture = fixture();
        RunEntity run = running("run_1", "REVIEW");
        SourceEntity source = source("src_1", "生成稿-run_generate.md", 123);
        source.setStatus("FAILED");
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.sourceMapper.selectById("src_1")).thenReturn(source);

        ApiException error = assertThrows(ApiException.class,
                () -> fixture.service.readSource("run_1", "src_1", null));

        assertEquals(404, error.status().value());
        assertTrue(error.getMessage().contains("材料不存在"));
    }

    private Fixture fixture() {
        RunMapper runMapper = mock(RunMapper.class);
        SourceMapper sourceMapper = mock(SourceMapper.class);
        ReviewFeedbackService reviewFeedbackService = mock(ReviewFeedbackService.class);
        WorkbenchProperties properties = new WorkbenchProperties(
                Path.of("target/test-uploads"),
                10_000_000,
                2_000_000,
                8,
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
                new ToolService(runMapper, sourceMapper, properties, reviewFeedbackService),
                runMapper,
                sourceMapper,
                reviewFeedbackService
        );
    }

    private RunEntity running(String id, String mode) {
        RunEntity run = new RunEntity();
        run.setId(id);
        run.setTaskId("task_1");
        run.setMode(mode);
        run.setStatus("RUNNING");
        return run;
    }

    private SourceEntity source(String id, String name, long size) {
        SourceEntity source = new SourceEntity();
        source.setId(id);
        source.setTaskId("task_1");
        source.setOriginalName(name);
        source.setSizeBytes(size);
        source.setStatus("READY");
        return source;
    }

    private record Fixture(
            ToolService service,
            RunMapper runMapper,
            SourceMapper sourceMapper,
            ReviewFeedbackService reviewFeedbackService
    ) {
    }
}
