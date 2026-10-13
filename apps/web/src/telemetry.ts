import { getToken } from "./session";

export interface TelemetryEventPayload {
  eventType?: string;
  module: string;
  action: string;
  status?: string;
  durationMs?: number;
  errorCode?: string;
  errorMessage?: string;
  metadata?: Record<string, unknown>;
}

function enabled() { return import.meta.env.VITE_TELEMETRY_ENABLED === "true"; }

let queue: TelemetryEventPayload[] = [];
let flushTimeout: ReturnType<typeof setTimeout> | null = null;
const BATCH_SIZE = 20;
const FLUSH_INTERVAL_MS = 500;

export async function flushTelemetry(): Promise<void> {
  if (!enabled()) { queue = []; return; }
  if (flushTimeout) {
    clearTimeout(flushTimeout);
    flushTimeout = null;
  }
  if (queue.length === 0) return;

  const batch = queue.splice(0, BATCH_SIZE);
  const token = getToken();

  // Send sequentially or in a simple loop without throwing
  for (const item of batch) {
    try {
      const headers: Record<string, string> = {
        "Content-Type": "application/json",
      };
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }

      // Use fetch with keepalive if possible
      await fetch("/api/v1/telemetry/events", {
        method: "POST",
        headers,
        body: JSON.stringify(item),
        keepalive: true,
      });
    } catch {
      // Telemetry must be completely silent and never disrupt the user.
    }
  }

  // If there are still items remaining in queue, schedule another flush
  if (queue.length > 0) {
    scheduleFlush();
  }
}

function safeMetadata(input?: Record<string, unknown>) {
  const result: Record<string, unknown> = {};
  for (const key of ["runId", "taskId"]) {
    const value = input?.[key];
    if (typeof value === "string" && /^(run|task)_[A-Za-z0-9]{1,64}$/.test(value)) result[key] = value;
  }
  for (const key of ["sourceCount", "fileCount"]) {
    const value = input?.[key];
    if (typeof value === "number" && Number.isInteger(value) && value >= 0 && value <= 10000) result[key] = value;
  }
  return result;
}

function scheduleFlush() {
  if (flushTimeout) return;
  flushTimeout = setTimeout(() => {
    flushTelemetry();
  }, FLUSH_INTERVAL_MS);
}

export function trackEvent(event: TelemetryEventPayload): void {
  if (!enabled() || !getToken()) return;
  queue.push({
    ...event,
    errorMessage: undefined,
    metadata: safeMetadata(event.metadata),
    eventType: event.eventType || "USER_ACTION",
  });
  if (queue.length >= BATCH_SIZE) {
    flushTelemetry();
  } else {
    scheduleFlush();
  }
}

export function trackFeature(module: string, action: string, metadata?: Record<string, unknown>, durationMs?: number): void {
  trackEvent({
    eventType: "USER_ACTION",
    module,
    action,
    status: "SUCCEEDED",
    durationMs,
    metadata,
  });
}

export function trackError(module: string, errorCode: string, errorMessage?: string, metadata?: Record<string, unknown>): void {
  trackEvent({
    eventType: "CLIENT_ERROR",
    module,
    action: "ERROR",
    status: "FAILED",
    errorCode,
    errorMessage: errorMessage?.slice(0, 500),
    metadata,
  });
}

// Flush on page unload or visibility hidden
if (typeof window !== "undefined") {
  window.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
      flushTelemetry();
    }
  });
  window.addEventListener("beforeunload", () => {
    flushTelemetry();
  });
}
