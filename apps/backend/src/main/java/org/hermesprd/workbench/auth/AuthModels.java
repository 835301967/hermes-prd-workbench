package org.hermesprd.workbench.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthModels {
    private AuthModels() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 32) String username,
            @NotBlank @Size(max = 72) String password,
            @Size(max = 64) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 32) String username,
            @NotBlank @Size(max = 4096) String password
    ) {
    }

    public record UserResponse(String id, String username, String displayName, String role) {
        public UserResponse(String id, String username, String displayName) {
            this(id, username, displayName, "USER");
        }
    }

    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 20) String role
    ) {
    }

    public record AdminCreateUserRequest(
            @NotBlank @Size(max = 32) String username,
            @NotBlank @Size(max = 72) String password,
            @Size(max = 64) String displayName,
            @NotBlank @Size(max = 20) String role
    ) {
    }
}
