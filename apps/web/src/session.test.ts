import { describe, expect, it } from "vitest";
import { storageKey } from "./session";

describe("storageKey", () => {
  it("scopes local records by user", () => {
    expect(storageKey("task-id", "user_1")).toBe("prd-workbench-task-id:user_1");
    expect(storageKey("run-id", "user_2")).toBe("prd-workbench-run-id:user_2");
  });
});
