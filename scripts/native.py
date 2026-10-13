#!/usr/bin/env python3
"""Optional native development entry points. Windows uses WSL2 for Hermes."""
import os
from pathlib import Path
import re
import subprocess
import sys

from workbench_cli import ROOT, load_environment, setup


def require_version(command, expected, pattern=None):
    result = subprocess.run(command, capture_output=True, text=True, check=True)
    output = result.stdout + result.stderr
    if pattern:
        valid = re.search(pattern, output)
    else:
        valid = expected in output
    if not valid:
        raise ValueError("Runtime version does not match config/versions.env")


def environment():
    path = Path(os.environ.get("PRD_ENV_FILE", ROOT / ".env"))
    for key, value in load_environment(path).items():
        os.environ.setdefault(key, value)
    for key in ["PRD_UPLOAD_DIR", "PRD_HERMES_API_KEY_FILE", "PRD_PLUGIN_KEY_FILE", "PRD_MODEL_API_KEY_FILE", "PRD_DB_PASSWORD_FILE", "PRD_BOOTSTRAP_ADMIN_PASSWORD_FILE"]:
        value = os.environ.get(key)
        if value and not Path(value).is_absolute():
            os.environ[key] = str(ROOT / value)
    os.environ.setdefault("HERMES_HOME", str(ROOT / ".runtime/hermes-home"))
    return path


def read_secret(variable, file_variable):
    if os.environ.get(variable):
        return
    path = os.environ.get(file_variable, "")
    if not path or not Path(path).is_file():
        raise ValueError("Required secret file missing")
    os.environ[variable] = Path(path).read_text().strip()


def main():
    component = sys.argv[1]
    try:
        path = environment()
        if component == "secrets":
            setup(path, non_interactive=True)
        elif component == "check":
            require_version(["java", "-version"], "21", r'version "21(?:\.|\")')
            require_version(["mvn", "-version"], "3.9.16")
            require_version(["node", "--version"], "22.23.1")
            require_version(["pnpm", "--version"], "11.9.0")
            require_version(["hermes", "--version"], "0.16.0")
            require_version([os.environ.get("PRD_HERMES_PYTHON", "python3"), "--version"], "3.11.15")
            print("Native versions match. Verify SELECT 1/schema and detailed Hermes health before acceptance.")
        elif component == "backend":
            require_version(["java", "-version"], "21", r'version "21(?:\.|\")')
            require_version(["mvn", "-version"], "3.9.16")
            read_secret("PRD_DB_PASSWORD", "PRD_DB_PASSWORD_FILE")
            os.chdir(ROOT / "apps/backend")
            os.execvp("mvn", ["mvn", "-q", "spring-boot:run"])
        elif component == "frontend":
            require_version(["node", "--version"], "22.23.1")
            require_version(["pnpm", "--version"], "11.9.0")
            os.environ["VITE_TELEMETRY_ENABLED"] = os.environ.get("PRD_TELEMETRY_ENABLED", "false")
            os.chdir(ROOT / "apps/web")
            if not Path("node_modules").is_dir():
                subprocess.run(["pnpm", "install", "--frozen-lockfile"], check=True)
            os.execvp("pnpm", ["pnpm", "dev"])
        elif component in {"profile", "hermes"}:
            require_version(["hermes", "--version"], "0.16.0")
            python = os.environ.get("PRD_HERMES_PYTHON", "python3")
            require_version([python, "--version"], "3.11.15")
            destination = Path(os.environ["HERMES_HOME"]) / "profiles/prd-workbench"
            if not destination.is_dir():
                subprocess.run(["hermes", "profile", "create", "prd-workbench", "--no-skills"], check=True)
            subprocess.run([python, str(ROOT / "scripts/configure-profile.py"), str(ROOT), str(destination)], check=True)
            if component == "profile":
                print("Dedicated profile configured; default profile and DPM runtime unchanged.")
                return
            read_secret("API_SERVER_KEY", "PRD_HERMES_API_KEY_FILE")
            read_secret("PRD_PLUGIN_KEY", "PRD_PLUGIN_KEY_FILE")
            read_secret("PRD_MODEL_API_KEY", "PRD_MODEL_API_KEY_FILE")
            provider = os.environ.get("PRD_MODEL_PROVIDER", "custom:workbench")
            if provider in {"openai", "openrouter"}:
                os.environ[{"openai": "OPENAI_API_KEY", "openrouter": "OPENROUTER_API_KEY"}[provider]] = os.environ["PRD_MODEL_API_KEY"]
            os.environ.update(API_SERVER_ENABLED="true", API_SERVER_HOST="127.0.0.1", API_SERVER_PORT="8643", API_SERVER_MODEL_NAME="prd-workbench")
            os.execvp("hermes", ["hermes", "-p", "prd-workbench", "gateway", "run"])
        else:
            raise ValueError("Unknown component")
    except Exception:
        sys.exit("Native operation failed: check pinned runtimes, literal environment configuration and secret files. No secret values printed.")


if __name__ == "__main__":
    main()
