import type { TocItem } from "./toc";

export type HeadingRange = {
  start: number;
  end: number;
  lineIndex: number;
};

export function findHeadingRange(markdown: string, heading: TocItem, ordinal: number): HeadingRange | null {
  const pattern = /^(#{1,6})[ \t]+([^\r\n]+?)(?:\r?\n|$)/gm;
  let match: RegExpExecArray | null;
  let headingIndex = 0;
  while ((match = pattern.exec(markdown))) {
    if (match[1].length === heading.level && match[2].trim() === heading.raw.trim()) {
      if (headingIndex === ordinal) {
        const line = match[0].replace(/\r?\n$/, "");
        return {
          start: match.index,
          end: match.index + line.length,
          lineIndex: markdown.slice(0, match.index).split(/\r?\n/).length - 1,
        };
      }
      headingIndex += 1;
    }
  }
  return null;
}
