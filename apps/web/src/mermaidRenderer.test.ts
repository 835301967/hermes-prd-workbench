import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { renderMarkdown } from "./markdown";
import { renderMermaidInContainer } from "./mermaidRenderer";

const originalBox = Object.getOwnPropertyDescriptor(SVGElement.prototype, "getBBox");
const originalLength = Object.getOwnPropertyDescriptor(SVGElement.prototype, "getComputedTextLength");

beforeAll(() => {
  // JSDOM has no SVG layout engine. Only geometry is stubbed; Mermaid and the
  // production Markdown/render/sanitizer path run unchanged with real labels.
  Object.defineProperty(SVGElement.prototype, "getBBox", {
    configurable: true,
    value: () => ({ x: 0, y: 0, width: 100, height: 30 }),
  });
  Object.defineProperty(SVGElement.prototype, "getComputedTextLength", {
    configurable: true,
    value: () => 100,
  });
});

afterEach(() => {
  document.body.replaceChildren();
});

afterAll(() => {
  for (const [name, descriptor] of [["getBBox", originalBox], ["getComputedTextLength", originalLength]] as const) {
    if (descriptor) Object.defineProperty(SVGElement.prototype, name, descriptor);
    else Reflect.deleteProperty(SVGElement.prototype, name);
  }
});

async function render(code: string) {
  const container = document.createElement("div");
  container.innerHTML = renderMarkdown("```mermaid\n" + code + "\n```");
  document.body.append(container);
  await renderMermaidInContainer(container);
  return container;
}

describe("real Mermaid labels through the PRD rendering path", () => {
  it("preserves Chinese flowchart node and edge text after SVG sanitization", async () => {
    const container = await render("flowchart TD\nA[提交申请] --> B{审核通过?}\nB -->|通过| C[生成文档]\nB -->|退回| A");
    expect(container.querySelector(".mermaid-error")).toBeNull();
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(svg!.querySelector("foreignObject")).toBeNull();
    const labels = Array.from(svg!.querySelectorAll("text")).map((el) => el.textContent).join(" ");
    for (const label of ["提交申请", "审核通过?", "生成文档", "通过", "退回"]) expect(labels).toContain(label);
  });

  it("preserves Chinese sequence actors and messages after SVG sanitization", async () => {
    const container = await render("sequenceDiagram\nparticipant U as 需求提出者\nparticipant W as PRD工作台\nU->>W: 提交需求材料\nW-->>U: 返回生成文档");
    expect(container.querySelector(".mermaid-error")).toBeNull();
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(svg!.querySelector("foreignObject")).toBeNull();
    const labels = Array.from(svg!.querySelectorAll("text")).map((el) => el.textContent).join(" ");
    for (const label of ["需求提出者", "PRD工作台", "提交需求材料", "返回生成文档"]) expect(labels).toContain(label);
  });

  it("retains source and an error notice for forbidden diagram configuration", async () => {
    const code = "%%{init: {'securityLevel':'loose'}}%%\nflowchart TD\nA-->B";
    const container = await render(code);
    expect(container.querySelector(".mermaid-error-notice")).not.toBeNull();
    expect(container.querySelector("pre code")?.textContent).toContain(code);
    expect(container.querySelector("svg")).toBeNull();
  });

  it("preserves sequence notes, alternatives, and loop labels", async () => {
    const container = await render("sequenceDiagram\nactor U as 需求提出者\nparticipant W as PRD工作台\nNote over U,W: 需求收集与校验\nalt 材料完整\nU->>W: 提交需求材料\nloop 逐项评审\nW-->>U: 返回问题清单\nend\nelse 材料不完整\nW-->>U: 补充材料\nend");
    expect(container.querySelector(".mermaid-error")).toBeNull();
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(svg!.querySelector("foreignObject")).toBeNull();
    const labels = Array.from(svg!.querySelectorAll("text")).map((el) => el.textContent).join(" ");
    for (const label of ["需求收集与校验", "材料完整", "逐项评审", "返回问题清单", "材料不完整", "补充材料"]) expect(labels).toContain(label);
  });
});
