import importlib.util
import json
import os
import pathlib
import unittest
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).with_name("tools.py")
SPEC = importlib.util.spec_from_file_location("prd_workbench_tools", MODULE_PATH)
TOOLS = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(TOOLS)


class SubmitResultTests(unittest.TestCase):
    def setUp(self):
        with TOOLS._DEMO_VALIDATION_LOCK:
            TOOLS._DEMO_VALIDATION_FAILURES.clear()
        with TOOLS._POC_VALIDATION_LOCK:
            TOOLS._POC_VALIDATION_FAILURES.clear()
        with TOOLS._PRD_VALIDATION_LOCK:
            TOOLS._PRD_VALIDATION_FAILURES.clear()

    def test_demo_syntax_error_is_rejected_without_mutating_payload(self):
        original_html = "<!DOCTYPE html><html><body><script>const data = { view-leads: 1 };</script></body></html>"
        payload = {"mode": "DEMO", "title": "Demo", "summary": "说明", "html": original_html}

        result = json.loads(
            TOOLS.submit_result(
                {"runId": "run_1", "mode": "DEMO", "payload": payload},
                session_id="prd-run-run_1",
            )
        )

        self.assertTrue(result["error"])
        self.assertEqual(original_html, payload["html"])

    @patch.object(TOOLS, "_verify_demo_html", return_value=[])
    @patch.object(TOOLS, "_request")
    @patch.object(TOOLS, "read_demo_file")
    def test_demo_submission_uses_workspace_file_instead_of_stale_inline_html(
        self, mock_read_demo_file, mock_request, _mock_verify
    ):
        latest_html = "<!DOCTYPE html><html><body><h1>Latest Workspace Demo</h1></body></html>"
        mock_read_demo_file.return_value = json.dumps({"success": True, "content": latest_html})
        mock_request.return_value = json.dumps({"status": "SUCCEEDED"})
        payload = {
            "mode": "DEMO",
            "title": "Demo",
            "summary": "说明",
            "workspaceFile": "index.html",
            "html": "<!DOCTYPE html><html><body><h1>Stale Inline Demo</h1></body></html>",
        }

        result = json.loads(TOOLS.submit_result({"runId": "run_latest", "mode": "DEMO", "payload": payload}))

        self.assertFalse(result.get("error", False))
        post_body = mock_request.call_args.args[2]
        self.assertEqual(latest_html, post_body["payload"]["html"])
        self.assertEqual("index.html", post_body["payload"]["workspaceFile"])

    @patch.object(TOOLS, "_request")
    @patch.object(TOOLS, "read_demo_file")
    def test_demo_submission_rejects_missing_workspace_file_without_inline_fallback(
        self, mock_read_demo_file, mock_request
    ):
        mock_read_demo_file.return_value = json.dumps({"error": True, "status": 404})
        payload = {
            "mode": "DEMO",
            "title": "Demo",
            "summary": "说明",
            "workspaceFile": "missing.html",
            "html": "<!DOCTYPE html><html><body><h1>Stale Inline Demo</h1></body></html>",
        }

        result = json.loads(TOOLS.submit_result({"runId": "run_missing", "mode": "DEMO", "payload": payload}))

        self.assertTrue(result["error"])
        self.assertIn("拒绝使用", result["detail"])
        mock_request.assert_not_called()

    @patch.object(TOOLS, "_verify_demo_html", return_value=["定向错误"])
    def test_demo_diagnostics_stop_after_initial_failure_and_two_repairs(self, _mock_verify):
        attempts = [
            json.loads(TOOLS.verify_demo({"runId": "run_budget", "html": "<!DOCTYPE html><html><body>Demo</body></html>"}))
            for _ in range(3)
        ]

        self.assertEqual([1, 2, 3], [item["failureCount"] for item in attempts])
        self.assertEqual([2, 1, 0], [item["repairsRemaining"] for item in attempts])
        self.assertEqual([False, False, True], [item["terminal"] for item in attempts])
        blocked = json.loads(TOOLS.write_demo_file({"runId": "run_budget", "filePath": "index.html", "content": "x"}))
        self.assertTrue(blocked["terminal"])
        self.assertFalse(blocked["repairAllowed"])

    def test_write_demo_file_rejects_truncated_html_without_closing_tag(self):
        result = json.loads(TOOLS.write_demo_file({
            "runId": "run_trunc",
            "filePath": "index.html",
            "content": "<!DOCTYPE html><html lang='zh-CN'><body><script>var x = {",
        }))
        self.assertTrue(result["error"])
        self.assertTrue(result["truncated"])
        self.assertIn("</html>", result["message"])
        self.assertIn("不要原样重试", result["message"])

    @patch.object(TOOLS, "_request")
    def test_write_demo_file_accepts_complete_html(self, mock_request):
        mock_request.return_value = json.dumps({"success": True, "filePath": "index.html", "size": 80})
        html = "<!DOCTYPE html><html><body><p>ok</p></body></html>"
        result = json.loads(TOOLS.write_demo_file({
            "runId": "run_ok",
            "filePath": "index.html",
            "content": html,
        }))
        self.assertFalse(result.get("error", False))
        mock_request.assert_called_once()

    @patch.object(TOOLS, "_verify_demo_html", return_value=[])
    def test_verify_demo_pass_is_limited_diagnostic_not_behavior_proof(self, _mock_verify):
        result = json.loads(TOOLS.verify_demo({
            "runId": "run_pass",
            "html": "<!DOCTYPE html><html><body>Demo</body></html>",
        }))

        self.assertTrue(result["passed"])
        self.assertFalse(result["behaviorProof"])
        self.assertIn("不构成", result["message"])




    def test_demo_with_declared_actions_but_no_click_wiring_is_rejected(self):
        inert_html = """<!DOCTYPE html><html><body>
        <nav><button data-view="orders">订单管理</button></nav>
        <main><button data-act="create">新增订单</button>
        <table><tbody><tr><td>SO-001</td></tr></tbody></table></main>
        <script>var ORDERS = [{id: 'SO-001'}];</script>
        </body></html>"""
        payload = {"mode": "DEMO", "title": "Demo", "summary": "说明", "html": inert_html}

        result = json.loads(
            TOOLS.submit_result(
                {"runId": "run_1", "mode": "DEMO", "payload": payload},
                session_id="prd-run-run_1",
            )
        )

        self.assertTrue(result.get("error"))
        self.assertIn("交互未绑定", result.get("detail", ""))

    def test_demo_with_undefined_inline_handlers_is_rejected(self):
        inert_html = """<!DOCTYPE html><html><body>
        <input id="keyword" oninput="renderOrders()">
        <table><tbody><tr><td>SO-001</td></tr></tbody></table>
        <script>var ORDERS = [{id: 'SO-001'}];</script>
        </body></html>"""
        payload = {"mode": "DEMO", "title": "Demo", "summary": "说明", "html": inert_html}

        result = json.loads(
            TOOLS.submit_result(
                {"runId": "run_1", "mode": "DEMO", "payload": payload},
                session_id="prd-run-run_1",
            )
        )

        self.assertTrue(result.get("error"))
        self.assertIn("行内事件处理器未定义", result.get("detail", ""))

    def test_demo_generation_placeholders_are_rejected(self):
        inert_html = """<!DOCTYPE html><html><body>
        <main><p>订单列表</p><table><tbody><tr><td>SO-001</td></tr></tbody></table></main>
        <script>var ORDERS = [["__SEED_ORDER_ROWS__"]];</script>
        </body></html>"""
        payload = {"mode": "DEMO", "title": "Demo", "summary": "说明", "html": inert_html}

        result = json.loads(
            TOOLS.submit_result(
                {"runId": "run_1", "mode": "DEMO", "payload": payload},
                session_id="prd-run-run_1",
            )
        )

        self.assertTrue(result.get("error"))
        self.assertIn("生成占位符", result.get("detail", ""))



    def test_non_function_inline_handler_is_rejected(self):
        html = """<!DOCTYPE html><html><body>
        <button data-act="save" onclick="save()">保存订单</button><p>订单业务操作主路径演示</p>
        <script>const save = 42;</script></body></html>"""

        errors = TOOLS._verify_demo_html(html)

        self.assertTrue(any("行内事件处理器未定义" in item for item in errors), errors)

    @patch.dict(os.environ, {"PRD_VERIFY_DEMO_DISABLE_HAPPY_DOM": "1"})
    def test_portable_audit_accepts_body_and_element_click_bindings(self):
        samples = (
            """document.body.addEventListener('click', function (event) {
              if (event.target.dataset.act === 'create') document.body.classList.add('created');
            });""",
            """document.querySelector('[data-act="create"]').addEventListener('click', function () {
              document.body.classList.add('created');
            });""",
        )
        for script in samples:
            html = f"""<!DOCTYPE html><html><body>
            <button data-act="create">执行订单动作</button><p>订单业务操作主路径演示</p>
            <script>{script}</script></body></html>"""

            self.assertEqual([], TOOLS._verify_demo_html(html), script)

    @patch.dict(os.environ, {"PRD_VERIFY_DEMO_DISABLE_HAPPY_DOM": "1"})
    def test_portable_audit_accepts_working_inline_and_navigation_handlers(self):
        inline_html = """<!DOCTYPE html><html><body>
        <button data-act="save" onclick="save()">保存订单</button><p>订单业务操作主路径演示</p>
        <script>function save(){document.body.classList.add('saved');}</script>
        </body></html>"""
        navigation_html = """<!DOCTYPE html><html><body>
        <button class="menu" data-view="home">首页</button>
        <button class="menu" data-view="orders">订单</button>
        <p>订单业务操作主路径演示</p>
        <script>
        document.addEventListener('click', function(event) {
          var menu = event.target.closest('.menu');
          if (menu) menu.classList.add('on');
        });
        </script></body></html>"""

        self.assertEqual([], TOOLS._verify_demo_html(inline_html))
        self.assertEqual([], TOOLS._verify_demo_html(navigation_html))

    @patch.dict(os.environ, {"PRD_VERIFY_DEMO_DISABLE_HAPPY_DOM": "1"})
    def test_portable_audit_does_not_treat_action_buttons_as_menus_or_mask(self):
        html = """<!DOCTYPE html><html><body>
        <div class="menu" data-view="customers">客户</div>
        <button class="btn" data-act="toggleRemind">待办</button>
        <button class="btn" data-act="addCustomer">新增</button>
        <div id="remindPanel" class="hidden">待办面板</div>
        <div id="mask" class="mask hidden"></div>
        <div id="toast">提示</div>
        <p>订单业务操作主路径演示</p>
        <script>
        function toast(msg){
          var t=document.getElementById('toast');
          t.textContent=msg;
          t.classList.add('show');
        }
        function handleAct(el){
          var act=el.getAttribute('data-act');
          if(act==='toggleRemind'){
            var p=document.getElementById('remindPanel');
            if(p.classList.contains('hidden')) p.classList.remove('hidden');
            else p.classList.add('hidden');
          } else if(act==='addCustomer'){
            toast('打开新增');
          }
        }
        document.addEventListener('click', function(e){
          var t=e.target;
          if(t && t.closest){
            var menu=t.closest('.menu');
            if(menu){ menu.classList.add('on'); return; }
            var actEl=t.closest('[data-act]');
            if(actEl) handleAct(actEl);
          }
        });
        document.getElementById('mask').addEventListener('click', function(){
          this.classList.add('hidden');
        });
        </script></body></html>"""

        self.assertEqual([], TOOLS._verify_demo_html(html))

    @patch.dict(os.environ, {"PRD_VERIFY_DEMO_DISABLE_HAPPY_DOM": "1"})
    def test_portable_audit_allows_gated_submit_without_prior_wizard_state(self):
        html = """<!DOCTYPE html><html><body>
        <button data-act="openClaim">申请</button>
        <button data-act="submitClaim">提交审批</button>
        <p>订单业务操作主路径演示</p>
        <script>
        var pendCust = null;
        document.addEventListener('click', function(event){
          var act = event.target && event.target.getAttribute('data-act');
          if(act==='openClaim'){ pendCust = 0; document.body.classList.add('claim-open'); }
          if(act==='submitClaim'){ if(pendCust===null) return; document.body.classList.add('claimed'); }
        });
        </script></body></html>"""

        self.assertEqual([], TOOLS._verify_demo_html(html))



    @patch.dict(os.environ, {"PRD_VERIFY_DEMO_DISABLE_HAPPY_DOM": "1"})
    def test_portable_audit_accepts_common_registration_and_script_patterns(self):
        samples = (
            """var button=document.querySelector('[data-act="create"]');
            button.onclick=function(){document.body.className='created';};""",
            """document.addEventListener('DOMContentLoaded',function(){
              document.addEventListener('click',function(event){
                if(event.target.matches('[data-act="create"]')) document.body.classList.add('created');
              });
            });""",
            """document.querySelector('[data-act="create"]').addEventListener('click',function(){
              document.body.classList.add('created');
            });""",
        )
        for script in samples:
            html = f"""<!DOCTYPE html><html><body>
            <button data-act="create">执行订单动作</button><p>订单业务操作主路径演示</p>
            <script type="application/json">{{"data-act":"ignored"}}</script>
            <script>{script}</script></body></html>"""

            self.assertEqual([], TOOLS._verify_demo_html(html), script)

    @patch.object(TOOLS, "verify_prd")
    @patch.object(TOOLS, "_request")
    def test_generate_submit_requires_verify_and_drops_chunk_metadata(self, mock_request, mock_verify):
        mock_verify.return_value = json.dumps({"passed": True, "files": ["README.md", "00-overview.md"]})
        mock_request.return_value = json.dumps({"status": "SUCCEEDED"})
        res = TOOLS.submit_result({
            "runId": "run_1",
            "mode": "GENERATE",
            "payload": {"title": "Test PRD", "markdown": "# Section 1"},
            "partIndex": 1,
            "totalParts": 2,
            "isFinal": False,
        })
        mock_request.assert_called_once()
        post_body = mock_request.call_args.args[2]
        self.assertEqual(post_body["mode"], "GENERATE")
        self.assertNotIn("partIndex", post_body)
        self.assertEqual(post_body["payload"]["workspaceFile"], "README.md")
        self.assertEqual(post_body["payload"]["files"], ["README.md", "00-overview.md"])
        self.assertFalse(json.loads(res).get("error", False))


def _load_poc_fixture():
    root = pathlib.Path(__file__).resolve().parents[2] / "tests" / "fixtures" / "poc-crm-spa"
    files = {}
    for path in root.rglob("*"):
        if path.is_file() and path.name != "README.md":
            files[path.relative_to(root).as_posix()] = path.read_text(encoding="utf-8")
    return files


class PocVerifyTests(unittest.TestCase):
    def setUp(self):
        with TOOLS._POC_VALIDATION_LOCK:
            TOOLS._POC_VALIDATION_FAILURES.clear()

    def test_golden_crm_fixture_has_no_configured_errors(self):
        files = _load_poc_fixture()
        self.assertIn("index.html", files)
        self.assertIn("data/catalog.json", files)
        self.assertIn("data/mock.json", files)
        self.assertEqual([], TOOLS._collect_poc_errors(files))
        self.assertEqual([], TOOLS._verify_poc_static(files))

    def test_missing_catalog_and_generic_columns_are_rejected(self):
        files = _load_poc_fixture()
        del files["data/catalog.json"]
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("catalog.json" in item for item in errors))

        files = _load_poc_fixture()
        files["data/catalog.json"] = json.dumps({
            "modules": [{
                "key": "customers",
                "title": "客户档案",
                "core": True,
                "columns": ["名称", "编号", "负责人"],
                "fields": ["id", "name", "owner"],
                "states": ["跟进中"],
                "actions": ["create"],
            }]
        }, ensure_ascii=False)
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("万能字段" in item for item in errors))

    def test_catalog_requires_chinese_labels(self):
        files = _load_poc_fixture()
        catalog = json.loads(files["data/catalog.json"])
        for item in catalog["modules"]:
            item["labels"] = {field: field for field in item.get("fields") or []}
        files["data/catalog.json"] = json.dumps(catalog, ensure_ascii=False)
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("中文" in item or "英文字段名" in item for item in errors))

    def test_ui_factory_rejects_english_field_labels_and_missing_pager(self):
        files = _load_poc_fixture()
        files["js/ui.js"] = (
            "window.PocUI = { ledger: function (el, spec) {\n"
            "  var fields = spec.fields || [];\n"
            "  el.innerHTML = fields.map(function (f) { return esc(f) + '：'; }).join('');\n"
            "} };\n"
        )
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("分页" in item for item in errors))
        self.assertTrue(any("label()" in item or "英文字段名" in item for item in errors))

    def test_write_poc_file_rejects_required_modules_overwrite(self):
        parsed = json.loads(TOOLS.write_poc_file({
            "runId": "run_poc_req",
            "filePath": "data/required-modules.json",
            "content": "{\"modules\":[]}",
        }))
        self.assertTrue(parsed.get("error"))
        self.assertIn("禁止覆盖", parsed.get("message", ""))

    def test_required_modules_must_appear_in_catalog(self):
        files = _load_poc_fixture()
        files["data/required-modules.json"] = json.dumps({
            "coverage": "all",
            "modules": [
                {"key": "customers", "title": "客户档案", "core": True},
                {"key": "gl", "title": "总账", "core": True},
            ],
        }, ensure_ascii=False)
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("缺少当前稿模块" in item and "gl" in item for item in errors))

    def test_quick_required_modules_reject_invented_keys(self):
        files = _load_poc_fixture()
        files["data/required-modules.json"] = json.dumps({
            "coverage": "subset",
            "modules": [
                {"key": "base-setup", "title": "基础设置", "core": True},
            ],
        }, ensure_ascii=False)
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("不能另起一套菜单" in item for item in errors))

    def test_catalog_menu_mismatch_is_rejected(self):
        files = _load_poc_fixture()
        files["js/router.js"] = files["js/router.js"].replace("customers", "accounts")
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("catalog" in item or "POC_MENUS" in item for item in errors))

    def test_fetch_and_unregistered_view_are_rejected(self):
        files = _load_poc_fixture()
        files["js/app.js"] = files["js/app.js"] + "\nfetch('/api/x')\n"
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("fetch" in item for item in errors))
        files = _load_poc_fixture()
        files["js/views/ghost.js"] = "window.Views.ghost = { render: function () {} };"
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("ghost" in item for item in errors))


    def test_lazy_append_mount_is_rejected(self):
        files = _load_poc_fixture()
        files["js/ui.js"] = (
            "window.PocUI = { buildLedger: function (cfg) {\n"
            "  var rootEl = null;\n"
            "  return { render: function (root) {\n"
            "    if (!rootEl) { rootEl = document.createElement('div'); root.appendChild(rootEl); }\n"
            "    rootEl.innerHTML = '<table><tr><td>' + cfg.key + '</td></tr></table>';\n"
            "  }};\n"
            "}};\n"
        )
        files["js/views/customers.js"] = (
            "window.Views = window.Views || {};\n"
            "window.Views.customers = { render: function (el) { PocUI.buildLedger({key:'customers'}).render(el); } };\n"
        )
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("惰性挂载" in item or "appendChild" in item for item in errors))

        files = _load_poc_fixture()
        files["js/app.js"] = files["js/app.js"].replace("view.innerHTML = '';\n    ", "")
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("清空" in item or "innerHTML" in item for item in errors))


    def test_text_url_allowed_and_external_resource_banned(self):
        files = _load_poc_fixture()
        files["index.html"] = files["index.html"].replace("示例 CRM 演示", "示例官网 https://hengan.example.com 演示")
        errors = TOOLS._collect_poc_errors(files)
        self.assertEqual([], errors)

        files = _load_poc_fixture()
        files["index.html"] = files["index.html"].replace("</head>", '<script src="https://cdn.example.com/lib.js"></script></head>')
        errors = TOOLS._collect_poc_errors(files)
        self.assertTrue(any("外部资源引用" in item or "fetch" in item for item in errors))

    def test_poc_diagnostics_stop_after_two_repairs(self):
        with patch.object(TOOLS, "list_poc_files", return_value=json.dumps({"success": True, "files": []})), \
             patch.object(TOOLS, "read_poc_file", return_value=json.dumps({"error": True})):
            attempts = [json.loads(TOOLS.verify_poc({"runId": "run_poc_budget"})) for _ in range(3)]
        self.assertEqual([1, 2, 3], [item["failureCount"] for item in attempts])
        self.assertEqual([2, 1, 0], [item["repairsRemaining"] for item in attempts])
        self.assertEqual([False, False, True], [item["terminal"] for item in attempts])
        blocked = json.loads(TOOLS.write_poc_file({"runId": "run_poc_budget", "filePath": "index.html", "content": "x"}))
        self.assertTrue(blocked["terminal"])

    def test_write_poc_file_rejects_engineering_skeleton(self):
        blocked = json.loads(TOOLS.write_poc_file({
            "runId": "run_poc_pkg",
            "filePath": "package.json",
            "content": "{}",
        }))
        self.assertTrue(blocked["error"])
        self.assertIn("禁止工程散件", blocked["message"])

    def test_write_poc_file_rejects_truncated_javascript(self):
        blocked = json.loads(TOOLS.write_poc_file({
            "runId": "run_poc_trunc_js",
            "filePath": "js/views/vouchers.js",
            "content": "window.Views = window.Views || {};\nwindow.Views.vouchers = { render: function (el) {",
        }))
        self.assertTrue(blocked["error"])
        self.assertTrue(blocked["truncated"])
        self.assertIn("不要原样重试", blocked["message"])
        self.assertIn("js/ui.js", blocked["message"])

    def test_js_unclosed_accepts_quoted_regex_and_skill_factory(self):
        self.assertFalse(TOOLS._js_unclosed('x.replace(/"/g, "q");\nfunction f() { return 1; }\n'))
        self.assertFalse(TOOLS._js_unclosed("(a+b)/c;\nfunction f() { return 1; }\n"))
        self.assertTrue(TOOLS._js_unclosed("window.PocUI = (function () { function ledger(el, spec) {"))
        skill = pathlib.Path(__file__).resolve().parents[2] / "apps" / "backend" / "src" / "main" / "resources" / "poc-skills" / "static-admin-poc.md"
        text = skill.read_text(encoding="utf-8")
        start = text.find("```js\nwindow.PocUI")
        end = text.find("\n```", start)
        factory = text[start + 6:end]
        self.assertFalse(TOOLS._js_unclosed(factory))

    @patch.object(TOOLS, "_request")
    def test_write_poc_file_accepts_skill_ui_factory(self, mock_request):
        mock_request.return_value = json.dumps({"success": True, "filePath": "js/ui.js", "size": 100})
        skill = pathlib.Path(__file__).resolve().parents[2] / "apps" / "backend" / "src" / "main" / "resources" / "poc-skills" / "static-admin-poc.md"
        text = skill.read_text(encoding="utf-8")
        start = text.find("```js\nwindow.PocUI")
        end = text.find("\n```", start)
        factory = text[start + 6:end]
        ok = json.loads(TOOLS.write_poc_file({
            "runId": "run_poc_ui_factory",
            "filePath": "js/ui.js",
            "content": factory,
        }))
        self.assertFalse(ok.get("error", False))
        mock_request.assert_called_once()

    def test_write_poc_file_rejects_oversized_javascript_without_writing(self):
        huge = "window.Views.x = { render: function (el) { el.innerHTML = '" + ("账" * 21000) + "'; } };"
        with patch.object(TOOLS, "_request") as mock_request:
            blocked = json.loads(TOOLS.write_poc_file({
                "runId": "run_poc_huge",
                "filePath": "js/views/ledger.js",
                "content": huge,
            }))
        mock_request.assert_not_called()
        self.assertTrue(blocked["error"])
        self.assertTrue(blocked["truncated"])
        self.assertIn("20000", blocked["message"])
        self.assertIn("不要原样重试", blocked["message"])
        self.assertIn("catalog/菜单", blocked["message"])

    def test_write_poc_file_rejects_truncated_json(self):
        blocked = json.loads(TOOLS.write_poc_file({
            "runId": "run_poc_trunc_json",
            "filePath": "data/mock.json",
            "content": '{"vouchers":[{"id":"V-1"',
        }))
        self.assertTrue(blocked["error"])
        self.assertTrue(blocked["truncated"])
        self.assertIn("JSON", blocked["message"])

    @patch.object(TOOLS, "_request")
    def test_write_poc_file_accepts_complete_small_javascript(self, mock_request):
        mock_request.return_value = json.dumps({"success": True, "filePath": "js/views/vouchers.js", "size": 80})
        ok = json.loads(TOOLS.write_poc_file({
            "runId": "run_poc_small",
            "filePath": "js/views/vouchers.js",
            "content": "window.Views = window.Views || {}; window.Views.vouchers = { render: function (el) { el.innerHTML = 'ok'; } };",
        }))
        self.assertFalse(ok.get("error", False))
        mock_request.assert_called_once()

    @patch.object(TOOLS, "verify_poc")
    @patch.object(TOOLS, "_request")
    def test_submit_result_poc_requires_passing_verify(self, mock_request, mock_verify):
        mock_verify.return_value = json.dumps({"passed": False, "summary": "缺文件", "errors": ["缺少必要文件 index.html"]})
        result = json.loads(TOOLS.submit_result({
            "runId": "run_poc_submit",
            "mode": "POC",
            "payload": {"mode": "POC", "title": "t", "summary": "s", "workspaceFile": "index.html", "files": []},
        }))
        self.assertTrue(result["error"])
        mock_request.assert_not_called()
        mock_verify.return_value = json.dumps({"passed": True, "files": ["index.html", "css/app.css"]})
        mock_request.return_value = json.dumps({"status": "SUCCEEDED"})
        ok = json.loads(TOOLS.submit_result({
            "runId": "run_poc_submit_ok",
            "mode": "POC",
            "payload": {"mode": "POC", "title": "t", "summary": "s", "workspaceFile": "index.html"},
        }))
        self.assertFalse(ok.get("error", False))
        self.assertEqual(["index.html", "css/app.css"], mock_request.call_args.args[2]["payload"]["files"])


class PrdVerifyTests(unittest.TestCase):
    def setUp(self):
        with TOOLS._PRD_VALIDATION_LOCK:
            TOOLS._PRD_VALIDATION_FAILURES.clear()

    def _pack(self):
        voucher = """# 凭证管理
## 页面
- 凭证列表
## 字段
| 字段名 | 控件类型 | 必填 | 校验 | 默认值/联动 |
| --- | --- | --- | --- | --- |
| 凭证号 | 输入框 | 是 | 唯一 | 系统生成 |
```mermaid
graph TD
    A[草稿] --> B[已审核]
```
- BR-voucher-1 借贷必须平衡
- AC-voucher-1 保存草稿后列表出现该凭证
- AC-voucher-2 审核后不可改分录
- AC-voucher-3 反审核需主管权限
"""
        return {
            "README.md": "# 顺序\n1. `00-overview.md`\n2. `modules/voucher.md`",
            "00-overview.md": "# 概述\n目标、范围、非目标、假设、术语。",
            "01-ia.md": "# 信息架构\nvoucher 凭证管理",
            "02-global.md": "# 全局\n空态、加载、失败、通用 CRUD。",
            "03-permissions.md": "| 角色 | 动作 | 数据域 |\n| --- | --- | --- |\n| 会计 | 审核 | 本公司账套 |",
            "04-nfr.md": "# NFR\n并发提交幂等，审计留痕。",
            "open-questions.md": "# 待确认\n汇率来源？",
            "data/catalog.json": json.dumps({
                "modules": [{"key": "voucher", "title": "凭证管理", "core": True, "pages": ["列表"], "entities": ["凭证"], "states": ["草稿"]}]
            }, ensure_ascii=False),
            "modules/voucher.md": voucher,
        }

    def test_complete_pack_has_no_configured_errors(self):
        self.assertEqual([], TOOLS._collect_prd_errors(self._pack()))

    def test_finance_fixture_has_no_configured_errors(self):
        root = pathlib.Path(__file__).resolve().parents[2] / "tests" / "fixtures" / "prd-pack-finance"
        files = {}
        for path in root.rglob("*"):
            if path.is_file() and path.name != "NOTES.md":
                files[path.relative_to(root).as_posix()] = path.read_text(encoding="utf-8")
        self.assertIn("README.md", files)
        self.assertIn("data/catalog.json", files)
        self.assertIn("modules/voucher.md", files)
        self.assertEqual([], TOOLS._collect_prd_errors(files))

    def test_missing_module_and_field_matrix_fail(self):
        files = self._pack()
        del files["modules/voucher.md"]
        errors = TOOLS._collect_prd_errors(files)
        self.assertTrue(any("modules/voucher.md" in item for item in errors))

        files = self._pack()
        files["modules/voucher.md"] = "# 凭证\n占位"
        errors = TOOLS._collect_prd_errors(files)
        self.assertTrue(any("字段矩阵" in item for item in errors))
        self.assertTrue(any("AC-" in item for item in errors))

    def test_ia_and_permissions_and_placeholder(self):
        files = self._pack()
        files["01-ia.md"] = "# IA"
        files["03-permissions.md"] = "无表格"
        files["00-overview.md"] = "__TODO_FILL__"
        errors = TOOLS._collect_prd_errors(files)
        self.assertTrue(any("01-ia.md" in item for item in errors))
        self.assertTrue(any("03-permissions.md" in item for item in errors))
        self.assertTrue(any("占位符" in item for item in errors))

    def test_write_prd_file_rejects_oversized_markdown_without_writing(self):
        with patch.object(TOOLS, "_request") as mock_request:
            blocked = json.loads(TOOLS.write_prd_file({
                "runId": "run_prd",
                "filePath": "00-overview.md",
                "content": "甲" * 18001,
            }))
            self.assertTrue(blocked.get("truncated"))
            mock_request.assert_not_called()

    def test_write_prd_file_rejects_unclosed_mermaid(self):
        blocked = json.loads(TOOLS.write_prd_file({
            "runId": "run_prd",
            "filePath": "modules/voucher.md",
            "content": "# 凭证\n```mermaid\ngraph TD\nA-->B\n",
        }))
        self.assertTrue(blocked.get("truncated"))
        self.assertIn("mermaid", blocked["message"])

    @patch.object(TOOLS, "verify_prd")
    @patch.object(TOOLS, "_request")
    def test_submit_result_prd_requires_passing_verify(self, mock_request, mock_verify):
        mock_verify.return_value = json.dumps({"passed": False, "summary": "缺文件", "errors": ["缺少必要文件 README.md"]})
        result = json.loads(TOOLS.submit_result({
            "runId": "run_prd_submit",
            "mode": "GENERATE",
            "payload": {"mode": "GENERATE", "title": "t", "summary": "s", "workspaceFile": "README.md"},
        }))
        self.assertTrue(result["error"])
        mock_request.assert_not_called()


class TestCasePackToolsTests(unittest.TestCase):
    def test_write_test_case_file_rejects_required_modules_overwrite(self):
        blocked = json.loads(TOOLS.write_test_case_file({
            "runId": "run_tc",
            "filePath": "data/required-modules.json",
            "content": "{}",
        }))
        self.assertTrue(blocked.get("error"))
        self.assertIn("禁止覆盖", blocked.get("message", ""))

    def test_write_test_case_file_rejects_oversized_json(self):
        with patch.object(TOOLS, "_request") as mock_request:
            blocked = json.loads(TOOLS.write_test_case_file({
                "runId": "run_tc",
                "filePath": "modules/gl.json",
                "content": "[" + ("{" + '"title":"x","expectedResult":"y"},') * 2000 + "]",
            }))
            self.assertTrue(blocked.get("truncated"))
            mock_request.assert_not_called()

    def test_write_test_case_file_rejects_invalid_json(self):
        blocked = json.loads(TOOLS.write_test_case_file({
            "runId": "run_tc",
            "filePath": "modules/gl.json",
            "content": '[{"title":"x"',
        }))
        self.assertTrue(blocked.get("truncated"))

    @patch.object(TOOLS, "verify_test_case")
    @patch.object(TOOLS, "_request")
    def test_submit_result_test_case_requires_passing_verify(self, mock_request, mock_verify):
        mock_verify.return_value = json.dumps({"passed": False, "summary": "缺模块", "errors": ["缺少 modules/gl.json"]})
        result = json.loads(TOOLS.submit_result({
            "runId": "run_tc_submit",
            "mode": "TEST_CASE",
            "payload": {"mode": "TEST_CASE", "title": "t", "summary": "s", "workspaceFile": "README.md"},
        }))
        self.assertTrue(result["error"])
        mock_request.assert_not_called()


class TechSpecPackToolsTests(unittest.TestCase):
    def test_write_tech_spec_file_rejects_required_modules_overwrite(self):
        blocked = json.loads(TOOLS.write_tech_spec_file({
            "runId": "run_ts",
            "filePath": "data/required-modules.json",
            "content": "{}",
        }))
        self.assertTrue(blocked.get("error"))

    def test_write_tech_spec_file_rejects_unclosed_mermaid(self):
        blocked = json.loads(TOOLS.write_tech_spec_file({
            "runId": "run_ts",
            "filePath": "architecture.md",
            "content": "# 架构\n```mermaid\ngraph TD\nA-->B\n",
        }))
        self.assertTrue(blocked.get("truncated"))

    def test_collect_tech_spec_errors_requires_delivery_fields(self):
        errors = TOOLS._collect_tech_spec_errors({
            "architecture.md": "短",
        })
        self.assertTrue(any("architecture.md 过短" in e for e in errors))
        self.assertTrue(any("tables.json" in e or "表定义" in e for e in errors))
        self.assertTrue(any("apis.json" in e or "API" in e for e in errors))

    def test_write_tech_spec_file_rejects_tasks_json(self):
        blocked = json.loads(TOOLS.write_tech_spec_file({
            "runId": "run_ts",
            "filePath": "data/tasks.json",
            "content": "[]",
        }))
        self.assertTrue(blocked.get("error"))
        self.assertIn("不再拆解工单", blocked.get("message", ""))

    def test_collect_tech_spec_errors_ignores_leftover_tasks_file(self):
        arch = "## 分层架构与模块依赖\n\n" + ("本系统采用模块化单体，Controller、Service、Mapper 分层清晰。" * 8)
        files = {
            "architecture.md": arch,
            "data/tables.json": json.dumps([{
                "tableName": "fin_voucher",
                "columns": [{"name": "id", "type": "BIGINT", "nullable": False, "comment": "pk"}],
            }]),
            "data/apis.json": json.dumps([{
                "method": "POST",
                "path": "/api/vouchers",
                "summary": "创建",
                "requestBody": "{}",
                "responseBody": "{\"id\":1}",
            }]),
            "data/tasks.json": "[]",
        }
        self.assertEqual(TOOLS._collect_tech_spec_errors(files), [])

    def test_collect_tech_spec_errors_accepts_complete_pack(self):
        arch = "## 分层架构与模块依赖\n\n" + ("本系统采用模块化单体，Controller、Service、Mapper 分层清晰。" * 8)
        files = {
            "architecture.md": arch,
            "data/tables.json": json.dumps([{
                "tableName": "fin_voucher",
                "columns": [{"name": "id", "type": "BIGINT", "nullable": False, "comment": "pk"}],
            }]),
            "data/apis.json": json.dumps([{
                "method": "POST",
                "path": "/api/vouchers",
                "summary": "创建",
                "requestBody": "{}",
                "responseBody": "{\"id\":1}",
                "errorCodes": ["X"],
            }]),
        }
        self.assertEqual(TOOLS._collect_tech_spec_errors(files), [])

    @patch.object(TOOLS, "verify_tech_spec")
    @patch.object(TOOLS, "_request")
    def test_submit_result_tech_spec_requires_passing_verify(self, mock_request, mock_verify):
        mock_verify.return_value = json.dumps({"passed": False, "summary": "缺工单", "errors": ["缺少 data/tasks.json"]})
        result = json.loads(TOOLS.submit_result({
            "runId": "run_ts_submit",
            "mode": "TECH_SPEC",
            "payload": {"mode": "TECH_SPEC", "title": "t", "summary": "s", "workspaceFile": "README.md"},
        }))
        self.assertTrue(result["error"])
        mock_request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
