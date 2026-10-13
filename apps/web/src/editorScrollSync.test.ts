import { describe, it, expect } from "vitest";
import { interpolateScroll, type ScrollAnchor } from "./editorScrollSync";

describe("editorScrollSync - interpolateScroll", () => {
  const anchors: ScrollAnchor[] = [
    { editorTop: 0, previewTop: 0 },
    { editorTop: 100, previewTop: 200 },
    { editorTop: 300, previewTop: 800 },
    { editorTop: 600, previewTop: 1200 },
  ];
  const editorMax = 600;
  const previewMax = 1200;

  it("returns boundary values accurately", () => {
    expect(interpolateScroll(0, anchors, "editorToPreview", editorMax, previewMax)).toBe(0);
    expect(interpolateScroll(-10, anchors, "editorToPreview", editorMax, previewMax)).toBe(0);
    expect(interpolateScroll(600, anchors, "editorToPreview", editorMax, previewMax)).toBe(1200);
    expect(interpolateScroll(700, anchors, "editorToPreview", editorMax, previewMax)).toBe(1200);

    expect(interpolateScroll(0, anchors, "previewToEditor", previewMax, editorMax)).toBe(0);
    expect(interpolateScroll(1200, anchors, "previewToEditor", previewMax, editorMax)).toBe(600);
  });

  it("hits exact anchor positions with 100% precision", () => {
    // 标题 1 顶部对齐
    expect(interpolateScroll(100, anchors, "editorToPreview", editorMax, previewMax)).toBe(200);
    // 标题 2 顶部对齐
    expect(interpolateScroll(300, anchors, "editorToPreview", editorMax, previewMax)).toBe(800);

    // 反向对齐
    expect(interpolateScroll(200, anchors, "previewToEditor", previewMax, editorMax)).toBe(100);
    expect(interpolateScroll(800, anchors, "previewToEditor", previewMax, editorMax)).toBe(300);
  });

  it("interpolates proportionally within local section without cross-section drifting", () => {
    // 在第 1 节（100 -> 300，对应 200 -> 800，中间增量 600）
    // 左侧在中点 200 时，右侧应严格在中点 200 + 0.5 * 600 = 500
    const midPreview = interpolateScroll(200, anchors, "editorToPreview", editorMax, previewMax);
    expect(midPreview).toBe(500);

    // 在第 2 节（300 -> 600，对应 800 -> 1200，增量 400）
    // 左侧在 450 时（中点），右侧应严格在 800 + 0.5 * 400 = 1000
    const midSection2 = interpolateScroll(450, anchors, "editorToPreview", editorMax, previewMax);
    expect(midSection2).toBe(1000);
  });

  it("falls back gracefully when anchors are empty or fewer than 2", () => {
    expect(interpolateScroll(150, [], "editorToPreview", 300, 600)).toBe(300);
    expect(interpolateScroll(150, [{ editorTop: 0, previewTop: 0 }], "editorToPreview", 300, 600)).toBe(300);
  });
});
