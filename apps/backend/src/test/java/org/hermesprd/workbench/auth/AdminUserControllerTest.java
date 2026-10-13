package org.hermesprd.workbench.auth;

import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.telemetry.TelemetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserControllerTest {
    private AuthService authService;
    private TelemetryService telemetryService;
    private AdminUserController controller;

    private UserEntity adminUser;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        telemetryService = mock(TelemetryService.class);
        controller = new AdminUserController(authService, telemetryService);

        adminUser = new UserEntity();
        adminUser.setId("user_admin_1");
        adminUser.setUsername("admin");
        adminUser.setRole("ADMIN");
        CurrentUser.set(adminUser);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    void updateUserRoleToAdminSuccess() {
        UserEntity target = new UserEntity();
        target.setId("user_target_2");
        target.setUsername("demo");
        target.setDisplayName("体验用户");
        target.setRole("ADMIN");

        when(authService.updateUserRole("user_admin_1", "user_target_2", "ADMIN")).thenReturn(target);

        ResponseEntity<AuthModels.UserResponse> resp = controller.updateUserRole(
                "user_target_2", new AuthModels.UpdateRoleRequest("ADMIN")
        );

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("ADMIN", resp.getBody().role());
        assertEquals("user_target_2", resp.getBody().id());

        verify(telemetryService).recordEvent(
                eq("USER_MGMT"), eq("USER"), eq("UPDATE_ROLE"), eq("SUCCEEDED"), any(), any(), any(), any()
        );
    }

    @Test
    void cannotDemoteSelfThrowsException() {
        when(authService.updateUserRole("user_admin_1", "user_admin_1", "USER"))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DEMOTE_SELF", "不能取消自身的管理员权限"));

        ApiException ex = assertThrows(ApiException.class, () ->
                controller.updateUserRole("user_admin_1", new AuthModels.UpdateRoleRequest("USER"))
        );

        assertEquals("CANNOT_DEMOTE_SELF", ex.code());
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    }

    @Test
    void createAdminUserSuccess() {
        UserEntity created = new UserEntity();
        created.setId("user_new_3");
        created.setUsername("new_admin");
        created.setDisplayName("新管理员");
        created.setRole("ADMIN");

        when(authService.createUserByAdmin("new_admin", "password123", "新管理员", "ADMIN")).thenReturn(created);

        ResponseEntity<AuthModels.UserResponse> resp = controller.createUser(
                new AuthModels.AdminCreateUserRequest("new_admin", "password123", "新管理员", "ADMIN")
        );

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("ADMIN", resp.getBody().role());
        assertEquals("new_admin", resp.getBody().username());
    }
}
