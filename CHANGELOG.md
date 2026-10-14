# Changelog

## Unreleased

## 0.4.0-beta.2 — preview

- Use shared, trusted Mermaid root-level SVG-text configuration for PRD previews and Word export, preserving Chinese node labels through strict SVG sanitization; add real Mermaid flowchart/sequence regression coverage.

- Preserve the browser Host port through the Compose Nginx proxy, fixing HTTP403 Invalid CORS request on same-origin login and writes through default/custom Web ports. Keep explicit cross-origin restrictions and add positive/negative transport regression coverage.

- Record Windows 11 WSL2 browser and Word-export visual acceptance, add an English project home, synthetic-data product media, and public community entry points.

- Make Maven copy packaged Skill resources even when reproducible source archives carry epoch timestamps; release checks now start from a clean backend target.

## 0.4.0-beta.1 — preview

- Move applications to apps/backend and apps/web; centralize synthetic fixtures and public documentation.
- Replace company defaults and private host scaffolding with a portable HTML template and configurable model service.
- Explicit local administrator bootstrap; remove fixed personal SQL privileges and secret output.
- Static generated-code diagnostics, isolated new-tab preview, strict sanitized diagrams, opt-in bounded telemetry.
- Compose deployment, lifecycle commands, contribution/security/license documentation and release checks.

This is a fresh-install preview limited to macOS arm64 with Docker Desktop Linux containers. Validation evidence and release gates are recorded in docs/maintainers/change-report.md. Internal customer release notes are excluded.

- Patch application dependencies, lock Hermes API runtime dependencies with hashes, and use a fresh MySQL 8.4 LTS volume.
- Enforce BCrypt’s 72 UTF-8 byte boundary without silent password truncation.
