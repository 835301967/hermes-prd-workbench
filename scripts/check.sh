#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"
python3 scripts/audit-public-tree.py
python3 scripts/check-docs.py
python3 -m unittest discover -s tests -p 'test_*.py'
python3 -m unittest discover -s integrations/hermes-plugin -p 'test_*.py'
apps/backend/mvnw -B -f apps/backend/pom.xml clean test
pnpm --dir apps/web typecheck
pnpm --dir apps/web test
pnpm --dir apps/web build
git diff --check
