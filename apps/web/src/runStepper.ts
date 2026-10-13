export const RUN_STAGES = [
  { key: "READING", label: "读取材料" },
  { key: "ANALYZING", label: "分析与组织" },
  { key: "SUBMITTING", label: "返回结果" },
] as const;

export function runStageIndex(stage: string | null | undefined) {
  if (stage === "ANALYZING") return 1;
  if (stage === "SUBMITTING") return 2;
  return 0;
}
