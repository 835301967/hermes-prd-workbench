package org.hermesprd.workbench.updatelog;

import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.telemetry.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/update-logs")
public class AdminUpdateLogController {
    private final UpdateLogService updateLogService;
    private final TelemetryService telemetryService;

    public AdminUpdateLogController(UpdateLogService updateLogService, TelemetryService telemetryService) {
        this.updateLogService = updateLogService;
        this.telemetryService = telemetryService;
    }

    @GetMapping
    public ResponseEntity<List<UpdateLogModels.UpdateLogDto>> getAdminLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(updateLogService.getAdminUpdateLogs(keyword, status));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UpdateLogModels.UpdateLogDto> createUpdateLog(
            @Valid @RequestBody UpdateLogModels.CreateUpdateLogRequest request
    ) {
        UserEntity operator = CurrentUser.get();
        String operatorId = operator != null ? operator.getId() : "system";
        UpdateLogModels.UpdateLogDto created = updateLogService.createUpdateLog(operatorId, request);
        if (telemetryService != null) {
            telemetryService.recordEvent(
                    "UPDATE_LOG", "CHANGELOG", "CREATE", "SUCCEEDED", null, null, null,
                    Map.of(
                            "id", created.id(),
                            "version", created.version(),
                            "status", created.status()
                    )
            );
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UpdateLogModels.UpdateLogDto> updateUpdateLog(
            @PathVariable String id,
            @Valid @RequestBody UpdateLogModels.UpdateUpdateLogRequest request
    ) {
        UserEntity operator = CurrentUser.get();
        String operatorId = operator != null ? operator.getId() : "system";
        UpdateLogModels.UpdateLogDto updated = updateLogService.updateUpdateLog(operatorId, id, request);
        if (telemetryService != null) {
            telemetryService.recordEvent(
                    "UPDATE_LOG", "CHANGELOG", "UPDATE", "SUCCEEDED", null, null, null,
                    Map.of(
                            "id", updated.id(),
                            "version", updated.version(),
                            "status", updated.status()
                    )
            );
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteUpdateLog(@PathVariable String id) {
        UserEntity operator = CurrentUser.get();
        String operatorId = operator != null ? operator.getId() : "system";
        updateLogService.deleteUpdateLog(operatorId, id);
        if (telemetryService != null) {
            telemetryService.recordEvent(
                    "UPDATE_LOG", "CHANGELOG", "DELETE", "SUCCEEDED", null, null, null,
                    Map.of("id", id)
            );
        }
        return ResponseEntity.ok(Map.of("success", true, "id", id));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<UpdateLogModels.UpdateLogDto> toggleStatus(
            @PathVariable String id,
            @RequestBody UpdateLogModels.UpdateStatusRequest request
    ) {
        UserEntity operator = CurrentUser.get();
        String operatorId = operator != null ? operator.getId() : "system";
        UpdateLogModels.UpdateLogDto updated = updateLogService.toggleStatus(operatorId, id, request.status(), request.isLatest());
        if (telemetryService != null) {
            telemetryService.recordEvent(
                    "UPDATE_LOG", "CHANGELOG", "TOGGLE_STATUS", "SUCCEEDED", null, null, null,
                    Map.of(
                            "id", updated.id(),
                            "status", updated.status(),
                            "isLatest", updated.isLatest()
                    )
            );
        }
        return ResponseEntity.ok(updated);
    }
}
