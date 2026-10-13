import { describe, expect, it } from "vitest";
import { RUN_STAGES, runStageIndex } from "./runStepper";

describe("run stage stepper", () => {
  it("keeps the user-facing stages aligned with the API stage", () => {
    expect(RUN_STAGES.map((stage) => stage.label)).toEqual(["读取材料", "分析与组织", "返回结果"]);
    expect(runStageIndex("READING")).toBe(0);
    expect(runStageIndex("ANALYZING")).toBe(1);
    expect(runStageIndex("SUBMITTING")).toBe(2);
  });

  it("does not invent progress for pending or unknown states", () => {
    expect(runStageIndex("PENDING")).toBe(0);
    expect(runStageIndex(undefined)).toBe(0);
  });
});
