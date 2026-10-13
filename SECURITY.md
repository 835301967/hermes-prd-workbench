# Security policy

Do not put vulnerabilities, credentials, private documents or exploit output in public issues. Report privately through the repository's GitHub **Report a vulnerability** feature when enabled. Maintainers must enable and test that channel before the first public release; do not invent a contact address or assume the channel is already active.

Report affected version, a minimal synthetic reproduction, expected/actual behavior and impact. Never include live secrets. Maintainers acknowledge reports, verify impact, prepare a fix and coordinate disclosure with the reporter; no response-time SLA is promised.

The initial supported line is 0.4.x after release. Self-host in a trusted environment. Model providers are external recipients of uploaded content. Generated code diagnostics are static only; HTML previews use opaque-origin sandbox frames. Downloaded artifacts are untrusted code and require user review before running outside the workbench.

If a real secret is exposed, revoke/rotate it first and assess repository history, releases, images, CI output and external copies. Deleting the current file is insufficient. See [release gates](docs/maintainers/releasing.md).

Maintainer: [YuxForge](https://github.com/YuxForge). If the private reporting feature is unavailable, contact [835301967@qq.com](mailto:835301967@qq.com) privately; do not post sensitive details publicly.
