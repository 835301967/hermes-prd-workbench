import { describe, expect, it, vi } from "vitest";
import {
  DEMO_PREVIEW_SANDBOX,
  buildFallbackDemoScript,
  createDemoPreviewUrl,
  createDemoPreviewDocument,
  createSandboxedDemoHtml,
  inlineStaticPocHtml,
  isDiagramBlock,
  markdownToDocxBlob,
  parseMarkdownBlocks,
  renderDiagramToPng,
  safeFilename,
} from "./exportDoc";

describe("export helpers", () => {
  it("sanitizes download names", () => {
    expect(safeFilename("渠道/入驻:PRD", "md")).toBe("渠道-入驻-PRD.md");
    expect(safeFilename("入驻 Demo", "html")).toBe("入驻 Demo.html");
  });

  it("parses headings lists and tables", () => {
    const blocks = parseMarkdownBlocks(`# 标题

## 小节

- 一项
- 二项

| 列 | 值 |
| --- | --- |
| 审批 | 人工 |
`);
    expect(blocks[0]).toMatchObject({ type: "heading", level: 1, text: "标题" });
    expect(blocks.some((block) => block.type === "list")).toBe(true);
    expect(blocks.some((block) => block.type === "table")).toBe(true);
  });

  it("previews demo html in an isolated blob url without same-origin sandbox", () => {
    expect(DEMO_PREVIEW_SANDBOX.split(/\s+/)).toEqual(["allow-scripts"]);
    expect(DEMO_PREVIEW_SANDBOX).not.toContain("allow-same-origin");
    vi.stubGlobal("URL", class extends URL {
      static createObjectURL = vi.fn(() => "blob:synthetic-preview");
      static revokeObjectURL = vi.fn();
    });
    const url = createDemoPreviewUrl("<!DOCTYPE html><html><body>demo</body></html>");
    expect(url.startsWith("blob:")).toBe(true);
    URL.revokeObjectURL(url);
    expect(createDemoPreviewUrl("   ")).toBe("");
    vi.unstubAllGlobals();
  });

  it("encodes malicious HTML inside a sandbox instead of executing it in the Blob shell", () => {
    const shell = createDemoPreviewDocument('<!doctype html><html><head></head><body></iframe><script>localStorage.getItem("prd-workbench-token")</script></body></html>');
    const doc = new DOMParser().parseFromString(shell, "text/html");
    expect(doc.querySelectorAll("script")).toHaveLength(0);
    expect(doc.querySelectorAll("iframe")).toHaveLength(1);
    const iframe = doc.querySelector("iframe")!;
    expect(iframe.getAttribute("sandbox")).toBe("allow-scripts");
    expect(iframe.getAttribute("srcdoc")).toContain("connect-src 'none'");
    expect(iframe.getAttribute("srcdoc")).toContain("localStorage");
  });

  it("inlines poc css and js for srcdoc preview without same-origin", () => {
    const html = inlineStaticPocHtml(
      `<!DOCTYPE html><html><head><link rel="stylesheet" href="css/app.css"></head><body><div class="app">POC工作台演示</div><script src="js/app.js"></script></body></html>`,
      { "css/app.css": ".app{color:red}", "js/app.js": "window.ready=true;" }
    );
    expect(html).toContain("<style>");
    expect(html).toContain(".app{color:red}");
    expect(html).toContain("window.ready=true;");
    expect(html).not.toContain("href=\"css/app.css\"");
    expect(DEMO_PREVIEW_SANDBOX).not.toContain("allow-same-origin");
  });

  it("injects mock json before scripts for srcdoc preview", () => {
    const html = inlineStaticPocHtml(
      `<!DOCTYPE html><html><body><div class="app">POC</div><script src="js/store.js"></script></body></html>`,
      { "data/mock.json": "{\"ok\":true}", "js/store.js": "console.log(window.__POC_MOCK__);" }
    );
    expect(html).toContain("window.__POC_MOCK__={\"ok\":true}");
    const mockIndex = html.indexOf("window.__POC_MOCK__");
    const scriptIndex = html.indexOf("console.log(window.__POC_MOCK__);");
    expect(mockIndex).toBeGreaterThan(-1);
    expect(scriptIndex).toBeGreaterThan(mockIndex);
  });

  it("adds a restrictive CSP only to the sandboxed preview document", () => {
    const preview = createSandboxedDemoHtml("<!DOCTYPE html><html><head></head><body>demo</body></html>");
    expect(preview).toContain("form-action 'none'");
    expect(preview).toContain("connect-src 'none'");
    expect(preview).not.toContain("allow-forms");
  });

  it("loads the Word exporter on demand and returns a non-empty document", async () => {
    const blob = await markdownToDocxBlob("测试 PRD", "# 目标\n\n- 可导出");
    expect(blob.type).toBe("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    expect(blob.size).toBeGreaterThan(1000);
  });

  it("builds fallback presentation script with title, summary and structure", () => {
    const script = buildFallbackDemoScript("退款审批台", "提供客服与财务两级审批闭环");
    expect(script).toContain("# 退款审批台 · 演示讲解词");
    expect(script).toContain("提供客服与财务两级审批闭环");
    expect(script).toContain("## 1. 演示开场");
    expect(script).toContain("## 2. 核心演示动线");
    expect(script).toContain("## 3. 常见追问与答疑要点");
    expect(script).toContain("## 4. 总结与后续排期");

    const defaultScript = buildFallbackDemoScript("", "");
    expect(defaultScript).toContain("# 系统原型 · 演示讲解词");
    expect(defaultScript).toContain("基于产品需求文档落地的交互演示原型");
  });

  it("identifies diagram code blocks by language tag and syntax", () => {
    expect(isDiagramBlock("mermaid", "graph TD\nA-->B")).toBe(true);
    expect(isDiagramBlock("flowchart", "flowchart LR\nA-->B")).toBe(true);
    expect(isDiagramBlock("sequenceDiagram", "sequenceDiagram\nAlice->>Bob: Hi")).toBe(true);
    expect(isDiagramBlock("", "graph TD\n  A[发起] --> B[完成]")).toBe(true);
    expect(isDiagramBlock("", "sequenceDiagram\n  autonumber\n  A->>B: Call")).toBe(true);
    expect(isDiagramBlock("", "stateDiagram-v2\n  [*] --> Active")).toBe(true);
    expect(isDiagramBlock("", "erDiagram\n  USER ||--o{ ORDER : places")).toBe(true);
    expect(isDiagramBlock("typescript", "const a = 10;")).toBe(false);
    expect(isDiagramBlock("json", '{"key": "value"}')).toBe(false);
    expect(isDiagramBlock("", "plain text message")).toBe(false);
  });

  it("parses markdown containing both diagrams and regular code blocks", () => {
    const md = `# 需求设计

\`\`\`mermaid
graph TD
  A[提交] --> B[审批]
\`\`\`

\`\`\`json
{ "code": 200 }
\`\`\`
`;
    const blocks = parseMarkdownBlocks(md);
    const diagram = blocks.find((b) => b.type === "diagram");
    const code = blocks.find((b) => b.type === "code");

    expect(diagram).toBeDefined();
    if (diagram && diagram.type === "diagram") {
      expect(diagram.lang).toBe("mermaid");
      expect(diagram.code).toContain("graph TD");
    }

    expect(code).toBeDefined();
    if (code && code.type === "code") {
      expect(code.text).toContain('"code": 200');
    }
  });

  it("exports Word documents with diagrams gracefully", async () => {
    const md = `# 业务流程需求

## 流程图
\`\`\`mermaid
graph TD
  A[发起申请] --> B[经理审批]
  B -->|同意| C[流程归档]
  B -->|驳回| D[重新修改]
\`\`\`

## 时序图
\`\`\`sequenceDiagram
autonumber
actor 用户
participant 系统
用户->>系统: 提交表单
系统-->>用户: 返回处理结果
\`\`\`
`;
    const blob = await markdownToDocxBlob("流程设计 PRD", md);
    expect(blob.type).toBe("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    expect(blob.size).toBeGreaterThan(1500);
  });

  it("safely handles diagram rendering in non-canvas environments", async () => {
    const result = await renderDiagramToPng("graph TD\nA-->B");
    // In happy-dom / test environment, getContext is undefined so it returns null safely
    expect(result).toBeNull();
  });
});
