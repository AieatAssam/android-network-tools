#!/usr/bin/env python3
"""Fail unless the release APK defines every SNMPv3 implementation we need."""

from __future__ import annotations

import sys
from pathlib import Path

REQUIRED_CLASSES = {
    "org.snmp4j.security.AuthSHA",
    "org.snmp4j.security.PrivAES128",
    "org.snmp4j.security.USM",
}


def missing_classes(package_tree: str) -> set[str]:
    defined_classes: set[str] = set()
    for line in package_tree.splitlines():
        columns = line.split()
        if len(columns) >= 3 and columns[0] == "C" and columns[1] == "d":
            defined_classes.add(columns[-1])
    return REQUIRED_CLASSES - defined_classes


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {Path(sys.argv[0]).name} <apkanalyzer-output>", file=sys.stderr)
        return 2
    try:
        package_tree = Path(sys.argv[1]).read_text(encoding="utf-8")
    except OSError as error:
        print(f"could not read apkanalyzer output: {error}", file=sys.stderr)
        return 2

    missing = sorted(missing_classes(package_tree))
    if missing:
        for class_name in missing:
            print(f"::error::Required SNMP4J class {class_name} is missing from the release APK", file=sys.stderr)
        return 1

    for class_name in sorted(REQUIRED_CLASSES):
        print(f"Verified SNMP4J class: {class_name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
