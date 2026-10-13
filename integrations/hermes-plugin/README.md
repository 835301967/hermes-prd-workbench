# Hermes Plugin Boundary

`prd-workbench` 插件提供当前 Run 的材料读取、结果提交，以及 Demo/POC/PRD/用例/方案沙箱文件工具：

| 工具 | 用途 |
| --- | --- |
| `list_sources` | 列出当前 Run 的材料 |
| `read_source` | 分页读取一份材料的正文 |
| `submit_result` | 提交 REVIEW、GENERATE、DEMO、POC、ESTIMATE、TEST_CASE 或 TECH_SPEC 结果 |
| `write_demo_file` / `read_demo_file` / `verify_demo` | DEMO 单文件 `index.html` 沙箱写入、读取与静态诊断 |
| `write_poc_file` / `read_poc_file` / `list_poc_files` / `verify_poc` | POC 多文件沙箱（`index.html`、`css/*.css`、`js/**/*.js`、`data/*.json`） |
| `write_prd_file` / `read_prd_file` / `list_prd_files` / `verify_prd` | GENERATE 可开工文档包（`README.md`、`*.md`、`modules/*.md`、`data/*.json`） |
| `write_test_case_file` / `read_test_case_file` / `list_test_case_files` / `verify_test_case` | TEST_CASE 用例包（`README.md`、`data/*.json`、`modules/*.json`） |
| `write_tech_spec_file` / `read_tech_spec_file` / `list_tech_spec_files` / `verify_tech_spec` | TECH_SPEC 方案包（`README.md`、`architecture.md`、`data/*.json`、`modules/*`） |

所有工具都接收业务 `runId`。插件不直接访问数据库或任意宿主机文件，只携带 `PRD_PLUGIN_KEY` 调用 Java 控制面的 `/internal/v1/runs/{runId}` 接口；Java 校验 Run 正在执行且材料属于对应任务。

控制面只检查 Run/模式、JSON 基本结构、结果大小和 Demo/POC 可运行性最小结构，以及 GENERATE/TEST_CASE/TECH_SPEC 文档包白名单，然后直接保存 Hermes payload。Demo 提交前，插件会执行静态脚本语法、声明的动作绑定、占位符、静态表格、菜单和弹窗诊断；POC 提交前检查 JS 语法、占位符、hash 路由、菜单与视图文件对齐，并禁止 fetch/Vue/HTTP 外链。GENERATE 提交前 `verify_prd` 做结构门禁（catalog 对齐、字段矩阵、Mermaid、AC-、权限表、占位符），不判断科目或流程对错。TEST_CASE / TECH_SPEC 提交前分别用 `verify_test_case` / `verify_tech_spec` 检查 catalog/模块文件、必填字段与 required-modules 覆盖，不设条数硬顶。`write_poc_file` 会拒绝截断或不完整的 JS/JSON，以及超过单轮写入预算（js/html/css 20000 字符、JSON 28000 字符）的 content；`write_prd_file` / `write_test_case_file` / `write_tech_spec_file` 对超预算或未闭合内容同样返回 `truncated:true` 且不落盘。这些检查不证明所有业务按钮、状态流转或浏览器兼容性正确。初次诊断失败后最多允许两次定向修复，第三次仍失败则终止本次运行并由用户手动重跑。DEMO 必须通过 `workspaceFile` 提交；POC 只提交 `workspaceFile` 与 `files` 路径清单，控制面从沙箱组装 `poc_files` 并内联预览 HTML。GENERATE / TEST_CASE / TECH_SPEC 只提交 `workspaceFile=README.md` 与 `files` 清单，控制面分别组装 `prd_files`+`markdown`、`test_case_files`+`items`、`tech_spec_files`+架构/表/API/工单。控制面不补写或改写 Agent HTML。首次成功提交后 Run 立即成功并冻结原始 Result；同一 Run 的重复提交会被拒绝，修正必须创建新 Run。当前 Spike 不做业务复判、精确引用匹配、幂等提交和第二模型修复。

Hermes Key、内部凭据和控制面 Secret 不得出现在工具参数、模型上下文、工具返回和日志中。

诊断不执行 Agent JavaScript，不使用 Node VM/HappyDOM，也不证明运行时交互可用。Node 子进程仅接收最小语言环境，不继承模型凭据、HOME、代理或 NODE_OPTIONS。真实交互必须由 sandbox iframe 中的浏览器验收确认。
