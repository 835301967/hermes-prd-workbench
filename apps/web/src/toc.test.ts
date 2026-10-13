import { describe, expect, it } from "vitest";
import { extractToc, injectHeadingAnchors, slugify } from "./toc";

describe("toc utility", () => {
  it("slugify handles english and chinese characters properly", () => {
    expect(slugify("1. 业务架构与流程", 0)).toBe("sec-0-1-业务架构与流程");
    expect(slugify("User Story & Acceptance Criteria", 1)).toBe("sec-1-User-Story-Acceptance-Criteria");
    expect(slugify("???!!!", 2)).toBe("sec-2-title");
  });

  it("extracts headings from markdown correctly, ignoring code blocks", () => {
    const md = `
# 标题一
这是一段正文。

\`\`\`markdown
## 这是代码块中的伪标题
\`\`\`

## 标题二：**加粗强调**
一些细节。

### 2.1 [子链接标题](http://example.com)
内容。
`;
    const toc = extractToc(md);
    expect(toc).toHaveLength(3);
    expect(toc[0]).toEqual({
      id: "sec-0-标题一",
      level: 1,
      text: "标题一",
      raw: "标题一",
    });
    expect(toc[1]).toEqual({
      id: "sec-1-标题二-加粗强调",
      level: 2,
      text: "标题二：加粗强调",
      raw: "标题二：**加粗强调**",
    });
    expect(toc[2]).toEqual({
      id: "sec-2-2-1-子链接标题",
      level: 3,
      text: "2.1 子链接标题",
      raw: "2.1 [子链接标题](http://example.com)",
    });
  });

  it("injects heading anchors into rendered HTML", () => {
    const toc = [
      { id: "sec-0-title-1", level: 1, text: "Title 1", raw: "Title 1" },
      { id: "sec-1-title-2", level: 2, text: "Title 2", raw: "Title 2" },
    ];
    const rawHtml = "<h1>Title 1</h1><p>Hello</p><h2>Title 2</h2>";
    const injected = injectHeadingAnchors(rawHtml, toc);
    expect(injected).toContain('<h1 id="sec-0-title-1" data-toc-target="true">Title 1</h1>');
    expect(injected).toContain('<h2 id="sec-1-title-2" data-toc-target="true">Title 2</h2>');
  });

  it("handles empty or heading-less markdown gracefully", () => {
    expect(extractToc("")).toEqual([]);
    expect(extractToc("普通段落文字没有标题")).toEqual([]);
    expect(injectHeadingAnchors("<p>No headings</p>", [])).toBe("<p>No headings</p>");
  });

  it("ignores both backtick and tilde fenced headings", () => {
    const md = "~~~md\n## 代码中的标题\n~~~\n\n## 真实标题";
    const toc = extractToc(md);
    expect(toc).toHaveLength(1);
    expect(toc[0].text).toBe("真实标题");
  });
});
