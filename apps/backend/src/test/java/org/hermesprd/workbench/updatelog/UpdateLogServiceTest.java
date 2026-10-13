package org.hermesprd.workbench.updatelog;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.shared.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UpdateLogServiceTest {
    static {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                UpdateLogEntity.class
        );
    }

    private UpdateLogMapper updateLogMapper;
    private ObjectMapper objectMapper;
    private UpdateLogService updateLogService;

    @BeforeEach
    void setUp() {
        updateLogMapper = mock(UpdateLogMapper.class);
        objectMapper = new ObjectMapper();
        updateLogService = new UpdateLogService(updateLogMapper, objectMapper);
    }

    @Test
    void getPublishedUpdateLogsReturnsLatestAndList() {
        UpdateLogEntity v38 = new UpdateLogEntity();
        v38.setId("log-v038");
        v38.setVersion("V0.3.8");
        v38.setReleaseDate("2026-09-18");
        v38.setTitle("最新版本");
        v38.setSummary("摘要38");
        v38.setHighlightsJson("[\"亮点A\"]");
        v38.setItemsJson("[{\"title\":\"条目1\",\"detail\":\"详情1\",\"type\":\"feature\",\"tone\":\"accent\"}]");
        v38.setIsLatest(1);
        v38.setStatus("PUBLISHED");
        v38.setCreatedAt(Instant.now());

        UpdateLogEntity v37 = new UpdateLogEntity();
        v37.setId("log-v037");
        v37.setVersion("V0.3.7");
        v37.setReleaseDate("2026-09-14");
        v37.setTitle("上一版本");
        v37.setSummary("摘要37");
        v37.setHighlightsJson("[]");
        v37.setItemsJson("[{\"title\":\"条目2\",\"detail\":\"详情2\",\"type\":\"fix\",\"tone\":\"amber\"}]");
        v37.setIsLatest(0);
        v37.setStatus("PUBLISHED");
        v37.setCreatedAt(Instant.now());

        when(updateLogMapper.selectList(any())).thenReturn(List.of(v38, v37));

        UpdateLogModels.UpdateLogSummaryResponse response = updateLogService.getPublishedUpdateLogs();

        assertNotNull(response.latest());
        assertEquals("V0.3.8", response.latest().version());
        assertTrue(response.latest().isLatest());
        assertEquals(1, response.latest().highlights().size());
        assertEquals("亮点A", response.latest().highlights().get(0));
        assertEquals(1, response.latest().items().size());
        assertEquals("feature", response.latest().items().get(0).type());

        assertEquals(2, response.history().size());
    }

    @Test
    void createUpdateLogThrowsOnDuplicateVersion() {
        when(updateLogMapper.selectCount(any())).thenReturn(1L);

        UpdateLogModels.CreateUpdateLogRequest req = new UpdateLogModels.CreateUpdateLogRequest(
                "V0.3.8", "2026-09-18", "标题", "概述",
                List.of("亮点"),
                List.of(new UpdateLogModels.UpdateLogItemDto("条目", "详情", "feature", "accent")),
                false, "PUBLISHED"
        );

        ApiException ex = assertThrows(ApiException.class, () -> updateLogService.createUpdateLog("admin1", req));
        assertEquals(HttpStatus.CONFLICT, ex.status());
    }

    @Test
    void createUpdateLogClearsLatestWhenMarkedLatest() {
        when(updateLogMapper.selectCount(any())).thenReturn(0L);

        UpdateLogModels.CreateUpdateLogRequest req = new UpdateLogModels.CreateUpdateLogRequest(
                "V0.3.9", "2026-09-19", "新版本标题", "新版本概述",
                List.of("新亮点"),
                List.of(new UpdateLogModels.UpdateLogItemDto("新功能", "功能详情", "feature", "accent")),
                true, "PUBLISHED"
        );

        UpdateLogModels.UpdateLogDto created = updateLogService.createUpdateLog("admin1", req);

        assertNotNull(created);
        assertEquals("V0.3.9", created.version());
        assertTrue(created.isLatest());
        // Verify that update was called to clear existing isLatest flags
        verify(updateLogMapper).update(isNull(), any());
        verify(updateLogMapper).insert(any(UpdateLogEntity.class));
    }

    @Test
    void updateUpdateLogUpdatesFieldsAndThrowsIfNotFound() {
        when(updateLogMapper.selectById("non-existent")).thenReturn(null);

        UpdateLogModels.UpdateUpdateLogRequest req = new UpdateLogModels.UpdateUpdateLogRequest(
                "V0.3.8", "2026-09-18", "新标题", "新概述",
                List.of(),
                List.of(new UpdateLogModels.UpdateLogItemDto("条目", "详情", "improve", "blue")),
                false, "PUBLISHED"
        );

        assertThrows(ApiException.class, () -> updateLogService.updateUpdateLog("admin1", "non-existent", req));

        UpdateLogEntity existing = new UpdateLogEntity();
        existing.setId("log-1");
        existing.setVersion("V0.3.8");
        existing.setIsLatest(0);
        when(updateLogMapper.selectById("log-1")).thenReturn(existing);

        UpdateLogModels.UpdateLogDto updated = updateLogService.updateUpdateLog("admin1", "log-1", req);
        assertNotNull(updated);
        assertEquals("新标题", updated.title());
        verify(updateLogMapper).updateById(existing);
    }

    @Test
    void deleteUpdateLogDeletesEntity() {
        when(updateLogMapper.selectById("log-1")).thenReturn(new UpdateLogEntity());

        updateLogService.deleteUpdateLog("admin1", "log-1");
        verify(updateLogMapper).deleteById("log-1");
    }

    @Test
    void toggleStatusUpdatesState() {
        UpdateLogEntity entity = new UpdateLogEntity();
        entity.setId("log-1");
        entity.setStatus("PUBLISHED");
        entity.setIsLatest(0);
        when(updateLogMapper.selectById("log-1")).thenReturn(entity);

        UpdateLogModels.UpdateLogDto result = updateLogService.toggleStatus("admin1", "log-1", "DRAFT", true);
        assertEquals("DRAFT", result.status());
        assertTrue(result.isLatest());
        verify(updateLogMapper).update(isNull(), any());
        verify(updateLogMapper).updateById(entity);
    }
    @Test
    void malformedJsonLogsOnlyExceptionTypeWithoutStoredContent() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UpdateLogService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var entity = new UpdateLogEntity();
            entity.setId("synthetic-log");
            entity.setHighlightsJson("[synthetic-private-material");
            entity.setItemsJson("[synthetic-private-material");
            when(updateLogMapper.selectList(any())).thenReturn(List.of(entity));
            var result = updateLogService.getPublishedUpdateLogs();
            assertTrue(result.history().get(0).highlights().isEmpty());
            assertTrue(result.history().get(0).items().isEmpty());
            assertEquals(2, appender.list.size());
            assertTrue(appender.list.stream().allMatch(event -> !event.getFormattedMessage().contains("synthetic-private-material")));
            assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

}
