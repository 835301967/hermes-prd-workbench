import { describe, expect, it } from "vitest";
import { formatElapsed } from "./formatElapsed";

describe("formatElapsed", () => {
  it("formats seconds and minutes", () => {
    expect(formatElapsed(1_000, 1_000)).toBe("已用 0 秒");
    expect(formatElapsed(1_000, 13_000)).toBe("已用 12 秒");
    expect(formatElapsed(1_000, 125_000)).toBe("已用 2 分 4 秒");
  });

  it("guards invalid start time", () => {
    expect(formatElapsed(Number.NaN, 10_000)).toBe("已用 0 秒");
  });
});
