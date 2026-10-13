package org.hermesprd.workbench.auth;

import org.hermesprd.workbench.shared.ApiExceptionHandler;
import org.hermesprd.workbench.telemetry.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {
    private UserMapper users;
    private SessionMapper sessions;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users = mock(UserMapper.class);
        sessions = mock(SessionMapper.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(new AuthService(users, sessions)))
                .setControllerAdvice(new ApiExceptionHandler(mock(TelemetryService.class))).build();
    }

    @Test
    void asciiSuffixBeyondBcryptBoundaryReturns401ThroughHttpValidation() throws Exception {
        UserEntity user = new UserEntity();
        user.setPasswordHash(Credentials.hashPassword("a".repeat(72)));
        when(users.selectOne(any())).thenReturn(user);
        login("a".repeat(72) + "x", 401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(sessions);
    }

    @Test
    void multibytePasswordOverflowReturns401ThroughHttpValidation() throws Exception {
        UserEntity user = new UserEntity();
        user.setPasswordHash(Credentials.hashPassword("界".repeat(24)));
        when(users.selectOne(any())).thenReturn(user);
        login("界".repeat(25), 401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(sessions);
    }

    @Test
    void excessiveRequestLengthIsRejectedBeforeAuthentication() throws Exception {
        login("x".repeat(4097), 400, "INVALID_REQUEST");
        verifyNoInteractions(users, sessions);
    }

    private void login(String password, int status, String code) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }
}
