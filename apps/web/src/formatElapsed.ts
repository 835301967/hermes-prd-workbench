export function formatElapsed(startedAtMs: number, nowMs = Date.now()): string {
  if (!Number.isFinite(startedAtMs) || startedAtMs <= 0) {
    return "已用 0 秒";
  }
  const sec = Math.max(0, Math.floor((nowMs - startedAtMs) / 1000));
  if (sec < 60) {
    return `已用 ${sec} 秒`;
  }
  const minutes = Math.floor(sec / 60);
  const seconds = sec % 60;
  return `已用 ${minutes} 分 ${seconds} 秒`;
}
