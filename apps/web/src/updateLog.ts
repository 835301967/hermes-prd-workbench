export type UpdateLogItem = {
  title: string;
  detail: string;
  tone: "accent" | "blue" | "amber" | string;
  type?: "feature" | "improve" | "fix" | "security" | string;
};

export type UpdateLogEntry = {
  id?: string;
  version: string;
  date: string;
  releaseDate?: string;
  title: string;
  summary: string;
  highlights: string[];
  items: UpdateLogItem[];
  isLatest?: boolean;
  status?: string;
};

/** Public release history only; internal customer notes are excluded. */
export const CURRENT_UPDATE_LOG: UpdateLogEntry = {
  version: "V0.4.0-beta.1",
  status: "PREVIEW",
  date: "2026-10-13",
  title: "首个开源预览版",
  summary: "预览版仅支持全新安装；验证范围为 macOS arm64 与 Docker Desktop Linux 容器。通用模板、可配置模型服务与公开项目目录；保留七种业务模式。",
  highlights: ["通用 HTML 原型", "自托管部署", "隔离预览", "默认关闭行为采集"],
  items: [
    {title:"通用模板", detail:"默认生成自包含 HTML，不依赖私有宿主。", tone:"blue", type:"improve"},
    {title:"隔离预览", detail:"新标签也使用 sandbox iframe，原始 HTML 可下载。", tone:"accent", type:"security"},
    {title:"静态诊断", detail:"生成代码仅做结构和语法检查；完整业务行为仍需浏览器验收。", tone:"amber", type:"security"},
  ],
};
export const UPDATE_LOG_HISTORY: UpdateLogEntry[] = [];
export const UPDATE_LOG_FOOTER = "查看记录仅保存在当前浏览器，不会影响工作内容。";
export function updateLogSeenKey(userId: string | null | undefined) {
  return `prd-workbench-updates-seen:${userId || "anonymous"}`;
}
