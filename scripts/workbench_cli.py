#!/usr/bin/env python3
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def load_environment(path):
    values = {}
    if path.is_file():
        for line in path.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            key, separator, raw = line.partition("=")
            if not separator or not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
                raise ValueError("Unsupported configuration syntax")
            parts = shlex.split(raw, comments=True)
            if len(parts) > 1 or "$" in raw or "`" in raw:
                raise ValueError("Use literal values in the environment file; shell expansion is not supported")
            values[key] = parts[0] if parts else ""
    return values


def secret_directory(environment):
    if os.name == "nt":
        raise ValueError("Run setup inside WSL2 to enforce POSIX secret permissions")
    directory = Path(environment.get("PRD_SECRETS_DIR", ".runtime/secrets"))
    if not directory.is_absolute():
        directory = ROOT / directory
    directory.mkdir(parents=True, exist_ok=True)
    directory.chmod(0o700)
    return directory


def write_secret(path, content, compose=True):
    if path.is_file() and path.stat().st_size:
        return False
    # Creation is private. Non-root Compose file mounts must be readable inside their container;
    # the enclosing 0700 directory prevents access by other host users.
    if path.exists():
        path.unlink()
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as handle:
        handle.write(content + "\n")
    path.chmod(0o444 if compose else 0o600)
    return True


def setup(path, non_interactive=False):
    values = load_environment(path) if path.exists() else load_environment(ROOT / ".env.example")
    directory = secret_directory(values)
    for name in ("hermes-api-key", "plugin-key", "db-password", "mysql-root-password"):
        write_secret(directory / name, secrets.token_hex(32))
    if not non_interactive:
        if values.get("PRD_MODEL_NAME") in {None, "", "configure-your-model"}:
            values["PRD_MODEL_BASE_URL"] = input("Model service base URL (with /v1): ").strip()
            values["PRD_MODEL_NAME"] = input("Tool-capable model name: ").strip()
        if not (directory / "model-api-key").exists():
            key = getpass.getpass("Model API key (hidden): ")
            if not key:
                raise ValueError("Model API key is required")
            write_secret(directory / "model-api-key", key)
        if not (directory / "admin-password").exists():
            values["PRD_BOOTSTRAP_ADMIN_USERNAME"] = input("Initial administrator username [maintainer]: ").strip() or "maintainer"
            password = getpass.getpass("Initial administrator password (at least 8 characters, at most 72 UTF-8 bytes): ")
            if (len(password) < 8 or len(password.encode("utf-8")) > 72) or password != getpass.getpass("Confirm password: "):
                raise ValueError("Administrator password validation failed")
            write_secret(directory / "admin-password", password)
    if not path.exists() or not non_interactive:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("# Local configuration; never publish this file.\n" + "\n".join(f"{key}={shlex.quote(value)}" for key, value in values.items()) + "\n")
        path.chmod(0o600)
    print("Controlled secret files prepared; existing secrets and configuration preserved. No secret values printed.")
    if non_interactive:
        print("Non-interactive setup does not invent model credentials or administrator passwords. Provide their secret files before start.")


def compose(path, arguments):
    if not shutil.which("docker"):
        raise ValueError("Install Docker with Compose v2 first")
    return subprocess.run(["docker", "compose", "--env-file", str(path), "-f", str(ROOT / "compose.yaml"), *arguments], cwd=ROOT, check=True)


def doctor(values, timeout=5):
    host = values.get("PRD_WEB_BIND", "127.0.0.1")
    if host == "0.0.0.0":
        host = "127.0.0.1"
    url = f"http://{host}:{values.get('PRD_WEB_PORT', '8180')}"
    try:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(url + "/api/v1/health", timeout=timeout) as response:
            result = json.load(response)
        if result.get("status") != "UP" or result.get("hermes") != "CONNECTED":
            raise ValueError("Control plane or Hermes is not ready")
        with opener.open(url, timeout=timeout) as response:
            if response.status != 200:
                raise ValueError("Web is not ready")
        print("Web, database-backed control plane, and Hermes connection ready at " + url)
        return True
    except Exception:
        return False


def main():
    parser = argparse.ArgumentParser(description="Hermes PRD Workbench local deployment")
    parser.add_argument("command", choices=["setup", "start", "stop", "doctor", "config", "logs"])
    parser.add_argument("--env-file", type=Path, default=Path(os.environ.get("PRD_ENV_FILE", ROOT / ".env")))
    parser.add_argument("--non-interactive", action="store_true")
    args = parser.parse_args()
    path = args.env_file.resolve()
    try:
        if args.command == "setup":
            setup(path, args.non_interactive)
            return
        values = load_environment(path)
        if args.command == "stop":
            compose(path, ["stop"]) # Never deletes volumes or data.
        elif args.command == "config":
            compose(path, ["config", "--quiet"]) # Do not dump interpolated environment values.
        elif args.command == "logs":
            print("Review runtime logs locally; redact them before sharing.")
            compose(path, ["logs", "--tail", "100"])
        elif args.command == "start":
            if values.get("PRD_MODEL_NAME") in {None, "", "configure-your-model"}:
                raise ValueError("Set model name and base URL first")
            directory = secret_directory(values)
            if any(not (directory / name).is_file() for name in ["model-api-key", "admin-password"]):
                raise ValueError("Model/admin secret files missing; run setup")
            compose(path, ["up", "-d", "--build"])
            for _ in range(60):
                if doctor(values):
                    return
                time.sleep(2)
            raise ValueError("Startup not ready; check local component logs and rerun doctor")
        elif not doctor(values):
            raise ValueError("Not ready: check database, backend, Hermes detailed health/capabilities and model settings")
    except (ValueError, OSError, subprocess.CalledProcessError):
        sys.exit("Operation failed. Check configuration syntax, Docker availability, model settings and local logs. Secrets are not printed.")


if __name__ == "__main__":
    main()
