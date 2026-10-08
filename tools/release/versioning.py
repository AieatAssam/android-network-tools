#!/usr/bin/env python3
"""Validate release versions and allocate a monotonic daily suffix."""

from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from pathlib import Path

MAX_DAILY_NUMBER = 99
MAX_PLAY_VERSION_CODE = 2_100_000_000
VERSION_RE = re.compile(r"(?P<date>[0-9]{4}\.[0-9]{2}\.[0-9]{2})\.(?P<daily>[1-9][0-9]?)\Z")


def parse_version(name: str) -> tuple[int, int]:
    """Return (versionCode, daily number), rejecting invalid release identities."""
    match = VERSION_RE.fullmatch(name)
    if match is None:
        raise ValueError(f"invalid version name: {name!r}")

    date_text = match.group("date")
    try:
        date = dt.datetime.strptime(date_text, "%Y.%m.%d").date()
    except ValueError as error:
        raise ValueError(f"invalid calendar date in version: {name!r}") from error

    daily = int(match.group("daily"))
    if daily > MAX_DAILY_NUMBER:
        raise ValueError(f"daily suffix must be between 1 and {MAX_DAILY_NUMBER}")

    code = int(match.group("date").replace(".", "")) * 100 + daily
    if code < 1 or code > MAX_PLAY_VERSION_CODE:
        raise ValueError(f"versionCode outside supported range: {code}")
    return code, daily


def _read_refs(path: Path, expected_prefix: str) -> list[str]:
    refs = _read_lines(path)
    for ref in refs:
        if not ref.startswith(expected_prefix):
            raise ValueError(f"unexpected ref from matching-refs query: {ref!r}")
    return refs


def _read_lines(path: Path) -> list[str]:
    return [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def _suffixes(refs: list[str], expected_prefix: str) -> set[int]:
    suffixes: set[int] = set()
    for ref in refs:
        if not ref.startswith(expected_prefix):
            raise ValueError(f"unexpected ref from matching-refs query: {ref!r}")
        suffix = ref[len(expected_prefix) :]
        if not re.fullmatch(r"[1-9][0-9]?", suffix):
            raise ValueError(f"malformed version ref: {ref!r}")
        daily = int(suffix)
        if daily > MAX_DAILY_NUMBER:
            raise ValueError(f"daily suffix exceeds {MAX_DAILY_NUMBER}: {ref!r}")
        suffixes.add(daily)
    return suffixes


def _released_suffixes(release_tags: list[str], expected_prefix: str) -> set[int]:
    suffixes: set[int] = set()
    for tag in release_tags:
        if not tag.startswith(expected_prefix):
            continue
        suffix = tag[len(expected_prefix) :]
        if not re.fullmatch(r"[1-9][0-9]?", suffix):
            raise ValueError(f"malformed published release tag: {tag!r}")
        daily = int(suffix)
        if daily > MAX_DAILY_NUMBER:
            raise ValueError(f"published release suffix exceeds {MAX_DAILY_NUMBER}: {tag!r}")
        suffixes.add(daily)
    return suffixes


def allocate(date_text: str, release_refs: list[str], release_tags: list[str]) -> tuple[str, int]:
    try:
        date = dt.datetime.strptime(date_text, "%Y.%m.%d").date()
    except ValueError as error:
        raise ValueError(f"invalid allocation date: {date_text!r}") from error

    release_prefix = f"refs/tags/v{date_text}."
    used = _suffixes(release_refs, release_prefix) | _released_suffixes(
        release_tags, f"v{date_text}."
    )
    daily = max(used, default=0) + 1
    if daily > MAX_DAILY_NUMBER:
        raise ValueError(f"no release suffixes remain for {date_text}; maximum is {MAX_DAILY_NUMBER}")

    name = f"{date.isoformat().replace('-', '.')}.{daily}"
    code, _ = parse_version(name)
    return name, code


def validate(name: str, release_tags: list[str] | None = None) -> tuple[int, int]:
    parsed = parse_version(name)
    tag_name = f"v{name}"
    if release_tags is not None and tag_name in release_tags:
        raise ValueError(f"a GitHub Release already uses tag: {tag_name}")
    return parsed


def _write_outputs(name: str, code: int) -> None:
    print(f"name={name}")
    print(f"code={code}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    validate_parser = subparsers.add_parser("validate", help="validate a pushed release tag")
    validate_parser.add_argument("name", help="version name without the leading v")
    validate_parser.add_argument(
        "--release-tags", type=Path, help="reject a name already used by a GitHub Release"
    )

    allocate_parser = subparsers.add_parser("allocate", help="allocate from existing version tags")
    allocate_parser.add_argument("--date", required=True, help="UTC date as YYYY.MM.DD")
    allocate_parser.add_argument("--release-refs", required=True, type=Path)
    allocate_parser.add_argument("--release-tags", required=True, type=Path)

    args = parser.parse_args()
    try:
        if args.command == "validate":
            release_tags = _read_lines(args.release_tags) if args.release_tags else None
            code, _ = validate(args.name, release_tags)
            _write_outputs(args.name, code)
        else:
            date = dt.datetime.strptime(args.date, "%Y.%m.%d").date()
            release_prefix = f"refs/tags/v{date:%Y.%m.%d}."
            release_refs = _read_refs(args.release_refs, release_prefix)
            release_tags = _read_lines(args.release_tags)
            name, code = allocate(args.date, release_refs, release_tags)
            _write_outputs(name, code)
    except (OSError, ValueError) as error:
        print(f"version allocation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
