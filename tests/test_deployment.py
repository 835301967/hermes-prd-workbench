import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("lifecycle", ROOT / "scripts/workbench_cli.py")
cli = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cli)


class DeploymentTests(unittest.TestCase):
    def test_configuration_is_parsed_without_shell_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "config"
            marker = Path(directory) / "must-not-exist"
            path.write_text(f'PRD_MODEL_NAME=$(touch {marker})\n')
            with self.assertRaises(ValueError):
                cli.load_environment(path)
            self.assertFalse(marker.exists())
            path.write_text("PRD_MODEL_NAME='a-model'\nPRD_WEB_PORT=8180\n")
            self.assertEqual({"PRD_MODEL_NAME": "a-model", "PRD_WEB_PORT": "8180"}, cli.load_environment(path))

    def test_secrets_are_preserved_and_not_printed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "secret"
            self.assertTrue(cli.write_secret(path, "synthetic-existing-secret"))
            self.assertFalse(cli.write_secret(path, "different-value"))
            self.assertEqual("synthetic-existing-secret\n", path.read_text())

    def test_native_windows_setup_is_rejected_before_creating_secrets(self):
        with patch.object(cli.os, "name", "nt"):
            with self.assertRaisesRegex(ValueError, "WSL2"):
                cli.secret_directory({})

    def test_compose_forwards_retention_and_pins_multiarch_mysql(self):
        import yaml
        config = yaml.safe_load((ROOT / "compose.yaml").read_text())
        self.assertNotIn("platform", config["services"]["mysql"])
        self.assertEqual("deploy/docker/mysql.Dockerfile", config["services"]["mysql"]["build"]["dockerfile"])
        self.assertIn("FROM mysql:8.4.11@sha256:", (ROOT / "deploy/docker/mysql.Dockerfile").read_text())
        self.assertIn("PRD_TELEMETRY_RETENTION_DAYS", config["services"]["backend"]["environment"])

    @patch.object(cli, "compose")
    @patch.object(cli, "load_environment", return_value={})
    def test_stop_never_deletes_volumes(self, _, compose):
        with patch("sys.argv", ["workbench", "stop"]):
            cli.main()
        self.assertEqual(["stop"], compose.call_args.args[1])

    def test_noninteractive_setup_has_no_fake_model_or_admin(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "config"
            secrets = Path(directory) / "secrets"
            config.write_text(f"PRD_SECRETS_DIR={secrets}\n")
            with patch("builtins.print") as output:
                cli.setup(config, non_interactive=True)
            self.assertFalse((secrets / "model-api-key").exists())
            self.assertFalse((secrets / "admin-password").exists())
            value = (secrets / "hermes-api-key").read_text().strip()
            self.assertNotIn(value, str(output.call_args_list))
            if os.name != "nt":
                self.assertEqual(0o700, secrets.stat().st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()
