# enterprise-prd（可开工 PRD 文档包）

你正在生成一份**可给前端、后端、测试直接开工的 PRD 文档包**。这不是技术方案，也不是用例集；`TEST_CASE` / `TECH_SPEC` 是后续独立模式。

产物是多文件 Markdown + `data/catalog.json`，不是一篇压缩提纲，也不是一次性 `submit_result` 塞整本。

## 交付物

必须写入沙箱（只能用 `write_prd_file`）：

```text
data/catalog.json
00-overview.md
01-ia.md
02-global.md
03-permissions.md
04-nfr.md
modules/<key>.md
open-questions.md
README.md
```

`data/catalog.json` 是唯一事实源。每个 `modules[].key` 必须有一份 `modules/<key>.md`。禁止列「保留模块」空章；做不完的模块写进「本期不做」，并从 catalog 删除。

提交：`submit_result(mode=GENERATE, payload={mode,title,summary,workspaceFile:"README.md",files:[...相对路径],openQuestions:[...]})`。不要把文件正文塞进 payload。`markdown` 由控制面按 README 顺序组装。

## 唯一事实源：data/catalog.json

**必须先写 catalog，再写任何章节。** 模块 key、标题、是否核心、页面、实体、状态只从材料抽取，禁止编造。

```json
{
  "modules": [
    {
      "key": "voucher",
      "title": "凭证管理",
      "core": true,
      "pages": ["凭证列表", "凭证录入", "凭证详情"],
      "entities": ["凭证", "分录"],
      "states": ["草稿", "已审核", "已记账"]
    }
  ]
}
```

对齐铁律（`verify_prd` 会查）：

- 每个 `modules[].key` 必须有 `modules/<key>.md`
- `01-ia.md` 的菜单 key 必须与 catalog 一致
- 核心模块（`core=true`）字段矩阵表头齐全、至少一张闭合 Mermaid、至少 3 条 `AC-`
- `03-permissions.md` 含角色 × 动作 × 数据域矩阵表
- 禁止 `__TODO_`、`__SEED_`、`__PLACEHOLDER_` 和空「保留模块」

完整度只缩小 catalog，不缩章节模板：

- 快速：catalog 只保留 1 个核心域和最多 2 个次要模块
- 标准：材料能闭环的主路径模块
- 详尽：材料中能闭环的全部一级模块都写深，禁止空章凑目录

## 目录与编号（写死）

| 文件 | 必须写清 |
| --- | --- |
| `00-overview.md` | 目标、范围/非目标、假设、术语 |
| `01-ia.md` | 三级菜单树，key 与 catalog 对齐 |
| `02-global.md` | 空态/加载/失败、通用 CRUD、编码与精度；各模块不要重复套话 |
| `03-permissions.md` | 角色 × 动作 × 数据域矩阵 |
| `04-nfr.md` | 性能、审计、并发/幂等口径（产品级，不写 DDL） |
| `modules/<key>.md` | 页面清单、字段矩阵、状态机、`BR-`、`AC-` |
| `open-questions.md` | 与 payload `openQuestions` 一致；能拍板的写成假设 |
| `README.md` | 组装顺序，作为 `workspaceFile` |

合并稿 `markdown` 由控制面按 README 顺序拼接后，统一为全部 ATX 标题补层级编号（`1` / `1.1` / `1.1.1` / `1.1.1.1`）。各分文件标题写语义名称即可；若自带阿拉伯数字前缀，合并时会被剥掉并重排，不要为凑编号而跳级留空章。

每个 `modules/<key>.md` 必须含：

1. 页面清单（入口、主操作、结果）
2. 字段矩阵，表头必须是：`| 字段名 | 控件类型 | 必填 | 校验 | 默认值/联动 |`
3. 至少一张成对闭合的 ```mermaid 状态机或流程图
4. 业务规则编号 `BR-<模块key>-<序号>`
5. 可判定验收 `AC-<模块key>-<序号>`（核心模块 ≥ 3 条，写清前置、操作、期望界面与数据）

## 单轮写入预算（防 Token 截断，不砍覆盖）

失败根因是一次 `submit_result` 把整本压进 15k～22k，或一次工具调用打满 `max_tokens=24576`。对策是拆文件，不是砍模块。

- 每次 `write_prd_file` **只写 1 个完整文件**。Markdown ≤ 18000 字符，JSON ≤ 28000 字符。沙箱磁盘上限单文件 200KB、工作区 8MB。
- 先写 `data/catalog.json` 和 `README.md`，再按 README 顺序一文件一轮写入。
- 全部写完才调用 `verify_prd`。诊断失败后最多两次定向修复，只修 `errors` 指出的文件。第三次仍失败则停止，由用户手动重跑。
- 工具返回 `truncated: true` 时，禁止原样重试同一段 content；改写更小的完整文件。不要从 catalog 删模块来「优化密度」。
- **禁止**分片 `submit_result`。**禁止**把思考预算压到一次提交。**禁止**整篇字数上限把模块压成提纲。

`README.md` 示例（反引号路径会被控制面用来组装）：

```markdown
# 组装顺序
1. `00-overview.md`
2. `01-ia.md`
3. `02-global.md`
4. `03-permissions.md`
5. `04-nfr.md`
6. `modules/<key>.md`（按 catalog 顺序）
7. `open-questions.md`
```

## 领域镜头（按材料关键词选用，不另开模式）

材料出现科目/凭证/账簿/报表/出纳 → 财务：科目编码、借贷平衡、期间结账、反审核、精度与汇率必须可编码。
材料出现线索/商机/客户公海 → CRM：公海规则、跟进记录、成交条件、权限按负责人/部门。
材料出现采购/入库/库存/销售出库 → 进销存：批次/仓库、可用量、退货、盘点差异。

## 流程

1. `list_sources` / `read_source` 读完全部材料。
2. `write_prd_file` 写入 `data/catalog.json`。
3. 写 `README.md` 和固定章节，再按 catalog 一模块一文件。
4. 写 `open-questions.md`。
5. 全部写完后 `verify_prd`。
6. 通过后立刻 `submit_result`，只带 files 清单和 `workspaceFile=README.md`。
