import tempfile
import unittest
from pathlib import Path

from versioning import allocate, parse_version, validate


class ReleaseVersioningTest(unittest.TestCase):
    def test_sparse_release_suffixes_use_maximum_plus_one(self) -> None:
        name, code = allocate(
            "2026.09.23",
            ["refs/tags/v2026.09.23.1", "refs/tags/v2026.09.23.3"],
            [],
        )

        self.assertEqual((name, code), ("2026.09.23.4", 2026092304))

    def test_artifact_only_tag_is_consumed_by_next_dispatch(self) -> None:
        next_build = allocate("2026.09.23", ["refs/tags/v2026.09.23.1"], [])

        self.assertEqual(next_build, ("2026.09.23.2", 2026092302))

    def test_matching_refs_query_must_only_return_expected_prefix(self) -> None:
        with self.assertRaisesRegex(ValueError, "unexpected ref"):
            allocate("2026.09.23", ["refs/tags/v2026.09.22.99"], [])

    def test_deleted_release_tag_still_consumes_its_version(self) -> None:
        next_build = allocate("2026.09.23", [], ["v2026.09.23.3"])

        self.assertEqual(next_build, ("2026.09.23.4", 2026092304))

    def test_paginated_release_history_does_not_hide_todays_maximum(self) -> None:
        older_releases = [f"v2026.09.22.{index}" for index in range(1, 121)]
        older_releases.append("v2026.09.23.3")

        self.assertEqual(
            allocate("2026.09.23", [], older_releases),
            ("2026.09.23.4", 2026092304),
        )


    def test_out_of_range_suffix_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            release_file = Path(temp_dir) / "release-refs.txt"
            # The old count-based allocator accepted malformed tags. The
            # replacement must reject out-of-range suffixes it encounters.
            release_file.write_text(
                "".join(f"refs/tags/v2026.09.23.{index}\n" for index in range(1, 101)),
                encoding="utf-8",
            )
            from versioning import _read_refs

            refs = _read_refs(release_file, "refs/tags/v2026.09.23.")
            with self.assertRaises(ValueError):
                allocate("2026.09.23", refs, [])

    def test_malformed_matching_ref_fails_closed(self) -> None:
        with self.assertRaisesRegex(ValueError, "malformed version ref"):
            allocate("2026.09.23", ["refs/tags/v2026.09.23.1x"], [])

    def test_daily_suffix_must_fit_version_code_layout(self) -> None:
        with self.assertRaisesRegex(ValueError, "no release suffixes remain"):
            allocate("2026.09.23", [f"refs/tags/v2026.09.23.{n}" for n in range(1, 100)], [])
        with self.assertRaisesRegex(ValueError, "invalid version name"):
            parse_version("2026.09.23.100")

    def test_push_tag_date_and_version_code_are_validated(self) -> None:
        self.assertEqual(parse_version("2026.09.23.1"), (2026092301, 1))
        for invalid in ("2026.02.30.1", "2026.9.23.1", "2026.09.23.0", "2100.01.01.1"):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                parse_version(invalid)

    def test_push_tag_cannot_reuse_an_existing_release_identity(self) -> None:
        with self.assertRaisesRegex(ValueError, "already uses tag"):
            validate("2026.09.23.1", ["v2026.09.23.2", "v2026.09.23.1"])

        self.assertEqual(
            validate("2026.09.23.1", ["v2026.09.23.2"]),
            (2026092301, 1),
        )


if __name__ == "__main__":
    unittest.main()
