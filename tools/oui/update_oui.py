#!/usr/bin/env python3
"""Download the IEEE OUI registry and generate the app's compact TSV resource."""

from __future__ import annotations

import csv
import io
import os
import pathlib
import tempfile
from collections.abc import Callable, Iterable
from typing import Any
import urllib.request


ROOT = pathlib.Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "core-network/src/main/resources/oui/oui-prefixes.tsv"
EXPECTED_COLUMNS = {"Registry", "Assignment", "Organization Name"}
REGISTRY_PREFIX_LENGTHS = {"MA-L": 6, "MA-M": 7, "MA-S": 9}
SOURCES = (
    ("https://standards-oui.ieee.org/oui/oui.csv", "MA-L"),
    ("https://standards-oui.ieee.org/oui28/mam.csv", "MA-M"),
    ("https://standards-oui.ieee.org/oui36/oui36.csv", "MA-S"),
)


def rows_for(payload: bytes, expected_registry: str) -> list[tuple[str, str]]:
    text = payload.decode("utf-8-sig")
    reader = csv.DictReader(io.StringIO(text), strict=True)
    rows: list[tuple[str, str]] = []
    try:
        fieldnames = reader.fieldnames or []
        normalized_fieldnames = [column.strip() if column else "" for column in fieldnames]
        if not normalized_fieldnames or any(not column for column in normalized_fieldnames):
            raise ValueError("CSV has a missing or blank header")
        if len(set(normalized_fieldnames)) != len(normalized_fieldnames):
            raise ValueError("CSV has duplicate headers")
        columns = dict(zip(normalized_fieldnames, fieldnames))
        missing = EXPECTED_COLUMNS - columns.keys()
        if missing:
            raise ValueError(f"CSV is missing expected column(s): {', '.join(sorted(missing))}")

        for row_number, row in enumerate(reader, start=2):
            if None in row:
                raise ValueError(f"CSV row {row_number} contains extra fields")
            if any(value is None for value in row.values()):
                raise ValueError(f"CSV row {row_number} is missing fields")
            if not any((value or "").strip() for value in row.values()):
                continue

            registry = " ".join(row.get(columns["Registry"], "").split()).upper()
            raw_assignment = row.get(columns["Assignment"], "").strip()
            organization = " ".join(row.get(columns["Organization Name"], "").split())
            if ":" in raw_assignment and "-" in raw_assignment:
                raise ValueError(f"CSV row {row_number} has mixed assignment separators")
            separator = ":" if ":" in raw_assignment else "-" if "-" in raw_assignment else None
            assignment_parts = raw_assignment.split(separator) if separator else [raw_assignment]
            if separator and (
                any(not part for part in assignment_parts)
                or any(len(part) != 2 for part in assignment_parts[:-1])
                or len(assignment_parts[-1]) not in {1, 2}
            ):
                raise ValueError(f"CSV row {row_number} has malformed assignment separators")
            assignment = "".join(assignment_parts).upper()
            if (
                any(ch not in "0123456789ABCDEF" for ch in assignment)
                or not organization
            ):
                raise ValueError(f"CSV row {row_number} has an invalid assignment or organization")
            if registry != expected_registry:
                raise ValueError(
                    f"CSV row {row_number} has {registry} registry data; expected {expected_registry}"
                )
            expected_length = REGISTRY_PREFIX_LENGTHS.get(registry)
            if expected_length is None:
                raise ValueError(f"CSV row {row_number} has an unsupported registry: {registry}")
            if len(assignment) != expected_length:
                raise ValueError(
                    f"CSV row {row_number} has prefix width {len(assignment)} that does not "
                    f"match {registry} ({expected_length} hex digits)"
                )
            rows.append((assignment, organization))
    except csv.Error as exc:
        raise ValueError(f"CSV is malformed: {exc}") from exc
    if not rows:
        raise ValueError("CSV contains no valid OUI assignments")
    return rows


def fetch_rows(
    url: str,
    expected_registry: str,
    opener: Callable[..., Any] = urllib.request.urlopen,
) -> list[tuple[str, str]]:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "NetSwissKnife OUI updater (contact-free)"},
    )
    try:
        with opener(request, timeout=30) as response:
            payload = response.read()
    except Exception as exc:
        raise RuntimeError(f"Unable to download {url}: {exc}") from exc

    try:
        return rows_for(payload, expected_registry)
    except ValueError as exc:
        raise ValueError(f"Invalid OUI registry from {url}: {exc}") from exc


def write_registry(output: pathlib.Path, result: dict[str, str]) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    permissions = output.stat().st_mode & 0o777 if output.exists() else 0o644
    fd, temporary_name = tempfile.mkstemp(prefix=f".{output.name}.", suffix=".tmp", dir=output.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as temporary_file:
            temporary_file.writelines(
                f"{prefix}\t{result[prefix]}\n" for prefix in sorted(result)
            )
        os.chmod(temporary_name, permissions)
        os.replace(temporary_name, output)
    finally:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass


def refresh(
    output: pathlib.Path = OUTPUT,
    sources: Iterable[tuple[str, str]] = SOURCES,
    opener: Callable[..., Any] = urllib.request.urlopen,
) -> dict[str, str]:
    result: dict[str, str] = {}
    for url, expected_registry in sources:
        for prefix, organization in fetch_rows(url, expected_registry, opener):
            result.setdefault(prefix, organization)
    if not result:
        raise RuntimeError("No OUI assignments were received from the required registries")
    write_registry(output, result)
    return result


def main() -> None:
    try:
        result = refresh()
    except Exception as exc:
        raise SystemExit(f"Unable to update OUI registry: {exc}") from exc
    print(f"wrote {len(result)} prefixes to {OUTPUT}")


if __name__ == "__main__":
    main()
