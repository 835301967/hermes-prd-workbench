# Open-source decisions

Date: 2026-10-12. These decisions implement the accepted open-source plan and the user's exclusion of unrelated DPM integration.

1. Keep the seven implemented modes, modular Java monolith, MySQL, local uploads and independent Profile. No additional infrastructure or second business decision engine.
2. Use neutral `Hermes PRD Workbench` and `org.hermesprd.workbench` as code namespace; this is not an upstream endorsement or proof of ownership of a domain/Maven namespace.
3. Move backend/frontend to apps/, synthetic fixtures to tests/, executable deployment to deploy/, public docs into purpose-based sections. Flyway resources are the only migration source; duplicate SQL and dangerous root/global-host scripts are excluded.
4. Archive original internal material outside the source tree before removing it. This includes customer evidence, private environment templates, historical internal planning, AI work memory, prototypes and DPM proposals. Neither an ignored directory nor .gitignore makes old Git history safe to publish.
5. Default to portable single-file HTML. Remove the private scaffold protocol rather than rename it. Optional safe text attachments remain a formal result field without compilation guarantees.
6. Parameterize model URL/provider/name and secret files. Compose fetches the exact upstream Hermes commit with its frozen lockfile. Local DPM changes are outside scope and are not patched, maintained or used as an installation prerequisite.
7. Do not execute generated code in the Agent process security domain. Static structure and node --check only; explicit dependency failures and behaviorProof=false. Dynamic browser behavior has a separate acceptance gate.
8. Isolate new-tab HTML as well as inline preview. Strict/sanitized Mermaid, explicit administrator bootstrap and default-off bounded telemetry form the public defaults.
9. This first public database chain is fresh-install only. Removing personal authorization changes migration checksums; existing internal installations must not point at it. Never repair away the mismatch.
10. Non-core security dependencies: Axios 1.7.2 → 1.20.0, DOMPurify 3.1.6 → 3.4.16, Element Plus 2.7.6 → 2.14.7, Vitest 1.6.0 → 3.2.6. Happy DOM is removed from both Agent diagnostics and frontend tests; tests now use JSDOM 30.1.2 because the latest DOMPurify/Happy DOM pair failed Markdown/SVG preservation tests. The test worker uses Tinypool 2.2.0, and patched lodash-es/source-map-js/KaTeX are locked with explicit overrides. All 89 frontend tests pass with these changes. Core frozen Vue/Vite versions remain unchanged; their outstanding advisories and image dependencies block public release pending a separately reviewed baseline/patch decision. Audit presence is not proof of exploitation: Vue server-renderer SSR, Vitest API/UI and Vite development/platform conditions need reachability assessment.
11. Apache-2.0 is the project license selected from the accepted plan; preserve upstream MIT and other dependency terms. Actual publication rights for inherited code/Prompt/assets need owner verification, not an AI assertion.
12. Create a new public repository from an audited clean source snapshot. The user later authorized committing, pushing and merging this unreleased candidate into the existing private repository after checks. No public publishing, remote visibility change, force push, history rewrite or credential revocation is authorized. Historical/external exposure remains a separate gate.

References: [Node VM safety](https://nodejs.org/download/release/v22.23.1/docs/api/vm.html), [Happy DOM advisory](https://github.com/capricorn86/happy-dom/security/advisories/GHSA-96g7-g7g9-jxw8), [GitHub sensitive-data handling](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository), [Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/).

13. Source export uses an explicit reviewed file manifest, rejects symlinks and nested secret/runtime files, strips host owner metadata, and normalizes archive time/modes/gzip headers. New paths require a deliberate manifest refresh plus independent review.
14. Native Windows secret setup is rejected; Windows users operate within WSL2 on its Linux filesystem. Compose on macOS uses the explicit amd64 MySQL image with emulation.
15. Browser acceptance found mobile selector specificity retained the desktop grid; the mobile workspace is now an explicit single-column layout, with a full-height task drawer and readable grid of mode buttons.

16. Final TECH_SPEC acceptance revealed local/view contracts, structured index intent and textual error codes. Schema, display and export now preserve these shapes without inventing a database/HTTP transport or mutating results. jsonschema 4.25.0 is a CI/test dependency for formal contract regression.
17. V013 retires nine fixed system changelog seeds and preserves operator entries; applied V010 checksums remain unchanged. Public candidate notes explicitly remain unreleased.

## Preview authorization and security baseline — 2026-10-13

The author confirmed sole development and publishing rights, Apache-2.0, neutral branding, a clean new public repository while the historical repository remains private, necessary security upgrades with recorded regression/review, and a fresh-install preview limited to verified platforms. Publication after passing checks is explicitly authorized; concrete unresolved risks require a separate disposition. These confirmations supersede the earlier candidate-only authorization and frozen core dependency decision above.

The public runtime now uses the security baseline in config/versions.env and the separately hash-locked Hermes API dependencies in integrations/hermes-runtime, excluding messaging extras. It no longer uses the upstream frozen dependency lock or Tinypool override described in the earlier candidate decisions. The full upgrade and residual review are recorded in security-release-decision.md. The maintainer confirmed public contact 835301967@qq.com.
