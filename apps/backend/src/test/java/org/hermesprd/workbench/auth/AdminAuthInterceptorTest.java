package org.hermesprd.workbench.auth;

import org.hermesprd.workbench.shared.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AdminAuthInterceptorTest {
    private AdminAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor();
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    void allowsOptionsRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/v1/admin/analytics/overview");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req, res, new Object()));
    }

    @Test
    void rejectsUnauthenticatedUser() {
        CurrentUser.clear();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/analytics/overview");
        MockHttpServletResponse res = new MockHttpServletResponse();
        ApiException ex = assertThrows(ApiException.class, () -> interceptor.preHandle(req, res, new Object()));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status());
    }

    @Test
    void rejectsOrdinaryUserWith403() {
        UserEntity user = new UserEntity();
        user.setId("u1");
        user.setUsername("bob");
        user.setRole("USER");
        CurrentUser.set(user);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/analytics/overview");
        MockHttpServletResponse res = new MockHttpServletResponse();
        ApiException ex = assertThrows(ApiException.class, () -> interceptor.preHandle(req, res, new Object()));
        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertEquals("FORBIDDEN", ex.code());
    }

    @Test
    void allowsAdminUser() {
        UserEntity user = new UserEntity();
        user.setId("u_admin");
        user.setUsername("admin");
        user.setRole("ADMIN");
        CurrentUser.set(user);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/analytics/overview");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req, res, new Object()));
    }
}
