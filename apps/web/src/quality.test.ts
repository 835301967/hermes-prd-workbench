import { describe, expect, it } from "vitest";
import {
  actionCompleteness,
  applyExtraLens,
  completenessName,
  completenessTone,
  defaultLenses,
  extraLenses,
  FALLBACK_QUALITY,
  toggleLens,
} from "./quality";

describe("quality options", () => {
  it("uses mode default lenses", () => {
    expect(defaultLenses(FALLBACK_QUALITY, "GENERATE")).toEqual(["module-rules"]);
    expect(defaultLenses(FALLBACK_QUALITY, "DEMO")).toEqual(["main-path"]);
    expect(defaultLenses(FALLBACK_QUALITY, "POC")).toEqual(["main-path", "more-states"]);
    expect(extraLenses(FALLBACK_QUALITY, "POC")).toEqual([]);
    expect(FALLBACK_QUALITY.lenses.POC.find((item) => item.id === "more-states")?.name).toBe("一级菜单可点台账");
  });

  it("keeps extras off the default GENERATE lens until a split is chosen", () => {
    expect(extraLenses(FALLBACK_QUALITY, "GENERATE").map((item) => item.id)).toEqual(["by-page", "by-role"]);
    expect(applyExtraLens(FALLBACK_QUALITY, "GENERATE", ["module-rules"], "by-page")).toEqual(["by-page"]);
    expect(applyExtraLens(FALLBACK_QUALITY, "GENERATE", ["by-page"], "by-page")).toEqual(["module-rules"]);
    expect(applyExtraLens(FALLBACK_QUALITY, "GENERATE", ["by-page"], "by-role")).toEqual(["by-page", "by-role"]);
  });

  it("adds review extras on top of the default lens", () => {
    expect(applyExtraLens(FALLBACK_QUALITY, "REVIEW", ["can-start"], "testable")).toEqual(["can-start", "testable"]);
    expect(applyExtraLens(FALLBACK_QUALITY, "REVIEW", ["can-start", "testable"], "testable")).toEqual(["can-start"]);
  });

  it("maps completeness to label and tone", () => {
    expect(completenessName("QUICK")).toBe("快速");
    expect(completenessName("STANDARD")).toBe("标准");
    expect(completenessName("THOROUGH")).toBe("详尽");
    expect(completenessName(undefined)).toBe("标准");
    expect(completenessTone("QUICK")).toBe("quick");
    expect(completenessTone("THOROUGH")).toBe("thorough");
    expect(completenessTone(undefined)).toBe("standard");
  });

  it("uses the form completeness while idle and the persisted value while running", () => {
    expect(actionCompleteness("THOROUGH", "QUICK", false)).toBe("THOROUGH");
    expect(actionCompleteness("THOROUGH", "QUICK", true)).toBe("QUICK");
    expect(actionCompleteness("STANDARD", null, true)).toBe("STANDARD");
  });
});
