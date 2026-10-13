export type DemoTextPatch = {
  from: string;
  to: string;
};

export const MAX_DEMO_TEXT_PATCHES = 50;

export type DemoPatchResult = {
  status: "APPLIED" | "NO_MATCH" | "MULTIPLE" | "INVALID";
  html: string;
  matches: number;
};

const HIDDEN_CONTAINERS = new Set(["script", "style", "head", "template", "noscript"]);

export function patchUniqueVisibleText(html: string, from: string, to: string): DemoPatchResult {
  if (!html.trim() || !from || !to || from === to || typeof DOMParser === "undefined") {
    return { status: "INVALID", html, matches: 0 };
  }

  const document = new DOMParser().parseFromString(html, "text/html");
  if (!document.documentElement || !document.body) {
    return { status: "INVALID", html, matches: 0 };
  }

  const matches: Text[] = [];
  const visit = (node: Node, hidden: boolean) => {
    if (node.nodeType === 3) {
      if (!hidden && (node.nodeValue || "").trim() === from) matches.push(node as Text);
      return;
    }
    if (node.nodeType !== 1 && node.nodeType !== 9 && node.nodeType !== 11) return;
    let nextHidden = hidden;
    if (node.nodeType === 1) {
      const element = node as Element;
      nextHidden =
        hidden ||
        HIDDEN_CONTAINERS.has(element.tagName.toLowerCase()) ||
        element.hasAttribute("hidden") ||
        element.getAttribute("aria-hidden") === "true";
    }
    for (const child of Array.from(node.childNodes)) visit(child, nextHidden);
  };
  visit(document, false);

  if (matches.length !== 1) {
    return { status: matches.length ? "MULTIPLE" : "NO_MATCH", html, matches: matches.length };
  }
  const text = matches[0].nodeValue || "";
  const leading = text.match(/^\s*/)?.[0] || "";
  const trailing = text.match(/\s*$/)?.[0] || "";
  matches[0].nodeValue = `${leading}${to}${trailing}`;
  const doctype = document.doctype ? `<!DOCTYPE ${document.doctype.name}>` : "";
  return { status: "APPLIED", html: `${doctype}${document.documentElement.outerHTML}`, matches: 1 };
}

export function replayDemoTextPatches(html: string, patches: DemoTextPatch[]) {
  if (patches.length > MAX_DEMO_TEXT_PATCHES) return { html, applied: 0, valid: false };
  let current = html;
  for (const patch of patches) {
    const result = patchUniqueVisibleText(current, patch.from, patch.to);
    if (result.status !== "APPLIED") return { html, applied: 0, valid: false };
    current = result.html;
  }
  return { html: current, applied: patches.length, valid: true };
}
