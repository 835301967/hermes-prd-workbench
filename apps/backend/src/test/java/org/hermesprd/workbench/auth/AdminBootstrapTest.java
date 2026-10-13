package org.hermesprd.workbench.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    @TempDir Path directory;

    @Test void explicitFileCreatesAdministratorWithoutIssuingASession() throws Exception {
        AuthService auth = mock(AuthService.class);
        UserMapper users = mock(UserMapper.class);
        when(users.selectCount(any())).thenReturn(0L);
        Path password = directory.resolve("bootstrap");
        Files.writeString(password, "synthetic-bootstrap-password\n");
        new AdminBootstrap(auth, users, "maintainer", password.toString()).initialize();
        verify(auth).createUserByAdmin("maintainer", "synthetic-bootstrap-password", "maintainer", "ADMIN");
        verifyNoMoreInteractions(auth);
    }

    @Test void preservesPasswordWhitespaceAndRemovesOnlyOneFileNewline() throws Exception {
        AuthService auth = mock(AuthService.class);
        UserMapper users = mock(UserMapper.class);
        when(users.selectCount(any())).thenReturn(0L);
        Path password = directory.resolve("bootstrap");
        Files.writeString(password, " password with spaces \r\n");
        new AdminBootstrap(auth, users, "maintainer", password.toString()).initialize();
        verify(auth).createUserByAdmin("maintainer", " password with spaces ", "maintainer", "ADMIN");
    }

    @Test void existingAdminDoesNotReadFileOrChangeAccounts() {
        AuthService auth = mock(AuthService.class);
        UserMapper users = mock(UserMapper.class);
        when(users.selectCount(any())).thenReturn(1L);
        new AdminBootstrap(auth, users, "maintainer", "missing").initialize();
        verifyNoInteractions(auth);
    }

    @Test void missingFileFailsInsteadOfUsingADefaultPassword() {
        AuthService auth = mock(AuthService.class);
        UserMapper users = mock(UserMapper.class);
        when(users.selectCount(any())).thenReturn(0L);
        assertThrows(IllegalStateException.class, () -> new AdminBootstrap(auth, users, "maintainer", "missing").initialize());
        verifyNoInteractions(auth);
    }

    @Test void noExplicitBootstrapCannotGrantPrivilege() {
        AuthService auth = mock(AuthService.class);
        UserMapper users = mock(UserMapper.class);
        new AdminBootstrap(auth, users, "", "").initialize();
        verifyNoInteractions(auth, users);
    }
}
