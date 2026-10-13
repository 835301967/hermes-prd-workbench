# Native development

Read AGENTS.md. Use the exact versions in config/versions.env. Version checks fail instead of silently accepting the system's Java 8 or a different Node/pnpm. Select JDK 21 with JAVA_HOME and PATH; install Node 22.23.1/pnpm 11.9.0 and Python 3.11.15 in controlled development paths.

Use the unmodified upstream Hermes 0.16.0 source at the manifest commit with the reviewed dependency lock in integrations/hermes-runtime. Follow that directory’s regeneration and installation instructions; the older upstream messaging-extra lock is not the public security baseline. The Docker runtime is the verified path. Native development is optional and is not a supported release platform. Do not import local DPM patches or modify installed Hermes profiles.

Create a fresh project schema with deploy/mysql/create-database.sql using a database administrator's controlled connection. Provision a prd_workbench user restricted to that schema; provide the password through PRD_DB_PASSWORD_FILE. No script changes global MySQL config or resets root.

Run `./workbench setup` with a new local config. Existing legacy .env syntax with shell expansion is deliberately rejected by the safe parser; preserve it outside the public checkout and create a clean config. PRD_ENV_FILE can select another literal environment file. No script sources/evals it as shell code.

```bash
./scripts/linux/check-env.sh
./scripts/linux/install-hermes-profile.sh
./scripts/linux/start-hermes.sh
# In another terminal:
./scripts/linux/start-backend.sh
# In another terminal:
./scripts/linux/start-frontend.sh
```

The dedicated profile lives under `.runtime/hermes-home/profiles/prd-workbench` by default; installed/default/DPM profiles are untouched. Model Key and internal credentials load from files into the Hermes process environment. Defaults are Hermes 8643, backend 18100 and formal Vue 5174, all loopback. Do not use a prototype or an unrelated old service for acceptance.

```bash
cd apps/backend
mvn test
cd ../web
pnpm install --frozen-lockfile
pnpm typecheck
pnpm test
pnpm build
```

From repository root: `python3 -m unittest discover -s integrations/hermes-plugin -p 'test_*.py'`, `python3 -m unittest discover -s tests -p 'test_*.py'`, `./scripts/check.sh`. The baseline Python for plugin acceptance is 3.11.15. Use new synthetic Chinese input files and record real browser/Hermes evidence per docs/testing/test-cases.md.

Python deployment/contract checks require `PyYAML==6.0.3` and `jsonschema==4.25.0` (CI/test dependencies only).
