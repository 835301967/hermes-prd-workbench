package org.hermesprd.workbench.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    private final SessionAuthInterceptor sessionAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public AuthWebConfig(SessionAuthInterceptor sessionAuthInterceptor, AdminAuthInterceptor adminAuthInterceptor) {
        this.sessionAuthInterceptor = sessionAuthInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionAuthInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/health", "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/update-logs");

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/v1/admin/**");
    }
}
