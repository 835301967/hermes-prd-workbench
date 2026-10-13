import type { Completeness, Mode } from "./api";

export type CompletenessOption = {
  id: Completeness;
  name: string;
  description: string;
  costHint: string;
  default: boolean;
};

export type LensOption = {
  id: string;
  name: string;
  description: string;
  default: boolean;
};

export type QualityCatalog = {
  completeness: CompletenessOption[];
  lenses: Record<Mode, LensOption[]>;
};

export const FALLBACK_QUALITY: QualityCatalog = {
  completeness: [
    {
      id: "QUICK",
      name: "快速",
      description: "只保留关键结论和主路径，适合赶时间出草稿。",
      costHint: "更快、更省。",
      default: false,
    },
    {
      id: "STANDARD",
      name: "标准",
      description: "当前默认完整度，结构清楚、可继续修改。",
      costHint: "平衡质量和耗时。",
      default: true,
    },
    {
      id: "THOROUGH",
      name: "详尽",
      description: "按模块补规则、异常、权限和验收，便于开工。",
      costHint: "可能更完整，但不保证更对；时间和费用更高，也更容易超时。",
      default: false,
    },
  ],
  lenses: {
    REVIEW: [
      { id: "can-start", name: "开发能否开工", description: "范围能否实现、规则能否编码、缺哪些字段/状态/接口。", default: true },
      { id: "testable", name: "测试能否验收", description: "验收是否可测、缺哪些判定口径和前置数据。", default: false },
      { id: "scope", name: "范围有没有膨胀", description: "非目标是否被悄悄做进需求。", default: false },
    ],
    GENERATE: [
      { id: "module-rules", name: "按模块拆 · 规则和验收", description: "每个模块先写业务规则和可测试验收，再写页面与流程。", default: true },
      { id: "by-page", name: "按页面拆", description: "按页面写入口、主操作和结果。", default: false },
      { id: "by-role", name: "按角色拆", description: "按角色写能做什么和权限边界。", default: false },
    ],
    DEMO: [
      { id: "main-path", name: "主路径可点", description: "优先打通主路径。", default: true },
      { id: "more-states", name: "多状态关键交互", description: "补充空态、失败和关键中间状态。", default: false },
    ],
    POC: [
      { id: "main-path", name: "核心域做深", description: "按当前稿 catalog 把核心业务域打通筛选、CRUD、详情和状态流转。", default: true },
      { id: "more-states", name: "一级菜单可点台账", description: "当前稿全部一级模块都要可点台账，禁止漏模块、禁止另起菜单。", default: true },
    ],
    ESTIMATE: [
      { id: "fpa-standard", name: "标准拆分 (页面+接口)", description: "严格按照页面与接口维度拆解，按 0.5 天/项基准估算研发与自测工时。", default: true },
      { id: "third-party-focus", name: "第三方接口对接重点", description: "重点细化并识别外部第三方采集与业务系统的 HTTP 接口对接。", default: false },
      { id: "complex-scenarios", name: "复杂场景与容错上浮", description: "重点识别批量导入导出、报表、定时任务等复杂处理，酌情上浮 0.2~0.5 天防止亏损。", default: false },
    ],
    TEST_CASE: [
      { id: "all-scenarios", name: "正向+边界+异常全覆盖", description: "覆盖核心主干流、边界值极限、逆向异常与校验拦截，生成全面测试用例。", default: true },
      { id: "security-permission", name: "权限与安全性测试重点", description: "重点覆盖角色权限访问控制、越权访问拦截与敏感数据校验用例。", default: false },
      { id: "interface-exception", name: "接口容错与网络异常", description: "重点覆盖超时、断网、接口异常返回及高频重复点击等健壮性用例。", default: false },
    ],
    TECH_SPEC: [
      { id: "arch-focus", name: "架构与模型重点", description: "重点规划系统分层、领域实体、数据库表结构字段设计与索引约束。", default: true },
      { id: "api-first", name: "API契约与联调重点", description: "重点规划核心 RESTful API 规范、请求响应 JSON 报文格式及异常错误码定义。", default: false },
      { id: "task-schedule", name: "任务拆解与排期重点", description: "重点细化前后端开发工单拆解、前后置依赖、工时人天估算与开工路线图。", default: false },
    ],
  },
};

export function defaultLenses(catalog: QualityCatalog, mode: Mode) {
  return (catalog.lenses[mode] || []).filter((item) => item.default).map((item) => item.id);
}

export function extraLenses(catalog: QualityCatalog, mode: Mode) {
  return (catalog.lenses[mode] || []).filter((item) => !item.default);
}

export function applyExtraLens(catalog: QualityCatalog, mode: Mode, current: string[], extraId: string) {
  const extras = extraLenses(catalog, mode).map((item) => item.id);
  if (!extras.includes(extraId)) {
    return current;
  }
  const selectedExtras = toggleLens(
    extras.filter((id) => current.includes(id)),
    extraId
  );
  const defaults = defaultLenses(catalog, mode);
  if (mode === "GENERATE") {
    return selectedExtras.length ? selectedExtras : defaults;
  }
  return [...defaults, ...selectedExtras.filter((id) => !defaults.includes(id))];
}

export function toggleLens(selected: string[], id: string) {
  return selected.includes(id) ? selected.filter((item) => item !== id) : [...selected, id];
}

export function completenessOption(catalog: QualityCatalog, id: Completeness) {
  return catalog.completeness.find((item) => item.id === id) || catalog.completeness.find((item) => item.default);
}

export function completenessName(id?: string | null) {
  if (id === "QUICK") return "快速";
  if (id === "THOROUGH") return "详尽";
  return "标准";
}

export function completenessTone(id?: string | null) {
  if (id === "QUICK") return "quick";
  if (id === "THOROUGH") return "thorough";
  return "standard";
}

export function actionCompleteness(
  selected: Completeness,
  activeRunCompleteness?: Completeness | null,
  active = false
) {
  return active ? activeRunCompleteness || selected : selected;
}
