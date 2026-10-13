#!/usr/bin/env python3
"""Export an audited source snapshot without .git, local files, credentials or build output."""
import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("audit", ROOT / "scripts/audit-public-tree.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def package(output):
    if not audit.audit():
        raise ValueError("Candidate audit failed")
    output.parent.mkdir(parents=True, exist_ok=True)
    manifest = []
    with output.open("wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed, tarfile.open(fileobj=compressed, mode="w", format=tarfile.PAX_FORMAT) as archive:
        for path in audit.candidates():
            relative = path.relative_to(ROOT).as_posix()
            manifest.append({"path": relative, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
            info = archive.gettarinfo(str(path), arcname="hermes-prd-workbench/" + relative)
            info.uid = info.gid = 0
            info.uname = info.gname = ""
            info.mtime = 0
            info.pax_headers = {}
            info.mode = 0o755 if relative == "workbench" or relative.endswith((".sh", "/mvnw")) else 0o644
            with path.open("rb") as handle:
                archive.addfile(info, handle)
    output.with_suffix(output.suffix + ".manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    output.with_suffix(output.suffix + ".sha256").write_text(hashlib.sha256(output.read_bytes()).hexdigest() + "  " + output.name + "\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    package(args.output.resolve())
