import type { MermaidConfig } from "mermaid";

/** Trusted application settings; diagram-supplied configuration is rejected. */
export function mermaidRenderConfig(): MermaidConfig {
  return {
    startOnLoad: false,
    theme: "default",
    securityLevel: "strict",
    maxTextSize: 20000,
    maxEdges: 500,
    // Mermaid 12's common node renderers read the root setting. HTML labels
    // would use foreignObject, which our SVG sanitizer intentionally removes.
    htmlLabels: false,
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    themeVariables: {
      primaryColor: "#EBF5FF",
      primaryTextColor: "#1E3A8A",
      primaryBorderColor: "#3B82F6",
      lineColor: "#64748B",
      secondaryColor: "#F1F5F9",
      tertiaryColor: "#FFFFFF",
    },
  };
}
