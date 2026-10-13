#!/usr/bin/env python3
"""Check the candidate tree, not ignored user files or historical Git objects. Never print matching secrets."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DIRECTORIES = ["apps/backend/src", "apps/web/src", "apps/web/public", "integrations", "contracts", "config", "deploy", "scripts", "tests", "docs", ".github"]
FILES = ["README.md", "README.zh-CN.md", "AGENTS.md", "LICENSE", "NOTICE", "CONTRIBUTING.md", "SECURITY.md", "CODE_OF_CONDUCT.md", "CHANGELOG.md", ".env.example", ".gitignore", ".dockerignore", ".editorconfig", "compose.yaml", "workbench", "apps/backend/pom.xml", "apps/backend/mvnw", "apps/backend/mvnw.cmd", "apps/backend/.mvn/wrapper/maven-wrapper.properties", "apps/web/package.json", "apps/web/pnpm-lock.yaml", "apps/web/index.html", "apps/web/tsconfig.json", "apps/web/vite.config.ts", "apps/web/pnpm-workspace.yaml", "config/public-files.txt"]
FORBIDDEN = re.compile("(?:" + "kuai" + "lu|快" + "鹭|192\\.168\\.102\\.|/Users/[A-Za-z0-9_.-]+/|/home/" + "deploy/|prd-" + "review|work" + "buddy-ai/memory)", re.I)
CREDENTIAL = re.compile(r"(?:sk-[A-Za-z0-9_-]{24,}|gh[pousr]_[A-Za-z0-9]{30,}|AKIA[0-9A-Z]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)")
PUBLIC_BINARY_ASSETS = {
    "docs/assets/hermes-prd-workbench-demo.gif": b"GIF89a",
    "docs/assets/mobile-workbench.jpg": b"\xff\xd8\xff",
    "docs/assets/poc-preview.jpg": b"\xff\xd8\xff",
    "docs/assets/sandboxed-preview.jpg": b"\xff\xd8\xff",
    "docs/assets/social-preview.jpg": b"\xff\xd8\xff",
    "docs/assets/workbench-demo.jpg": b"\xff\xd8\xff",
}
MAX_PUBLIC_BINARY_BYTES = 2 * 1024 * 1024


EXCLUDED_PARTS = {"__pycache__", "node_modules", "target", ".venv", "dist", "coverage", ".runtime", "logs", "backups"}
SOURCE_NAME_EXCEPTIONS = {
    "apps/backend/src/main/java/org/hermesprd/workbench/auth/Credentials.java",
    "apps/backend/src/test/java/org/hermesprd/workbench/auth/CredentialsTest.java",
    "apps/backend/src/main/java/org/hermesprd/workbench/shared/SecretFiles.java",
}
FORBIDDEN_NAME = re.compile(r"^(?:\.env(?:\..*)?|\.npmrc|credentials.*|secrets?.*|id_rsa.*)$|\.(?:log|bak|dump|pem|key|zip|tar|gz|sqlite|db)$", re.I)


def available_files():
    seen = set()
    for directory in DIRECTORIES:
        base = ROOT / directory
        if base.is_symlink():
            raise ValueError("Public directory cannot be a symlink")
        for path in base.rglob("*"):
            relative = path.relative_to(ROOT)
            if any(part in EXCLUDED_PARTS for part in relative.parts):
                continue
            if path.is_symlink():
                raise ValueError("Symlink refused: " + relative.as_posix())
            if path.is_file():
                if FORBIDDEN_NAME.search(path.name) and relative.as_posix() not in SOURCE_NAME_EXCEPTIONS:
                    raise ValueError("Runtime/secret file refused: " + relative.as_posix())
                seen.add(path)
    for name in FILES:
        path = ROOT / name
        if path.is_symlink():
            raise ValueError("Symlink refused: " + name)
        if path.is_file():
            seen.add(path)
    return sorted(seen)


def candidates():
    paths = available_files()
    manifest = ROOT / "config/public-files.txt"
    if not manifest.is_file():
        raise ValueError("Reviewed public file manifest is required")
    approved = set(manifest.read_text().splitlines())
    actual = {path.relative_to(ROOT).as_posix() for path in paths}
    if actual != approved:
        raise ValueError("Public file manifest differs from the tree; inspect changes and refresh explicitly")
    return paths


def audit():
    failures = []
    try:
        paths = candidates()
    except ValueError as ex:
        print("FAIL " + str(ex))
        return False
    for path in paths:
        relative = path.relative_to(ROOT).as_posix()
        raw = path.read_bytes()
        signature = PUBLIC_BINARY_ASSETS.get(relative)
        if signature:
            if not raw.startswith(signature):
                failures.append((relative, "public image has an invalid signature"))
                continue
            if len(raw) > MAX_PUBLIC_BINARY_BYTES:
                failures.append((relative, "public binary exceeds size limit"))
                continue
            if b"Exif\x00\x00" in raw:
                failures.append((relative, "public image contains EXIF metadata"))
                continue
            text = raw.decode("latin-1")
        else:
            try:
                text = raw.decode("utf-8")
            except UnicodeError:
                failures.append((relative, "unreviewed binary"))
                continue
        if FORBIDDEN.search(text):
            failures.append((relative, "private identity/topology marker"))
            continue
        for line in text.splitlines():
            if CREDENTIAL.search(line):
                # Exact synthetic adversarial literals in credential-validator tests are intentional.
                if path.name in {"HtmlPayloadTest.java", "ResultValidatorTest.java", "demoPackage.test.ts"} and any(word in line.lower() for word in ["fake", "dummy", "test", "private key"]):
                    continue
                failures.append((relative, "credential candidate; inspect privately"))
                break
    for path, reason in failures:
        print(f"FAIL {path}: {reason}")
    print(f"Candidate scan: {len(paths)} files, {len(failures)} findings (no matching values printed).")
    return not failures


if __name__ == "__main__":
    if sys.argv[1:] == ["--refresh-manifest"]:
        manifest = ROOT / "config/public-files.txt"
        manifest.parent.mkdir(parents=True, exist_ok=True)
        manifest.touch(exist_ok=True)
        manifest.write_text("\n".join(p.relative_to(ROOT).as_posix() for p in available_files()) + "\n")
        print("Manifest refreshed. Independently review added/removed paths before release.")
    else:
        sys.exit(0 if audit() else 1)
