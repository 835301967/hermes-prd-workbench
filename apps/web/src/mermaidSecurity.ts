import DOMPurify from "dompurify";

export function safeDiagramCode(code: string): string {
  const text = code.trim();
  if (text.length > 20000 || /%%\s*\{|^---\s*\n|\bclick\s+/im.test(text)) {
    throw new Error("Diagram contains unsupported configuration or exceeds limits");
  }
  if ((text.match(/-->|---|==>|-.->/g) || []).length > 500) {
    throw new Error("Diagram exceeds edge limit");
  }
  return text;
}

export function sanitizeDiagramSvg(svg: string): string {
  const clean = DOMPurify.sanitize(svg, {
    USE_PROFILES: { svg: true, svgFilters: true },
    FORBID_TAGS: ["script", "foreignObject", "image", "a"],
    FORBID_ATTR: ["href", "xlink:href"],
  });
  // Mermaid's local url(#id) markers are retained; remote CSS resources are removed.
  return clean.replace(/url\(([^)]*)\)/gi, (match, ref: string) => /^['"]?#/.test(ref.trim()) ? match : "none").replace(/@import[^;]+;/gi, "");
}
