#!/usr/bin/env python3
"""Check production coroutine tokens and inventory raw network-resource creation sites.

The raw-resource inventory is deliberately an audit ledger, not a claim that all
sites already use OperationScope. Each live site must name its current seam,
regression-test reference, and owning migration task. New sites and stale ledger
entries fail closed.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


PRODUCTION_ROOTS = (
    Path("app/src/main"),
    Path("core-network/src/main"),
    Path("core-domain/src/main"),
)
SOURCE_SUFFIXES = {".kt", ".java"}
LEDGER_PATH = Path("tools/quality/raw_resource_sites.json")

BANNED_PATTERNS = (
    ("GlobalScope", re.compile(r"\bGlobalScope\b")),
    ("Channel.UNLIMITED", re.compile(r"\bChannel\s*\.\s*UNLIMITED\b")),
)

# These patterns intentionally match call expressions, not imports or type
# annotations. They are intentionally specific to the raw constructors/openers
# named by E03-T01 so injected factories and the NetworkBinder method names do
# not create false positives.
RESOURCE_PATTERNS = (
    ("Socket", re.compile(r"\bSocket\s*\(")),
    ("DatagramSocket", re.compile(r"\bDatagramSocket\s*\(")),
    ("MulticastSocket", re.compile(r"\bMulticastSocket\s*\(")),
    ("SSLSocket", re.compile(r"\bSSLSocket\s*\(")),
    # SSLSocket instances are created by SSLSocketFactory.createSocket(), so
    # require the returned value to be explicitly typed/cast as SSLSocket.
    (
        "SSLSocketFactory.createSocket",
        re.compile(
            r"\.\s*createSocket\s*\([^\n;]*?\)\s*as\s+SSLSocket\b"
        ),
    ),
    # Require the result type so ordinary URLConnection/openConnection calls
    # for other protocols do not enter this inventory.
    (
        "HttpURLConnection.openConnection",
        re.compile(
            r"\.\s*openConnection\s*\([^\n;]*?\)\s*as\s+HttpURLConnection\b"
        ),
    ),
)


@dataclass(frozen=True, order=True)
class DetectedSite:
    file: str
    kind: str
    occurrence: int
    line: int

    @property
    def key(self) -> tuple[str, str, int]:
        return (self.file, self.kind, self.occurrence)

    def describe(self) -> str:
        return f"{self.file}:{self.line} ({self.kind} occurrence {self.occurrence})"


def _production_sources(root: Path) -> Iterable[Path]:
    for source_root in PRODUCTION_ROOTS:
        absolute_root = root / source_root
        if not absolute_root.exists():
            continue
        for path in absolute_root.rglob("*"):
            if path.is_file() and path.suffix in SOURCE_SUFFIXES:
                yield path


def _line_at(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def detect_banned_tokens(root: Path) -> list[str]:
    violations: list[str] = []
    for path in sorted(_production_sources(root)):
        relative = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8")
        for token, pattern in BANNED_PATTERNS:
            for match in pattern.finditer(text):
                violations.append(f"{relative}:{_line_at(text, match.start())}: banned {token}")
    return violations


def detect_resource_sites(root: Path) -> list[DetectedSite]:
    detected: list[DetectedSite] = []
    for path in sorted(_production_sources(root)):
        relative = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8")
        file_hits: list[tuple[int, str]] = []
        for kind, pattern in RESOURCE_PATTERNS:
            file_hits.extend((match.start(), kind) for match in pattern.finditer(text))
        file_hits.sort()
        occurrences: dict[str, int] = {}
        for offset, kind in file_hits:
            occurrences[kind] = occurrences.get(kind, 0) + 1
            detected.append(
                DetectedSite(
                    file=relative,
                    kind=kind,
                    occurrence=occurrences[kind],
                    line=_line_at(text, offset),
                )
            )
    return sorted(detected)


def _read_ledger(root: Path, ledger_path: Path | None = None) -> tuple[list[dict], list[str]]:
    path = root / (ledger_path or LEDGER_PATH)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [], [f"Cannot read resource-site ledger {path.relative_to(root)}: {error}"]

    if not isinstance(data, dict) or data.get("schema_version") != 1:
        return [], ["Resource-site ledger must be an object with schema_version: 1"]
    sites = data.get("sites")
    if not isinstance(sites, list):
        return [], ["Resource-site ledger field 'sites' must be an array"]
    return sites, []


def validate_resource_sites(
    root: Path,
    ledger_path: Path | None = None,
) -> tuple[list[str], list[DetectedSite]]:
    ledger, errors = _read_ledger(root, ledger_path)
    if errors:
        return errors, []

    detected = detect_resource_sites(root)
    actual_by_key = {site.key: site for site in detected}
    ledger_by_key: dict[tuple[str, str, int], dict] = {}

    for row in ledger:
        if not isinstance(row, dict):
            errors.append("Resource-site ledger entries must be objects")
            continue
        required = ("id", "file", "kind", "occurrence", "scope_seam", "test_path", "migration_owner", "status")
        missing = [field for field in required if not row.get(field)]
        if missing:
            errors.append(f"Ledger entry {row.get('id', '<unknown>')} missing required fields: {', '.join(missing)}")
            continue
        if row["status"] not in {
            "legacy_unscoped",
            "scope_hook_present_scope_review_open",
            "scope_registered_reviewed",
        }:
            errors.append(f"Ledger entry {row['id']} has invalid status {row['status']!r}")
        if not isinstance(row["occurrence"], int) or row["occurrence"] < 1:
            errors.append(f"Ledger entry {row['id']} occurrence must be a positive integer")
            continue

        key = (str(row["file"]), str(row["kind"]), row["occurrence"])
        if key in ledger_by_key:
            errors.append(f"Duplicate ledger site key for {row['id']}: {key}")
            continue
        ledger_by_key[key] = row

        source_path = root / str(row["file"])
        if not source_path.is_file() or source_path.suffix not in SOURCE_SUFFIXES:
            errors.append(f"Ledger entry {row['id']} references missing production source {row['file']}")
        test_path = root / str(row["test_path"])
        if not test_path.is_file() or ("/src/test/" not in f"/{row['test_path']}/" and "/src/androidTest/" not in f"/{row['test_path']}/"):
            errors.append(f"Ledger entry {row['id']} has missing/non-test reference {row['test_path']}")
        if key not in actual_by_key:
            errors.append(f"Stale ledger entry {row['id']} no longer matches a detected source site: {key}")

    for key, site in actual_by_key.items():
        if key not in ledger_by_key:
            errors.append(f"Unregistered raw resource site: {site.describe()}")

    errors.extend(detect_banned_tokens(root))
    return errors, detected


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--ledger", type=Path, default=None, help="ledger path relative to --root")
    args = parser.parse_args(argv)
    root = args.root.resolve()

    errors, sites = validate_resource_sites(root, args.ledger)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"Resource-site check failed with {len(errors)} issue(s).", file=sys.stderr)
        return 1

    statuses = [row.get("status") for row in _read_ledger(root, args.ledger)[0]]
    pending = statuses.count("legacy_unscoped")
    review_open = statuses.count("scope_hook_present_scope_review_open")
    scope_reviewed = statuses.count("scope_registered_reviewed")
    print(
        f"Resource-site check passed: {len(sites)} raw creation/open site(s) inventoried; "
        f"{pending} legacy migration follow-up(s), {review_open} scope review(s) open, "
        f"{scope_reviewed} scope registration(s) reviewed."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
