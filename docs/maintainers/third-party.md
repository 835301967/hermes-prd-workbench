# Third-party provenance

The project license does not replace third-party licenses. Before publication maintainers verify authority over inherited code, Prompts, fixture logic and UI assets. An accepted implementation plan is not proof of legal ownership. The new favicon is a simple original geometric SVG; unreviewed old identity images and customer screenshots are excluded.

| Component | Source | License/source of exact dependency version |
| --- | --- | --- |
| Hermes 0.16.0 | NousResearch/hermes-agent, manifest commit | MIT; LICENSE retained in runtime image |
| Spring Boot/Spring, MyBatis-Plus, Flyway, Apache POI | Maven Central coordinates in apps/backend/pom.xml | Dependency POM/license metadata and generated Maven SBOM |
| Vue/Vite/Element Plus/Mermaid/DOMPurify/JSZip/docx and other frontend libraries | npm versions in apps/web/pnpm-lock.yaml | Package LICENSE metadata and generated inventory |
| MySQL | Official MySQL 8.4.11 image, fixed multiarchitecture digest | Vendor distribution terms; server/connector rights reviewed separately |
| Python/Node/JDK/Nginx and image layers | Dockerfiles and config/versions.env | Upstream images and bundled notices; inventory by release image digest |

Do not assume every dependency uses Apache/MIT or infer a license from its package name. Generate dependency metadata for the actual release, inspect missing/ambiguous/copyleft entries and preserve any required text/NOTICE. Hermes optional dependencies and system layers must be included in the final release inventory, not only direct Maven/npm dependencies.

No source code from a local DPM fork or private host scaffold is distributed. Model services are operator-selected external services, not a bundled licensed model. Copyright, trademarks and model terms remain their owners'.

## First preview distribution

The author confirmed personal publication rights on2026-10-13 and selected Apache-2.0 for original project code. First release assets contain source, checksums and dependency/license inventories; no compiled JAR, frontend bundle, MySQL archive or prebuilt container image is published. Referencing an official upstream image does not relicense it.

Connector/J selected by the backend BOM is GPLv2 with the [Universal FOSS Exception](https://oss.oracle.com/licenses/universal-foss-exception/), not Apache-2.0. The exception and the server license must be reviewed separately. Any future binary/image release must preserve exact-version license/exception texts and applicable source obligations for all bundled libraries and system layers. Missing license identifiers in an SBOM require investigation; they are not an automatic approval.

Hermes messaging extras are excluded from the workbench hash lock; the API adapter explicitly retains aiohttp. The core source retains its upstream MIT attribution. Runtime dependency overrides and their evidence are documented in integrations/hermes-runtime/README.md and the security decision.

Exact metadata review: khroma2.1.0 omits a package.json license identifier, but its installed `license` file and [upstream v2.1.0 license](https://github.com/fabiospampinato/khroma/blob/v2.1.0/license) explicitly provide MIT. Preserve the raw inventory’s Unknown marker with this review disposition. Logback offers EPL-2.0/LGPL-2.1 alternatives; JSQLParser Apache-2.0/LGPL-2.1; jakarta.annotation includes EPL-2.0/GPL with classpath exception. Source-only publication does not bundle these libraries or replace their terms. Public inventory generation removes local package paths and excludes the previous manifest from its own hash inputs.
