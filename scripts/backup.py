#!/usr/bin/env python3
"""Explicit private backup; no restore/delete operation and no password in argv."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

from workbench_cli import ROOT, load_environment


def backup(environment, output):
    load_environment(environment) # Reject shell interpolation; never echo its contents.
    output.mkdir(parents=True, exist_ok=False)
    output.chmod(0o700)
    command = ["docker", "compose", "--env-file", str(environment), "-f", str(ROOT / "compose.yaml")]
    query = "MYSQL_PWD=$(cat /run/secrets/PRD_DB_PASSWORD) mysql -h127.0.0.1 -uprd_workbench -Dprd_workbench -Nse \"SELECT DATABASE(); SELECT COUNT(*) FROM agent_run WHERE status IN ('PENDING','RUNNING')\""
    result = subprocess.run(command + ["exec", "-T", "mysql", "sh", "-c", query], capture_output=True, text=True, check=True)
    if result.stdout.strip().splitlines() != ["prd_workbench", "0"]:
        raise ValueError("Verify target schema and finish active runs before backup")
    subprocess.run(command + ["stop", "web", "backend", "hermes"], check=True)
    try:
        dump = "MYSQL_PWD=$(cat /run/secrets/PRD_DB_PASSWORD) mysqldump -h127.0.0.1 -uprd_workbench --single-transaction --no-tablespaces --set-gtid-purged=OFF prd_workbench"
        with (output / "database.sql").open("wb") as handle:
            subprocess.run(command + ["exec", "-T", "mysql", "sh", "-c", dump], stdout=handle, stderr=subprocess.DEVNULL, check=True)
        with (output / "uploads.tar").open("wb") as handle:
            subprocess.run(command + ["run", "--rm", "--no-deps", "-T", "--entrypoint", "tar", "backend", "-C", "/data", "-cf", "-", "uploads"], stdout=handle, stderr=subprocess.DEVNULL, check=True)
        manifest = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in output.iterdir() if path.is_file()}
        (output / "manifest.json").write_text(json.dumps({"schema": "prd_workbench", "sha256": manifest}, indent=2) + "\n")
        for path in output.iterdir():
            path.chmod(0o600)
    finally:
        print("Backup operation ended; services remain stopped. Inspect private backup results, then explicitly start when ready.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        backup(args.env_file.resolve(), args.output.resolve())
    except Exception:
        raise SystemExit("Backup failed; inspect local state and keep prior backups. No credentials printed.")
