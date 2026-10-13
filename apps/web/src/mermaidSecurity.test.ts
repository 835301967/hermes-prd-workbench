import { describe, expect, it } from "vitest";
import { safeDiagramCode, sanitizeDiagramSvg } from "./mermaidSecurity";

describe("untrusted diagram boundaries", () => {
  it("rejects configuration overrides, links, and excessive input", () => {
    expect(() => safeDiagramCode("%%{init: {'securityLevel':'loose'}}%%\ngraph TD\nA-->B")).toThrow();
    expect(() => safeDiagramCode("---\nconfig: {}\n---\ngraph TD\nA-->B")).toThrow();
    expect(() => safeDiagramCode("graph TD\nA-->B\nclick A callback")).toThrow();
    expect(() => safeDiagramCode("A".repeat(20001))).toThrow();
    expect(safeDiagramCode("graph TD\nA-->B")).toBe("graph TD\nA-->B");
  });
  it("strips active SVG, event handlers, and external resources", () => {
    const svg = sanitizeDiagramSvg('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script><foreignObject>private</foreignObject><image href="https://example.com/a"/><path onclick="alert(1)" style="fill:url(https://example.com/a)" d="M0 0"/></svg>');
    expect(svg).not.toMatch(/script|foreignObject|onclick|https:\/\/example/);
    expect(svg).toContain("svg");
    expect(sanitizeDiagramSvg('<svg xmlns="http://www.w3.org/2000/svg"><path d="M0 0"/></svg>')).toContain("path");
  });
});
