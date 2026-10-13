import type { EstimateItem } from "./api";

export type ColumnConfig = {
  key: string;
  title: string;
  visible: boolean;
  align?: "left" | "center" | "right";
  isCustom?: boolean;
  width?: number;
};

export type EstimateColumnConfig = ColumnConfig;

export function getDefaultEstimateColumns(): ColumnConfig[] {
  return [
    { key: "index", title: "序号", visible: true, align: "center", width: 70 },
    { key: "module", title: "一级模块", visible: true, align: "left", width: 140 },
    { key: "subModule", title: "二级模块", visible: true, align: "left", width: 140 },
    { key: "type", title: "类型", visible: true, align: "center", width: 110 },
    { key: "name", title: "页面/接口名称", visible: true, align: "left", width: 200 },
    { key: "detail", title: "接口/页面详情", visible: true, align: "left", width: 240 },
    { key: "complexity", title: "复杂度", visible: true, align: "center", width: 90 },
    { key: "devDays", title: "开发人天", visible: true, align: "right", width: 100 },
    { key: "testDays", title: "自测人天", visible: true, align: "right", width: 100 },
    { key: "totalDays", title: "合计人天", visible: true, align: "right", width: 100 },
    { key: "unitPrice", title: "单价(元/天)", visible: false, align: "right", width: 110 },
    { key: "totalPrice", title: "预估金额(元)", visible: false, align: "right", width: 130 },
    { key: "description", title: "计算依据与说明", visible: true, align: "left", width: 260 },
  ];
}

export const DEFAULT_ESTIMATE_COLUMNS: ColumnConfig[] = getDefaultEstimateColumns();

export function formatDays(days?: number): string {
  return (days ?? 0).toFixed(1);
}

export function formatCurrency(amount?: number): string {
  return Number(amount || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
}

export function typeLabel(type: string): string {
  const t = (type || "").toUpperCase();
  if (t === "PAGE") return "页面";
  if (t === "API") return "内部接口";
  if (t === "THIRD_PARTY_API") return "第三方接口";
  return type || "功能";
}

export function typeTagClass(type: string): string {
  const t = (type || "").toUpperCase();
  if (t === "PAGE") return "tag-page";
  if (t === "API") return "tag-api";
  if (t === "THIRD_PARTY_API") return "tag-third-party";
  return "tag-default";
}

export function complexityLabel(complexity: string): string {
  const c = (complexity || "").toUpperCase();
  if (c === "SIMPLE") return "简单";
  if (c === "MEDIUM") return "中等";
  if (c === "COMPLEX") return "复杂";
  return complexity || "普通";
}

export function complexityTagClass(complexity: string): string {
  const c = (complexity || "").toUpperCase();
  if (c === "SIMPLE") return "tag-simple";
  if (c === "MEDIUM") return "tag-medium";
  if (c === "COMPLEX") return "tag-complex";
  return "tag-default";
}

export function computeQuoteTotal(totalDays: number, dailyRate: number): number {
  if (!totalDays || totalDays <= 0 || !dailyRate || dailyRate <= 0) {
    return 0;
  }
  return Math.round(totalDays * dailyRate * 100) / 100;
}

export type ModuleGroup = {
  module: string;
  items: EstimateItem[];
  pageCount: number;
  apiCount: number;
  thirdPartyCount: number;
  devDays: number;
  testDays: number;
  totalDays: number;
};

export function buildModuleGroups(items: EstimateItem[]): ModuleGroup[] {
  if (!items || items.length === 0) {
    return [];
  }
  const map = new Map<string, ModuleGroup>();
  for (const item of items) {
    const mod = item.module || "通用模块";
    let group = map.get(mod);
    if (!group) {
      group = {
        module: mod,
        items: [],
        pageCount: 0,
        apiCount: 0,
        thirdPartyCount: 0,
        devDays: 0,
        testDays: 0,
        totalDays: 0,
      };
      map.set(mod, group);
    }
    group.items.push(item);
    const t = (item.type || "").toUpperCase();
    if (t === "PAGE") group.pageCount++;
    else if (t === "THIRD_PARTY_API") group.thirdPartyCount++;
    else if (t === "API") group.apiCount++;

    group.devDays = Math.round((group.devDays + (item.devDays || 0)) * 10) / 10;
    group.testDays = Math.round((group.testDays + (item.testDays || 0)) * 10) / 10;
    group.totalDays = Math.round((group.devDays + group.testDays) * 10) / 10;
  }
  return Array.from(map.values());
}

export const calculateEstimateGroups = buildModuleGroups;
export const getEstimateTypeBadgeClass = typeTagClass;
export const getEstimateComplexityBadgeClass = complexityTagClass;

