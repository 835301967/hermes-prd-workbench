# Architecture and baseline

The system consists of one Spring MVC control plane, one Vue frontend, MySQL and a dedicated Hermes runtime. Compose packages these existing components; it is not a microservice redesign. Browser requests go through the Web proxy to /api/v1; Hermes calls only /internal/v1. The public proxy never exposes /internal.

| Component | Frozen baseline |
| --- | --- |
| Backend | Java 21, Maven 3.9.16, Spring Boot 3.5.16, MyBatis-Plus 3.5.7 |
| Data | MySQL 8.4.11, Flyway versioned migrations, generated local paths |
| Frontend | Node 22.23.1, pnpm 11.9.0, Vue 3.5.43, Vite 6.4.4, TypeScript 5.9.3 |
| Agent | Hermes 0.16.0 at config/versions.env commit, Python 3.11.15 |

Happy DOM is removed from both frontend tests and production diagnostics. Tests use JSDOM 30.1.2; diagnostics never execute generated JS. Security dependency decisions and remaining advisories are recorded in docs/maintainers/security-release-decision.md. Hermes source is fixed, but its workbench dependency environment uses a separate reviewed hash lock. No Hermes local DPM changes are packaged or maintained here.

Eight tables are present: prd_task, prd_source, agent_run, agent_result, prd_user, prd_session, prd_telemetry_event and prd_update_log. The only migration source is apps/backend/src/main/resources/db/migration, versions V001–V013. The public chain is for a fresh schema; internal applied checksums are not compatible. baseline-on-migrate is false to reject unrecognized nonempty databases. Public mode constraints support the seven implemented modes.

Runs execute asynchronously within one Java process. Startup fails leftover RUNNING work; no leases/heartbeats/automatic retry. HermesRunsClient is the only Hermes HTTP client. Plugins use current-run endpoints, not direct DB/filesystem access. Uploaded disk paths are server-generated; the current PRD draft lives in database content, separate from the original generated result and file creation snapshot.

The dedicated Profile declares 22 tools: list_sources/read_source/submit_result; three Demo tools; four each for POC, PRD, test cases and technical specifications. Terminal, arbitrary files/network, browser, memory, delegation and skill mutation are disabled. Model output organization remains Hermes' responsibility.

Code diagnostics parse syntax with node --check under a minimal environment and perform static file/binding checks. They never eval/VM generated scripts. Dynamic behavior requires real browser acceptance. Preview and new-tab wrappers use allow-scripts-only frames with restrictive resource CSP. Mermaid uses shared trusted root-level htmlLabels=false settings for previews and Word export, strict rendering, configuration-override rejection and SVG sanitization. Labels are native SVG text; foreignObject, active handlers and external resources remain forbidden. Oversized/unsupported diagrams retain source. None of these operations rewrites stored Agent HTML/Markdown.

Nacos: 不适用：当前冻结基线不接入 Nacos. Redis, MinIO, queues and additional databases are also excluded.
