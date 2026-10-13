package org.hermesprd.workbench.telemetry;

import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {
    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> recordClientEvent(@Valid @RequestBody TelemetryModels.ClientEventRequest request) {
        if (request != null) {
            telemetryService.recordEvent(
                    request.eventType(),
                    request.module(),
                    request.action(),
                    request.status(),
                    request.durationMs(),
                    request.errorCode(),
                    request.errorMessage(),
                    request.metadata()
            );
        }
        return ResponseEntity.ok(Map.of("status", "RECEIVED"));
    }
}
