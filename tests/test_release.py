import importlib.util
from pathlib import Path
import tempfile
import tarfile
import hashlib
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('public_audit', ROOT / 'scripts/audit-public-tree.py')
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class ReleaseBoundaryTests(unittest.TestCase):
    def tree(self, root):
        (root / 'docs').mkdir()
        (root / 'config').mkdir()
        (root / 'docs/README.md').write_text('Public documentation.\n')
        (root / 'config/public-files.txt').write_text('config/public-files.txt\ndocs/README.md\n')

    def test_nested_env_and_backups_are_refused_even_with_plain_password(self):
        for name in ['.env', '.env.local', 'credentials.txt', 'database.sql.bak']:
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.tree(root)
                (root / 'docs' / name).write_text('PASSWORD=synthetic-plain-password\n')
                with patch.object(audit, 'ROOT', root), self.assertRaises(ValueError):
                    audit.candidates()

    def test_unreviewed_file_requires_manifest_review(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.tree(root)
            (root / 'docs/new.md').write_text('Unreviewed\n')
            with patch.object(audit, 'ROOT', root), self.assertRaises(ValueError):
                audit.candidates()

    def test_symlink_cannot_export_external_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.tree(root)
            (root / 'private.txt').write_text('synthetic private text')
            (root / 'docs/link.md').symlink_to(root / 'private.txt')
            with patch.object(audit, 'ROOT', root), self.assertRaises(ValueError):
                audit.candidates()

    def test_reviewed_snapshot_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.tree(root)
            with patch.object(audit, 'ROOT', root):
                self.assertEqual(['config/public-files.txt', 'docs/README.md'], [p.relative_to(root).as_posix() for p in audit.candidates()])


class ArchiveMetadataTests(unittest.TestCase):
    def test_archive_has_neutral_metadata_and_reproducible_bytes(self):
        spec = importlib.util.spec_from_file_location('release', ROOT / 'scripts/package-release.py')
        release = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(release)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'README.md'
            source.write_text('Synthetic public text.\n')
            with patch.object(release, 'ROOT', root), patch.object(release.audit, 'audit', return_value=True), patch.object(release.audit, 'candidates', return_value=[source]):
                release.package(root / 'first.tar.gz')
                release.package(root / 'second.tar.gz')
            self.assertEqual((root / 'first.tar.gz').read_bytes(), (root / 'second.tar.gz').read_bytes())
            with tarfile.open(root / 'first.tar.gz') as archive:
                member = archive.getmembers()[0]
                self.assertEqual((0, 0, '', '', 0, 0o644), (member.uid, member.gid, member.uname, member.gname, member.mtime, member.mode))
                self.assertTrue(member.isfile())
