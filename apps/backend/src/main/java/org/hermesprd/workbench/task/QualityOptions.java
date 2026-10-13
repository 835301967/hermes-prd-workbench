package org.hermesprd.workbench.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class QualityOptions {
    public static final String QUICK = "QUICK";
    public static final String STANDARD = "STANDARD";
    public static final String THOROUGH = "THOROUGH";

    public record CompletenessOption(
            String id,
            String name,
            String description,
            String costHint,
            @JsonProperty("default") boolean defaultOption
    ) {
    }

    public record LensOption(
            String id,
            String name,
            String description,
            @JsonProperty("default") boolean defaultOption
    ) {
    }

    public record Catalog(List<CompletenessOption> completeness, Map<String, List<LensOption>> lenses) {
    }

    private static final List<CompletenessOption> COMPLETENESS = List.of(
            new CompletenessOption(QUICK, "快速", "只保留关键结论和主路径，适合赶时间出草稿。", "更快、更省。", false),
            new CompletenessOption(STANDARD, "标准", "当前默认完整度，结构清楚、可继续修改。", "平衡质量和耗时。", true),
            new CompletenessOption(THOROUGH, "详尽", "按模块补规则、异常、权限和验收，便于开工。", "可能更完整，但不保证更对；时间和费用更高，也更容易超时。", false)
    );

    private static final Map<String, List<LensOption>> LENSES = new LinkedHashMap<>();

    static {
        LENSES.put("REVIEW", List.of(
                new LensOption("can-start", "开发能否开工", "范围能否实现、规则能否编码、缺哪些字段/状态/接口。", true),
                new LensOption("testable", "测试能否验收", "验收是否可测、缺哪些判定口径和前置数据。", false),
                new LensOption("scope", "范围有没有膨胀", "非目标是否被悄悄做进需求。", false)
        ));
        LENSES.put("GENERATE", List.of(
                new LensOption("module-rules", "按模块拆 · 规则和验收", "每个模块先写业务规则和可测试验收，再写页面与流程。", true),
                new LensOption("by-page", "按页面拆", "按页面写入口、主操作和结果。", false),
                new LensOption("by-role", "按角色拆", "按角色写能做什么和权限边界。", false)
        ));
        LENSES.put("DEMO", List.of(
                new LensOption("main-path", "主路径可点", "优先打通主路径。", true),
                new LensOption("more-states", "多状态关键交互", "补充空态、失败和关键中间状态。", false)
        ));
        LENSES.put("POC", List.of(
                new LensOption("main-path", "核心域做深", "按当前稿 catalog 把核心业务域打通筛选、CRUD、详情和状态流转。", true),
                new LensOption("more-states", "一级菜单可点台账", "当前稿全部一级模块都要可点台账，禁止漏模块、禁止另起菜单。", true)
        ));
        LENSES.put("ESTIMATE", List.of(
                new LensOption("fpa-standard", "标准拆分 (页面+接口)", "严格按照页面与接口维度拆解，按 0.5 天/项基准估算研发与自测工时。", true),
                new LensOption("third-party-focus", "第三方接口对接重点", "重点细化并识别外部第三方采集与业务系统的 HTTP 接口对接。", false),
                new LensOption("complex-scenarios", "复杂场景与容错上浮", "重点识别批量导入导出、报表、定时任务等复杂处理，酌情上浮 0.2~0.5 天防止亏损。", false)
        ));
        LENSES.put("TEST_CASE", List.of(
                new LensOption("all-scenarios", "正向+边界+异常全覆盖", "覆盖核心主干流、边界值极限、逆向异常与校验拦截，生成全面测试用例。", true),
                new LensOption("security-permission", "权限与安全性测试重点", "重点覆盖角色权限访问控制、越权访问拦截与敏感数据校验用例。", false),
                new LensOption("interface-exception", "接口容错与网络异常", "重点覆盖超时、断网、接口异常返回及高频重复点击等健壮性用例。", false)
        ));
        LENSES.put("TECH_SPEC", List.of(
                new LensOption("arch-focus", "架构与数据模型重点", "重点规划系统分层、领域实体、数据库表结构字段设计与索引约束。", true),
                new LensOption("api-first", "API 契约与前后端联调重点", "重点规划核心 RESTful API 规范、请求响应 JSON 报文格式及异常错误码定义。", false),
                new LensOption("module-deep", "分模块设计补充重点", "重点按一级模块补充 modules/<key>.md：页面与接口对照、规则落地、异常与权限边界。", false)
        ));
    }

    private QualityOptions() {
    }

    public static Catalog catalog() {
        return new Catalog(COMPLETENESS, LENSES);
    }

    public static String normalizeCompleteness(String raw) {
        if (raw == null || raw.isBlank()) {
            return STANDARD;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (QUICK.equals(value) || STANDARD.equals(value) || THOROUGH.equals(value)) {
            return value;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "完整度只能是 QUICK、STANDARD 或 THOROUGH");
    }

    public static List<String> normalizeLenses(String mode, List<String> raw, boolean omitted) {
        List<LensOption> allowed = LENSES.getOrDefault(mode, List.of());
        Set<String> allowedIds = new java.util.LinkedHashSet<>();
        List<String> defaults = new ArrayList<>();
        for (LensOption option : allowed) {
            allowedIds.add(option.id());
            if (option.defaultOption()) {
                defaults.add(option.id());
            }
        }
        if (omitted) {
            return List.copyOf(defaults);
        }
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> selected = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String id = item.trim();
            if (!allowedIds.contains(id)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "不支持的镜头：" + id);
            }
            if (!selected.contains(id)) {
                selected.add(id);
            }
        }
        return List.copyOf(selected);
    }

    public static String joinLenses(List<String> lenses) {
        return lenses == null || lenses.isEmpty() ? null : String.join(",", lenses);
    }

    public static List<String> splitLenses(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : stored.split(",")) {
            if (!part.isBlank()) {
                values.add(part.trim());
            }
        }
        return List.copyOf(values);
    }

    public static Duration timeout(String completeness, String mode, WorkbenchProperties properties) {
        String level = normalizeCompleteness(completeness);
        if ("POC".equals(mode)) {
            if (QUICK.equals(level)) {
                Duration cap = Duration.ofSeconds(900);
                return properties.pocRunTimeout().compareTo(cap) < 0 ? properties.pocRunTimeout() : cap;
            }
            if (THOROUGH.equals(level)) {
                return properties.pocRunTimeout().plus(Duration.ofSeconds(300));
            }
            return properties.pocRunTimeout();
        }
        if ("GENERATE".equals(mode)) {
            if (QUICK.equals(level)) {
                Duration cap = Duration.ofSeconds(900);
                return properties.generateRunTimeout().compareTo(cap) < 0 ? properties.generateRunTimeout() : cap;
            }
            if (THOROUGH.equals(level)) {
                return properties.generateRunTimeout().plus(Duration.ofSeconds(600));
            }
            return properties.generateRunTimeout();
        }
        if ("TEST_CASE".equals(mode)) {
            if (QUICK.equals(level)) {
                Duration cap = Duration.ofSeconds(900);
                return properties.testCaseRunTimeout().compareTo(cap) < 0 ? properties.testCaseRunTimeout() : cap;
            }
            if (THOROUGH.equals(level)) {
                return properties.testCaseRunTimeout().plus(Duration.ofSeconds(300));
            }
            return properties.testCaseRunTimeout();
        }
        if ("TECH_SPEC".equals(mode)) {
            if (QUICK.equals(level)) {
                Duration cap = Duration.ofSeconds(900);
                return properties.techSpecRunTimeout().compareTo(cap) < 0 ? properties.techSpecRunTimeout() : cap;
            }
            if (THOROUGH.equals(level)) {
                return properties.techSpecRunTimeout().plus(Duration.ofSeconds(300));
            }
            return properties.techSpecRunTimeout();
        }
        boolean demo = "DEMO".equals(mode);
        if (demo) {
            if (QUICK.equals(level)) {
                Duration cap = Duration.ofSeconds(600);
                return properties.demoRunTimeout().compareTo(cap) < 0 ? properties.demoRunTimeout() : cap;
            }
            if (THOROUGH.equals(level)) {
                return properties.demoRunTimeout().plus(Duration.ofSeconds(300));
            }
            return properties.demoRunTimeout();
        }
        if (QUICK.equals(level)) {
            return Duration.ofSeconds(180);
        }
        if (THOROUGH.equals(level)) {
            return properties.runTimeout().plus(Duration.ofSeconds(300));
        }
        return properties.runTimeout();
    }

    public static String promptBlock(String mode, String completeness, List<String> lenses, String parentRunId) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, completenessPrompt(mode, completeness));
        for (String lens : lenses == null ? List.<String>of() : lenses) {
            if (THOROUGH.equals(completeness) && "GENERATE".equals(mode) && "module-rules".equals(lens)) {
                continue;
            }
            if ("POC".equals(mode) && "main-path".equals(lens)) {
                appendLine(builder, "镜头：按 catalog 把核心业务域打通筛选、分页列表、中文详情、CRUD 和状态流转。");
                continue;
            }
            if ("POC".equals(mode) && "more-states".equals(lens)) {
                appendLine(builder, "镜头：必须覆盖当前稿全部一级模块：分页列表、筛选、中文表单/详情和删除，禁止空菜单、漏模块、另起菜单和英文标签。");
                continue;
            }
            appendLine(builder, lensPrompt(lens));
        }
        if (parentRunId != null && !parentRunId.isBlank()) {
            if ("POC".equals(mode)) {
                appendLine(builder, """
                        这是一次基于已有 POC 的加深或修订，不是从零另起一套菜单。
                        新沙箱是空的，不能假设上一份 POC 文件已经复制进来。
                        先阅读当前稿、data/required-modules.json 和 POC 补充材料，按当前稿模块 key 写 catalog（key、title、core、columns、fields、labels、states、actions），再写 mock 与视图。
                        保留上一轮已正确的模块边界和字段名；加深核心域交互、补齐未实现的一级菜单，不要换成无关模块。
                        材料没有的列不要用「名称/编号/负责人」凑数。详情和表单必须继续用中文 labels，列表必须分页。
                        """.trim());
            } else if ("GENERATE".equals(mode)) {
                appendLine(builder, """
                        这是一次基于当前稿的修订，不是从零重写。
                        新沙箱是空的，必须重新 write_prd_file 写入文档包。
                        必须阅读材料中的「%s」作为底稿，保留已有正确章节、模块 key 和结论方向。
                        可以改写表述、补字段矩阵和验收、把能根据原材料拍板的待确认写成明确假设；不要另起一套完全不同的 catalog。
                        待确认问题必须写入 open-questions.md 与 payload.openQuestions。
                        """.formatted(SourceRoles.draftName(parentRunId)).trim());
            } else {
                appendLine(builder, """
                        这是一次基于当前稿的修订，不是从零重写。
                        必须阅读材料中的「%s」作为底稿，保留已有正确章节、目录和结论方向。
                        可以改写表述、补验收和异常、把能根据原材料拍板的待确认写成明确假设；不要另起一套完全不同的目录。
                        不保证逐句不改。待确认问题必须写入 payload.openQuestions 数组，不要只写在正文里。
                        """.formatted(SourceRoles.draftName(parentRunId)).trim());
            }
        }
        return builder.toString().trim();
    }

    static String completenessPrompt(String mode, String completeness) {
        if (QUICK.equals(completeness)) {
            return switch (mode) {
                case "REVIEW" -> "产出完整度：快速。只给出一句结论和真正阻断开发的问题，阻断项不超过 3 条，不要展开长建议。";
                case "DEMO" -> "产出完整度：快速。只做主路径可点击的单页，不要做多状态和边缘流程。";
                case "POC" -> "产出完整度：快速 POC。从当前稿模块清单挑选 1 个核心域和最多 2 个次要台账，key 必须来自清单；每个模块仍要中文 labels、分页列表和可点表单，禁止把英文字段名画到页面上。";
                case "ESTIMATE" -> "产出完整度：快速预估。聚焦核心一级模块与关键页面/聚合接口组，条目控制在 12~18 项，快速给出基准工时与报价范围。";
                case "TEST_CASE" -> "产出完整度：快速用例。从当前稿模块清单挑选 1 个核心域和最多 2 个次要模块；每个模块写可提测的 P0 主干用例，禁止空模块。";
                case "TECH_SPEC" -> "产出完整度：快速技术方案。从当前稿模块清单挑选 1 个核心域和最多 2 个次要模块；交付可开工的架构说明、带字段的核心表、带请求/响应样例的 API。不要拆工单。";
                case "GENERATE" -> "产出完整度：快速文档包。catalog 只保留 1 个核心域和最多 2 个次要模块；每个列入 catalog 的模块仍必须写完整章节模板（页面、字段矩阵、状态机、BR-/AC-），禁止空章。";
                default -> "产出完整度：快速草稿。控制篇幅，写成一页纸结构，只保留目标、主路径和必须拍板的规则，不要展开完整验收矩阵。";
            };
        }
        if (THOROUGH.equals(completeness)) {
            return switch (mode) {
                case "REVIEW" -> """
                        产出完整度：详尽评审。请逐模块检查规则完整性、异常与验收是否足以测试，列出真正阻断开发的问题；不要为凑数增加模板化意见。
                        对每个阻断项给出影响和可执行补法。问题变多不代表材料更差，请把可延后事项放入 otherSuggestions。
                        blockingIssues 的每项必须包含 title。""";
                case "DEMO" -> """
                        产出完整度：详尽。围绕材料中的核心价值主路径展开深度增删改查、流转留痕、详情抽屉和关键状态；按用户要求与剩余预算补充代表性次要模块。
                        不要求覆盖 PRD 全目录，也不设置固定菜单数、视图数或字符数。侧栏只声明已经实现并绑定真实交互的入口；次要模块可采用 Schema 字典驱动的动态台账，但必须使用各模块自己的字段和示例数据。""";
                case "POC" -> """
                        产出完整度：详尽 POC。catalog 覆盖当前稿全部一级菜单，key 必须与当前稿 catalog 1:1 对齐。每个列入 catalog 的模块都做深：筛选、分页列表、中文表头/详情/表单、新增编辑删除；有状态则流转。
                        2～3 个核心域 mock 不少于 10 行以便翻页。禁止空菜单、漏模块、另起菜单、万能列名、英文字段名上屏、无分页长表。讲解词按侧栏真实菜单顺序写，步骤标题必须能在 catalog 模块标题中找到。""";
                case "ESTIMATE" -> "产出完整度：详尽拆分。全面覆盖各个模块的业务页面、接口组与第三方对接接口；若用户有明确的逐项对应或不合并指令，严格遵照用户指令按原始清单条目逐项拆解；单条保持精炼，确保一次性完整输出合法闭合 JSON。";
                case "TEST_CASE" -> "产出完整度：详尽用例。覆盖当前稿全部一级模块；每模块含正向、边界、异常、权限与并发防重；步骤与预期写清界面提示和数据状态。不设固定条数上限，按模块拆文件写完再提交。";
                case "TECH_SPEC" -> "产出完整度：详尽技术方案。覆盖当前稿全部一级模块：分层架构（可嵌 Mermaid）、每表字段/索引/约束、每接口请求响应与错误码、分模块设计补充。按文件拆写；导出 Markdown 须可直接给前后端实施。不要拆工单或估人天。";
                case "GENERATE" -> """
                        产出完整度：详尽可开工文档包。catalog 覆盖材料中能闭环的全部一级模块，每个模块都写深：页面清单、字段矩阵、状态机、BR-、核心模块至少 3 条 AC-。
                        禁止「保留模块」空章；做不完的写进本期不做并从 catalog 删除。待确认问题写入 open-questions.md 与 payload.openQuestions。""";
                default -> """
                        产出完整度：可开发详尽稿。
                        请写成开发可直接开工的 PRD：按模块拆分；每个模块包含目标、用户路径、业务规则、状态与异常、权限、验收标准；材料冲突处写明假设并放入 openQuestions。
                        不要写成空泛目录或一页纸摘要。待确认问题必须写入 payload.openQuestions 数组，不要只写在正文里。""";
            };
        }
        return switch (mode) {
            case "REVIEW" -> "产出完整度：标准。抓住真正影响开发、测试和上线的问题，允许材料已经足够好。";
            case "DEMO" -> "产出完整度：标准。按 skill 完成可本地打开的主路径原型，只做必要导航，不要铺成巨型单页。";
            case "POC" -> "产出完整度：标准 POC。从材料抽出 catalog 作为唯一事实源；若已有当前稿模块清单，key 必须 1:1 对齐并覆盖全部一级菜单。列入 catalog 的每个模块都要：中文 columns/labels、分页列表、筛选、新增编辑、详情（中文标签）、删除确认；1～2 个核心域再加状态流转与空/错态。禁止英文字段名上屏，禁止无分页的一屏堆完，禁止另起一套无关菜单。静态多文件 hash 路由，禁止 Vue/CDN/fetch。";
            case "ESTIMATE" -> "产出完整度：标准评估。按页面与接口维度拆分核心功能，输出开发与自测人天、说明与汇总；若用户有明确条目要求则优先遵从用户指令。";
            case "TEST_CASE" -> "产出完整度：标准用例。覆盖当前稿全部一级模块；每模块至少含主干正向与关键异常/边界。不设 18～32 条硬顶，按 modules/<key>.json 拆写。";
            case "TECH_SPEC" -> "产出完整度：标准技术方案。覆盖当前稿全部一级模块：架构可实施、表带字段、API 带契约样例；建议补 modules/<key>.md。按文件拆写；禁止只交架构摘要，不要拆工单。";
            case "GENERATE" -> "产出完整度：标准文档包。从材料抽出 catalog 作为唯一事实源；列入 catalog 的模块都按固定章节模板写完，禁止空章。不要压缩成 15k 提纲。";
            default -> "产出完整度：标准。写成结构清楚、可继续修改的 PRD 初稿。";
        };
    }

    static String lensPrompt(String lensId) {
        return switch (lensId) {
            case "can-start" -> "请按「开发能否开工」视角：范围是否可实现、规则是否可编码、缺哪些字段/状态/接口、哪些问题不解决就无法排期。";
            case "testable" -> "请按「测试能否验收」视角：验收是否可测、缺哪些判定口径和前置数据。";
            case "scope" -> "请关注范围是否膨胀，以及非目标是否被悄悄做进需求。";
            case "module-rules" -> "请按功能模块拆分文档结构，每个模块先写清业务规则和可测试验收标准，再写页面与流程。待确认问题必须写入 payload.openQuestions。";
            case "by-page" -> "请按页面拆分结构，每个页面写清入口、主操作和结果。";
            case "by-role" -> "请按角色拆分：每个角色能做什么，以及关键权限边界。";
            case "main-path" -> "优先打通主路径，让关键操作可点击完成。";
            case "more-states" -> "在核心主流程上补充驳回/失败、待确认与已完成关键状态，复用通用提示与弹窗，避免代码膨胀。";
            case "fpa-standard" -> "拆分镜头：标准拆分。严格遵循 FPA 功能点分析法，全面分解为页面与接口，每个接口与页面明确给出开发与自测人天。";
            case "third-party-focus" -> "拆分镜头：第三方对接重点。系统性盘点所有与外部系统的对接与数据采集接口，标记对接复杂度与接口协议。";
            case "complex-scenarios" -> "拆分镜头：复杂场景重点。细致审视包含大量字段、批量导入导出、复杂聚合统计、定时任务的业务场景，合理追加 0.2~0.5 人天。";
            case "all-scenarios" -> "测试镜头：全场景覆盖。全面覆盖正向主流程、输入边界值临界状态、逆向异常拦截与系统容错。";
            case "security-permission" -> "测试镜头：权限与安全重点。重点增加不同角色权限越权校验、敏感数据防泄露与非法参数篡改测试用例。";
            case "interface-exception" -> "测试镜头：接口与异常重点。重点增加接口超时、网络中断重试、弱网降级与高频重复提交幂等测试用例。";
            case "arch-focus" -> "技术镜头：架构与模型重点。系统性设计数据库表结构、核心字段类型与索引约束，理清实体间关联与分层架构职责。";
            case "api-first" -> "技术镜头：接口契约重点。全面定义核心接口的 HTTP 方法、路径、请求体参数样例、返回结构以及标准业务错误码。";
            case "module-deep" -> "技术镜头：分模块设计补充。按一级模块写 modules/<key>.md，对照页面与接口、规则落地、异常与权限边界，便于前后端直接实施。";
            case "task-schedule" -> "技术镜头：分模块设计补充。按一级模块写清实现边界与联调约定；不要输出工单、人天或排期。";
            default -> "";
        };
    }

    private static void appendLine(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(text.trim());
    }
}
