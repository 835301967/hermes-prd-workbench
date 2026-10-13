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

## Post-preview CORS regression correction

The open-source Compose proxy introduced a deployment regression: its Host normalization discarded the browser's external port. A browser POST with Origin on8180 was then treated as cross-origin by Spring and rejected with403, even while doctor/GET requests without Origin succeeded. The earlier native-development CORS allowlist implementation was preserved; the new proxy path caused the regression. The preview's developer-port/API-only acceptance failed to detect this deployed-browser case.

The proxy now preserves the original Host header, including port, while leaving Origin unchanged. Explicit CORS allowlists, session/administrator enforcement and generated-frame opaque origins remain intact. New CorsRequestTest coverage checks same-origin/default/custom ports, the stripped-port reproduction, foreign/null origins, allowed and denied development preflights. Real Docker Nginx against the existing backend/MySQL/Hermes passed localhost/127.0.0.1 on8180 and a separate custom published port: login200, task creation201, refresh200, invalid/absent credentials401, foreign/null/mismatched-port origins403 and internal routes404.

No product Java change, version upgrade or executable migration is required. Existing installations update Git and rebuild/recreate only Web using the same environment/project; volumes remain preserved. Windows/WSL2 instructions describe the update path, but a local macOS Docker transport reproduction is not a full WSL2 platform certification. Real same-origin Agent upload/submit/persisted REVIEW/PUT feedback/refresh also passed, and a registered USER remains denied from administrator endpoints. The complete local suite passed234 backend,89 frontend,60 plugin and15 deployment/contract/inventory tests. CI adds a real Nginx transport test using the shipped configuration and a synthetic upstream to catch future port stripping. Final browser and independent-review evidence are recorded separately.

## PRD diagram label correction

The open-source SVG sanitizer correctly forbids foreignObject, but the preview/export configuration only set the legacy flowchart.htmlLabels option. Mermaid12 common node renderers could still emit HTML labels; sanitization then removed the label nodes and left empty flowchart boxes. The new real-library regression failed on missing Chinese node text before the fix.

Preview and Word export now use one trusted configuration factory with root-level htmlLabels=false, producing native SVG text while preserving strict security, size/edge bounds, rejected diagram directives and SVG sanitization. Flowchart node/edge text and sequence actors/messages/notes/alternatives/loops are covered using real Mermaid. The tests stub JSDOM geometry only and do not claim pixel-layout or PNG visual verification. A synthetic PRD fixture is provided for browser/export acceptance. Original generated Markdown and user drafts remain unmodified.

After both fixes, the complete local suite passed234 backend,93 frontend,60 plugin and15 deployment/contract/inventory tests, plus the separate real Nginx transport test. The reviewed source manifest now includes313 files. Browser/WSL2 and Word/PNG visual confirmation remain pending; the passing SVG-label tests do not substitute for those checks.
