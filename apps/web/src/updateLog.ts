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
  version: "V0.4.0-beta.2",
  status: "PREVIEW",
  date: "2026-10-13",
  title: "跨平台与图表修复预览版",
  summary: "预览版仍仅支持全新安装；新增 Windows 11 WSL2 验收，修复 Compose 同源请求的 CORS 403，并恢复流程图、时序图及 Word 导出中的文字。",
  highlights: ["WSL2 验收", "CORS 修复", "Mermaid 文字恢复", "英文开源首页"],
  items: [
    {title:"Compose 同源访问", detail:"Nginx 保留浏览器 Host 端口，WSL2 登录和写操作不再被错误识别为跨域。", tone:"blue", type:"fix"},
    {title:"图表文字", detail:"流程图与时序图使用原生 SVG 文字，严格清理后仍保留节点、角色、消息和备注。", tone:"accent", type:"fix"},
    {title:"导出与展示", detail:"Word 图表导出完成视觉验收，并增加英文 README、合成数据截图和演示 GIF。", tone:"amber", type:"improve"},
  ],
};
export const UPDATE_LOG_HISTORY: UpdateLogEntry[] = [
  {
    version: "V0.4.0-beta.1",
    status: "PREVIEW",
    date: "2026-10-13",
    title: "首个开源预览版",
    summary: "全新安装预览版，提供通用模板、可配置模型服务、隔离预览和七种业务模式。",
    highlights: ["通用 HTML 原型", "自托管部署", "隔离预览", "默认关闭行为采集"],
    items: [
      {title:"通用模板", detail:"默认生成自包含 HTML，不依赖私有宿主。", tone:"blue", type:"improve"},
      {title:"隔离预览", detail:"新标签也使用 sandbox iframe，原始 HTML 可下载。", tone:"accent", type:"security"},
      {title:"静态诊断", detail:"生成代码仅做结构和语法检查；完整业务行为仍需浏览器验收。", tone:"amber", type:"security"},
    ],
  },
];
export const UPDATE_LOG_FOOTER = "查看记录仅保存在当前浏览器，不会影响工作内容。";
export function updateLogSeenKey(userId: string | null | undefined) {
  return `prd-workbench-updates-seen:${userId || "anonymous"}`;
}
