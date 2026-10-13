package org.hermesprd.workbench.hermes;

import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.SecretFiles;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PluginAuthInterceptor implements HandlerInterceptor {
    private final WorkbenchProperties properties;

    public PluginAuthInterceptor(WorkbenchProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String expected = SecretFiles.readRequired(properties.pluginKeyFile(), "plugin-key-file");
        String provided = request.getHeader("X-PRD-Plugin-Key");
        if (provided == null || !expected.equals(provided)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "插件认证失败");
        }
        return true;
    }
}
