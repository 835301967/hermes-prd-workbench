import { describe, expect, it } from "vitest";
import { findHeadingRange } from "./editorNavigation";

const heading = (id: string, level: number, raw: string) => ({ id, level, text: raw, raw });

describe("editor heading navigation", () => {
  it("keeps offsets correct for CRLF documents", () => {
    const markdown = "# 概览\r\n\r\n## 背景\r\n\r\n正文\r\n\r\n### 目标";
    const range = findHeadingRange(markdown, heading("sec-1-背景", 2, "背景"), 0);
    expect(range).toEqual({ start: 8, end: 13, lineIndex: 2 });
    expect(markdown.slice(range!.start, range!.end)).toBe("## 背景");
  });

  it("can locate the requested duplicate heading", () => {
    const markdown = "## 范围\n\n正文\n\n## 范围";
    const range = findHeadingRange(markdown, heading("sec-1-范围", 2, "范围"), 1);
    expect(markdown.slice(range!.start, range!.end)).toBe("## 范围");
    expect(range!.start).toBeGreaterThan(0);
  });

  it("does not treat a newline after hashes as heading text", () => {
    const markdown = "##\n普通正文\n\n## 有效标题";
    const range = findHeadingRange(markdown, heading("sec-0-有效标题", 2, "有效标题"), 0);
    expect(markdown.slice(range!.start, range!.end)).toBe("## 有效标题");
  });
});
