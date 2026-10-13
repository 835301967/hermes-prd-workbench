import { describe, expect, it } from "vitest";
import { buildSettingsSummary, hasDistinctContextBody, initialDemoPreviewState } from "./workbenchUi";

describe("workbench UI state", () => {
  it("summarizes collapsed run settings without losing selected options", () => {
    expect(
      buildSettingsSummary({
        completeness: "标准",
        defaultFocus: "默认开发能否开工",
        selectedExtraCount: 2,
      })
    ).toBe("标准 · 默认开发能否开工 · 2 个补充项");
    expect(
      buildSettingsSummary({
        completeness: "详尽",
        defaultFocus: "默认主路径可点",
        selectedExtraCount: 0,
        skill: "Frontend Design",
      })
    ).toBe("详尽 · 默认主路径可点 · Frontend Design");
  });

  it("uses a loading state only when demo HTML exists", () => {
    expect(initialDemoPreviewState("<!DOCTYPE html><html></html>")).toBe("loading");
    expect(initialDemoPreviewState("  ")).toBe("empty");
  });

  it("does not repeat an issue title as context body", () => {
    expect(hasDistinctContextBody("缺少状态", "缺少状态")).toBe(false);
    expect(hasDistinctContextBody("缺少状态", "开发无法定义状态流转")).toBe(true);
    expect(hasDistinctContextBody("缺少状态", " ")).toBe(false);
  });
});
