import DOMPurify from "dompurify";
import { marked } from "marked";
import { extractToc, injectHeadingAnchors, type TocItem } from "./toc";

marked.use({ gfm: true, breaks: true });

const TITLE_KEYS = ["title", "item", "name", "summary", "suggestion", "detail", "description", "content", "text", "impact"];
const DETAIL_KEYS = ["detail", "description", "suggestion", "reference", "impact", "content", "text"];

export function unwrapMarkdown(markdown: string): string {
  const trimmed = (markdown || "").trim();
  const fenced = trimmed.match(/^```(?:markdown|md)?\s*\n([\s\S]*?)\n```$/i);
  return fenced ? fenced[1].trim() : trimmed;
}

export function stripDuplicateLeadingHeading(markdown: string, title?: string): string {
  const body = unwrapMarkdown(markdown);
  const normalizedTitle = (title || "").trim();
  if (!normalizedTitle) {
    return body;
  }
  const match = body.match(/^#\s+(.+?)\s*(?:\n|$)/);
  if (!match) {
    return body;
  }
  const heading = match[1].trim();
  const compact = (value: string) => value.replace(/\s+/g, "");
  if (heading === normalizedTitle || compact(heading) === compact(normalizedTitle)) {
    return body.slice(match[0].length).trimStart();
  }
  return body;
}

export function renderMarkdown(markdown: string): string {
  const html = marked.parse(unwrapMarkdown(markdown), { async: false }) as string;
  if (typeof window === "undefined") {
    return html;
  }
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ["script", "iframe", "object", "embed", "form", "style"],
    FORBID_ATTR: ["onerror", "onload", "onclick", "onmouseover"],
  });
}

export function renderMarkdownWithAnchors(markdown: string): { html: string; toc: TocItem[] } {
  const toc = extractToc(markdown);
  const baseHtml = renderMarkdown(markdown);
  const html = injectHeadingAnchors(baseHtml, toc);
  return { html, toc };
}

export function itemList(value: unknown): Array<Record<string, unknown>> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => (typeof item === "string" ? { title: item } : (item as Record<string, unknown>)));
}

function pickText(item: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = String(item[key] || "").trim();
    if (value) {
      return value;
    }
  }
  return "";
}

function allTextValues(item: Record<string, unknown>) {
  const seen = new Set<string>();
  const values: string[] = [];
  for (const key of [...TITLE_KEYS, ...DETAIL_KEYS]) {
    const value = String(item[key] || "").trim();
    if (value && !seen.has(value)) {
      seen.add(value);
      values.push(value);
    }
  }
  for (const value of Object.values(item)) {
    if (typeof value === "string") {
      const trimmed = value.trim();
      if (trimmed && !seen.has(trimmed)) {
        seen.add(trimmed);
        values.push(trimmed);
      }
    }
  }
  return values;
}

export function issueTitle(item: Record<string, unknown>) {
  const text = pickText(item, TITLE_KEYS);
  if (text) {
    return text.length > 120 ? `${text.slice(0, 117)}…` : text;
  }
  return "未命名问题";
}

export function issueDetail(item: Record<string, unknown>) {
  const values = allTextValues(item);
  if (values.length <= 1) {
    return "";
  }
  const headline = pickText(item, TITLE_KEYS);
  for (const value of values) {
    if (value !== headline) {
      return value;
    }
  }
  return "";
}
