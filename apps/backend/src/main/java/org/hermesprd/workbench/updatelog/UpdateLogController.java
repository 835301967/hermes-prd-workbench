package org.hermesprd.workbench.updatelog;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/update-logs")
public class UpdateLogController {
    private final UpdateLogService updateLogService;

    public UpdateLogController(UpdateLogService updateLogService) {
        this.updateLogService = updateLogService;
    }

    @GetMapping
    public ResponseEntity<UpdateLogModels.UpdateLogSummaryResponse> getPublishedLogs() {
        return ResponseEntity.ok(updateLogService.getPublishedUpdateLogs());
    }
}
