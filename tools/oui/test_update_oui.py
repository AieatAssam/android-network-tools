"""Regression tests for safe OUI registry refreshes."""

from __future__ import annotations

import pathlib
import stat
import tempfile
import unittest
from unittest import mock

from tools.oui import update_oui


class Response:
    def __init__(self, payload: bytes) -> None:
        self.payload = payload

    def __enter__(self) -> Response:
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def read(self) -> bytes:
        return self.payload


class UpdateOuiTest(unittest.TestCase):
    SOURCES = (
        ("https://example.test/oui.csv", "MA-L"),
        ("https://example.test/mam.csv", "MA-M"),
        ("https://example.test/oui36.csv", "MA-S"),
    )

    def test_source_failure_preserves_existing_registry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((self.csv("001122", "First Vendor"), OSError("source unavailable")))

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                payload = next(payloads)
                if isinstance(payload, Exception):
                    raise payload
                return Response(payload)

            with self.assertRaisesRegex(RuntimeError, "mam.csv"):
                update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))

    def test_malformed_source_header_preserves_existing_registry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((self.csv("001122", "First Vendor"), b"Registry,Prefix,Vendor\nMA-M,334455,Second Vendor\n"))

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                return Response(next(payloads))

            with self.assertRaisesRegex(ValueError, "Organization Name"):
                update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))

    def test_duplicate_source_header_preserves_existing_registry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((
                self.csv("001122", "First Vendor", "MA-L"),
                b"Registry,Assignment,Assignment,Organization Name\nMA-M,001122,3344556,Second Vendor\n",
            ))

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                return Response(next(payloads))

            with self.assertRaisesRegex(ValueError, "duplicate headers"):
                update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))

    def test_empty_registry_preserves_existing_registry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((self.csv("001122", "First Vendor"), b"Registry,Assignment,Organization Name\n"))

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                return Response(next(payloads))

            with self.assertRaisesRegex(ValueError, "no valid OUI assignments"):
                update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))

    def test_malformed_csv_quoting_preserves_existing_registry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((self.csv("001122", "First Vendor"), b'Registry,Assignment,Organization Name\nMA-M,334455,"Truncated Vendor\n'))

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                return Response(next(payloads))

            with self.assertRaisesRegex(ValueError, "CSV is malformed"):
                update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))

    def test_truncated_row_preserves_existing_registry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((
                self.csv("001122", "First Vendor"),
                b"Registry,Assignment,Organization Name\nMA-M,3344556,Second Vendor\nMA-M,6677889\n",
            ))

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                return Response(next(payloads))

            with self.assertRaisesRegex(ValueError, "missing fields"):
                update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))

    def test_registry_width_mismatch_preserves_existing_registry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((
                self.csv("001122", "First Vendor", "MA-L"),
                self.csv("3344556", "Wrong Registry Width", "MA-L"),
            ))
            sources = (
                self.SOURCES[0],
                (self.SOURCES[1][0], "MA-L"),
                self.SOURCES[2],
            )

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                return Response(next(payloads))

            with self.assertRaisesRegex(ValueError, "does not match MA-L"):
                update_oui.refresh(output=output, sources=sources, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))

    def test_wrong_registry_class_from_source_preserves_existing_registry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((
                self.csv("001122", "First Vendor", "MA-L"),
                self.csv("334455", "Wrong Source Class", "MA-L"),
            ))

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                return Response(next(payloads))

            with self.assertRaisesRegex(ValueError, "expected MA-M"):
                update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))

    def test_malformed_assignment_punctuation_preserves_existing_registry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((
                self.csv("001122", "First Vendor", "MA-L"),
                b"Registry,Assignment,Organization Name\nMA-M,33/44/556,Malformed Vendor\n",
            ))

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                return Response(next(payloads))

            with self.assertRaisesRegex(ValueError, "invalid assignment"):
                update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))

    def test_registry_width_is_validated_even_when_source_expectation_matches(self) -> None:
        with self.assertRaisesRegex(ValueError, "does not match MA-L"):
            update_oui.rows_for(
                self.csv("3344556", "Wrong Width", "MA-L"),
                expected_registry="MA-L",
            )

    def test_only_canonical_assignment_separators_are_accepted(self) -> None:
        self.assertEqual(
            [("001122", "Colon Vendor")],
            update_oui.rows_for(
                b"Registry,Assignment,Organization Name\nMA-L,00:11:22,Colon Vendor\n",
                expected_registry="MA-L",
            ),
        )
        self.assertEqual(
            [("001122", "Hyphen Vendor")],
            update_oui.rows_for(
                b"Registry,Assignment,Organization Name\nMA-L,00-11-22,Hyphen Vendor\n",
                expected_registry="MA-L",
            ),
        )
        with self.assertRaisesRegex(ValueError, "invalid assignment"):
            update_oui.rows_for(
                b"Registry,Assignment,Organization Name\nMA-L,00/11/22,Bad Vendor\n",
                expected_registry="MA-L",
            )

    def test_complete_refresh_writes_all_sources_in_sorted_order(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "nested" / "oui-prefixes.tsv"
            output.parent.mkdir()
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            output.chmod(0o640)
            expected_mode = stat.S_IMODE(output.stat().st_mode)
            payloads = iter((
                self.csv("001122", "First Vendor", "MA-L"),
                self.csv("3344556", "Second Vendor", "MA-M"),
                self.csv("AABBCCDDE", "Third Vendor", "MA-S"),
            ))
            requested_urls: list[str] = []

            def opener(request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                requested_urls.append(request.full_url)
                return Response(next(payloads))

            result = update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual(3, len(result))
            self.assertEqual([url for url, _ in self.SOURCES], requested_urls)
            self.assertEqual(
                "001122\tFirst Vendor\n3344556\tSecond Vendor\nAABBCCDDE\tThird Vendor\n",
                output.read_text(encoding="utf-8"),
            )
            self.assertEqual(expected_mode, stat.S_IMODE(output.stat().st_mode))
            self.assertEqual(["oui-prefixes.tsv"], [path.name for path in output.parent.iterdir()])

    def test_replace_failure_keeps_old_registry_and_removes_temporary_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = pathlib.Path(temporary_directory) / "oui-prefixes.tsv"
            output.write_text("OLD\tRegistry\n", encoding="utf-8")
            payloads = iter((
                self.csv("001122", "First Vendor", "MA-L"),
                self.csv("3344556", "Second Vendor", "MA-M"),
                self.csv("AABBCCDDE", "Third Vendor", "MA-S"),
            ))

            def opener(_request: object, timeout: int) -> Response:
                self.assertEqual(30, timeout)
                return Response(next(payloads))

            with mock.patch.object(update_oui.os, "replace", side_effect=OSError("replace failed")):
                with self.assertRaisesRegex(OSError, "replace failed"):
                    update_oui.refresh(output=output, sources=self.SOURCES, opener=opener)

            self.assertEqual("OLD\tRegistry\n", output.read_text(encoding="utf-8"))
            self.assertEqual(["oui-prefixes.tsv"], [path.name for path in output.parent.iterdir()])

    @staticmethod
    def csv(assignment: str, organization: str, registry: str = "MA-L") -> bytes:
        return (
            "Registry,Assignment,Organization Name,Organization Address\n"
            f"{registry},{assignment},{organization},Somewhere\n"
        ).encode("utf-8")


if __name__ == "__main__":
    unittest.main()
