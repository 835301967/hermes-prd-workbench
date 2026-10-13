import { describe, expect, it } from "vitest";
import { filterTasks } from "./taskSearch";

const tasks = [
  { id: "1", title: "医疗满意度" },
  { id: "2", title: "未命名需求" },
  { id: "3", title: "渠道商入驻" },
];

describe("filterTasks", () => {
  it("shows all tasks when query is empty or still the current title", () => {
    expect(filterTasks(tasks, "", "医疗满意度")).toEqual(tasks);
    expect(filterTasks(tasks, "医疗满意度", "医疗满意度")).toEqual(tasks);
  });

  it("filters by title without requiring exact case", () => {
    expect(filterTasks(tasks, "入驻", "医疗满意度").map((item) => item.id)).toEqual(["3"]);
    expect(filterTasks(tasks, "未命名", "医疗满意度")).toEqual([{ id: "2", title: "未命名需求" }]);
  });
});
