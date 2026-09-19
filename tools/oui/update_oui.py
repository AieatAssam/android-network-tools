#!/usr/bin/env python3
"""Download the IEEE OUI registry and generate the app's compact TSV resource."""

from __future__ import annotations

import csv
import io
import pathlib
import urllib.request


ROOT = pathlib.Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "core-network/src/main/resources/oui/oui-prefixes.tsv"
SOURCES = (
    "https://standards-oui.ieee.org/oui/oui.csv",
    "https://standards-oui.ieee.org/oui28/mam.csv",
    "https://standards-oui.ieee.org/oui36/oui36.csv",
)


def rows_for(payload: bytes) -> list[tuple[str, str]]:
    text = payload.decode("utf-8-sig", errors="replace")
    reader = csv.DictReader(io.StringIO(text))
    rows: list[tuple[str, str]] = []
    for row in reader:
        assignment = "".join(ch for ch in row.get("Assignment", "") if ch.isalnum()).upper()
        organization = " ".join(row.get("Organization Name", "").split())
        if len(assignment) >= 6 and organization:
            rows.append((assignment, organization))
    return rows


def main() -> None:
    result: dict[str, str] = {}
    for url in SOURCES:
        try:
            request = urllib.request.Request(
                url,
                headers={"User-Agent": "NetSwissKnife OUI updater (contact-free)"},
            )
            with urllib.request.urlopen(request, timeout=30) as response:
                for prefix, organization in rows_for(response.read()):
                    result.setdefault(prefix, organization)
        except Exception as exc:
            if not result:
                raise SystemExit(f"Unable to download {url}: {exc}") from exc
            print(f"warning: skipped {url}: {exc}")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        "".join(f"{prefix}\t{result[prefix]}\n" for prefix in sorted(result)),
        encoding="utf-8",
    )
    print(f"wrote {len(result)} prefixes to {OUTPUT}")


if __name__ == "__main__":
    main()
