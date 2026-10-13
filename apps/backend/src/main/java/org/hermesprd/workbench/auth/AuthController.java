package org.hermesprd.workbench.auth;

import org.hermesprd.workbench.auth.AuthModels.AuthResponse;
import org.hermesprd.workbench.auth.AuthModels.LoginRequest;
import org.hermesprd.workbench.auth.AuthModels.RegisterRequest;
import org.hermesprd.workbench.auth.AuthModels.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return toResponse(authService.register(request.username(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authService.logout(SessionAuthInterceptor.readToken(request));
        CurrentUser.clear();
    }

    @GetMapping("/me")
    public UserResponse me() {
        return toUser(CurrentUser.require());
    }

    private AuthResponse toResponse(AuthService.AuthSession session) {
        return new AuthResponse(session.token(), session.expiresAt(), toUser(session.user()));
    }

    private UserResponse toUser(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
