import { describe, it, expect } from "vitest";
import {
  getDefaultTestCaseColumns,
  typeLabel,
  typeTagClass,
  priorityLabel,
  priorityTagClass,
  buildTestCaseModuleGroups,
} from "./testCaseExport";
import type { TestCaseItem } from "./api";

describe("testCaseExport helpers", () => {
  it("provides standard default test case columns", () => {
    const cols = getDefaultTestCaseColumns();
    expect(cols.length).toBeGreaterThanOrEqual(10);
    expect(cols.find((c) => c.key === "caseId")?.visible).toBe(true);
    expect(cols.find((c) => c.key === "priority")?.visible).toBe(true);
    expect(cols.find((c) => c.key === "steps")?.visible).toBe(true);
  });

  it("translates test types and priorities properly", () => {
    expect(typeLabel("FUNCTION")).toBe("功能测试");
    expect(typeLabel("BOUNDARY")).toBe("边界值");
    expect(typeLabel("EXCEPTION")).toBe("异常流");
    expect(typeLabel("PERMISSION")).toBe("权限测试");
    expect(typeLabel("CONCURRENCY")).toBe("并发测试");

    expect(typeTagClass("FUNCTION")).toBe("tag-function");
    expect(typeTagClass("BOUNDARY")).toBe("tag-boundary");
    expect(typeTagClass("EXCEPTION")).toBe("tag-exception");

    expect(priorityLabel("P0")).toBe("P0 冒烟");
    expect(priorityLabel("P1")).toBe("P1 关键");
    expect(priorityLabel("P2")).toBe("P2 次要");

    expect(priorityTagClass("P0")).toBe("tag-p0");
    expect(priorityTagClass("P1")).toBe("tag-p1");
    expect(priorityTagClass("P2")).toBe("tag-p2");
  });

  it("groups test cases by module and aggregates subtotals", () => {
    const items: TestCaseItem[] = [
      {
        caseId: "TC-001",
        module: "案源管理",
        subModule: "渠道设置",
        title: "创建渠道成功",
        type: "FUNCTION",
        priority: "P0",
        steps: "1. 新建 2. 保存",
        expectedResult: "成功",
      },
      {
        caseId: "TC-002",
        module: "案源管理",
        subModule: "渠道设置",
        title: "渠道名称超长截断",
        type: "BOUNDARY",
        priority: "P1",
        steps: "1. 输入超长字符",
        expectedResult: "报错拦截",
      },
      {
        caseId: "TC-003",
        module: "权限中心",
        subModule: "角色权限",
        title: "无权访问配置拦截",
        type: "EXCEPTION",
        priority: "P0",
        steps: "1. 访问后台配置",
        expectedResult: "403 禁止访问",
      },
    ];

    const groups = buildTestCaseModuleGroups(items);
    expect(groups.length).toBe(2);

    const leadGroup = groups.find((g) => g.module === "案源管理");
    expect(leadGroup?.items.length).toBe(2);
    expect(leadGroup?.p0Count).toBe(1);
    expect(leadGroup?.p1Count).toBe(1);
    expect(leadGroup?.boundaryCount).toBe(1);

    const permGroup = groups.find((g) => g.module === "权限中心");
    expect(permGroup?.items.length).toBe(1);
    expect(permGroup?.p0Count).toBe(1);
    expect(permGroup?.exceptionCount).toBe(1);
  });
});
