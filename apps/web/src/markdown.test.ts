import { describe, expect, it } from "vitest";
import {
  issueDetail,
  issueTitle,
  itemList,
  renderMarkdown,
  renderMarkdownWithAnchors,
  stripDuplicateLeadingHeading,
  unwrapMarkdown,
} from "./markdown";

describe("itemList", () => {
  it("normalizes strings and objects", () => {
    expect(itemList(["a", { title: "b" }])).toEqual([{ title: "a" }, { title: "b" }]);
  });
});

describe("review issue display", () => {
  it("falls back to suggestion when title is missing", () => {
    expect(issueTitle({ suggestion: "补充验收口径" })).toBe("补充验收口径");
    expect(issueDetail({ suggestion: "补充验收口径" })).toBe("");
  });

  it("shows detail separately from title", () => {
    expect(issueTitle({ title: "缺少状态机", detail: "未定义驳回后重提" })).toBe("缺少状态机");
    expect(issueDetail({ title: "缺少状态机", detail: "未定义驳回后重提" })).toBe("未定义驳回后重提");
  });

  it("supports ready item and description fields", () => {
    expect(issueTitle({ item: "角色权限模型完整", reference: "3.7" })).toBe("角色权限模型完整");
    expect(issueDetail({ item: "角色权限模型完整", reference: "3.7" })).toBe("3.7");
    expect(issueTitle({ title: "验收口径清晰", description: "已给出 4 条验收标准" })).toBe("验收口径清晰");
    expect(issueDetail({ title: "验收口径清晰", description: "已给出 4 条验收标准" })).toBe("已给出 4 条验收标准");
  });
});

describe("stripDuplicateLeadingHeading", () => {
  it("removes a leading h1 when it matches the result title", () => {
    const markdown = "# 智慧园区能耗看板 PRD\n\n## 1. 产品概述";
    expect(stripDuplicateLeadingHeading(markdown, "智慧园区能耗看板 PRD")).toBe("## 1. 产品概述");
  });
});

describe("unwrapMarkdown", () => {
  it("unwraps a whole-document markdown fence", () => {
    expect(unwrapMarkdown("```markdown\n# 标题\n\n正文\n```")).toBe("# 标题\n\n正文");
  });
});

describe("renderMarkdown", () => {
  it("renders standard GFM headings lists tables and code", () => {
    const html = renderMarkdown(`# 标题

## 小节

- 列表项

| 列 | 值 |
| --- | --- |
| 审批 | 人工 |

\`code\`

> 引用
`);
    expect(html).toContain("<h1>");
    expect(html).toContain("<h2>");
    expect(html).toContain("<ul>");
    expect(html).toContain("<table>");
    expect(html).toContain("<blockquote>");
    expect(html).toContain("<code>");
    expect(html).not.toContain("<script>");
  });

  it("renderMarkdownWithAnchors produces TOC and anchors simultaneously", () => {
    const md = "## 第一章 背景\n\n正文内容\n\n### 1.1 目标\n\n目标明细";
    const result = renderMarkdownWithAnchors(md);
    expect(result.toc).toHaveLength(2);
    expect(result.toc[0].text).toBe("第一章 背景");
    expect(result.html).toContain(`id="${result.toc[0].id}"`);
    expect(result.html).toContain(`id="${result.toc[1].id}"`);
  });
});
