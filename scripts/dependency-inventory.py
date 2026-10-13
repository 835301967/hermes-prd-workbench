#!/usr/bin/env python3
"""Generate reviewable dependency/license evidence; does not certify license compatibility."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def public_license_inventory(licenses):
    """Keep release metadata while rejecting local paths and unexpected host fields."""
    allowed = {'name', 'versions', 'license', 'homepage', 'description'}
    return {license_name: [{key: value for key, value in item.items() if key in allowed}
                           for item in items] for license_name, items in licenses.items()}


def generate(output):
    output.mkdir(parents=True, exist_ok=True)
    pnpm = shutil.which('pnpm')
    if not pnpm:
        raise ValueError('Pinned pnpm must be available')
    raw = subprocess.check_output([pnpm, '--dir', str(ROOT / 'apps/web'), 'licenses', 'list', '--json'], text=True)
    licenses = json.loads(raw)
    (output / 'frontend-licenses.json').write_text(json.dumps(public_license_inventory(licenses), indent=2) + '\n')
    subprocess.run([str(ROOT / 'apps/backend/mvnw'), '-B', '-f', str(ROOT / 'apps/backend/pom.xml'),
                    'org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom', '-DskipTests'], check=True)
    for suffix in ['json', 'xml']:
        shutil.copyfile(ROOT / f'apps/backend/target/bom.{suffix}', output / f'backend-bom.{suffix}')
    manifest = {'scope': 'Maven resolved backend and pnpm installed frontend; upstream Hermes and OS images require separate inventory',
                'sha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in output.iterdir() if p.is_file() and p.name != 'manifest.json'}}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    generate(args.output.resolve())
