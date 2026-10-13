import { describe, expect, it } from "vitest";
import {
  isPollFailureLimitExceeded,
  isTaskActive,
  MAX_CONSECUTIVE_POLL_FAILURES,
  removeTaskRunning,
  runningModeLabel,
  shouldResumeWatchersAfterHealthRecovery,
  shouldStopPollingImmediately,
  updateTaskRunning,
  type TaskRunningMap,
} from "./taskRunningState";

describe("taskRunningState", () => {
  it("formats running mode labels", () => {
    expect(runningModeLabel("GENERATE")).toBe("PRD 生成");
    expect(runningModeLabel("DEMO")).toBe("Demo 生成");
    expect(runningModeLabel("REVIEW")).toBe("评审");
    expect(runningModeLabel("ESTIMATE")).toBe("工作量评估");
    expect(runningModeLabel("TEST_CASE")).toBe("测试用例生成");
  });

  it("adds and updates active tasks without mutating the original map", () => {
    const initial: TaskRunningMap = {};
    const updated = updateTaskRunning(initial, "task_1", {
      runId: "run_1",
      mode: "DEMO",
      status: "RUNNING",
      taskTitle: "采购单据",
    });

    expect(isTaskActive(initial, "task_1")).toBe(false);
    expect(isTaskActive(updated, "task_1")).toBe(true);
    expect(updated["task_1"]?.mode).toBe("DEMO");
  });

  it("removes completed tasks cleanly", () => {
    const map: TaskRunningMap = {
      task_1: { runId: "run_1", mode: "GENERATE", status: "RUNNING" },
      task_2: { runId: "run_2", mode: "DEMO", status: "RUNNING" },
    };

    const next = removeTaskRunning(map, "task_1");
    expect(isTaskActive(next, "task_1")).toBe(false);
    expect(isTaskActive(next, "task_2")).toBe(true);
  });

  it("identifies fatal errors that must abort polling immediately", () => {
    const makeAxiosError = (status?: number) => ({
      isAxiosError: true,
      response: status ? { status, data: { message: "Error" } } : undefined,
    });

    expect(shouldStopPollingImmediately(makeAxiosError(404))).toBe(true);
    expect(shouldStopPollingImmediately(makeAxiosError(401))).toBe(true);
    expect(shouldStopPollingImmediately(makeAxiosError(403))).toBe(true);
    expect(shouldStopPollingImmediately(makeAxiosError(500))).toBe(false);
    expect(shouldStopPollingImmediately(makeAxiosError(502))).toBe(false);
    expect(shouldStopPollingImmediately(new Error("Network Error"))).toBe(false);
  });

  it("detects consecutive poll failure limit exceeded", () => {
    expect(MAX_CONSECUTIVE_POLL_FAILURES).toBe(5);
    expect(isPollFailureLimitExceeded(0)).toBe(false);
    expect(isPollFailureLimitExceeded(4)).toBe(false);
    expect(isPollFailureLimitExceeded(5)).toBe(true);
    expect(isPollFailureLimitExceeded(6)).toBe(true);
  });

  it("resumes watchers only when health recovers from DOWN to UP", () => {
    expect(shouldResumeWatchersAfterHealthRecovery("DOWN", "UP")).toBe(true);
    expect(shouldResumeWatchersAfterHealthRecovery("UP", "UP")).toBe(false);
    expect(shouldResumeWatchersAfterHealthRecovery("DOWN", "DOWN")).toBe(false);
    expect(shouldResumeWatchersAfterHealthRecovery(undefined, "UP")).toBe(false);
  });
});
