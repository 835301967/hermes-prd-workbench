export type DemoPreviewState = "empty" | "loading" | "ready" | "failed";

type SettingsSummaryInput = {
  completeness: string;
  defaultFocus: string;
  selectedExtraCount: number;
  skill?: string;
};

export function buildSettingsSummary(input: SettingsSummaryInput) {
  const parts = [input.completeness, input.defaultFocus];
  if (input.selectedExtraCount > 0) {
    parts.push(`${input.selectedExtraCount} 个补充项`);
  }
  if (input.skill) {
    parts.push(input.skill);
  }
  return parts.filter(Boolean).join(" · ");
}

export function initialDemoPreviewState(html: string): DemoPreviewState {
  return html.trim() ? "loading" : "empty";
}

export function hasDistinctContextBody(title: string, body: string) {
  const normalizedTitle = title.trim();
  const normalizedBody = body.trim();
  return Boolean(normalizedBody && normalizedBody !== normalizedTitle);
}
