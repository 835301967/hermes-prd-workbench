# Repository instructions

## Product boundaries

- This is an independent, single-instance Hermes PRD workbench. Do not add legacy compatibility, dual writes, microservices, queues, distributed leases, idempotency infrastructure or automatic retry/recovery.
- Hermes decides business conclusions, priorities and document organization. The control plane validates run/mode, basic JSON/size and minimum artifact structure; do not add a second model, business rule engine or silent result repair.
- Keep original results immutable and user drafts/feedback separate. Run states are PENDING/RUNNING/SUCCEEDED/FAILED; startup fails hung RUNNING work and users rerun manually.
- Hermes credentials never enter the browser. Product tools cannot access arbitrary host files, terminals, browsers, networking, memory, delegation or skill mutation.
- Generated HTML is previewed only in an iframe with `sandbox="allow-scripts"` and no `allow-same-origin`, including new-tab wrappers. Do not execute generated JS in Node VM or Happy DOM. Raw downloads remain separate artifacts.

## Frozen baseline and structure

Use config/versions.env: JDK 21, Maven 3.9.16, Spring Boot 3.5.16/MVC, MyBatis-Plus 3.5.7, MySQL 8.4.11, Vue 3.5.43, Vite 6.4.4, TypeScript 5.9.3, Node 22.23.1, pnpm 11.9.0, Hermes 0.16.0 and Python 3.11.15. Security dependency changes need a recorded decision and affected regression.

Backend is apps/backend; frontend is apps/web; integration is integrations; contracts is the formal OpenAPI/Schema source. Flyway under apps/backend/src/main/resources/db/migration is the only executable migration source. No Nacos, Spring Cloud, Redis, MinIO, Swagger UI or external Actuator.

## Development gates

1. Before behavior changes read README, relevant docs, contracts, migrations and tests. Map requirements, code and test IDs. Inspect branch/HEAD/workspace and actual versions, ports, health and schema; do not assume previously running services remain ready.
2. Never modify unrelated databases, namespaces, buckets, services, default Hermes profiles or DPM installations. Local acceptance uses an isolated `prd_workbench` schema and ports 18100/8643/5174; avoid existing services and prototype addresses.
3. Update producers, consumers, contracts, tests and docs together. Credentials, uploaded documents, complete prompts and private model responses must not enter logs, Git, screenshots or reports.
4. Run applicable unit/contract/interaction tests, type/build/format checks, git diff --check and sensitive scans. Never claim unexecuted checks passed.
5. Obtain independent review by a person/agent who did not implement the change; if alone, conduct a distinct review round over the full diff and call chain. Review correctness, exceptions, boundaries, tests, documentation and requirement/contract/DDL consistency. Close P0/P1; P2 needs a fix or approved disposition, followed by affected regression and re-review.
6. Database changes require a verified target and backup/recovery point, display the versions to execute, then real local execution and Flyway/table/index/constraint checks. Never mutate an internal applied checksum or use repair to conceal a new public migration chain.
7. Functional completion requires real MySQL, dedicated Hermes, backend and formal Vue startup; SELECT 1/schema, Hermes detailed health/capabilities/tool list; real synthetic Chinese materials, Agent submit and persisted results; real browser clicks including success/empty/validation/failure/refresh/manual rerun, at 390/1366/1440/1920 widths. Mocks/API-only/prototypes are insufficient.
8. Report files, traceability, independent review, commands/results, SQL execution, Nacos applicability, environment, browser evidence, screenshot/log locations, risks and Git status. Missing gates mean work remains in progress.

## Open-source hygiene

- Public defaults cannot depend on a company model gateway, personal administrator, private scaffold or company branding. Preserve legally required upstream attribution.
- Do not publish .env, runtime files, AI memory, customer evidence, credentials or host details. Use allowlisted release/build contexts and synthetic fixtures.
- Nacos record: “不适用：当前冻结基线不接入 Nacos”. Introducing it would require a separate architecture decision and independent namespace, never reuse an unrelated project.
- Do not publish, force-push, rewrite history, delete volumes, or change repository visibility without explicit user authorization. Working-tree cleanup does not revoke old disclosures.
