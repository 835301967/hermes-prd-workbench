package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DemoSkills {
    public static final String GENERIC_ADMIN_HTML = "generic-admin-html";
    public static final String DEFAULT = GENERIC_ADMIN_HTML;

    public record SkillInfo(String id, String name, String description) {
    }

    private static final Map<String, SkillInfo> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put(GENERIC_ADMIN_HTML, new SkillInfo(GENERIC_ADMIN_HTML, "通用 HTML 交互原型", "材料驱动的自包含 HTML 原型，不依赖私有宿主或构建工具"));
        CATALOG.put("erp-enterprise", new SkillInfo("erp-enterprise", "ERP 供应链与业务单据", "高密度单据主子表、物料行网格、单据状态流转与审批"));
        CATALOG.put("crm-pipeline", new SkillInfo("crm-pipeline", "CRM 客户与商机管道", "商机阶段推进漏斗看板、客户 360° 全景画像与跟进动态流"));
        CATALOG.put("finance-ledger", new SkillInfo("finance-ledger", "财务会计与核算对账", "借贷分录记账凭证、借贷平账校验、千分位金额与账期勾稽"));
        CATALOG.put("frontend-design", new SkillInfo("frontend-design", "通用工作台与业务台", "标准工作台与通用管理操作台"));
        CATALOG.put("lightweight-html", new SkillInfo("lightweight-html", "轻量交互原型", "极简本地 HTML 交互原型"));
    }

    private DemoSkills() {
    }

    public static List<SkillInfo> catalog() {
        return List.copyOf(CATALOG.values());
    }

    public static String normalize(String skill) {
        String id = skill == null || skill.isBlank() ? DEFAULT : skill.trim();
        if (!CATALOG.containsKey(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "不支持的 Demo skill，当前可选 " + String.join(", ", CATALOG.keySet()));
        }
        return id;
    }

    public static String promptFor(String skill) {
        String id = normalize(skill);
        String resource = "/demo-skills/" + id + ".md";
        try (InputStream in = DemoSkills.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "未找到 Demo skill 文本");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "无法读取 Demo skill 文本");
        }
    }

}
