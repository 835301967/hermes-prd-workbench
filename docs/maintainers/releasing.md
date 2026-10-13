# Release process

The0.4.0-beta.1 tree is a preview prepared under the author’s explicit publication authorization. Final technical gates and concrete residual risk dispositions must still pass before making the new repository public. Maintainers release only after the implementation evidence and below gates pass. Current internal Git history must not be made public; populate the new repository from the audited snapshot.

1. Confirm publication rights, Apache-2.0, upstream/third-party notices, dependency inventory/SBOM, model/asset source terms and neutral project name.
2. Close P0/P1 and disposition P2; independent review verifies code, contracts, DDL, docs and tests. Freeze exact versions/commit and record unavoidable security decisions.
3. Run scripts/check.sh with pinned runtimes, full standard secret/history scans, dependency/image vulnerability scans and manual binary/metadata checks. Inspect external releases, caches, forks and previous evidence; revoke/rotate real exposed secrets before history cleanup.
4. Rehearse the declared fresh Compose installation, real MySQL migrations and backup/restore, Hermes detailed health/capabilities/tool list and real synthetic seven-mode runs. Use formal Vue browser clicks and four viewport checks; publish platform support only with evidence.
5. Use scripts/package-release.py --output <private-output>/hermes-prd-workbench-0.4.0-beta.1.tar.gz. Before export, inspect added/removed paths and explicitly run `python scripts/audit-public-tree.py --refresh-manifest`; independently review `config/public-files.txt`. The exporter allowlists candidate files and adds manifest/hash, excluding .git, .env, runtime, private local files and build output. Inspect the extracted archive and every built image layer; a candidate source scan is not a full history scan.
6. Populate a new public repository from the audited snapshot. Maintainers explicitly approve creation/publication; do not force-push the internal repository or transfer its unreviewed history. Configure private security reports, contact channel, branch protections, default permissions and release ownership in the actual repository.
7. Tag the approved version, build through the protected workflow, attach source/hash/SBOM/notices and release notes. An unreleased candidate changelog is not proof a release exists.

CI uses read-only permissions for PRs and no model credentials. Real Agent acceptance is separately manually authorized using controlled secrets and synthetic files. Do not use privileged pull_request_target to execute untrusted code. Release packaging can be generated locally without publishing it.

Nacos: 不适用：当前冻结基线不接入 Nacos. This release also excludes Redis/MinIO, automatic recovery, arbitrary plugin extension and production scale claims.
