"""HTTP handlers for workbench tools. Uses the Python standard library only."""

from __future__ import annotations

import json
import os
import re
import threading
import urllib.error
import urllib.parse
import urllib.request


_MAX_DEMO_REPAIRS = 2
_DEMO_VALIDATION_FAILURES: dict[str, int] = {}
_DEMO_VALIDATION_LOCK = threading.Lock()
_MAX_POC_REPAIRS = 2
_POC_VALIDATION_FAILURES: dict[str, int] = {}
_POC_VALIDATION_LOCK = threading.Lock()
# 沙箱磁盘上限仍是 100KB/文件；这里是模型单轮输出预算，避免 max_tokens=24576 截断。
_POC_WRITE_CHAR_LIMIT = 20000
_POC_JSON_WRITE_CHAR_LIMIT = 28000
_MAX_PRD_REPAIRS = 2
_PRD_VALIDATION_FAILURES: dict[str, int] = {}
_PRD_VALIDATION_LOCK = threading.Lock()
_PRD_WRITE_CHAR_LIMIT = 18000
_PRD_JSON_WRITE_CHAR_LIMIT = 28000
_PRD_REQUIRED = (
    "README.md",
    "00-overview.md",
    "01-ia.md",
    "02-global.md",
    "03-permissions.md",
    "04-nfr.md",
    "open-questions.md",
    "data/catalog.json",
)
_PRD_FIELD_HEADER = "| 字段名 | 控件类型 | 必填 | 校验 | 默认值/联动 |"
_PRD_PLACEHOLDER = re.compile(r"__TODO_|__SEED_|__PLACEHOLDER_")
_CJK = re.compile(r"[\u4e00-\u9fff]")
_POC_ENGLISH_LABEL = re.compile(r"esc\(\s*f\s*\)\s*\+\s*['\"]：")


def _demo_validation_terminal(run_id: str) -> bool:
    if not run_id:
        return False
    with _DEMO_VALIDATION_LOCK:
        return _DEMO_VALIDATION_FAILURES.get(run_id, 0) > _MAX_DEMO_REPAIRS


def _demo_failure(run_id: str, errors: list[str]) -> dict:
    with _DEMO_VALIDATION_LOCK:
        failure_count = _DEMO_VALIDATION_FAILURES.get(run_id, 0) + 1
        _DEMO_VALIDATION_FAILURES[run_id] = failure_count
    repairs_remaining = max(0, _MAX_DEMO_REPAIRS - (failure_count - 1))
    terminal = repairs_remaining == 0
    return {
        "passed": False,
        "diagnostic": True,
        "behaviorProof": False,
        "errors": errors,
        "failureCount": failure_count,
        "repairsRemaining": repairs_remaining,
        "repairAllowed": not terminal,
        "terminal": terminal,
    }


def _demo_terminal_response(run_id: str) -> dict:
    with _DEMO_VALIDATION_LOCK:
        failure_count = _DEMO_VALIDATION_FAILURES.get(run_id, _MAX_DEMO_REPAIRS + 1)
    return {
        "error": True,
        "passed": False,
        "diagnostic": True,
        "behaviorProof": False,
        "repairAllowed": False,
        "repairsRemaining": 0,
        "failureCount": failure_count,
        "terminal": True,
        "message": "Demo 初次诊断及最多两次定向修复均未通过。停止继续写入、验证和提交；结束本次运行，由用户手动重跑。",
    }


def _clear_demo_failures(run_id: str) -> None:
    if not run_id:
        return
    with _DEMO_VALIDATION_LOCK:
        _DEMO_VALIDATION_FAILURES.pop(run_id, None)


def _poc_validation_terminal(run_id: str) -> bool:
    if not run_id:
        return False
    with _POC_VALIDATION_LOCK:
        return _POC_VALIDATION_FAILURES.get(run_id, 0) > _MAX_POC_REPAIRS


def _poc_failure(run_id: str, errors: list[str]) -> dict:
    with _POC_VALIDATION_LOCK:
        failure_count = _POC_VALIDATION_FAILURES.get(run_id, 0) + 1
        _POC_VALIDATION_FAILURES[run_id] = failure_count
    repairs_remaining = max(0, _MAX_POC_REPAIRS - (failure_count - 1))
    terminal = repairs_remaining == 0
    return {
        "passed": False,
        "diagnostic": True,
        "behaviorProof": False,
        "errors": errors,
        "failureCount": failure_count,
        "repairsRemaining": repairs_remaining,
        "repairAllowed": not terminal,
        "terminal": terminal,
    }


def _poc_terminal_response(run_id: str) -> dict:
    with _POC_VALIDATION_LOCK:
        failure_count = _POC_VALIDATION_FAILURES.get(run_id, _MAX_POC_REPAIRS + 1)
    return {
        "error": True,
        "passed": False,
        "diagnostic": True,
        "behaviorProof": False,
        "repairAllowed": False,
        "repairsRemaining": 0,
        "failureCount": failure_count,
        "terminal": True,
        "message": "POC 初次诊断及最多两次定向修复均未通过。停止继续写入、验证和提交；由用户手动重跑。",
    }


def _clear_poc_failures(run_id: str) -> None:
    if not run_id:
        return
    with _POC_VALIDATION_LOCK:
        _POC_VALIDATION_FAILURES.pop(run_id, None)


def _base_url() -> str:
    return os.environ.get("PRD_CONTROL_PLANE_URL", "http://127.0.0.1:18100").rstrip("/")


def _plugin_key() -> str:
    return os.environ.get("PRD_PLUGIN_KEY", "")


def _request(method: str, path: str, payload: dict | None = None) -> str:
    url = _base_url() + path
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Accept", "application/json")
    request.add_header("X-PRD-Plugin-Key", _plugin_key())
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read().decode("utf-8")
            return body or json.dumps({"ok": True})
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        return json.dumps({"error": True, "status": exc.code, "detail": _redact(detail)})
    except Exception as exc:  # noqa: BLE001 - tool handlers must not raise
        return json.dumps({"error": True, "message": "control plane unavailable"})


def _redact(text: str) -> str:
    lowered = text.lower()
    if "authorization" in lowered or "api-key" in lowered or "bearer" in lowered:
        return "redacted"
    return text[:500]


def _extract_run_id(args: dict, kwargs: dict, payload: dict | None = None) -> str:
    run_id = str(args.get("runId") or "")
    if not run_id and isinstance(payload, dict):
        run_id = str(payload.get("runId") or "")
    if not run_id:
        sess = str(kwargs.get("session_id") or kwargs.get("task_id") or "")
        if sess.startswith("prd-run-"):
            run_id = sess[len("prd-run-"):].strip()
        elif sess.startswith("run_"):
            run_id = sess.strip()
    return run_id


def list_sources(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    return _request("GET", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/sources")


def read_source(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    source_id = str(args.get("sourceId") or "")
    if not run_id or not source_id:
        return json.dumps({"error": True, "message": "runId and sourceId are required"})
    query = ""
    cursor = args.get("cursor")
    if cursor:
        query = "?" + urllib.parse.urlencode({"cursor": cursor})
    return _request(
        "GET",
        f"/internal/v1/runs/{urllib.parse.quote(run_id)}/sources/{urllib.parse.quote(source_id)}{query}",
    )


def _diagnostic_environment() -> dict[str, str]:
    # Node runs by absolute path. Do not inherit model/plugin keys, HOME, NODE_OPTIONS,
    # preload flags, proxies, or any application configuration.
    return {"LANG": "C.UTF-8"}


def _check_js_syntax(script: str) -> str | None:
    """Parse JavaScript with node --check. Never execute it or silently skip missing tools."""
    if not script or not script.strip():
        return None
    import shutil
    import subprocess
    import tempfile
    node = shutil.which("node")
    if not node:
        return "Node.js 不可用，无法完成语法诊断"
    try:
        with tempfile.TemporaryDirectory(prefix="prd-syntax-") as directory:
            path = os.path.join(directory, "input.js")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(script)
            result = subprocess.run([node, "--check", path], capture_output=True, timeout=5,
                                    env=_diagnostic_environment())
            if result.returncode:
                text = result.stderr.decode("utf-8", errors="replace")
                line = next((line.strip() for line in text.splitlines() if "SyntaxError" in line), "语法诊断进程失败")
                return "Node.js check: " + line[:200]
    except subprocess.TimeoutExpired:
        return "Node.js 语法诊断超时"
    except Exception:
        return "Node.js 语法诊断不可用"
    return None


def _verify_demo_html(html: str) -> list[str]:
    """Static structure and declared-binding diagnostic; not a runtime sandbox."""
    import shutil
    import subprocess
    import tempfile
    if not html or not html.strip():
        return ["Demo HTML 内容为空"]
    node = shutil.which("node")
    verifier = os.path.join(os.path.dirname(__file__), "verify_demo.js")
    if not node or not os.path.isfile(verifier):
        return ["静态诊断依赖不可用（Node.js 或 verify_demo.js）"]
    try:
        with tempfile.TemporaryDirectory(prefix="prd-static-") as directory:
            path = os.path.join(directory, "index.html")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(html)
            result = subprocess.run([node, verifier, path], capture_output=True, timeout=10,
                                    env=_diagnostic_environment())
            data = json.loads(result.stdout.decode("utf-8"))
            if not isinstance(data, dict) or not isinstance(data.get("valid"), bool) or not isinstance(data.get("errors"), list):
                return ["静态诊断响应结构无效"]
            if result.returncode and data["valid"]:
                return ["静态诊断进程异常退出"]
            errors = [str(item)[:500] for item in data["errors"]]
            if not data["valid"] and not errors:
                return ["静态诊断失败但未返回原因"]
            return errors
    except subprocess.TimeoutExpired:
        return ["静态诊断超时"]
    except Exception:
        return ["静态诊断不可用或响应无法解析"]


def write_demo_file(args: dict, **kwargs) -> str:
    """Write a file directly into the current run's isolated sandbox workspace."""
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    content = args.get("content")
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    if _demo_validation_terminal(run_id):
        return json.dumps(_demo_terminal_response(run_id), ensure_ascii=False)
    if not file_path:
        return json.dumps({"error": True, "message": "filePath is required"})
    if content is None:
        return json.dumps({"error": True, "message": "content is required"})

    # Proactively guide the agent: if it tries to write package files into sandbox
    norm_path = file_path.replace("\\", "/").strip().lstrip("/")
    html_text = str(content)
    if norm_path == "index.html" and (
        "<!DOCTYPE" in html_text.upper() or "<html" in html_text.lower()
    ) and re.search(r"</html\s*>", html_text, re.I) is None:
        return json.dumps({
            "error": True,
            "truncated": True,
            "message": "index.html 在 </html> 之前被截断。不要原样重试这一大页。请只保留一条核心主路径，输出完整可闭合且建议不超过 25000 字符的单文件，再调用 write_demo_file。",
        }, ensure_ascii=False)
    if norm_path in ("package.json", "vite.config.ts", "vitest.config.ts", "tsconfig.json", "tsconfig.node.json", "main.ts") or norm_path.startswith("src/"):
        return json.dumps({
            "error": True,
            "message": f"沙箱工作区专属服务于单文件原型 index.html 的运行与自检。package_files 仅需在 submit_result 的 payload.package_files 中作为 JSON 字典一次性提交，严禁使用 write_demo_file 逐个文件落盘！请立即专注于完善 index.html 并调用 verify_demo。"
        }, ensure_ascii=False)

    return _request(
        "POST",
        f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files",
        {"filePath": file_path, "content": str(content)},
    )


def read_demo_file(args: dict, **kwargs) -> str:
    """Read a file from the current run's isolated sandbox workspace."""
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    if not file_path:
        return json.dumps({"error": True, "message": "filePath is required"})

    query = "?" + urllib.parse.urlencode({"filePath": file_path})
    return _request(
        "GET",
        f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files{query}",
    )


def verify_demo(args: dict, **kwargs) -> str:
    """Explicitly verify Demo HTML structure and syntax without executing generated scripts.

    Can take inline 'html' string, or 'filePath'/'filename' to verify a file in workspace.
    Returns structured pass/fail diagnostics to guide autonomous self-repair.
    """
    html = str(args.get("html") or "").strip()
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    run_id = _extract_run_id(args, kwargs)

    if not run_id:
        return json.dumps({"passed": False, "diagnostic": True, "behaviorProof": False, "errors": ["runId is required"]})
    if _demo_validation_terminal(run_id):
        return json.dumps(_demo_terminal_response(run_id), ensure_ascii=False)

    if not html and file_path and run_id:
        read_res_raw = read_demo_file({"runId": run_id, "filePath": file_path}, **kwargs)
        try:
            read_res = json.loads(read_res_raw)
            if read_res.get("success"):
                html = read_res.get("content", "")
            else:
                failure = _demo_failure(run_id, [f"无法读取工作区文件 {file_path}"])
                failure["summary"] = "Demo 有限自动化诊断未通过。"
                return json.dumps(failure, ensure_ascii=False)
        except Exception:
            failure = _demo_failure(run_id, [f"读取工作区文件响应解析异常: {read_res_raw}"])
            failure["summary"] = "Demo 有限自动化诊断未通过。"
            return json.dumps(failure, ensure_ascii=False)

    if not html:
        failure = _demo_failure(run_id, ["缺少待验证的 Demo HTML 内容或有效 filePath"])
        failure["summary"] = "Demo 有限自动化诊断未通过。"
        return json.dumps(failure, ensure_ascii=False)

    import re
    syntax_errors = []
    for m in re.finditer(r'<script\b([^>]*)>([\s\S]*?)</script>', html, re.I):
        attrs = m.group(1).lower()
        if "src=" in attrs:
            continue
        type_match = re.search(r'type\s*=\s*["\']?([^"\'\s>]+)', attrs)
        if type_match:
            script_type = type_match.group(1).lower()
            js_types = {
                "text/javascript",
                "application/javascript",
                "text/ecmascript",
                "application/ecmascript",
                "module",
            }
            if script_type not in js_types:
                continue
        err = _check_js_syntax(m.group(2))
        if err:
            syntax_errors.append(err)

    review_errors = _verify_demo_html(html)
    all_errors = syntax_errors + review_errors

    if all_errors:
        failure = _demo_failure(run_id, all_errors)
        if failure["terminal"]:
            failure["summary"] = (
                f"Demo 有限自动化诊断第 {failure['failureCount']} 次未通过，已用完两次定向修复额度。"
                "停止继续修复和提交，由用户手动重跑。"
            )
        else:
            failure["summary"] = (
                f"Demo 有限自动化诊断未通过，检出 {len(all_errors)} 项问题；"
                f"仅修复这些问题后重新验证，剩余定向修复次数 {failure['repairsRemaining']}。"
            )
        return json.dumps(failure, ensure_ascii=False)

    return json.dumps({
        "passed": True,
        "diagnostic": True,
        "behaviorProof": False,
        "errors": [],
        "message": "Demo 有限自动化诊断通过：未发现当前检查范围内的结构、占位符、JS 语法、已声明动作绑定，未执行脚本；这不构成所有业务按钮、状态流转或浏览器兼容性的完整行为证明。请立即使用 workspaceFile='index.html' 调用 submit_result。"
    }, ensure_ascii=False)


_JS_REGEX_PRECEDERS = set("([{,;=:!?~&|^%<>+-*\n")
_JS_REGEX_KEYWORDS = {
    "return", "throw", "case", "in", "of", "typeof", "void", "delete", "await", "new", "yield",
}


def _js_regex_start(script: str, slash_index: int) -> bool:
    j = slash_index - 1
    while j >= 0 and script[j] in " \t\r\n":
        j -= 1
    if j < 0:
        return True
    prev = script[j]
    if prev in _JS_REGEX_PRECEDERS:
        return True
    k = j
    while k >= 0 and (script[k].isalnum() or script[k] in "_$"):
        k -= 1
    return script[k + 1 : j + 1] in _JS_REGEX_KEYWORDS


def _js_skip_regex(script: str, slash_index: int) -> int:
    i = slash_index + 1
    length = len(script)
    in_class = False
    escape = False
    while i < length:
        ch = script[i]
        if escape:
            escape = False
            i += 1
            continue
        if ch == "\\":
            escape = True
            i += 1
            continue
        if ch == "[" and not in_class:
            in_class = True
        elif ch == "]" and in_class:
            in_class = False
        elif ch == "/" and not in_class:
            i += 1
            while i < length and script[i] in "gimsuy":
                i += 1
            return i
        elif ch == "\n":
            return i
        i += 1
    return i


def _js_unclosed(script: str) -> bool:
    """Cheap truncation detector that does not depend on Node being installed."""
    depth = 0
    in_single = False
    in_double = False
    in_template = False
    in_line_comment = False
    in_block_comment = False
    escape = False
    i = 0
    length = len(script or "")
    while i < length:
        ch = script[i]
        nxt = script[i + 1] if i + 1 < length else ""
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if escape:
            escape = False
            i += 1
            continue
        if (in_single or in_double or in_template) and ch == "\\":
            escape = True
            i += 1
            continue
        if not (in_single or in_double or in_template):
            if ch == "/" and nxt == "/":
                in_line_comment = True
                i += 2
                continue
            if ch == "/" and nxt == "*":
                in_block_comment = True
                i += 2
                continue
            if ch == "/" and _js_regex_start(script, i):
                i = _js_skip_regex(script, i)
                continue
            if ch == "'":
                in_single = True
            elif ch == '"':
                in_double = True
            elif ch == "`":
                in_template = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
            i += 1
            continue
        if in_single and ch == "'":
            in_single = False
        elif in_double and ch == '"':
            in_double = False
        elif in_template and ch == "`":
            in_template = False
        i += 1
    return depth > 0 or in_single or in_double or in_template or in_block_comment


def _poc_truncated_write(message: str) -> str:
    return json.dumps({"error": True, "truncated": True, "message": message}, ensure_ascii=False)


def write_poc_file(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    content = args.get("content")
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    if _poc_validation_terminal(run_id):
        return json.dumps(_poc_terminal_response(run_id), ensure_ascii=False)
    if not file_path:
        return json.dumps({"error": True, "message": "filePath is required"})
    if content is None:
        return json.dumps({"error": True, "message": "content is required"})
    norm_path = file_path.replace("\\", "/").strip().lstrip("/")
    if norm_path == "data/required-modules.json":
        return json.dumps({
            "error": True,
            "message": "data/required-modules.json 由控制面按当前稿 catalog 预置，禁止覆盖。请按其中 modules[].key 写 data/catalog.json。",
        }, ensure_ascii=False)
    if norm_path in ("package.json", "vite.config.ts", "main.ts") or norm_path.startswith("src/"):
        return json.dumps({
            "error": True,
            "message": "POC 沙箱只允许 index.html、css/*.css、js/**/*.js、data/*.json，禁止工程散件。",
        }, ensure_ascii=False)
    text = str(content)
    lower = norm_path.lower()
    if lower.endswith((".js", ".html", ".css")) and len(text) > _POC_WRITE_CHAR_LIMIT:
        return _poc_truncated_write(
            f"{norm_path} 单次 content 有 {len(text)} 字符，超过模型单轮安全上限 {_POC_WRITE_CHAR_LIMIT}。"
            "不要原样重试，也不要删 catalog/菜单来凑体积。"
            "请把共享 CRUD 放进 js/ui.js 的 PocUI.ledger，每个 js/views/<key>.js 只写该模块的 catalog 配置调用。"
        )
    if lower.endswith(".json") and len(text) > _POC_JSON_WRITE_CHAR_LIMIT:
        return _poc_truncated_write(
            f"{norm_path} 有 {len(text)} 字符，单次 JSON 不要超过 {_POC_JSON_WRITE_CHAR_LIMIT}。"
            "不要原样重试，也不要从 catalog 删模块。mock 只保留 catalog.fields 短字段，去掉长描述。"
        )
    if lower.endswith(".html") and (
        "<!doctype" in text.lower() or "<html" in text.lower()
    ) and re.search(r"</html\s*>", text, re.I) is None:
        return _poc_truncated_write(
            f"{norm_path} 在 </html> 之前被截断。不要原样重试。"
            "index.html 只写壳（顶栏、侧栏、#view 和 script src），业务不要内联进 HTML。"
        )
    if lower.endswith(".json"):
        try:
            json.loads(text)
        except Exception as exc:
            return _poc_truncated_write(
                f"{norm_path} JSON 无法解析（{exc}），文件疑似截断。"
                "不要原样重试，写完整且紧凑的 JSON。"
            )
    if lower.endswith(".js"):
        syntax_err = _check_js_syntax(text)
        if syntax_err:
            return _poc_truncated_write(
                f"{norm_path} JS 语法错误，文件疑似截断：{syntax_err}。"
                "不要原样重试这一大段。请改用 js/ui.js 工厂，单文件完整且不超过 "
                f"{_POC_WRITE_CHAR_LIMIT} 字符。"
            )
        if _js_unclosed(text):
            return _poc_truncated_write(
                f"{norm_path} 花括号或字符串未闭合，文件疑似截断。"
                "不要原样重试这一大段。请改用 js/ui.js 工厂，单文件完整且不超过 "
                f"{_POC_WRITE_CHAR_LIMIT} 字符。"
            )
    return _request(
        "POST",
        f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files",
        {"filePath": file_path, "content": text},
    )


def read_poc_file(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    if not file_path:
        return json.dumps({"error": True, "message": "filePath is required"})
    query = "?" + urllib.parse.urlencode({"filePath": file_path})
    return _request("GET", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files{query}")


def list_poc_files(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    return _request("GET", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files")


def _poc_banned(text: str) -> bool:
    if not text:
        return False
    lower = text.lower()
    if "fetch(" in lower or "xmlhttprequest" in lower or "axios" in lower:
        return True
    if "from 'vue'" in lower or 'from "vue"' in lower or "createapp(" in lower:
        return True
    import re
    if re.search(r"(?m)^\s*import\s", text):
        return True
    if re.search(
        r"""(?i)(?:<(?:script|iframe)\b[^>]*src\s*=\s*['"]https?://|<link\b[^>]*href\s*=\s*['"]https?://|@import\s+['"]?https?://|url\s*\(\s*['"]?https?://|['"](?:https?:)?//cdn\.[^'"]+['"])""",
        text,
    ):
        return True
    return False


_POC_GENERIC_COLUMNS = {"名称", "编号", "负责人"}
_POC_ACTION_ALIASES = {
    "create": ('data-act="add"', "data-act='add'", 'id="ledger-add"', "id='ledger-add'"),
    "del": ('data-act="delete"', "data-act='delete'", 'data-act="confirm-del"', "data-act='confirm-del'"),
}
_POC_LAZY_MOUNT = re.compile(r"if\s*\(\s*!\s*\w*El\s*\)[\s\S]{0,400}appendChild", re.I)


def _poc_menu_keys(router: str) -> list[str]:
    return re.findall(r"key\s*:\s*['\"]([^'\"]+)['\"]", router or "")


def _poc_action_haystack(files: dict[str, str], view_src: str) -> str:
    parts = [view_src or ""]
    for name in ("js/ui.js", "js/app.js"):
        extra = files.get(name)
        if extra:
            parts.append(extra)
    return "\n".join(parts)


def _poc_action_present(token: str, haystack: str) -> bool:
    if f'data-act="{token}"' in haystack or f"data-act='{token}'" in haystack:
        return True
    return any(alias in haystack for alias in _POC_ACTION_ALIASES.get(token, ()))


def _reject_lazy_view_mount(files: dict[str, str], errors: list[str]) -> None:
    for path, content in files.items():
        if not str(path).endswith(".js"):
            continue
        if _POC_LAZY_MOUNT.search(content or ""):
            errors.append(
                f"{path} 禁止 if (!rootEl) createElement+appendChild 惰性挂载。"
                "必须对传入的 el 直接 el.innerHTML=...；js/app.js 切菜单前必须 view.innerHTML=''，"
                "否则后面的菜单会叠在首屏下面，看起来像点了没反应。"
            )
    app_js = files.get("js/app.js", "")
    if not re.search(r"innerHTML\s*=\s*['\"]['\"]", app_js):
        errors.append(
            "js/app.js 切换菜单前必须清空 #view（view.innerHTML=''）。"
            "只改 location.hash 或往 #view appendChild 都会让后续菜单无响应。"
        )


def _poc_menu_keys(router: str) -> list[str]:
    return re.findall(r"key\s*:\s*['\"]([^'\"]+)['\"]", router or "")


def _align_poc_required_modules(files: dict[str, str], errors: list[str]) -> None:
    raw = files.get("data/required-modules.json")
    if not raw:
        return
    try:
        required = json.loads(raw)
    except Exception:
        return
    modules = required.get("modules") if isinstance(required, dict) else None
    if not isinstance(modules, list) or not modules:
        return
    required_keys: list[str] = []
    for item in modules:
        if not isinstance(item, dict):
            continue
        key = str(item.get("key") or "").strip()
        if key:
            required_keys.append(key)
    if not required_keys:
        return
    catalog_raw = files.get("data/catalog.json")
    if not catalog_raw:
        return
    try:
        catalog = json.loads(catalog_raw)
    except Exception:
        return
    catalog_modules = catalog.get("modules") if isinstance(catalog, dict) else None
    catalog_keys = []
    if isinstance(catalog_modules, list):
        for item in catalog_modules:
            if isinstance(item, dict) and str(item.get("key") or "").strip():
                catalog_keys.append(str(item.get("key")).strip())
    coverage = str(required.get("coverage") or "all").strip().lower()
    missing = [key for key in required_keys if key not in catalog_keys]
    if coverage != "subset" and missing:
        errors.append(
            "catalog 缺少当前稿模块: "
            + "、".join(missing)
            + "。必须按 data/required-modules.json 的 key 覆盖，禁止另起菜单。"
        )
    if coverage == "subset" and not any(key in required_keys for key in catalog_keys):
        errors.append("快速 POC 也必须从 data/required-modules.json 里挑选模块 key，不能另起一套菜单")


def _align_poc_catalog(files: dict[str, str], errors: list[str]) -> None:
    raw = files.get("data/catalog.json")
    if raw is None:
        return
    try:
        catalog = json.loads(raw)
    except Exception as exc:
        errors.append(f"data/catalog.json 无法解析：{exc}")
        return
    modules = catalog.get("modules") if isinstance(catalog, dict) else None
    if not isinstance(modules, list) or not modules:
        errors.append("data/catalog.json 必须包含非空 modules 数组")
        return
    parsed: list[dict] = []
    for index, item in enumerate(modules):
        if not isinstance(item, dict) or not str(item.get("key") or "").strip():
            errors.append(f"data/catalog.json modules[{index}] 缺少 key")
            continue
        parsed.append(item)
    if not parsed:
        return
    if not any(bool(item.get("core")) for item in parsed):
        errors.append("catalog 至少需要 1 个 core=true 模块")
    catalog_keys = [str(item.get("key")).strip() for item in parsed]
    if len(catalog_keys) != len(set(catalog_keys)):
        errors.append("catalog 模块 key 重复")
    view_keys = []
    for path in files:
        if path.startswith("js/views/") and path.endswith(".js"):
            view_keys.append(path[len("js/views/"):-3])
    router_keys = _poc_menu_keys(files.get("js/router.js", ""))
    html = files.get("index.html", "")
    mock = None
    mock_raw = files.get("data/mock.json")
    if mock_raw is not None:
        try:
            loaded = json.loads(mock_raw)
            mock = loaded if isinstance(loaded, dict) else {}
            if not isinstance(loaded, dict):
                errors.append("data/mock.json 必须是对象，key 为模块")
        except Exception as exc:
            errors.append(f"data/mock.json 无法解析：{exc}")
            mock = {}
    for item in parsed:
        key = str(item.get("key")).strip()
        if not str(item.get("title") or "").strip():
            errors.append(f"catalog 模块 {key} 缺少 title")
        if key not in view_keys:
            errors.append(f"缺少视图文件 js/views/{key}.js，与 catalog 不对齐")
        if key not in router_keys:
            errors.append(f"js/router.js 的 POC_MENUS 未声明 catalog 模块 {key}")
        if f"js/views/{key}.js" not in html:
            errors.append(f"index.html 未引入 js/views/{key}.js")
        columns = [str(col).strip() for col in (item.get("columns") or []) if str(col).strip()]
        if not columns:
            errors.append(f"catalog 模块 {key} 缺少 columns")
        elif set(columns) <= _POC_GENERIC_COLUMNS:
            errors.append(f"{key} 列名只有万能字段（名称/编号/负责人），必须使用材料中的业务字段")
        view_src = files.get(f"js/views/{key}.js", "")
        for col in columns:
            if col not in view_src:
                errors.append(f"js/views/{key}.js 未出现 catalog 列「{col}」")
        for act in item.get("actions") or []:
            token = str(act).strip()
            if not token:
                continue
            haystack = _poc_action_haystack(files, view_src)
            if not _poc_action_present(token, haystack):
                errors.append(f"js/views/{key}.js 未实现 catalog 动作 data-act={token}")
        if isinstance(mock, dict):
            rows = mock.get(key)
            if not isinstance(rows, list):
                errors.append(f"data/mock.json 缺少模块 {key} 的数组")
            else:
                minimum = 4 if item.get("core") else 3
                if len(rows) < minimum:
                    errors.append(f"{key} mock 至少需要 {minimum} 行，当前 {len(rows)} 行")
                fields = [str(field).strip() for field in (item.get("fields") or []) if str(field).strip()]
                if not fields:
                    errors.append(f"catalog 模块 {key} 缺少 fields")
                elif rows and isinstance(rows[0], dict):
                    missing = [field for field in fields if field not in rows[0]]
                    if missing:
                        errors.append(f"{key} mock 行缺少 catalog 字段：" + ",".join(missing))
        labels = item.get("labels") if isinstance(item.get("labels"), dict) else {}
        fields = [str(field).strip() for field in (item.get("fields") or []) if str(field).strip()]
        if fields:
            if not labels:
                errors.append(f"catalog 模块 {key} 缺少中文 labels，详情禁止直接显示英文字段名")
            else:
                for field in fields:
                    lab = str(labels.get(field) or "").strip()
                    if not lab or not _CJK.search(lab):
                        errors.append(f"{key} 字段 {field} 的 labels 必须是中文")
                    elif lab == field or re.fullmatch(r"[A-Za-z0-9_]+", lab):
                        errors.append(f"{key} 不能把英文字段名 {field} 当作界面文案")
    for key in view_keys:
        if key not in catalog_keys:
            errors.append(f"js/views/{key}.js 不在 catalog 中，禁止侧栏外的幽灵视图")
    for key in router_keys:
        if key not in catalog_keys:
            errors.append(f"POC_MENUS 声明了 catalog 中没有的 {key}")


def _collect_poc_errors(files: dict[str, str]) -> list[str]:
    errors: list[str] = []
    required = [
        "index.html",
        "css/app.css",
        "js/store.js",
        "js/router.js",
        "js/app.js",
        "data/catalog.json",
        "data/mock.json",
    ]
    for name in required:
        if name not in files:
            errors.append(f"缺少必要文件 {name}")
    html = files.get("index.html", "")
    lower = html.lower()
    if "<!doctype html>" not in lower:
        errors.append("index.html 缺少 <!DOCTYPE html>")
    if "<body" not in lower or "</body>" not in lower:
        errors.append("index.html 缺少闭合 body")
    if "css/app.css" not in html:
        errors.append("index.html 必须引用 css/app.css")
    if "#/" not in html and "hashchange" not in files.get("js/app.js", "") and "hashchange" not in files.get("js/router.js", ""):
        errors.append("POC 必须使用 hash 路由（hashchange 或 #/）")
    for path, content in files.items():
        if "__SEED_" in content or "__TODO_" in content or "__PLACEHOLDER_" in content:
            errors.append(f"{path} 含有未替换占位符")
        if path.endswith((".js", ".html", ".css")) and _poc_banned(content):
            errors.append(f"{path} 含有 fetch/外部资源引用/Vue/import，POC 只允许本地自包含实现")
        if path.endswith(".js"):
            err = _check_js_syntax(content)
            if err:
                errors.append(f"{path} 语法错误：{err}")
    router = files.get("js/router.js", "")
    view_keys = []
    for path in files:
        if path.startswith("js/views/") and path.endswith(".js"):
            key = path[len("js/views/"):-3]
            view_keys.append(key)
            pattern = rf"\bViews\s*(?:\.\s*{re.escape(key)}|\[\s*['\"]{re.escape(key)}['\"]\s*\])"
            if not re.search(pattern, files[path]):
                errors.append(f"{path} 必须注册 window.Views.{key}")
            if key not in router:
                errors.append(f"js/router.js 未声明视图 {key}")
    if not view_keys:
        errors.append("至少需要一个 js/views/<key>.js")
    _align_poc_catalog(files, errors)
    _align_poc_required_modules(files, errors)
    _reject_lazy_view_mount(files, errors)
    ui = files.get("js/ui.js") or ""
    if ui:
        if "上一页" not in ui or "共 " not in ui:
            errors.append("js/ui.js 列表必须带分页（共 N 条 / 上一页）")
        if "function label" not in ui:
            errors.append("js/ui.js 必须用中文 label() 渲染详情和表单")
        if _POC_ENGLISH_LABEL.search(ui):
            errors.append("js/ui.js 详情/表单不得直接显示英文字段名，必须使用 labels 中文")
    return errors


def _verify_poc_static(files: dict[str, str]) -> list[str]:
    errors = []
    for source in re.findall(r'<script\b[^>]*\bsrc=[\'"]([^\'"]+)[\'"]', files.get("index.html", ""), re.I):
        if source.removeprefix("./") not in files:
            errors.append("index.html 引用未写入的脚本文件")
    return errors


def verify_poc(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    if not run_id:
        return json.dumps({"passed": False, "errors": ["runId is required"]})
    if _poc_validation_terminal(run_id):
        return json.dumps(_poc_terminal_response(run_id), ensure_ascii=False)
    listed_raw = list_poc_files({"runId": run_id}, **kwargs)
    try:
        listed = json.loads(listed_raw)
    except Exception:
        failure = _poc_failure(run_id, [f"无法列出工作区文件: {listed_raw}"])
        return json.dumps(failure, ensure_ascii=False)
    names = listed.get("files") if isinstance(listed, dict) else None
    if not isinstance(names, list):
        failure = _poc_failure(run_id, ["工作区文件列表无效"])
        return json.dumps(failure, ensure_ascii=False)
    files: dict[str, str] = {}
    for name in names:
        raw = read_poc_file({"runId": run_id, "filePath": str(name)}, **kwargs)
        try:
            parsed = json.loads(raw)
        except Exception:
            failure = _poc_failure(run_id, [f"无法读取 {name}"])
            return json.dumps(failure, ensure_ascii=False)
        if not parsed.get("success"):
            failure = _poc_failure(run_id, [f"无法读取 {name}"])
            return json.dumps(failure, ensure_ascii=False)
        files[str(name)] = str(parsed.get("content") or "")
    errors = _collect_poc_errors(files)
    if not errors:
        errors.extend(_verify_poc_static(files))
    if errors:
        failure = _poc_failure(run_id, errors)
        if failure["terminal"]:
            failure["summary"] = "POC 诊断已用完两次定向修复额度。"
        else:
            failure["summary"] = f"POC 诊断未通过，检出 {len(errors)} 项；剩余修复次数 {failure['repairsRemaining']}。"
        return json.dumps(failure, ensure_ascii=False)
    return json.dumps({
        "passed": True,
        "diagnostic": True,
        "behaviorProof": False,
        "errors": [],
        "files": sorted(files.keys()),
        "message": "POC 诊断未发现已配置检查项的问题。请立即 submit_result，只带 files 清单和 workspaceFile=index.html。",
    }, ensure_ascii=False)


def _prd_validation_terminal(run_id: str) -> bool:
    if not run_id:
        return False
    with _PRD_VALIDATION_LOCK:
        return _PRD_VALIDATION_FAILURES.get(run_id, 0) > _MAX_PRD_REPAIRS


def _prd_failure(run_id: str, errors: list[str]) -> dict:
    with _PRD_VALIDATION_LOCK:
        failure_count = _PRD_VALIDATION_FAILURES.get(run_id, 0) + 1
        _PRD_VALIDATION_FAILURES[run_id] = failure_count
    repairs_remaining = max(0, _MAX_PRD_REPAIRS - (failure_count - 1))
    terminal = repairs_remaining == 0
    return {
        "passed": False,
        "diagnostic": True,
        "behaviorProof": False,
        "errors": errors,
        "failureCount": failure_count,
        "repairsRemaining": repairs_remaining,
        "repairAllowed": not terminal,
        "terminal": terminal,
    }


def _prd_terminal_response(run_id: str) -> dict:
    with _PRD_VALIDATION_LOCK:
        failure_count = _PRD_VALIDATION_FAILURES.get(run_id, _MAX_PRD_REPAIRS + 1)
    return {
        "error": True,
        "passed": False,
        "diagnostic": True,
        "behaviorProof": False,
        "repairAllowed": False,
        "repairsRemaining": 0,
        "failureCount": failure_count,
        "terminal": True,
        "message": "PRD 文档包初次诊断及最多两次定向修复均未通过。停止继续写入、验证和提交；结束本次运行，由用户手动重跑。",
    }


def _clear_prd_failures(run_id: str) -> None:
    if not run_id:
        return
    with _PRD_VALIDATION_LOCK:
        _PRD_VALIDATION_FAILURES.pop(run_id, None)


def _prd_allowed_path(path: str) -> bool:
    if not path or len(path) > 512 or ".." in path or path.startswith("/") or "\\" in path or ":" in path:
        return False
    lower = path.lower()
    if lower in (".env", "package.json") or lower.startswith(".env.") or "/.env" in lower or lower.startswith("src/") or "/src/" in lower:
        return False
    if path == "README.md" or (path.endswith(".md") and "/" not in path):
        return bool(path) and path not in (".", "..")
    if path.startswith("modules/") and path.endswith(".md") and path.count("/") == 1:
        name = path[8:]
        return bool(name) and name not in (".", "..")
    if path.startswith("data/") and path.endswith(".json") and path.count("/") == 1:
        name = path[5:]
        return bool(name) and name not in (".", "..")
    return False


def _prd_truncated_write(message: str) -> str:
    return json.dumps({"error": True, "truncated": True, "message": message}, ensure_ascii=False)


def _prd_unclosed_mermaid(text: str) -> bool:
    if not text:
        return False
    opens = len(re.findall(r"```mermaid\b", text, re.I))
    fences = len(re.findall(r"```", text))
    return opens > 0 and fences < opens * 2


def write_prd_file(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    content = args.get("content")
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    if _prd_validation_terminal(run_id):
        return json.dumps(_prd_terminal_response(run_id), ensure_ascii=False)
    if not file_path:
        return json.dumps({"error": True, "message": "filePath is required"})
    if content is None:
        return json.dumps({"error": True, "message": "content is required"})
    norm_path = file_path.replace("\\", "/").strip().lstrip("/")
    if not _prd_allowed_path(norm_path):
        return json.dumps({
            "error": True,
            "message": "PRD 沙箱只允许 README.md、根目录 *.md、modules/*.md、data/*.json。",
        }, ensure_ascii=False)
    text = str(content)
    lower = norm_path.lower()
    if lower.endswith(".md") and len(text) > _PRD_WRITE_CHAR_LIMIT:
        return _prd_truncated_write(
            f"{norm_path} 单次 content 有 {len(text)} 字符，超过模型单轮安全上限 {_PRD_WRITE_CHAR_LIMIT}。"
            "不要原样重试，也不要删 catalog 模块。请把该章拆成更小的完整文件，或压缩口水话后重写本文件。"
        )
    if lower.endswith(".json") and len(text) > _PRD_JSON_WRITE_CHAR_LIMIT:
        return _prd_truncated_write(
            f"{norm_path} 单次 JSON 有 {len(text)} 字符，超过 {_PRD_JSON_WRITE_CHAR_LIMIT}。请只保留 catalog 必要字段。"
        )
    if lower.endswith(".json"):
        try:
            json.loads(text)
        except Exception:
            return _prd_truncated_write(f"{norm_path} JSON 无法解析，可能被截断。请提交完整合法 JSON。")
    if lower.endswith(".md") and _prd_unclosed_mermaid(text):
        return _prd_truncated_write(f"{norm_path} 含未闭合 mermaid 代码块，视为截断。请写完整后再提交。")
    return _request(
        "POST",
        f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files",
        {"filePath": file_path, "content": text},
    )


def read_prd_file(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    if not file_path:
        return json.dumps({"error": True, "message": "filePath is required"})
    query = "?" + urllib.parse.urlencode({"filePath": file_path})
    return _request("GET", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files{query}")


def list_prd_files(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    return _request("GET", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files")


def _prd_parse_catalog(raw: str) -> tuple[dict | None, str | None]:
    try:
        data = json.loads(raw or "")
    except Exception:
        return None, "data/catalog.json 不是合法 JSON"
    if not isinstance(data, dict):
        return None, "data/catalog.json 必须是对象"
    modules = data.get("modules")
    if not isinstance(modules, list) or not modules:
        return None, "data/catalog.json 需要非空 modules 数组"
    return data, None


def _collect_prd_errors(files: dict[str, str]) -> list[str]:
    errors: list[str] = []
    for required in _PRD_REQUIRED:
        if required not in files or not str(files.get(required) or "").strip():
            errors.append(f"缺少必要文件 {required}")
    catalog, catalog_error = _prd_parse_catalog(files.get("data/catalog.json") or "")
    if catalog_error:
        errors.append(catalog_error)
        catalog = None
    modules = catalog.get("modules") if isinstance(catalog, dict) else []
    keys: list[str] = []
    core_keys: list[str] = []
    if isinstance(modules, list):
        for item in modules:
            if not isinstance(item, dict):
                errors.append("catalog.modules 每一项必须是对象")
                continue
            key = str(item.get("key") or "").strip()
            title = str(item.get("title") or "").strip()
            if not key or not title:
                errors.append("catalog.modules 需要 key 和 title")
                continue
            keys.append(key)
            if item.get("core") is True:
                core_keys.append(key)
            path = f"modules/{key}.md"
            if path not in files or not str(files.get(path) or "").strip():
                errors.append(f"catalog 模块 {key} 缺少 {path}")
    ia = files.get("01-ia.md") or ""
    for key in keys:
        if key not in ia:
            errors.append(f"01-ia.md 未包含 catalog 菜单 key={key}")
    permissions = files.get("03-permissions.md") or ""
    if "|" not in permissions or permissions.count("|") < 8:
        errors.append("03-permissions.md 需要角色 × 动作 × 数据域矩阵表")
    for path, content in files.items():
        text = content or ""
        if _PRD_PLACEHOLDER.search(text):
            errors.append(f"{path} 仍包含生成占位符")
        if "保留模块" in text and len(text.strip()) < 80:
            errors.append(f"{path} 存在「保留模块」空章")
        if path.endswith(".md") and _prd_unclosed_mermaid(text):
            errors.append(f"{path} mermaid 代码块未闭合")
    for key in core_keys:
        path = f"modules/{key}.md"
        body = files.get(path) or ""
        header_ok = (
            "| 字段名 |" in body
            and "| 控件类型 |" in body
            and "| 必填 |" in body
            and ("| 校验 |" in body or "| 校验规则 |" in body)
            and ("| 默认值/联动 |" in body or "| 默认值/联动说明 |" in body)
        )
        if not header_ok:
            errors.append(f"{path} 核心模块缺少完整字段矩阵表头")
        if "```mermaid" not in body.lower():
            errors.append(f"{path} 核心模块至少需要一张 Mermaid")
        ac_count = len(re.findall(r"AC-[A-Za-z0-9_-]+", body))
        if ac_count < 3:
            errors.append(f"{path} 核心模块至少需要 3 条 AC-，当前 {ac_count} 条")
        if "BR-" not in body:
            errors.append(f"{path} 核心模块需要 BR- 业务规则编号")
    return errors


def verify_prd(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    if not run_id:
        return json.dumps({"passed": False, "errors": ["runId is required"]})
    if _prd_validation_terminal(run_id):
        return json.dumps(_prd_terminal_response(run_id), ensure_ascii=False)
    listed_raw = list_prd_files({"runId": run_id}, **kwargs)
    try:
        listed = json.loads(listed_raw)
    except Exception:
        failure = _prd_failure(run_id, [f"无法列出工作区文件: {listed_raw}"])
        return json.dumps(failure, ensure_ascii=False)
    names = listed.get("files") if isinstance(listed, dict) else None
    if not isinstance(names, list):
        failure = _prd_failure(run_id, ["工作区文件列表无效"])
        return json.dumps(failure, ensure_ascii=False)
    files: dict[str, str] = {}
    for name in names:
        raw = read_prd_file({"runId": run_id, "filePath": str(name)}, **kwargs)
        try:
            parsed = json.loads(raw)
        except Exception:
            failure = _prd_failure(run_id, [f"无法读取 {name}"])
            return json.dumps(failure, ensure_ascii=False)
        if not parsed.get("success"):
            failure = _prd_failure(run_id, [f"无法读取 {name}"])
            return json.dumps(failure, ensure_ascii=False)
        files[str(name)] = str(parsed.get("content") or "")
    errors = _collect_prd_errors(files)
    if errors:
        failure = _prd_failure(run_id, errors)
        if failure["terminal"]:
            failure["summary"] = "PRD 诊断已用完两次定向修复额度。"
        else:
            failure["summary"] = f"PRD 诊断未通过，检出 {len(errors)} 项；剩余修复次数 {failure['repairsRemaining']}。"
        return json.dumps(failure, ensure_ascii=False)
    return json.dumps({
        "passed": True,
        "diagnostic": True,
        "behaviorProof": False,
        "errors": [],
        "files": sorted(files.keys()),
        "message": "PRD 诊断未发现已配置检查项的问题。请立即 submit_result，只带 files 清单和 workspaceFile=README.md。",
    }, ensure_ascii=False)


_MAX_PACK_REPAIRS = 2
_TC_VALIDATION_FAILURES: dict[str, int] = {}
_TC_VALIDATION_LOCK = threading.Lock()
_TS_VALIDATION_FAILURES: dict[str, int] = {}
_TS_VALIDATION_LOCK = threading.Lock()
_PACK_MD_WRITE_CHAR_LIMIT = 18000
_PACK_JSON_WRITE_CHAR_LIMIT = 28000


def _pack_truncated_write(message: str) -> str:
    return json.dumps({"error": True, "truncated": True, "message": message}, ensure_ascii=False)


def _tc_validation_terminal(run_id: str) -> bool:
    if not run_id:
        return False
    with _TC_VALIDATION_LOCK:
        return _TC_VALIDATION_FAILURES.get(run_id, 0) > _MAX_PACK_REPAIRS


def _tc_failure(run_id: str, errors: list[str]) -> dict:
    with _TC_VALIDATION_LOCK:
        failure_count = _TC_VALIDATION_FAILURES.get(run_id, 0) + 1
        _TC_VALIDATION_FAILURES[run_id] = failure_count
    repairs_remaining = max(0, _MAX_PACK_REPAIRS - (failure_count - 1))
    terminal = repairs_remaining == 0
    return {
        "passed": False,
        "diagnostic": True,
        "errors": errors,
        "failureCount": failure_count,
        "repairsRemaining": repairs_remaining,
        "repairAllowed": not terminal,
        "terminal": terminal,
    }


def _clear_tc_failures(run_id: str) -> None:
    if not run_id:
        return
    with _TC_VALIDATION_LOCK:
        _TC_VALIDATION_FAILURES.pop(run_id, None)


def _ts_validation_terminal(run_id: str) -> bool:
    if not run_id:
        return False
    with _TS_VALIDATION_LOCK:
        return _TS_VALIDATION_FAILURES.get(run_id, 0) > _MAX_PACK_REPAIRS


def _ts_failure(run_id: str, errors: list[str]) -> dict:
    with _TS_VALIDATION_LOCK:
        failure_count = _TS_VALIDATION_FAILURES.get(run_id, 0) + 1
        _TS_VALIDATION_FAILURES[run_id] = failure_count
    repairs_remaining = max(0, _MAX_PACK_REPAIRS - (failure_count - 1))
    terminal = repairs_remaining == 0
    return {
        "passed": False,
        "diagnostic": True,
        "errors": errors,
        "failureCount": failure_count,
        "repairsRemaining": repairs_remaining,
        "repairAllowed": not terminal,
        "terminal": terminal,
    }


def _clear_ts_failures(run_id: str) -> None:
    if not run_id:
        return
    with _TS_VALIDATION_LOCK:
        _TS_VALIDATION_FAILURES.pop(run_id, None)


def _tc_allowed_path(path: str) -> bool:
    if not path or ".." in path or path.startswith("/") or "\\" in path or ":" in path:
        return False
    if path == "README.md":
        return True
    if path.startswith("modules/") and path.endswith(".json") and path.count("/") == 1:
        return bool(path[8:-5])
    if path.startswith("data/") and path.endswith(".json") and path.count("/") == 1:
        return bool(path[5:-5])
    return False


def _ts_allowed_path(path: str) -> bool:
    if not path or ".." in path or path.startswith("/") or "\\" in path or ":" in path:
        return False
    if path in ("README.md", "architecture.md"):
        return True
    if path.endswith(".md") and "/" not in path:
        return True
    if path.startswith("modules/") and path.count("/") == 1 and (path.endswith(".md") or path.endswith(".json")):
        return True
    if path.startswith("data/") and path.endswith(".json") and path.count("/") == 1:
        return True
    return False


def write_test_case_file(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    content = args.get("content")
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    if _tc_validation_terminal(run_id):
        return json.dumps({"error": True, "terminal": True, "message": "用例诊断修复额度已用完，停止写入。"}, ensure_ascii=False)
    if content is None or not file_path:
        return json.dumps({"error": True, "message": "filePath and content are required"})
    norm_path = file_path.replace("\\", "/").strip().lstrip("/")
    if norm_path == "data/required-modules.json":
        return json.dumps({"error": True, "message": "data/required-modules.json 由控制面预置，禁止覆盖。"}, ensure_ascii=False)
    if not _tc_allowed_path(norm_path):
        return json.dumps({"error": True, "message": "用例沙箱只允许 README.md、modules/*.json、data/*.json。"}, ensure_ascii=False)
    text = str(content)
    lower = norm_path.lower()
    if lower.endswith(".json") and len(text) > _PACK_JSON_WRITE_CHAR_LIMIT:
        return _pack_truncated_write(f"{norm_path} 超过 {_PACK_JSON_WRITE_CHAR_LIMIT} 字符，请拆模块或压缩后重写。")
    if lower.endswith(".md") and len(text) > _PACK_MD_WRITE_CHAR_LIMIT:
        return _pack_truncated_write(f"{norm_path} 超过 {_PACK_MD_WRITE_CHAR_LIMIT} 字符，请压缩后重写。")
    if lower.endswith(".json"):
        try:
            json.loads(text)
        except Exception as exc:
            return _pack_truncated_write(f"{norm_path} JSON 无法解析（{exc}）。")
    return _request("POST", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files", {"filePath": file_path, "content": text})


def read_test_case_file(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    if not run_id or not file_path:
        return json.dumps({"error": True, "message": "runId and filePath are required"})
    query = "?" + urllib.parse.urlencode({"filePath": file_path})
    return _request("GET", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files{query}")


def list_test_case_files(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    return _request("GET", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files")


def write_tech_spec_file(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    content = args.get("content")
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    if _ts_validation_terminal(run_id):
        return json.dumps({"error": True, "terminal": True, "message": "技术方案诊断修复额度已用完，停止写入。"}, ensure_ascii=False)
    if content is None or not file_path:
        return json.dumps({"error": True, "message": "filePath and content are required"})
    norm_path = file_path.replace("\\", "/").strip().lstrip("/")
    if norm_path == "data/required-modules.json":
        return json.dumps({"error": True, "message": "data/required-modules.json 由控制面预置，禁止覆盖。"}, ensure_ascii=False)
    if norm_path == "data/tasks.json" or re.match(r"^data/tasks([._-].*)?\.json$", norm_path):
        return json.dumps({"error": True, "message": "技术方案不再拆解工单，禁止写入 data/tasks.json。请只写 architecture/tables/apis/modules，然后 verify_tech_spec 并 submit_result。"}, ensure_ascii=False)
    if not _ts_allowed_path(norm_path):
        return json.dumps({"error": True, "message": "技术方案沙箱只允许 README.md、architecture.md、*.md、modules/*、data/*.json。"}, ensure_ascii=False)
    text = str(content).replace("\x00", "")
    lower = norm_path.lower()
    if lower.endswith(".json") and len(text) > _PACK_JSON_WRITE_CHAR_LIMIT:
        return _pack_truncated_write(f"{norm_path} 超过 {_PACK_JSON_WRITE_CHAR_LIMIT} 字符，请拆文件后重写。")
    if lower.endswith(".md") and len(text) > _PACK_MD_WRITE_CHAR_LIMIT:
        return _pack_truncated_write(f"{norm_path} 超过 {_PACK_MD_WRITE_CHAR_LIMIT} 字符，请压缩后重写。")
    if lower.endswith(".json"):
        try:
            json.loads(text)
        except Exception as exc:
            return _pack_truncated_write(f"{norm_path} JSON 无法解析（{exc}）。")
    if lower.endswith(".md"):
        opens = len(re.findall(r"```mermaid\b", text, re.I))
        fences = len(re.findall(r"```", text))
        if opens > 0 and fences < opens * 2:
            return _pack_truncated_write(f"{norm_path} 含未闭合 mermaid 代码块。")
    return _request("POST", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files", {"filePath": file_path, "content": text})


def read_tech_spec_file(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    file_path = str(args.get("filePath") or args.get("path") or args.get("name") or "").strip()
    if not run_id or not file_path:
        return json.dumps({"error": True, "message": "runId and filePath are required"})
    query = "?" + urllib.parse.urlencode({"filePath": file_path})
    return _request("GET", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files{query}")


def list_tech_spec_files(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    if not run_id:
        return json.dumps({"error": True, "message": "runId is required"})
    return _request("GET", f"/internal/v1/runs/{urllib.parse.quote(run_id)}/workspace/files")


def _align_required_module_keys(files: dict[str, str], catalog_keys: list[str], errors: list[str], label: str) -> None:
    raw = files.get("data/required-modules.json")
    if not raw:
        return
    try:
        required = json.loads(raw)
    except Exception:
        return
    modules = required.get("modules") if isinstance(required, dict) else None
    if not isinstance(modules, list) or not modules:
        return
    required_keys = [str(item.get("key") or "").strip() for item in modules if isinstance(item, dict) and str(item.get("key") or "").strip()]
    if not required_keys:
        return
    coverage = str(required.get("coverage") or "all").strip().lower()
    missing = [key for key in required_keys if key not in catalog_keys]
    if coverage != "subset" and missing:
        errors.append(f"{label}缺少当前稿模块: " + "、".join(missing))
    if coverage == "subset" and not any(key in required_keys for key in catalog_keys):
        errors.append(f"快速{label}也必须从 data/required-modules.json 挑选模块 key")


def _collect_test_case_errors(files: dict[str, str]) -> list[str]:
    errors: list[str] = []
    if "data/catalog.json" not in files:
        errors.append("缺少 data/catalog.json")
        return errors
    try:
        catalog = json.loads(files["data/catalog.json"])
    except Exception as exc:
        errors.append(f"data/catalog.json 无法解析：{exc}")
        return errors
    modules = catalog.get("modules") if isinstance(catalog, dict) else None
    if not isinstance(modules, list) or not modules:
        errors.append("catalog.modules 必须非空")
        return errors
    catalog_keys = []
    for index, item in enumerate(modules):
        if not isinstance(item, dict) or not str(item.get("key") or "").strip():
            errors.append(f"catalog.modules[{index}] 缺少 key")
            continue
        key = str(item.get("key")).strip()
        catalog_keys.append(key)
        path = f"modules/{key}.json"
        if path not in files:
            errors.append(f"缺少 {path}")
            continue
        try:
            root = json.loads(files[path])
        except Exception as exc:
            errors.append(f"{path} 无法解析：{exc}")
            continue
        items = root if isinstance(root, list) else (root.get("items") if isinstance(root, dict) else None)
        if not isinstance(items, list) or not items:
            errors.append(f"{path} 需要非空 items")
            continue
        for case in items:
            if not isinstance(case, dict):
                errors.append(f"{path} 含非法用例项")
                continue
            title = str(case.get("title") or case.get("name") or "").strip()
            expected = str(case.get("expectedResult") or case.get("expected") or "").strip()
            if not title:
                errors.append(f"{path} 有用例缺少 title")
            if not expected:
                errors.append(f"{path} 有用例缺少 expectedResult")
    _align_required_module_keys(files, catalog_keys, errors, "用例包")
    return errors


def verify_test_case(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    if not run_id:
        return json.dumps({"passed": False, "errors": ["runId is required"]})
    if _tc_validation_terminal(run_id):
        return json.dumps({"passed": False, "terminal": True, "errors": ["修复额度已用完"], "message": "停止继续修复"}, ensure_ascii=False)
    listed_raw = list_test_case_files({"runId": run_id}, **kwargs)
    try:
        listed = json.loads(listed_raw)
    except Exception:
        failure = _tc_failure(run_id, [f"无法列出工作区: {listed_raw}"])
        return json.dumps(failure, ensure_ascii=False)
    names = listed.get("files") if isinstance(listed, dict) else None
    if not isinstance(names, list):
        failure = _tc_failure(run_id, ["工作区文件列表无效"])
        return json.dumps(failure, ensure_ascii=False)
    files: dict[str, str] = {}
    for name in names:
        raw = read_test_case_file({"runId": run_id, "filePath": str(name)}, **kwargs)
        try:
            parsed = json.loads(raw)
        except Exception:
            failure = _tc_failure(run_id, [f"无法读取 {name}"])
            return json.dumps(failure, ensure_ascii=False)
        if not parsed.get("success"):
            failure = _tc_failure(run_id, [f"无法读取 {name}"])
            return json.dumps(failure, ensure_ascii=False)
        files[str(name)] = str(parsed.get("content") or "")
    errors = _collect_test_case_errors(files)
    if errors:
        failure = _tc_failure(run_id, errors)
        failure["summary"] = f"用例诊断未通过，检出 {len(errors)} 项；剩余修复次数 {failure['repairsRemaining']}。"
        return json.dumps(failure, ensure_ascii=False)
    return json.dumps({
        "passed": True,
        "diagnostic": True,
        "errors": [],
        "files": sorted(files.keys()),
        "message": "用例诊断通过。请立即 submit_result，只带 files 清单和 workspaceFile=README.md。",
    }, ensure_ascii=False)


def _collect_tech_spec_json_array(files: dict[str, str], primary: str, field_names: tuple[str, ...]) -> list:
    items: list = []
    stem = primary.rsplit("/", 1)[-1]
    if stem.endswith(".json"):
        stem = stem[:-5]
    stem = stem.lower()
    paths = []
    if primary in files:
        paths.append(primary)
    for path in sorted(files.keys()):
        if path == primary:
            continue
        if (path.startswith("data/") or path.startswith("modules/")) and path.endswith(".json"):
            paths.append(path)
    for path in paths:
        raw = files.get(path)
        if not raw or not str(raw).strip():
            continue
        allow_root_array = path == primary or (stem and stem in path.lower())
        try:
            root = json.loads(raw)
        except Exception:
            continue
        if isinstance(root, list):
            if allow_root_array:
                items.extend(root)
            continue
        if isinstance(root, dict):
            for field in field_names:
                arr = root.get(field)
                if isinstance(arr, list):
                    items.extend(arr)
                    break
    return items


def _collect_tech_spec_errors(files: dict[str, str]) -> list[str]:
    errors: list[str] = []
    arch = str(files.get("architecture.md") or "").strip()
    if not arch:
        errors.append("缺少 architecture.md")
    elif len(arch) < 200:
        errors.append("architecture.md 过短，需写清分层职责与关键流转，达到可开工说明")

    tables = _collect_tech_spec_json_array(files, "data/tables.json", ("databaseTables", "tables"))
    if "data/tables.json" not in files and not tables:
        errors.append("缺少 data/tables.json")
    elif not tables:
        errors.append("data/tables.json 需要非空表定义")
    else:
        for index, table in enumerate(tables):
            if not isinstance(table, dict):
                errors.append(f"tables[{index}] 非法")
                continue
            if not str(table.get("tableName") or table.get("name") or "").strip():
                errors.append(f"tables[{index}] 缺少 tableName")
            columns = table.get("columns")
            if not isinstance(columns, list) or not columns:
                errors.append(f"表 {table.get('tableName') or index} 缺少 columns 字段明细")
            else:
                for col in columns[:1]:
                    if not isinstance(col, dict) or not str(col.get("name") or "").strip() or not str(col.get("type") or "").strip():
                        errors.append(f"表 {table.get('tableName') or index} 的 columns 需含 name/type")
                        break

    apis = _collect_tech_spec_json_array(files, "data/apis.json", ("apiEndpoints", "apis"))
    if "data/apis.json" not in files and not apis:
        errors.append("缺少 data/apis.json")
    elif not apis:
        errors.append("data/apis.json 需要非空 API 契约")
    else:
        for index, api in enumerate(apis):
            if not isinstance(api, dict):
                errors.append(f"apis[{index}] 非法")
                continue
            if not str(api.get("method") or "").strip() or not str(api.get("path") or "").strip():
                errors.append(f"apis[{index}] 缺少 method/path")
            if not str(api.get("summary") or api.get("title") or "").strip():
                errors.append(f"apis[{index}] 缺少 summary")
            if not str(api.get("requestBody") or "").strip() and not str(api.get("responseBody") or "").strip():
                errors.append(f"apis[{index}] 需提供 requestBody 或 responseBody 样例")

    if "data/tasks.json" in files:
        # Ignore leftover work-order files; TECH_SPEC no longer delivers task breakdowns.
        files = {k: v for k, v in files.items() if k != "data/tasks.json" and not (
            k.startswith("data/tasks") and k.endswith(".json")
        )}

    for path in ("data/tables.json", "data/apis.json"):
        if path in files:
            try:
                json.loads(files[path])
            except Exception as exc:
                errors.append(f"{path} 无法解析：{exc}")

    catalog_keys: list[str] = []
    if "data/catalog.json" in files:
        try:
            catalog = json.loads(files["data/catalog.json"])
            modules = catalog.get("modules") if isinstance(catalog, dict) else None
            if isinstance(modules, list):
                catalog_keys = [str(item.get("key")).strip() for item in modules if isinstance(item, dict) and str(item.get("key") or "").strip()]
        except Exception:
            errors.append("data/catalog.json 无法解析")
    if "data/required-modules.json" in files and not catalog_keys:
        errors.append("有 data/required-modules.json 时必须写 data/catalog.json 对齐模块覆盖")
    if catalog_keys:
        _align_required_module_keys(files, catalog_keys, errors, "技术方案")
    return errors


def verify_tech_spec(args: dict, **kwargs) -> str:
    run_id = _extract_run_id(args, kwargs)
    if not run_id:
        return json.dumps({"passed": False, "errors": ["runId is required"]})
    if _ts_validation_terminal(run_id):
        return json.dumps({"passed": False, "terminal": True, "errors": ["修复额度已用完"], "message": "停止继续修复"}, ensure_ascii=False)
    listed_raw = list_tech_spec_files({"runId": run_id}, **kwargs)
    try:
        listed = json.loads(listed_raw)
    except Exception:
        failure = _ts_failure(run_id, [f"无法列出工作区: {listed_raw}"])
        return json.dumps(failure, ensure_ascii=False)
    names = listed.get("files") if isinstance(listed, dict) else None
    if not isinstance(names, list):
        failure = _ts_failure(run_id, ["工作区文件列表无效"])
        return json.dumps(failure, ensure_ascii=False)
    files: dict[str, str] = {}
    for name in names:
        raw = read_tech_spec_file({"runId": run_id, "filePath": str(name)}, **kwargs)
        try:
            parsed = json.loads(raw)
        except Exception:
            failure = _ts_failure(run_id, [f"无法读取 {name}"])
            return json.dumps(failure, ensure_ascii=False)
        if not parsed.get("success"):
            failure = _ts_failure(run_id, [f"无法读取 {name}"])
            return json.dumps(failure, ensure_ascii=False)
        files[str(name)] = str(parsed.get("content") or "")
    errors = _collect_tech_spec_errors(files)
    if errors:
        failure = _ts_failure(run_id, errors)
        failure["summary"] = f"技术方案诊断未通过，检出 {len(errors)} 项；剩余修复次数 {failure['repairsRemaining']}。"
        return json.dumps(failure, ensure_ascii=False)
    return json.dumps({
        "passed": True,
        "diagnostic": True,
        "errors": [],
        "files": sorted(files.keys()),
        "message": "技术方案诊断通过。请立即 submit_result，只带 files 清单和 workspaceFile=README.md。",
    }, ensure_ascii=False)


def submit_result(args: dict, **kwargs) -> str:
    if not isinstance(args, dict) or not args:
        return json.dumps({
            "error": True,
            "message": "submit_result arguments were empty or unrepairable. Your previous output may have exceeded the single-turn token limit or gateway timeout. Submit a smaller complete payload; for DEMO use workspaceFile='index.html' instead of embedding the HTML again."
        })
    payload = args.get("payload")
    run_id = _extract_run_id(args, kwargs, payload if isinstance(payload, dict) else None)
    mode = str(args.get("mode") or "")
    if not mode and isinstance(payload, dict):
        mode = str(payload.get("mode") or "")
    if not run_id or not mode or not isinstance(payload, dict):
        return json.dumps({"error": True, "message": "runId, mode and payload are required"})

    if isinstance(payload, dict):
        if not payload.get("mode") and mode:
            payload["mode"] = mode
        elif payload.get("mode") and mode and str(payload.get("mode")).upper() == mode.upper():
            payload["mode"] = mode

    mode_upper = mode.upper()
    if mode_upper == "DEMO":
        if _demo_validation_terminal(run_id):
            return json.dumps(_demo_terminal_response(run_id), ensure_ascii=False)

        # workspaceFile is canonical: always reload it and replace any stale inline html.
        workspace_file = payload.get("workspaceFile") or payload.get("entryFile") or args.get("workspaceFile")
        if workspace_file:
            read_res_raw = read_demo_file({"runId": run_id, "filePath": str(workspace_file)}, **kwargs)
            try:
                read_res = json.loads(read_res_raw)
                if read_res.get("success"):
                    payload["html"] = read_res.get("content", "")
                    payload["workspaceFile"] = str(workspace_file)
                    payload.pop("entryFile", None)
                else:
                    failure = _demo_failure(run_id, [f"无法读取工作区文件 {workspace_file}"])
                    failure.update({"error": True, "status": 400, "detail": f"无法读取工作区文件 {workspace_file}，拒绝使用 payload 中可能过期的内联 html。"})
                    return json.dumps(failure, ensure_ascii=False)
            except Exception:
                failure = _demo_failure(run_id, [f"读取工作区文件响应解析异常: {read_res_raw}"])
                failure.update({"error": True, "status": 400, "detail": "读取工作区文件响应无法解析，拒绝使用 payload 中可能过期的内联 html。"})
                return json.dumps(failure, ensure_ascii=False)

        html = payload.get("html")
        if isinstance(html, str):
            import re

            for m in re.finditer(r'<script\b([^>]*)>([\s\S]*?)</script>', html, re.I):
                attrs = m.group(1).lower()
                if "src=" in attrs:
                    continue
                type_match = re.search(r'type\s*=\s*["\']?([^"\'\s>]+)', attrs)
                if type_match:
                    script_type = type_match.group(1).lower()
                    js_types = {
                        "text/javascript",
                        "application/javascript",
                        "text/ecmascript",
                        "application/ecmascript",
                        "module",
                    }
                    if script_type not in js_types:
                        continue
                js_err = _check_js_syntax(m.group(2))
                if js_err:
                    failure = _demo_failure(run_id, [f"Demo 脚本存在语法错误：{js_err}"])
                    failure.update({
                        "error": True,
                        "status": 400,
                        "detail": f"Demo 脚本存在语法错误：{js_err}。请按返回的剩余修复次数进行定向修复；自动化诊断不代表完整行为证明。",
                    })
                    return json.dumps(failure, ensure_ascii=False)

            review_errors = _verify_demo_html(html)
            if review_errors:
                formatted_errors = "\n".join(f"{i+1}. {err}" for i, err in enumerate(review_errors))
                failure = _demo_failure(run_id, review_errors)
                terminal_note = (
                    "已用完两次定向修复额度，停止继续修复和提交，由用户手动重跑。"
                    if failure["terminal"]
                    else f"仅修复上述具体问题后重试；剩余定向修复次数 {failure['repairsRemaining']}。"
                )
                failure.update({
                    "error": True,
                    "status": 400,
                    "detail": (
                        f"【Demo 有限自动化诊断未通过】：\n"
                        f"{formatted_errors}\n\n{terminal_note}"
                        "该诊断不构成所有业务按钮、状态流转或浏览器兼容性的完整行为证明。"
                    ),
                })
                return json.dumps(failure, ensure_ascii=False)


    if mode_upper == "POC":
        if _poc_validation_terminal(run_id):
            return json.dumps(_poc_terminal_response(run_id), ensure_ascii=False)
        verify_raw = verify_poc({"runId": run_id}, **kwargs)
        try:
            verify = json.loads(verify_raw)
        except Exception:
            return json.dumps({"error": True, "status": 400, "detail": "POC 提交前诊断无法解析"}, ensure_ascii=False)
        if not verify.get("passed"):
            verify.update({"error": True, "status": 400, "detail": verify.get("summary") or "POC 诊断未通过"})
            return json.dumps(verify, ensure_ascii=False)
        payload["workspaceFile"] = payload.get("workspaceFile") or "index.html"
        if verify.get("files"):
            payload["files"] = verify.get("files")

    if mode_upper == "TEST_CASE":
        if _tc_validation_terminal(run_id):
            return json.dumps({"error": True, "terminal": True, "detail": "用例诊断修复额度已用完"}, ensure_ascii=False)
        verify_raw = verify_test_case({"runId": run_id}, **kwargs)
        try:
            verify = json.loads(verify_raw)
        except Exception:
            return json.dumps({"error": True, "status": 400, "detail": "用例提交前诊断无法解析"}, ensure_ascii=False)
        if not verify.get("passed"):
            verify.update({"error": True, "status": 400, "detail": verify.get("summary") or "用例诊断未通过"})
            return json.dumps(verify, ensure_ascii=False)
        payload["workspaceFile"] = payload.get("workspaceFile") or "README.md"
        if verify.get("files"):
            payload["files"] = verify.get("files")
        payload.pop("partIndex", None)
        payload.pop("totalParts", None)
        payload.pop("isFinal", None)

    if mode_upper == "TECH_SPEC":
        if _ts_validation_terminal(run_id):
            return json.dumps({"error": True, "terminal": True, "detail": "技术方案诊断修复额度已用完"}, ensure_ascii=False)
        verify_raw = verify_tech_spec({"runId": run_id}, **kwargs)
        try:
            verify = json.loads(verify_raw)
        except Exception:
            return json.dumps({"error": True, "status": 400, "detail": "技术方案提交前诊断无法解析"}, ensure_ascii=False)
        if not verify.get("passed"):
            verify.update({"error": True, "status": 400, "detail": verify.get("summary") or "技术方案诊断未通过"})
            return json.dumps(verify, ensure_ascii=False)
        payload["workspaceFile"] = payload.get("workspaceFile") or "README.md"
        if verify.get("files"):
            payload["files"] = verify.get("files")
        payload.pop("partIndex", None)
        payload.pop("totalParts", None)
        payload.pop("isFinal", None)

    if mode_upper == "GENERATE":
        if _prd_validation_terminal(run_id):
            return json.dumps(_prd_terminal_response(run_id), ensure_ascii=False)
        verify_raw = verify_prd({"runId": run_id}, **kwargs)
        try:
            verify = json.loads(verify_raw)
        except Exception:
            return json.dumps({"error": True, "status": 400, "detail": "PRD 提交前诊断无法解析"}, ensure_ascii=False)
        if not verify.get("passed"):
            verify.update({"error": True, "status": 400, "detail": verify.get("summary") or "PRD 诊断未通过"})
            return json.dumps(verify, ensure_ascii=False)
        payload["workspaceFile"] = payload.get("workspaceFile") or "README.md"
        if verify.get("files"):
            payload["files"] = verify.get("files")
        payload.pop("partIndex", None)
        payload.pop("totalParts", None)
        payload.pop("isFinal", None)

    post_body = {"mode": mode, "payload": payload}
    for key in ("partIndex", "totalParts", "isFinal"):
        if mode_upper in ("GENERATE", "TEST_CASE", "TECH_SPEC"):
            continue
        if key in args:
            post_body[key] = args[key]
        elif isinstance(payload, dict) and key in payload:
            post_body[key] = payload[key]

    response = _request(
        "POST",
        f"/internal/v1/runs/{urllib.parse.quote(run_id)}/result",
        post_body,
    )
    if mode_upper == "DEMO":
        try:
            parsed = json.loads(response)
            if not parsed.get("error"):
                _clear_demo_failures(run_id)
        except Exception:
            pass
    if mode_upper == "POC":
        try:
            parsed = json.loads(response)
            if not parsed.get("error"):
                _clear_poc_failures(run_id)
        except Exception:
            pass
    if mode_upper == "GENERATE":
        try:
            parsed = json.loads(response)
            if not parsed.get("error"):
                _clear_prd_failures(run_id)
        except Exception:
            pass
    if mode_upper == "TEST_CASE":
        try:
            parsed = json.loads(response)
            if not parsed.get("error"):
                _clear_tc_failures(run_id)
        except Exception:
            pass
    if mode_upper == "TECH_SPEC":
        try:
            parsed = json.loads(response)
            if not parsed.get("error"):
                _clear_ts_failures(run_id)
        except Exception:
            pass
    return response
