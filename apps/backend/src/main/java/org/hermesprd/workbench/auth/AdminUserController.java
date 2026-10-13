package org.hermesprd.workbench.auth;

import org.hermesprd.workbench.telemetry.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AuthService authService;
    private final TelemetryService telemetryService;

    public AdminUserController(AuthService authService, TelemetryService telemetryService) {
        this.authService = authService;
        this.telemetryService = telemetryService;
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<AuthModels.UserResponse> updateUserRole(
            @PathVariable("userId") String targetUserId,
            @Valid @RequestBody AuthModels.UpdateRoleRequest request
    ) {
        UserEntity operator = CurrentUser.get();
        String operatorId = operator != null ? operator.getId() : null;
        UserEntity updated = authService.updateUserRole(operatorId, targetUserId, request.role());
        if (telemetryService != null) {
            telemetryService.recordEvent(
                    "USER_MGMT", "USER", "UPDATE_ROLE", "SUCCEEDED", null, null, null,
                    Map.of(
                            "targetUserId", targetUserId,
                            "targetUsername", updated.getUsername(),
                            "newRole", updated.getRole()
                    )
            );
        }
        return ResponseEntity.ok(toUser(updated));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthModels.UserResponse> createUser(
            @Valid @RequestBody AuthModels.AdminCreateUserRequest request
    ) {
        UserEntity created = authService.createUserByAdmin(
                request.username(),
                request.password(),
                request.displayName(),
                request.role()
        );
        if (telemetryService != null) {
            telemetryService.recordEvent(
                    "USER_MGMT", "USER", "CREATE_USER", "SUCCEEDED", null, null, null,
                    Map.of(
                            "targetUserId", created.getId(),
                            "targetUsername", created.getUsername(),
                            "role", created.getRole()
                    )
            );
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toUser(created));
    }

    private AuthModels.UserResponse toUser(UserEntity user) {
        return new AuthModels.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
    }
}
