import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { trackFeature, trackError, flushTelemetry } from "./telemetry";
import { saveSession, clearSession } from "./session";

describe("opt-in telemetry", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ok:true}));
    saveSession("synthetic-session", {id:"user_1", username:"sample", displayName:"sample"});
  });
  afterEach(async () => { await flushTelemetry(); vi.unstubAllEnvs(); vi.unstubAllGlobals(); clearSession(); });
  it("does not enqueue or send by default", async () => {
    vi.stubEnv("VITE_TELEMETRY_ENABLED", "false");
    trackFeature("PRD", "START_RUN", {prompt:"private"});
    await flushTelemetry();
    expect(fetch).not.toHaveBeenCalled();
  });
  it("requires a session and drops arbitrary metadata and raw errors", async () => {
    vi.stubEnv("VITE_TELEMETRY_ENABLED", "true");
    trackError("SOURCE", "UPLOAD_FAILED", "private raw text", {taskId:"task_safe", prompt:"private", token:"private"});
    await flushTelemetry();
    const body = JSON.parse(vi.mocked(fetch).mock.calls[0][1]!.body as string);
    expect(body.errorMessage).toBeUndefined();
    expect(body.metadata).toEqual({taskId:"task_safe"});
    vi.mocked(fetch).mockClear();
    clearSession();
    trackFeature("PRD", "START_RUN");
    await flushTelemetry();
    expect(fetch).not.toHaveBeenCalled();
  });
});
