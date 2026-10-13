# Dedicated Hermes API runtime

Source stays at the exact upstream commit in config/versions.env. The workbench runtime uses a separately reviewed, hash-locked dependency set: core dependencies plus aiohttp for the API adapter, without messaging extras. Explicit security overrides change dependencies, not upstream source; it is not an unmodified upstream dependency environment. Native installs must use this same isolated environment.

Regenerate only for a reviewed dependency change, using the fixed upstream pyproject and the recorded uv version:

```sh
uv pip compile upstream/pyproject.toml integrations/hermes-runtime/api.in --overrides integrations/hermes-runtime/security-overrides.txt --python-version 3.11 --python-platform linux --generate-hashes --no-header --no-annotate --output-file integrations/hermes-runtime/requirements.lock.txt
```

The Linux lock is used for Docker containers on the verified macOS arm64 host. It is not proof of native macOS/Windows compatibility. Install with require-hashes; install the checked upstream source separately with no dependency resolution. Never patch the host's default Hermes or DPM environment.
