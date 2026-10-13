package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class PocSkills {
    public static final String STATIC_ADMIN_POC = "static-admin-poc";

    private PocSkills() {
    }

    public static String prompt() {
        try (InputStream in = PocSkills.class.getResourceAsStream("/poc-skills/static-admin-poc.md")) {
            if (in == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "未找到 POC skill 文本");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "无法读取 POC skill 文本");
        }
    }
}
