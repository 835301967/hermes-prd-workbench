import { describe, expect, it } from "vitest";
import { formatTechnicalDetail } from "./techSpecDisplay";

describe("original technical detail display", () => {
  it("retains structured index intent without mutating it", () => {
    const indexes = ["PRIMARY KEY (id)", { name: "note_order", columns: ["createdAt", "id"], intent: "local sort" }];
    const before = JSON.stringify(indexes);
    const shown = formatTechnicalDetail(indexes);
    expect(shown).toContain("PRIMARY KEY (id)");
    expect(shown).toContain('"columns":["createdAt","id"]');
    expect(shown).not.toContain("[object Object]");
    expect(JSON.stringify(indexes)).toBe(before);
  });
  it("accepts error-code text and arrays without joining individual characters", () => {
    expect(formatTechnicalDetail("STORAGE_UNAVAILABLE: fallback")).toBe("STORAGE_UNAVAILABLE: fallback");
    expect(formatTechnicalDetail(["TITLE_REQUIRED", { code: "SAVE_FAILED" }])).toBe('TITLE_REQUIRED; {"code":"SAVE_FAILED"}');
    expect(formatTechnicalDetail(null)).toBe("");
  });
});
