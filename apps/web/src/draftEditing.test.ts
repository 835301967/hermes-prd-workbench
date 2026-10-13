import { describe, expect, it } from "vitest";
import { draftContentState, latestDraftCandidate, normalizeDraftForComparison, recoverUniqueDraftRunId } from "./draftEditing";
import type { RunHistoryItem } from "./api";

function history(id: string, status: RunHistoryItem["status"] = "SUCCEEDED"): RunHistoryItem {
  return {
    id,
    taskId: "task_1",
    taskTitle: "退款审批",
    mode: "GENERATE",
    status,
    summary: null,
    error: null,
    accepted: false,
    createdAt: "2026-08-26T00:00:00Z",
    finishedAt: null,
  };
}

describe("draft editing state", () => {
  it("does not report edited for line ending and surrounding whitespace differences", () => {
    expect(normalizeDraftForComparison(" # PRD\r\n内容\r\n ")).toBe("# PRD\n内容");
    expect(draftContentState("# PRD\r\n内容\r\n", "# PRD\n内容", "# PRD\r\n内容\r\n")).toBe("ORIGINAL");
  });

  it("distinguishes saved edits from unsaved editor input", () => {
    expect(draftContentState("# PRD\n改后", "# PRD\n原稿", "# PRD\n改后")).toBe("EDITED");
    expect(draftContentState("# PRD\n原稿", "# PRD\n原稿", "# PRD\n未保存")).toBe("UNSAVED");
  });

  it("derives only the newest successful candidate newer than the active run", () => {
    const items = [history("run_3"), history("run_2", "FAILED"), history("run_1")];
    expect(latestDraftCandidate(items, "run_1")?.id).toBe("run_3");
    expect(latestDraftCandidate(items, "run_3")).toBeNull();
    expect(latestDraftCandidate(items, null)?.id).toBe("run_3");
  });

  it("recovers a missing pointer only from one legal current draft", () => {
    expect(recoverUniqueDraftRunId(["run_old"], ["run_new", "run_old"])).toBe("run_old");
    expect(recoverUniqueDraftRunId(["run_old", "run_new"], ["run_new", "run_old"])).toBeNull();
    expect(recoverUniqueDraftRunId(["run_orphan"], ["run_new"])).toBeNull();
    expect(recoverUniqueDraftRunId([], ["run_new"])).toBeNull();
  });
});
