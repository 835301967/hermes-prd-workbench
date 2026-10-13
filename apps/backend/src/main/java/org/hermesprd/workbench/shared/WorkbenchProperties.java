package org.hermesprd.workbench.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "workbench")
public record WorkbenchProperties(
        Path uploadDir,
        long maxSourceBytes,
        long maxResultBytes,
        int sourcePageChars,
        @DefaultValue("600s") Duration runTimeout,
        @DefaultValue("1200s") Duration demoRunTimeout,
        @DefaultValue("2400s") Duration pocRunTimeout,
        @DefaultValue("2400s") Duration generateRunTimeout,
        @DefaultValue("2400s") Duration testCaseRunTimeout,
        @DefaultValue("2400s") Duration techSpecRunTimeout,
        Hermes hermes,
        Path pluginKeyFile
) {
    public record Hermes(
            String baseUrl,
            Path apiKeyFile,
            String profile,
            String expectedVersion,
            @DefaultValue("3s") Duration connectTimeout
    ) {
    }
}
