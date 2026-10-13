/** Render original technical detail as text; never normalize or mutate the Agent result. */
export function formatTechnicalDetail(value: unknown): string {
  if (value == null) return "";
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return value.map(formatTechnicalDetail).join("; ");
  return JSON.stringify(value);
}
