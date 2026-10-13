package org.hermesprd.workbench.auth;

import org.hermesprd.workbench.shared.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private SessionMapper sessionMapper;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, sessionMapper);
    }

    @Test
    void registerCreatesUserAndSession() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(UserEntity.class))).thenReturn(1);
        when(sessionMapper.insert(any(SessionEntity.class))).thenReturn(1);

        AuthService.AuthSession session = authService.register("Alice", "password1", "产品经理");

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("alice", userCaptor.getValue().getUsername());
        assertEquals("产品经理", session.user().getDisplayName());
        assertFalse(session.token().isBlank());
        assertTrue(Credentials.passwordMatches("password1", userCaptor.getValue().getPasswordHash()));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        ApiException ex = assertThrows(ApiException.class, () -> authService.register("alice", "password1", null));
        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertEquals("USERNAME_TAKEN", ex.code());
    }

    @Test
    void loginRejectsWrongPassword() {
        UserEntity user = new UserEntity();
        user.setUsername("alice");
        user.setPasswordHash(Credentials.hashPassword("password1"));
        when(userMapper.selectOne(any())).thenReturn(user);

        ApiException ex = assertThrows(ApiException.class, () -> authService.login("alice", "wrong-pass"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status());
    }

    @Test
    void registerRejectsUtf8ByteOverflowBeforeDatabaseWrite() {
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.register("alice", "密".repeat(25), null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertEquals("INVALID_REQUEST", ex.code());
        org.mockito.Mockito.verifyNoInteractions(userMapper, sessionMapper);
    }

    @Test
    void loginOverlongPasswordIsUnauthorizedRatherThanServerError() {
        UserEntity user = new UserEntity();
        user.setPasswordHash(Credentials.hashPassword("a".repeat(72)));
        when(userMapper.selectOne(any())).thenReturn(user);
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login("alice", "a".repeat(72) + "x"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status());
        org.mockito.Mockito.verifyNoInteractions(sessionMapper);
    }
}
