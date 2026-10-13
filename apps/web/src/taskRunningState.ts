import { apiHttpStatus, type Mode } from "./api";

export const MAX_CONSECUTIVE_POLL_FAILURES = 5;

/**
 * 判断是否应立即停止轮询的致命错误：
 * - 401 / 403: 鉴权失败或无权限，继续轮询只会重复报错并可能触发页面跳转
 * - 404: 任务或 Run 记录不存在（如已被清理、误删或 ID 错乱），继续轮询无意义
 */
export function shouldStopPollingImmediately(error: unknown): boolean {
  const status = apiHttpStatus(error);
  return status === 401 || status === 403 || status === 404;
}

export function isPollFailureLimitExceeded(consecutiveFailures: number): boolean {
  return consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES;
}

export function shouldResumeWatchersAfterHealthRecovery(
  previousHealth: string | null | undefined,
  currentHealth: string | null | undefined
): boolean {
  return previousHealth === "DOWN" && currentHealth === "UP";
}

export type TaskActiveRun = {
  runId: string;
  mode: Mode;
  status: string;
  taskTitle?: string;
};

export type TaskRunningMap = Record<string, TaskActiveRun>;

export function runningModeLabel(mode: Mode): string {
  switch (mode) {
    case "GENERATE":
      return "PRD 生成";
    case "DEMO":
      return "Demo 生成";
    case "POC":
      return "POC 演示系统生成";
    case "REVIEW":
      return "评审";
    case "ESTIMATE":
      return "工作量评估";
    case "TEST_CASE":
      return "测试用例生成";
    case "TECH_SPEC":
      return "技术方案生成";
    default:
      return "运行";
  }
}

export function updateTaskRunning(
  map: TaskRunningMap,
  taskId: string,
  run: TaskActiveRun
): TaskRunningMap {
  return {
    ...map,
    [taskId]: run,
  };
}

export function removeTaskRunning(
  map: TaskRunningMap,
  taskId: string
): TaskRunningMap {
  const next = { ...map };
  delete next[taskId];
  return next;
}

export function isTaskActive(
  map: TaskRunningMap,
  taskId: string
): boolean {
  return Boolean(map[taskId]);
}
