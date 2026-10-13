import type { TestCaseItem } from "./api";

export type TestCaseColumnConfig = {
  key: string;
  title: string;
  visible: boolean;
  align?: "left" | "center" | "right";
  isCustom?: boolean;
  width?: number;
};

export function getDefaultTestCaseColumns(): TestCaseColumnConfig[] {
  return [
    { key: "index", title: "序号", visible: true, align: "center", width: 65 },
    { key: "caseId", title: "用例编号", visible: true, align: "center", width: 120 },
    { key: "module", title: "一级模块", visible: true, align: "left", width: 120 },
    { key: "subModule", title: "二级模块", visible: true, align: "left", width: 120 },
    { key: "title", title: "用例标题", visible: true, align: "left", width: 220 },
    { key: "type", title: "测试类型", visible: true, align: "center", width: 100 },
    { key: "priority", title: "优先级", visible: true, align: "center", width: 85 },
    { key: "precondition", title: "前置条件", visible: true, align: "left", width: 180 },
    { key: "steps", title: "操作步骤", visible: true, align: "left", width: 260 },
    { key: "testData", title: "测试数据", visible: true, align: "left", width: 140 },
    { key: "expectedResult", title: "预期结果", visible: true, align: "left", width: 240 },
    { key: "description", title: "设计说明", visible: true, align: "left", width: 180 },
  ];
}

export const DEFAULT_TEST_CASE_COLUMNS: TestCaseColumnConfig[] = getDefaultTestCaseColumns();

export function typeLabel(type: string): string {
  const t = (type || "").toUpperCase();
  if (t === "FUNCTION") return "功能测试";
  if (t === "BOUNDARY") return "边界值";
  if (t === "EXCEPTION") return "异常流";
  if (t === "PERMISSION") return "权限测试";
  if (t === "CONCURRENCY") return "并发测试";
  return type || "功能";
}

export function typeTagClass(type: string): string {
  const t = (type || "").toUpperCase();
  if (t === "FUNCTION") return "tag-function";
  if (t === "BOUNDARY") return "tag-boundary";
  if (t === "EXCEPTION") return "tag-exception";
  if (t === "PERMISSION") return "tag-permission";
  if (t === "CONCURRENCY") return "tag-concurrency";
  return "tag-default";
}

export function priorityLabel(priority: string): string {
  const p = (priority || "").toUpperCase();
  if (p === "P0") return "P0 冒烟";
  if (p === "P1") return "P1 关键";
  if (p === "P2") return "P2 次要";
  return priority || "P1";
}

export function priorityTagClass(priority: string): string {
  const p = (priority || "").toUpperCase();
  if (p === "P0") return "tag-p0";
  if (p === "P1") return "tag-p1";
  if (p === "P2") return "tag-p2";
  return "tag-default";
}

export type TestCaseModuleGroup = {
  module: string;
  items: TestCaseItem[];
  totalCount: number;
  p0Count: number;
  p1Count: number;
  p2Count: number;
  boundaryCount: number;
  exceptionCount: number;
};

export function buildTestCaseModuleGroups(items: TestCaseItem[]): TestCaseModuleGroup[] {
  if (!items || items.length === 0) {
    return [];
  }
  const map = new Map<string, TestCaseModuleGroup>();
  for (const item of items) {
    const mod = item.module || "通用模块";
    let group = map.get(mod);
    if (!group) {
      group = {
        module: mod,
        items: [],
        totalCount: 0,
        p0Count: 0,
        p1Count: 0,
        p2Count: 0,
        boundaryCount: 0,
        exceptionCount: 0,
      };
      map.set(mod, group);
    }
    group.items.push(item);
    group.totalCount++;

    const p = (item.priority || "").toUpperCase();
    if (p === "P0") group.p0Count++;
    else if (p === "P2") group.p2Count++;
    else group.p1Count++;

    const t = (item.type || "").toUpperCase();
    if (t === "BOUNDARY") group.boundaryCount++;
    else if (t === "EXCEPTION") group.exceptionCount++;
  }
  return Array.from(map.values());
}
