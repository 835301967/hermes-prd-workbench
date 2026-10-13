package org.hermesprd.workbench.hermes;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InternalWebConfig implements WebMvcConfigurer {
    private final PluginAuthInterceptor pluginAuthInterceptor;

    public InternalWebConfig(PluginAuthInterceptor pluginAuthInterceptor) {
        this.pluginAuthInterceptor = pluginAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pluginAuthInterceptor).addPathPatterns("/internal/v1/**");
    }
}
