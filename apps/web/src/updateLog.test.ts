import { describe, expect, it } from "vitest";
import { CURRENT_UPDATE_LOG, UPDATE_LOG_HISTORY, updateLogSeenKey } from "./updateLog";
describe("public update log", () => {
  it("uses the public version without internal history", () => {
    expect(CURRENT_UPDATE_LOG.version).toBe("V0.4.0-beta.1");
    expect(CURRENT_UPDATE_LOG.status).toBe("PREVIEW");
    expect(CURRENT_UPDATE_LOG.summary).toContain("仅支持全新安装");
    expect(UPDATE_LOG_HISTORY).toEqual([]);
    expect(CURRENT_UPDATE_LOG.items.length).toBeGreaterThan(0);
  });
  it("scopes viewed state by user", () => {
    expect(updateLogSeenKey("user_1")).toBe("prd-workbench-updates-seen:user_1");
    expect(updateLogSeenKey(null)).toBe("prd-workbench-updates-seen:anonymous");
  });
});
