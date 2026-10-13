import { describe, it, expect } from "vitest";
import {
  getDefaultEstimateColumns,
  typeLabel,
  complexityLabel,
  computeQuoteTotal,
  buildModuleGroups,
} from "./estimateExport";
import type { EstimateItem } from "./api";

describe("estimateExport helpers", () => {
  it("provides standard default columns", () => {
    const cols = getDefaultEstimateColumns();
    expect(cols.length).toBeGreaterThan(8);
    expect(cols.find((c) => c.key === "module")?.visible).toBe(true);
    expect(cols.find((c) => c.key === "totalDays")?.visible).toBe(true);
  });

  it("translates types and complexities properly", () => {
    expect(typeLabel("PAGE")).toBe("页面");
    expect(typeLabel("API")).toBe("内部接口");
    expect(typeLabel("THIRD_PARTY_API")).toBe("第三方接口");

    expect(complexityLabel("SIMPLE")).toBe("简单");
    expect(complexityLabel("MEDIUM")).toBe("中等");
    expect(complexityLabel("COMPLEX")).toBe("复杂");
  });

  it("computes quote totals accurately", () => {
    expect(computeQuoteTotal(10.5, 1200)).toBe(12600);
    expect(computeQuoteTotal(0, 1200)).toBe(0);
    expect(computeQuoteTotal(10, 0)).toBe(0);
  });

  it("groups items by module and aggregates subtotals", () => {
    const items: EstimateItem[] = [
      {
        module: "线索",
        type: "PAGE",
        name: "列表",
        devDays: 0.5,
        testDays: 0.2,
        totalDays: 0.7,
      },
      {
        module: "线索",
        type: "API",
        name: "搜索",
        devDays: 0.5,
        testDays: 0.1,
        totalDays: 0.6,
      },
      {
        module: "财务",
        type: "PAGE",
        name: "发票台账",
        devDays: 0.8,
        testDays: 0.2,
        totalDays: 1.0,
      },
    ];

    const groups = buildModuleGroups(items);
    expect(groups.length).toBe(2);

    const clueGroup = groups.find((g) => g.module === "线索");
    expect(clueGroup?.items.length).toBe(2);
    expect(clueGroup?.pageCount).toBe(1);
    expect(clueGroup?.apiCount).toBe(1);
    expect(clueGroup?.devDays).toBe(1.0);
    expect(clueGroup?.testDays).toBe(0.3);
    expect(clueGroup?.totalDays).toBe(1.3);

    const finGroup = groups.find((g) => g.module === "财务");
    expect(finGroup?.items.length).toBe(1);
    expect(finGroup?.totalDays).toBe(1.0);
  });
});
