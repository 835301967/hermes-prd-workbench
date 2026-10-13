import { describe, expect, it } from "vitest";
import { MAX_DEMO_TEXT_PATCHES, patchUniqueVisibleText, replayDemoTextPatches } from "./demoPatches";

describe("local Demo text patches", () => {
  it("replaces one exact visible text node and preserves a complete document", () => {
    const html = "<!DOCTYPE html><html><body><button> 提交申请 </button></body></html>";
    const result = patchUniqueVisibleText(html, "提交申请", "发起审批");
    expect(result.status).toBe("APPLIED");
    expect(result.html).toContain("<button> 发起审批 </button>");
    expect(result.html.startsWith("<!DOCTYPE html>")).toBe(true);
  });

  it("refuses zero or multiple matches", () => {
    const html = "<!DOCTYPE html><html><body><button>提交</button><a>提交</a></body></html>";
    expect(patchUniqueVisibleText(html, "不存在", "确认").status).toBe("NO_MATCH");
    expect(patchUniqueVisibleText(html, "提交", "确认")).toMatchObject({ status: "MULTIPLE", matches: 2 });
  });

  it("does not patch script, style, head or attributes", () => {
    const html = '<!DOCTYPE html><html><head><title>提交</title><style>.x{content:"提交"}</style></head><body><button aria-label="提交">确认</button><script>const label="提交"</script></body></html>';
    expect(patchUniqueVisibleText(html, "提交", "保存").status).toBe("NO_MATCH");
  });

  it("does not patch hidden or aria-hidden text", () => {
    const html = '<!DOCTYPE html><html><body><p hidden>提交</p><p aria-hidden="true">提交</p></body></html>';
    expect(patchUniqueVisibleText(html, "提交", "保存").status).toBe("NO_MATCH");
  });

  it("replays run-scoped patches in order", () => {
    const html = "<!DOCTYPE html><html><body><button>提交申请</button></body></html>";
    const replayed = replayDemoTextPatches(html, [
      { from: "提交申请", to: "发起审批" },
      { from: "发起审批", to: "确认审批" },
    ]);
    expect(replayed).toMatchObject({ valid: true, applied: 2 });
    expect(replayed.html).toContain("确认审批");
  });

  it("rejects empty and no-op patches", () => {
    expect(patchUniqueVisibleText("", "提交", "保存").status).toBe("INVALID");
    expect(patchUniqueVisibleText("<p>提交</p>", "提交", "提交").status).toBe("INVALID");
  });

  it("rejects more than the persisted run limit", () => {
    const html = "<!DOCTYPE html><html><body><button>提交</button></body></html>";
    const patches = Array.from({ length: MAX_DEMO_TEXT_PATCHES + 1 }, () => ({ from: "提交", to: "确认" }));
    expect(replayDemoTextPatches(html, patches)).toMatchObject({ valid: false, applied: 0, html });
  });
});
