import importlib.util
import os
import pathlib
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("static_tools", pathlib.Path(__file__).with_name("tools.py"))
tools = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tools)


class StaticDiagnosticTests(unittest.TestCase):
    def test_generated_script_is_parsed_but_never_executed(self):
        with tempfile.TemporaryDirectory() as directory:
            sentinel = pathlib.Path(directory) / "must-not-exist"
            script = f"require('fs').writeFileSync({str(sentinel)!r}, process.env.PRD_PLUGIN_KEY || 'executed');"
            with patch.dict(os.environ, {"PRD_PLUGIN_KEY": "synthetic-sensitive-environment", "NODE_OPTIONS": "--invalid-node-option"}):
                self.assertIsNone(tools._check_js_syntax(script))
                self.assertEqual([], tools._verify_demo_html(f"<!doctype html><html><body><script>{script}</script></body></html>"))
            self.assertFalse(sentinel.exists())

    @patch("shutil.which", return_value=None)
    def test_missing_node_is_an_explicit_diagnostic_failure(self, _):
        self.assertIsNotNone(tools._check_js_syntax("const x = 1;"))
        self.assertTrue(tools._verify_demo_html("<!doctype html><html><body>test</body></html>"))

    def test_environment_contains_no_credentials_or_preload_flags(self):
        with patch.dict(os.environ, {"PRD_MODEL_API_KEY": "synthetic", "PRD_PLUGIN_KEY": "synthetic", "NODE_OPTIONS": "--require private"}):
            environment = tools._diagnostic_environment()
        self.assertEqual({"LANG": "C.UTF-8"}, environment)

    def test_malformed_javascript_still_fails(self):
        self.assertIsNotNone(tools._check_js_syntax("const values = { broken-key: 1 };"))

    @patch("subprocess.run")
    def test_invalid_validator_response_does_not_pass(self, run):
        run.return_value.stdout = b'{}'
        run.return_value.returncode = 0
        self.assertTrue(tools._verify_demo_html("<!doctype html><html><body>test</body></html>"))


if __name__ == "__main__":
    unittest.main()
