import json
import sys
import tempfile
import unittest
from pathlib import Path


HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import check_resource_sites as checker  # noqa: E402


class ResourceSiteCheckerTest(unittest.TestCase):
    def make_root(self, source: str, *, test_exists: bool = True) -> Path:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        source_path = root / "app/src/main/kotlin/Fixture.kt"
        source_path.parent.mkdir(parents=True)
        source_path.write_text(source, encoding="utf-8")
        if test_exists:
            test_path = root / "app/src/test/kotlin/FixtureTest.kt"
            test_path.parent.mkdir(parents=True)
            test_path.write_text("class FixtureTest\n", encoding="utf-8")
        return root

    def write_ledger(self, root: Path, sites: list[dict]) -> None:
        ledger = root / checker.LEDGER_PATH
        ledger.parent.mkdir(parents=True, exist_ok=True)
        ledger.write_text(json.dumps({"schema_version": 1, "sites": sites}), encoding="utf-8")

    @staticmethod
    def ledger_site(*, test_path: str = "app/src/test/kotlin/FixtureTest.kt") -> dict:
        return {
            "id": "TEST-001",
            "file": "app/src/main/kotlin/Fixture.kt",
            "kind": "Socket",
            "occurrence": 1,
            "scope_seam": "fixture socket factory",
            "test_path": test_path,
            "migration_owner": "checker test owner",
            "status": "legacy_unscoped",
        }

    def test_detects_banned_global_scope_and_unlimited_channel(self) -> None:
        root = self.make_root(
            """import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
fun bad() { GlobalScope.launch {}; val c = Channel<Int>(Channel.UNLIMITED) }
"""
        )
        self.write_ledger(root, [])

        errors, _ = checker.validate_resource_sites(root)

        self.assertTrue(any("banned GlobalScope" in error for error in errors), errors)
        self.assertTrue(any("banned Channel.UNLIMITED" in error for error in errors), errors)

    def test_known_ledger_site_passes_and_non_creation_mentions_are_ignored(self) -> None:
        source = (HERE / "fixtures/ResourceSiteFixture.kt").read_text(encoding="utf-8")
        root = self.make_root(source)
        site = self.ledger_site()
        site["status"] = "scope_registered_reviewed"
        self.write_ledger(root, [site])

        errors, sites = checker.validate_resource_sites(root)

        self.assertEqual([], errors)
        self.assertEqual(1, len(sites))
        self.assertEqual("Socket", sites[0].kind)

    def test_unlisted_new_resource_site_fails(self) -> None:
        root = self.make_root("fun create() = DatagramSocket()\n")
        self.write_ledger(root, [])

        errors, _ = checker.validate_resource_sites(root)

        self.assertTrue(any("Unregistered raw resource site" in error for error in errors), errors)

    def test_missing_test_reference_fails(self) -> None:
        root = self.make_root("fun create() = Socket()\n", test_exists=False)
        self.write_ledger(root, [self.ledger_site()])

        errors, _ = checker.validate_resource_sites(root)

        self.assertTrue(any("missing/non-test reference" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
