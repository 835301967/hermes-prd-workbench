export type TocItem = {
  id: string;
  level: number;
  text: string;
  raw: string;
};

export function slugify(text: string, index: number): string {
  const clean = text
    .replace(/[^\w\u4e00-\u9fa5\d-]/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
  return `sec-${index}-${clean || "title"}`;
}

export function extractToc(markdown: string): TocItem[] {
  if (!markdown) return [];
  const lines = markdown.split(/\r?\n/);
  const items: TocItem[] = [];
  let codeFence: { marker: "`" | "~"; length: number } | null = null;
  let headingIndex = 0;

  for (const line of lines) {
    const trimmed = line.trim();
    const fence = trimmed.match(/^(`{3,}|~{3,})/);
    if (fence) {
      const marker = fence[1][0] as "`" | "~";
      if (!codeFence) {
        codeFence = { marker, length: fence[1].length };
      } else if (codeFence.marker === marker && fence[1].length >= codeFence.length) {
        codeFence = null;
      }
      continue;
    }
    if (codeFence) continue;

    const match = line.match(/^(#{1,6})[ \t]+(.+)$/);
    if (match) {
      const level = match[1].length;
      const rawText = match[2].trim();
      const plainText = rawText
        .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1")
        .replace(/[*_`~]/g, "")
        .trim();

      if (plainText) {
        items.push({
          id: slugify(plainText, headingIndex++),
          level,
          text: plainText,
          raw: rawText,
        });
      }
    }
  }

  return items;
}

export function injectHeadingAnchors(html: string, toc: TocItem[]): string {
  if (!html || !toc.length) return html;
  let idx = 0;
  return html.replace(/<h([1-6])([^>]*)>(.*?)<\/h\1>/gi, (match, levelStr, attrs, inner) => {
    const level = parseInt(levelStr, 10);
    const item = toc[idx];
    if (item && item.level === level) {
      idx++;
      if (!attrs.includes("id=")) {
        return `<h${level}${attrs} id="${item.id}" data-toc-target="true">${inner}</h${level}>`;
      }
    }
    return match;
  });
}
