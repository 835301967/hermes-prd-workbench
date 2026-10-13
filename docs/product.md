# Product requirements — public preview

| ID | Current requirement |
| --- | --- |
| PRD-01 | A user can register/login and manage their own tasks and uploaded MD/TXT/DOCX/XLSX materials, including Chinese filenames and pasted text. |
| PRD-02 | REVIEW, GENERATE, DEMO, POC, ESTIMATE, TEST_CASE and TECH_SPEC run through real Hermes tools and persist parseable results; only four task states exist. |
| THIN-REQ-01 | Generated PRD has a separate editable current draft with optimistic save and unsaved-change protection; original Agent result stays immutable. |
| THIN-REQ-02 | Users explicitly record review issue decisions; the platform does not silently edit the PRD. |
| THIN-REQ-03 | Subsequent review receives prior user decisions as internal read-only material. |
| THIN-REQ-05 | Demo can include optional narration and explicit local preview text replacements, keeping the stored original result intact. |
| THIN-REQ-07 | Single-instance experiment boundary, limited tools and minimal JSON/artifact validation; no evidence/business conclusion re-judgment. |
| OSS-REQ-01 | No company/default private model gateway, host scaffold, personal privilege or branded code namespace. |
| OSS-REQ-02 | Fresh self-hosting needs one setup/start/doctor lifecycle; preserve data when stopping. |
| OSS-REQ-03 | Public documents describe implemented behavior and distinguish unreleased validation status. |
| OSS-REQ-04 | Secrets and private materials are excluded from Git/build/release artifacts; generated code does not execute with credentials. |
| OSS-REQ-05 | License, contributions, security reporting, versioning, CI and release gates are documented. |

Users provide a tool-capable model service. Missing credentials, unavailable models, invalid JSON and missing required files fail explicitly; no fabricated results or automatic Java repair. Failed runs require manual rerun. A successful static diagnostic does not establish business correctness.

DEMO defaults to self-contained HTML. ERP/CRM/finance and lightweight presets are examples, not official company design specifications. Safe `package_files` attachments remain optional; no private application import or Vue compilation guarantee. POC is static hash-route HTML/CSS/JS with in-memory synthetic data, not a deployable business backend.

Estimates use explicit assumptions and a versioned development/self-test example scope; users can override scope. The product does not certify FPA, fix a team's speed or enforce an industry rate through Java.

Accounts remain local with USER/ADMIN roles and task ownership. Explicit local bootstrap creates an administrator only when none exists; normal registration never grants ADMIN. Browser telemetry is off by default. Run failure monitoring remains available from the run table.

Future SSO, multi-tenancy, collaboration, object storage, distributed jobs, automatic retries, engineering code builds and enterprise integration are outside this release.
