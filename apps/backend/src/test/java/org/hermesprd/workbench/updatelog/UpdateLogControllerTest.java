package org.hermesprd.workbench.updatelog;

import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.telemetry.TelemetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UpdateLogControllerTest {
    private UpdateLogService updateLogService;
    private TelemetryService telemetryService;
    private UpdateLogController publicController;
    private AdminUpdateLogController adminController;

    private UserEntity adminUser;

    @BeforeEach
    void setUp() {
        updateLogService = mock(UpdateLogService.class);
        telemetryService = mock(TelemetryService.class);
        publicController = new UpdateLogController(updateLogService);
        adminController = new AdminUpdateLogController(updateLogService, telemetryService);

        adminUser = new UserEntity();
        adminUser.setId("admin_1");
        adminUser.setUsername("admin");
        adminUser.setRole("ADMIN");
        CurrentUser.set(adminUser);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    void publicControllerReturnsPublishedLogs() {
        UpdateLogModels.UpdateLogDto latest = new UpdateLogModels.UpdateLogDto(
                "log-1", "V0.3.8", "2026-09-18", "标题", "概述",
                List.of("亮点"), List.of(), true, "PUBLISHED", "sys", Instant.now(), Instant.now()
        );
        UpdateLogModels.UpdateLogSummaryResponse summary = new UpdateLogModels.UpdateLogSummaryResponse(
                latest, List.of(latest)
        );
        when(updateLogService.getPublishedUpdateLogs()).thenReturn(summary);

        ResponseEntity<UpdateLogModels.UpdateLogSummaryResponse> resp = publicController.getPublishedLogs();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("V0.3.8", resp.getBody().latest().version());
    }

    @Test
    void adminControllerCreateUpdateLogRecordsTelemetry() {
        UpdateLogModels.CreateUpdateLogRequest req = new UpdateLogModels.CreateUpdateLogRequest(
                "V0.3.9", "2026-09-19", "新版本", "新概述",
                List.of(), List.of(), true, "PUBLISHED"
        );
        UpdateLogModels.UpdateLogDto created = new UpdateLogModels.UpdateLogDto(
                "log-2", "V0.3.9", "2026-09-19", "新版本", "新概述",
                List.of(), List.of(), true, "PUBLISHED", "admin_1", Instant.now(), Instant.now()
        );
        when(updateLogService.createUpdateLog(eq("admin_1"), any())).thenReturn(created);

        ResponseEntity<UpdateLogModels.UpdateLogDto> resp = adminController.createUpdateLog(req);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals("V0.3.9", resp.getBody().version());

        verify(telemetryService).recordEvent(
                eq("UPDATE_LOG"), eq("CHANGELOG"), eq("CREATE"), eq("SUCCEEDED"), any(), any(), any(), any()
        );
    }

    @Test
    void adminControllerToggleStatusSuccess() {
        UpdateLogModels.UpdateStatusRequest req = new UpdateLogModels.UpdateStatusRequest("DRAFT", false);
        UpdateLogModels.UpdateLogDto updated = new UpdateLogModels.UpdateLogDto(
                "log-2", "V0.3.9", "2026-09-19", "新版本", "新概述",
                List.of(), List.of(), false, "DRAFT", "admin_1", Instant.now(), Instant.now()
        );
        when(updateLogService.toggleStatus("admin_1", "log-2", "DRAFT", false)).thenReturn(updated);

        ResponseEntity<UpdateLogModels.UpdateLogDto> resp = adminController.toggleStatus("log-2", req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("DRAFT", resp.getBody().status());
        assertFalse(resp.getBody().isLatest());
    }
}
