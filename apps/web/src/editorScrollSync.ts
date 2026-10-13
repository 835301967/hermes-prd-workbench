import type { TocItem } from "./toc";
import { findHeadingRange } from "./editorNavigation";

export interface ScrollAnchor {
  editorTop: number;
  previewTop: number;
}

export function computeScrollAnchors(options: {
  textarea: HTMLTextAreaElement;
  previewEl: HTMLElement;
  headings: TocItem[];
  markdown: string;
}): ScrollAnchor[] {
  const { textarea, previewEl, headings, markdown } = options;
  if (!textarea || !previewEl) return [];

  const editorMax = Math.max(0, textarea.scrollHeight - textarea.clientHeight);
  const previewMax = Math.max(0, previewEl.scrollHeight - previewEl.clientHeight);

  const rawAnchors: ScrollAnchor[] = [{ editorTop: 0, previewTop: 0 }];

  if (headings && headings.length > 0 && markdown) {
    // 收集所有标题在 Markdown 中的起始字符位置
    const headingRanges = headings.map((h, idx) => {
      const ordinal = headings
        .slice(0, idx)
        .filter((item) => item.level === h.level && item.raw.trim() === h.raw.trim()).length;
      return {
        heading: h,
        range: findHeadingRange(markdown, h, ordinal),
      };
    });

    const validRanges = headingRanges.filter((hr) => hr.range !== null);
    const charPositions = validRanges.map((vr) => vr.range!.start);
    const editorTops = computeTextareaOffsets(textarea, charPositions, validRanges.map((vr) => vr.range!.lineIndex));

    const containerRect = previewEl.getBoundingClientRect();
    const currentPreviewScroll = previewEl.scrollTop;

    for (let i = 0; i < validRanges.length; i++) {
      const item = validRanges[i];
      const eTop = editorTops[i];
      const previewHeadingEl = previewEl.querySelector<HTMLElement>(`#${CSS.escape(item.heading.id)}`);
      if (previewHeadingEl) {
        const hRect = previewHeadingEl.getBoundingClientRect();
        const pTop = Math.max(0, hRect.top - containerRect.top + currentPreviewScroll);
        rawAnchors.push({
          editorTop: Math.max(0, eTop),
          previewTop: pTop,
        });
      }
    }
  }

  rawAnchors.push({ editorTop: editorMax, previewTop: previewMax });

  // 保证单调递增并过滤冗余重叠锚点
  const anchors: ScrollAnchor[] = [rawAnchors[0]];
  for (let i = 1; i < rawAnchors.length; i++) {
    const prev = anchors[anchors.length - 1];
    const curr = rawAnchors[i];
    if (curr.editorTop > prev.editorTop && curr.previewTop > prev.previewTop) {
      anchors.push(curr);
    }
  }

  // 确保最后一个锚点覆盖最大滚动边界
  const last = anchors[anchors.length - 1];
  if (last.editorTop < editorMax || last.previewTop < previewMax) {
    anchors.push({ editorTop: editorMax, previewTop: previewMax });
  }

  return anchors;
}

function computeTextareaOffsets(
  textarea: HTMLTextAreaElement,
  charPositions: number[],
  lineIndices: number[]
): number[] {
  if (typeof document === "undefined" || !textarea.clientWidth) {
    // SSR / Mock 环境退化处理
    const lineHeight = 24;
    return lineIndices.map((idx) => idx * lineHeight);
  }

  try {
    const style = window.getComputedStyle(textarea);
    const mirror = document.createElement("div");
    mirror.style.position = "absolute";
    mirror.style.visibility = "hidden";
    mirror.style.left = "-9999px";
    mirror.style.top = "0";
    mirror.style.width = `${textarea.clientWidth}px`;
    mirror.style.boxSizing = "border-box";
    mirror.style.font = style.font;
    mirror.style.fontFamily = style.fontFamily;
    mirror.style.fontSize = style.fontSize;
    mirror.style.fontWeight = style.fontWeight;
    mirror.style.lineHeight = style.lineHeight;
    mirror.style.letterSpacing = style.letterSpacing;
    mirror.style.padding = style.padding;
    mirror.style.border = style.border;
    mirror.style.whiteSpace = "pre-wrap";
    mirror.style.wordBreak = "break-word";
    mirror.style.overflowWrap = "break-word";

    document.body.appendChild(mirror);

    const offsets: number[] = [];
    const text = textarea.value;
    for (const pos of charPositions) {
      mirror.textContent = text.slice(0, pos);
      const span = document.createElement("span");
      span.textContent = "\u200b";
      mirror.appendChild(span);
      offsets.push(span.offsetTop);
    }
    document.body.removeChild(mirror);
    return offsets;
  } catch {
    const lineHeight = parseFloat(window.getComputedStyle(textarea).lineHeight) || 24;
    return lineIndices.map((idx) => idx * lineHeight);
  }
}

export function interpolateScroll(
  sourceY: number,
  anchors: ScrollAnchor[],
  direction: "editorToPreview" | "previewToEditor",
  sourceMax: number,
  targetMax: number
): number {
  if (sourceMax <= 0 || targetMax <= 0) return 0;
  if (sourceY <= 0) return 0;
  if (sourceY >= sourceMax) return targetMax;
  if (!anchors || anchors.length < 2) {
    return (sourceY / sourceMax) * targetMax;
  }

  const srcKey = direction === "editorToPreview" ? "editorTop" : "previewTop";
  const tgtKey = direction === "editorToPreview" ? "previewTop" : "editorTop";

  if (sourceY <= anchors[0][srcKey]) {
    const srcDist = anchors[0][srcKey];
    if (srcDist <= 0) return anchors[0][tgtKey];
    return (sourceY / srcDist) * anchors[0][tgtKey];
  }

  const last = anchors[anchors.length - 1];
  if (sourceY >= last[srcKey]) {
    const srcDist = sourceMax - last[srcKey];
    if (srcDist <= 0) return targetMax;
    const ratio = (sourceY - last[srcKey]) / srcDist;
    return last[tgtKey] + ratio * (targetMax - last[tgtKey]);
  }

  // 二分查找区间 [mid, mid+1]
  let low = 0;
  let high = anchors.length - 1;
  while (low <= high) {
    const mid = Math.floor((low + high) / 2);
    if (anchors[mid][srcKey] <= sourceY) {
      if (mid === anchors.length - 1 || anchors[mid + 1][srcKey] > sourceY) {
        const segSrcLen = anchors[mid + 1][srcKey] - anchors[mid][srcKey];
        if (segSrcLen <= 0) return anchors[mid][tgtKey];
        const ratio = (sourceY - anchors[mid][srcKey]) / segSrcLen;
        return anchors[mid][tgtKey] + ratio * (anchors[mid + 1][tgtKey] - anchors[mid][tgtKey]);
      }
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  return (sourceY / sourceMax) * targetMax;
}
