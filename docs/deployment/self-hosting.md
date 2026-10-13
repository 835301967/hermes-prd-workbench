# Self-hosting

## Compose installation

Use a fresh checkout and a new project-owned database. Install Docker with Compose v2 and Python 3.10+. Run `./workbench setup`, then `./workbench config`, `./workbench start`, `./workbench doctor`. On Windows run setup and all commands inside WSL2 on its Linux filesystem with Docker Desktop integration enabled. Native Windows setup is rejected because chmod cannot enforce a private Windows DACL. Docker Desktop must use Linux containers.

setup prompts for model URL/name/key and an initial administrator. It preserves existing secrets. Model URL cannot contain credentials. Use a Chat Completions endpoint with tools; custom:workbench, openai and openrouter are the configured provider choices. Other protocols/providers need explicit adapter/dependency testing; a syntactically valid setting does not establish compatibility.

Non-interactive setup creates only local infrastructure secrets; it never invents a model Key or administrator password. For automation, provide model-api-key and admin-password files through the deployment platform, plus non-secret model settings. Never put a Key in an argument, image build arg or source file.

The default Web entry is http://127.0.0.1:8180. MySQL, backend and Hermes have no published host ports. The backend connects to schema prd_workbench as a project user; Flyway completes before the explicit bootstrap bean and server readiness. Existing administrators are preserved; an existing ordinary username is not silently promoted. Registering a user does not grant administrator rights.

File-backed Compose secrets are mounted to each recipient only. Source secret directories are 0700; setup creates files readable inside non-root containers (0444). Other host users cannot traverse the private source directory. Do not relocate those files into a shared directory. Native-only platform secret management can use 0600 with the executing user's identity. `.env` is 0600 and should contain only non-secret settings. Compose file mounts do not reliably apply declared UID/mode; do not assume they fix host permissions.

Containers use project-specific volumes for MySQL, uploads and Hermes state. `./workbench stop` never deletes volumes. Do not use `docker compose down -v` unless intentionally discarding all data with a verified backup.

MySQL8.4.11 LTS uses a fixed multiarchitecture image digest. This preview is a fresh-install release; never mount an existing8.0 data volume into it. Back up and migrate existing installations through a separate reviewed plan.

## Readiness and troubleshooting

start builds and launches services, then waits for Web and /api/v1/health. doctor checks the same endpoints; database readiness includes SELECT 1 and the schema name through backend health. For Agent acceptance also inspect /health/detailed, authenticated /v1/capabilities and `hermes -p prd-workbench tools list --platform api_server` within the container. Verify exactly the intended tools; do not expose Hermes publicly for debugging.

`./workbench logs` shows local operational logs. Review/redact before sharing: Hermes can retain private conversations within its private volume; that volume is never a public artifact. If startup fails, inspect component readiness, secret-file readability, occupied Web port and provider credentials. Invalid model/configuration must fail explicitly, not show fabricated analysis.

### HTTP403 / Invalid CORS request after login

Use the same Web origin for the page and API: for example `http://localhost:8180` with relative `/api/v1` requests. The Compose Nginx proxy preserves the complete browser Host header, including a custom published port. Dropping the port makes Spring treat the same-origin login or write request as cross-origin and reject it; health checks without an Origin header can still pass. This transport issue can occur on WSL2 as well as other Docker hosts.

For an existing checkout containing the old proxy configuration, run inside the checkout's WSL2/Linux filesystem:

```bash
git pull --ff-only
docker compose build web
docker compose up -d --no-deps web
./workbench doctor
```

With a custom environment file use the same `--env-file` and project name used for the original start on both Compose commands. These commands recreate only Web and preserve database/upload/Hermes volumes. `doctor` is a readiness check, not a browser CORS test; then test login and a write action in the browser. No new database migration is needed.

Native development cross-origin requests still use the explicit `PRD_CORS_ALLOWED_ORIGINS` list. Do not use `*`, strip/rewrite Origin, disable browser security or disable server CORS checks to work around a403. Requests from untrusted or opaque (`null`) origins remain rejected. A403 with application code FORBIDDEN on an administrator endpoint is a role-denial response and must not be bypassed.

Loopback binding is intentional. Serving a trusted network requires an explicit PRD_WEB_BIND change; Internet exposure additionally requires a separately reviewed TLS/auth/proxy deployment. No TLS production guarantee is implied by the local example.

## Backup, upgrade and restore

Stop new user operations and wait for active runs to finish. Use `scripts/backup.py` to make a local logical database dump and uploads archive while backend/Hermes are stopped. The backup directory is private and outside any public release. It contains user materials and must not be attached to issues. Configuration, secret files and model-provider settings require a separate encrypted administrative backup.

Restore only into an empty project-owned database using a reviewed release with the same migration lineage. Verify dump hash, database/schema identity and free space; restore SQL via mysql stdin using a controlled file/env secret, restore upload paths, then start and check history, SELECT 1, tables, current drafts, historical results and manual failed-run recovery. Rehearse before upgrading; do not run automatic destructive restore commands.

This first public preview uses a new migration checksum lineage. Internal pre-open-source installations are excluded from in-place upgrade. Keep a separate backup and explicit migration plan; never edit applied migration history or use Flyway repair to mask differences.

## Platform evidence

Support is a test result, not a file claim. This preview declares macOS arm64 and Windows 11 WSL2 with Docker Desktop Linux containers after their recorded acceptance. Linux amd64 is not yet verified or declared supported. Native development is a contributor workflow, not an additional supported deployment. Native Windows Hermes is not supported here. Nacos: 不适用：当前冻结基线不接入 Nacos.
