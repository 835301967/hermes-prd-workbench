package org.hermesprd.workbench.telemetry;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {
    private final TelemetryService telemetryService;

    public AdminAnalyticsController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<TelemetryModels.AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(telemetryService.getDashboard());
    }

    @GetMapping("/overview")
    public ResponseEntity<TelemetryModels.OverviewResponse> getOverview() {
        return ResponseEntity.ok(telemetryService.getOverview());
    }

    @GetMapping("/users")
    public ResponseEntity<List<TelemetryModels.UserProfileDto>> getUsers() {
        return ResponseEntity.ok(telemetryService.getUserProfiles());
    }

    @GetMapping("/features")
    public ResponseEntity<List<TelemetryModels.FeatureUsageDto>> getFeatures(
            @RequestParam(name = "days", defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(telemetryService.getFeatureUsage(days));
    }

    @GetMapping("/errors")
    public ResponseEntity<List<TelemetryModels.ErrorStatDto>> getErrors(
            @RequestParam(name = "days", defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(telemetryService.getErrorStats(days));
    }

    @GetMapping("/recent-errors")
    public ResponseEntity<List<TelemetryModels.RecentErrorEventDto>> getRecentErrors(
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(telemetryService.getRecentErrors(limit));
    }
}
