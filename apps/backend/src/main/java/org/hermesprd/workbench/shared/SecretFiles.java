package org.hermesprd.workbench.shared;

import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SecretFiles {
    private SecretFiles() {
    }

    public static String readRequired(Path path, String name) {
        if (path == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_MISSING", "缺少配置: " + name);
        }
        try {
            String value = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_MISSING", "空的密钥文件: " + name);
            }
            return value;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_MISSING", "无法读取密钥文件: " + name);
        }
    }
}
