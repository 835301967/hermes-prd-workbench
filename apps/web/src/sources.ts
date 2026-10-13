import type { Mode, Source } from "./api";

export const DRAFT_PREFIX = "生成稿-";
export const REVIEW_EXTRA_PREFIX = "评审补充-";
export const DEMO_EXTRA_PREFIX = "演示补充-";
export const POC_EXTRA_PREFIX = "POC补充-";
export const ESTIMATE_EXTRA_PREFIX = "评估补充-";
export const TEST_CASE_EXTRA_PREFIX = "用例补充-";
export const TECH_SPEC_EXTRA_PREFIX = "方案补充-";

export function isReviewSource(name: string) {
  return (
    name.startsWith(DRAFT_PREFIX) ||
    name.startsWith(REVIEW_EXTRA_PREFIX) ||
    name.startsWith(DEMO_EXTRA_PREFIX) ||
    name.startsWith(POC_EXTRA_PREFIX) ||
    name.startsWith(ESTIMATE_EXTRA_PREFIX) ||
    name.startsWith(TEST_CASE_EXTRA_PREFIX) ||
    name.startsWith(TECH_SPEC_EXTRA_PREFIX)
  );
}

export function isGenerateSource(name: string) {
  return !isReviewSource(name);
}

export function draftSourceName(runId: string) {
  return `${DRAFT_PREFIX}${runId}.md`;
}

export function draftRunId(name: string) {
  if (!name.startsWith(DRAFT_PREFIX) || !name.endsWith(".md")) {
    return null;
  }
  return name.slice(DRAFT_PREFIX.length, -3);
}

function leafName(original: string) {
  return original.split(/[/\\]/).pop() || "untitled.txt";
}

export function reviewUploadName(original: string) {
  const name = leafName(original);
  return isReviewSource(name) ? name : `${REVIEW_EXTRA_PREFIX}${name}`;
}

export function demoUploadName(original: string) {
  const name = leafName(original);
  return isReviewSource(name) ? name : `${DEMO_EXTRA_PREFIX}${name}`;
}

export function pocUploadName(original: string) {
  const name = leafName(original);
  return isReviewSource(name) ? name : `${POC_EXTRA_PREFIX}${name}`;
}

export function estimateUploadName(original: string) {
  const name = leafName(original);
  return isReviewSource(name) ? name : `${ESTIMATE_EXTRA_PREFIX}${name}`;
}

export function testCaseUploadName(original: string) {
  const name = leafName(original);
  return isReviewSource(name) ? name : `${TEST_CASE_EXTRA_PREFIX}${name}`;
}

export function techSpecUploadName(original: string) {
  const name = leafName(original);
  return isReviewSource(name) ? name : `${TECH_SPEC_EXTRA_PREFIX}${name}`;
}

export function visibleForMode(
  mode: Mode,
  name: string,
  draftRunIdForRevise?: string | null,
  hasDraft: boolean = false
) {
  if (mode === "GENERATE" && draftRunIdForRevise && name === draftSourceName(draftRunIdForRevise)) {
    return true;
  }
  if (mode === "REVIEW") {
    if (hasDraft) {
      return name.startsWith(DRAFT_PREFIX) || name.startsWith(REVIEW_EXTRA_PREFIX);
    }
    return isGenerateSource(name) || name.startsWith(REVIEW_EXTRA_PREFIX);
  }
  if (mode === "DEMO") {
    if (hasDraft) {
      return name.startsWith(DRAFT_PREFIX) || name.startsWith(DEMO_EXTRA_PREFIX);
    }
    return isGenerateSource(name) || name.startsWith(DEMO_EXTRA_PREFIX);
  }
  if (mode === "POC") {
    if (hasDraft) {
      return name.startsWith(DRAFT_PREFIX) || name.startsWith(POC_EXTRA_PREFIX);
    }
    return isGenerateSource(name) || name.startsWith(POC_EXTRA_PREFIX);
  }
  if (mode === "ESTIMATE") {
    if (hasDraft) {
      return name.startsWith(DRAFT_PREFIX) || name.startsWith(ESTIMATE_EXTRA_PREFIX);
    }
    return isGenerateSource(name) || name.startsWith(ESTIMATE_EXTRA_PREFIX);
  }
  if (mode === "TEST_CASE") {
    if (hasDraft) {
      return name.startsWith(DRAFT_PREFIX) || name.startsWith(TEST_CASE_EXTRA_PREFIX);
    }
    return isGenerateSource(name) || name.startsWith(TEST_CASE_EXTRA_PREFIX);
  }
  if (mode === "TECH_SPEC") {
    if (hasDraft) {
      return name.startsWith(DRAFT_PREFIX) || name.startsWith(TECH_SPEC_EXTRA_PREFIX);
    }
    return isGenerateSource(name) || name.startsWith(TECH_SPEC_EXTRA_PREFIX);
  }
  return isGenerateSource(name);
}

export function visibleSources(all: Source[], mode: Mode, draftRunIdForRevise?: string | null) {
  // Mirrors the server, which only counts a READY draft: a failed or still-parsing draft source
  // must not lock the material set, otherwise the browser hides materials the run can still read.
  const hasDraft = all.some((source) => source.status === "READY" && isDraftSource(source.name));
  return all.filter((source) => visibleForMode(mode, source.name, draftRunIdForRevise, hasDraft));
}

export function isDraftSource(name: string) {
  return name.startsWith(DRAFT_PREFIX);
}

export function displaySourceName(name: string, draftTitles: Record<string, string> = {}) {
  if (name.startsWith(DRAFT_PREFIX)) {
    const runId = draftRunId(name);
    const title = runId ? String(draftTitles[runId] || "").trim() : "";
    if (title) {
      return title;
    }
    return "生成稿";
  }
  if (name.startsWith(REVIEW_EXTRA_PREFIX)) {
    return name.slice(REVIEW_EXTRA_PREFIX.length);
  }
  if (name.startsWith(DEMO_EXTRA_PREFIX)) {
    return name.slice(DEMO_EXTRA_PREFIX.length);
  }
  if (name.startsWith(POC_EXTRA_PREFIX)) {
    return name.slice(POC_EXTRA_PREFIX.length);
  }
  if (name.startsWith(ESTIMATE_EXTRA_PREFIX)) {
    return name.slice(ESTIMATE_EXTRA_PREFIX.length);
  }
  if (name.startsWith(TEST_CASE_EXTRA_PREFIX)) {
    return name.slice(TEST_CASE_EXTRA_PREFIX.length);
  }
  if (name.startsWith(TECH_SPEC_EXTRA_PREFIX)) {
    return name.slice(TECH_SPEC_EXTRA_PREFIX.length);
  }
  return leafName(name);
}

type DraftTitleRun = {
  id: string;
  mode: string;
  status: string;
  result?: Record<string, unknown> | null;
} | null;

export function draftTitlesFromRuns(runs: DraftTitleRun[]) {
  const titles: Record<string, string> = {};
  for (const run of runs) {
    const title = run?.result?.title;
    if (run?.mode === "GENERATE" && run.status === "SUCCEEDED" && typeof title === "string" && title.trim()) {
      titles[run.id] = title.trim();
    }
  }
  return titles;
}
