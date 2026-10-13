import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("inventory", ROOT / "scripts/dependency-inventory.py")
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)


class PublicInventoryTests(unittest.TestCase):
    def test_local_and_future_host_metadata_cannot_enter_release_inventory(self):
        raw = {"Unknown": [{"name": "synthetic-package", "versions": ["1.0.0"],
                            "license": "Unknown", "paths": ["/private/build/node_modules/package"],
                            "author": "private builder", "futureHostMetadata": {"home": "/private/home"}}]}
        public = inventory.public_license_inventory(raw)
        self.assertEqual("Unknown", public["Unknown"][0]["license"])
        self.assertEqual(["1.0.0"], public["Unknown"][0]["versions"])
        self.assertNotIn("private", str(public))
        self.assertNotIn("paths", public["Unknown"][0])


if __name__ == "__main__":
    unittest.main()
