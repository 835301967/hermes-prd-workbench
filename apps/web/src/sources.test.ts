import { describe, expect, it } from "vitest";
import {
  demoUploadName,
  displaySourceName,
  draftTitlesFromRuns,
  draftRunId,
  draftSourceName,
  estimateUploadName,
  isDraftSource,
  isGenerateSource,
  isReviewSource,
  pocUploadName,
  reviewUploadName,
  techSpecUploadName,
  testCaseUploadName,
  visibleSources,
} from "./sources";

describe("source roles", () => {
  it("keeps original files on generate and isolates review vs demo extras", () => {
    const all = [
      { id: "1", taskId: "t", name: "IOC需求清单.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
      { id: "2", taskId: "t", name: "生成稿-run_1.md", mediaType: "text/markdown", size: 1, status: "READY", createdAt: "" },
      { id: "3", taskId: "t", name: "演示补充-风格.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
      { id: "4", taskId: "t", name: "评审补充-口径.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
      { id: "5", taskId: "t", name: "评估补充-工时.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
      { id: "6", taskId: "t", name: "用例补充-范围.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
      { id: "7", taskId: "t", name: "方案补充-架构.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
      { id: "8", taskId: "t", name: "POC补充-范围.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
    ];
    expect(visibleSources(all, "GENERATE").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(all, "GENERATE", "run_1").map((item) => item.name)).toEqual(["IOC需求清单.txt", "生成稿-run_1.md"]);
    expect(visibleSources(all, "REVIEW").map((item) => item.name)).toEqual(["生成稿-run_1.md", "评审补充-口径.txt"]);
    expect(visibleSources(all, "DEMO").map((item) => item.name)).toEqual(["生成稿-run_1.md", "演示补充-风格.txt"]);
    expect(visibleSources(all, "POC").map((item) => item.name)).toEqual(["生成稿-run_1.md", "POC补充-范围.txt"]);
    // When PRD exists: only PRD and own extras are visible, raw materials are hidden
    expect(visibleSources(all, "ESTIMATE").map((item) => item.name)).toEqual(["生成稿-run_1.md", "评估补充-工时.txt"]);
    expect(visibleSources(all, "TEST_CASE").map((item) => item.name)).toEqual(["生成稿-run_1.md", "用例补充-范围.txt"]);
    expect(visibleSources(all, "TECH_SPEC").map((item) => item.name)).toEqual(["生成稿-run_1.md", "方案补充-架构.txt"]);

    // When PRD does NOT exist: raw materials are visible for direct estimation / spec / testcase
    const withoutDraft = [
      { id: "1", taskId: "t", name: "IOC需求清单.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
      { id: "5", taskId: "t", name: "评估补充-工时.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
    ];
    expect(visibleSources(withoutDraft, "ESTIMATE").map((item) => item.name)).toEqual(["IOC需求清单.txt", "评估补充-工时.txt"]);
    expect(visibleSources(withoutDraft, "TEST_CASE").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(withoutDraft, "TECH_SPEC").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(withoutDraft, "REVIEW").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(withoutDraft, "DEMO").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(withoutDraft, "POC").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);

    // A draft that is not READY must not lock the material set, matching the server's READY-only check
    const failedDraft = [
      { id: "1", taskId: "t", name: "IOC需求清单.txt", mediaType: "text/plain", size: 1, status: "READY", createdAt: "" },
      { id: "2", taskId: "t", name: "生成稿-run_1.md", mediaType: "text/markdown", size: 1, status: "FAILED", createdAt: "" },
    ];
    expect(visibleSources(failedDraft, "TEST_CASE").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(failedDraft, "ESTIMATE").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(failedDraft, "TECH_SPEC").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(failedDraft, "REVIEW").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(failedDraft, "DEMO").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);
    expect(visibleSources(failedDraft, "POC").map((item) => item.name)).toEqual(["IOC需求清单.txt"]);

    expect(isGenerateSource("IOC需求清单.txt")).toBe(true);
    expect(isReviewSource("生成稿-run_1.md")).toBe(true);
    expect(isDraftSource("生成稿-run_1.md")).toBe(true);
    expect(draftSourceName("run_1")).toBe("生成稿-run_1.md");
    expect(draftRunId("生成稿-run_1.md")).toBe("run_1");
    expect(reviewUploadName("补充.txt")).toBe("评审补充-补充.txt");
    expect(demoUploadName("风格.txt")).toBe("演示补充-风格.txt");
    expect(pocUploadName("范围.txt")).toBe("POC补充-范围.txt");
    expect(estimateUploadName("工时.txt")).toBe("评估补充-工时.txt");
    expect(testCaseUploadName("范围.txt")).toBe("用例补充-范围.txt");
    expect(techSpecUploadName("架构.txt")).toBe("方案补充-架构.txt");
    expect(displaySourceName("生成稿-run_1.md")).toBe("生成稿");
    expect(displaySourceName("生成稿-run_1.md", { run_1: "智慧园区能耗看板 PRD" })).toBe("智慧园区能耗看板 PRD");
    expect(displaySourceName("folder/IOC需求清单.txt")).toBe("IOC需求清单.txt");
    expect(displaySourceName("评审补充-补充.txt")).toBe("补充.txt");
    expect(displaySourceName("演示补充-风格.txt")).toBe("风格.txt");
    expect(displaySourceName("POC补充-范围.txt")).toBe("范围.txt");
    expect(displaySourceName("评估补充-工时.txt")).toBe("工时.txt");
    expect(displaySourceName("用例补充-范围.txt")).toBe("范围.txt");
    expect(displaySourceName("方案补充-架构.txt")).toBe("架构.txt");
  });

  it("keeps titles for loaded historical PRD runs", () => {
    const titles = draftTitlesFromRuns([
      { id: "run_latest", mode: "GENERATE", status: "SUCCEEDED", result: { title: "最新 PRD" } },
      { id: "run_old", mode: "GENERATE", status: "SUCCEEDED", result: { title: "历史 PRD" } },
      { id: "run_review", mode: "REVIEW", status: "SUCCEEDED", result: { title: "评审报告" } },
    ]);
    expect(titles).toEqual({ run_latest: "最新 PRD", run_old: "历史 PRD" });
    expect(displaySourceName("生成稿-run_old.md", titles)).toBe("历史 PRD");
  });
});
