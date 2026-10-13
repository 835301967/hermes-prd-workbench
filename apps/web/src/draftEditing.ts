import type { RunHistoryItem } from "./api";

export type DraftContentState = "ORIGINAL" | "EDITED" | "UNSAVED";

export function normalizeDraftForComparison(content: string) {
  return content.replace(/\r\n?/g, "\n").trim();
}

export function draftContentState(saved: string, original: string, editor: string): DraftContentState {
  if (editor !== saved) return "UNSAVED";
  return normalizeDraftForComparison(saved) === normalizeDraftForComparison(original) ? "ORIGINAL" : "EDITED";
}

export function hasUnsavedDraft(saved: string, editor: string) {
  return saved !== editor;
}

export function latestDraftCandidate(history: RunHistoryItem[], activeRunId: string | null) {
  const succeeded = history.filter((item) => item.mode === "GENERATE" && item.status === "SUCCEEDED");
  if (!succeeded.length) return null;
  if (!activeRunId) return succeeded[0];
  const activeIndex = succeeded.findIndex((item) => item.id === activeRunId);
  if (activeIndex <= 0) return activeIndex === -1 ? succeeded[0] : null;
  return succeeded[0];
}

export function recoverUniqueDraftRunId(draftRunIds: Array<string | null>, availableRunIds: string[]) {
  if (draftRunIds.length !== 1) return null;
  const runId = draftRunIds[0];
  return runId && availableRunIds.includes(runId) ? runId : null;
}
