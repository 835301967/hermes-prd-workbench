# Preview preparation and validation report

The initial public version is 0.4.0-beta.1, licensed under Apache-2.0, with fresh installation only. The author confirmed personal publication rights and authorized a clean new public repository; the original repository remains private. The author confirmed browser acceptance and accepted the two restricted-preview security residuals on 2026-10-13, authorizing publication after the remaining final checks.

## Scope

Neutral Hermes PRD Workbench name and org.hermesprd Java namespace; apps/backend, apps/web, integrations, contracts, deploy and synthetic tests. Company-specific defaults, internal documents, AI memory, private scaffolds and customer evidence are excluded. Local DPM/Hermes integration is outside scope.

Secrets use ignored controlled files, explicit administrator bootstrap and isolated volumes. Public model settings are operator-configured. Generated HTML uses opaque-origin sandbox previews; diagnostics remain static. Seven Agent modes and immutable results/current drafts remain separate. Nacos: 不适用：当前冻结基线不接入 Nacos.

## Security and versions

See [recorded upgrade decisions](security-release-decision.md) for exact dependency changes, image digests, advisories, regressions and residual risk dispositions. First publication contains source/build instructions and inventories; no prebuilt container images or compiled binaries.

## Declared platform

macOS arm64 with Docker Desktop Linux containers only. Linux amd64, Windows WSL2 and native deployments are not declared supported. Existing internal installations have no in-place migration guarantee.

## Evidence

Final local regression passed: 227 backend tests, 89 frontend tests, 60 Hermes plugin tests and 15 deployment/contract/inventory tests; frontend type checking/build, public manifest, documentation and Git whitespace checks passed. No tests were skipped in the backend suite. The frontend build retains its nonblocking large-chunk warning.

Real acceptance used an isolated fresh MySQL 8.4.11 database, dedicated Hermes 0.16.0, the rebuilt Java control plane and formal Vue application. V001–V013 applied successfully; all nine tables were inspected. Synthetic Chinese Markdown, DOCX and XLSX imports succeeded; malformed Office files/multipart returned generic 400, oversized uploads returned 413, and overlong login credentials returned 401. Both XLSX exporters succeeded after removal of fontconfig/Expat.

All seven Agent modes produced persisted results that passed their formal JSON schemas. An earlier REVIEW with invalid optional list items remains immutable; its manual rerun passed after the minimum structural validation and producer instructions were corrected. A controlled Hermes outage produced a persisted FAILED run. A supplementary explicit manual API rerun succeeded, persisted a schema-valid result and preserved the original FAILED record; browser interactions were confirmed separately by the author. No silent result repair or automatic business retry was added.

Backup and isolated restoration passed on the new runtime: all nine table row counts, 13 Flyway checksums and 35 uploaded-file hashes matched. Restore containers were stopped with volumes retained; the primary environment restarted and doctor passed. A real backend replacement changed its IP; the existing Nginx container resolved the service name and returned health UP without manual restart.

Independent review covered the full substantive diff, exception/consumer call chains, contracts, migrations, tests and documentation. Findings for the Docker context, malformed Office errors and password input truncation were fixed and re-reviewed; no unresolved P0/P1/P2 code findings remain. The author confirmed the upgraded formal Vue browser checklist at 390/1366/1440/1920 widths: success/empty/validation/failure, refresh, manual rerun, data-model/API tabs, preview changelog, generated-page/new-window previews and untruncated 73-character password rejection. This is human acceptance evidence; no new automated browser screenshots are claimed. The author also explicitly accepted the restricted-preview TLS/zlib residuals. Cloud CI passed source checks, all test/build gates, snapshot generation and dependency inventories on the clean new repository. Its archive matches all 307 audited files byte for byte.

The public tree contains 307 explicitly allowlisted source files. Four final image identities passed known-secret checks including decompressed retained layers for the six acceptance secrets. The frontend registry dependency audit reported zero findings. Runtime findings are assessed individually in the security decision; the release does not claim all images have zero vulnerabilities. The staged source archive, clean Git history and release inventories passed Gitleaks with zero findings; the six acceptance secrets were also checked against their payloads. Regenerate/rescan if files change before publication. Private evidence, model payloads, credentials, host paths and internal history are excluded from the release.
