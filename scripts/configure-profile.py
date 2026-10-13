#!/usr/bin/env python3
"""Render a dedicated profile using non-secret model settings. Never touch a default profile."""
import os
from pathlib import Path
import shutil
import sys
from urllib.parse import urlparse

import yaml


def configure(root: Path, destination: Path):
    if destination.name != "prd-workbench":
        raise ValueError("Only the dedicated prd-workbench profile may be configured")
    model = os.environ.get("PRD_MODEL_NAME", "").strip()
    url = os.environ.get("PRD_MODEL_BASE_URL", "").strip()
    provider = os.environ.get("PRD_MODEL_PROVIDER", "custom:workbench")
    parsed = urlparse(url)
    if not model or model == "configure-your-model" or parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("Set PRD_MODEL_NAME and a credential-free PRD_MODEL_BASE_URL")
    if provider not in {"custom:workbench", "openai", "openrouter"}:
        raise ValueError("Supported providers: custom:workbench, openai, openrouter (Chat Completions with tools)")
    config = yaml.safe_load((root / "integrations/hermes-profile/config.yaml").read_text())
    config["model"].update(default=model, provider=provider, base_url=url)
    config["providers"]["workbench"].update(api=url, default_model=model)
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "config.yaml").write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False))
    shutil.copyfile(root / "integrations/hermes-profile/SOUL.md", destination / "SOUL.md")
    plugin = destination / "plugins/prd-workbench"
    plugin.mkdir(parents=True, exist_ok=True)
    for path in (root / "integrations/hermes-plugin").iterdir():
        if path.name in {"__init__.py", "tools.py", "schemas.py", "plugin.yaml", "verify_demo.js"}:
            shutil.copyfile(path, plugin / path.name)
    # Remove obsolete executable diagnostic copied by earlier profile installers.
    (plugin / "verify_poc.js").unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        configure(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve())
    except Exception:
        sys.exit("Profile configuration failed; check model settings, dedicated path, and dependencies (no secrets printed).")
