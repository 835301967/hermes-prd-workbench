# Contributing

Read [AGENTS.md](AGENTS.md) and [development setup](docs/development/getting-started.md). Keep changes focused and respect the single-instance boundary. Start product ideas and setup questions in [Discussions](https://github.com/YuxForge/hermes-prd-workbench/discussions); use Issues for accepted work or reproducible defects. Discuss core version, contract, data migration and security-boundary changes before implementation.

Use synthetic materials and local secret files. Do not include customer documents, screenshots, keys or complete model prompts in issues, tests or PRs. Include behavior before/after, affected requirement/test IDs, commands actually run and remaining risks. Contract changes update both sides and docs.

Run `./scripts/check.sh`, then obtain independent review. Functional changes also need real Hermes and browser acceptance; unit tests do not replace that gate. Add a `Signed-off-by` line to contributions to certify the [Developer Certificate of Origin](https://developercertificate.org/); submit only material you have the right to license under Apache-2.0.
