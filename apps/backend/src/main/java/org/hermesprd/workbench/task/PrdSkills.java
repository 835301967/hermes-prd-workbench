package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class PrdSkills {
    public static final String ENTERPRISE_PRD = "enterprise-prd";

    private PrdSkills() {
    }

    public static String prompt() {
        try (InputStream in = PrdSkills.class.getResourceAsStream("/prd-skills/enterprise-prd.md")) {
            if (in == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "未找到 PRD skill 文本");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "无法读取 PRD skill 文本");
        }
    }
}
