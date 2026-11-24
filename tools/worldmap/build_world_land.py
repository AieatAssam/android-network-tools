#!/usr/bin/env python3
"""Convert Natural Earth 1:110m land polygons into the app's compact base-map asset.

Input: ``ne_110m_land.geojson`` from Natural Earth (public domain), for example
https://naciscdn.org/naturalearth/110m/physical/ne_110m_land.zip converted to
GeoJSON, or the GeoJSON published in the natural-earth-vector repository.
The input is treated as data only.

Output (``app/src/main/assets/worldmap/land-110m.bin``), all integers big-endian:

    magic        4 bytes  "NSKL"
    version      u16      1
    ring count   u16
    per ring:
      point count  u16    (3 or more; the closing point is implied)
      points       point count x (i16 longitude, i16 latitude) in centidegrees

Every ring of every polygon is written (outer rings and holes); the app fills
with the even-odd rule. Coordinates are rounded to 0.01 degree (about 1 km),
far finer than the 1:110m source, and consecutive duplicates created by the
rounding are dropped. The format is parsed by ``WorldLandParser`` in :app.

Usage: python3 -I tools/worldmap/build_world_land.py <land.geojson> [output.bin]
"""

from __future__ import annotations

import json
import pathlib
import struct
import sys
from collections.abc import Iterable, Sequence

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = ROOT / "app/src/main/assets/worldmap/land-110m.bin"
MAGIC = b"NSKL"
VERSION = 1
SCALE = 100
MAX_U16 = 0xFFFF
MIN_RING_POINTS = 3


def quantize_ring(ring: Sequence[Sequence[float]]) -> list[tuple[int, int]]:
    """Round a GeoJSON ring to centidegrees, drop repeats and the closing point."""
    points: list[tuple[int, int]] = []
    for coordinate in ring:
        if len(coordinate) < 2:
            raise ValueError(f"coordinate has fewer than two values: {coordinate!r}")
        lon, lat = float(coordinate[0]), float(coordinate[1])
        if not (-180.0 <= lon <= 180.0 and -90.0 <= lat <= 90.0):
            raise ValueError(f"coordinate out of range: {lon}, {lat}")
        point = (round(lon * SCALE), round(lat * SCALE))
        if not points or points[-1] != point:
            points.append(point)
    if len(points) > 1 and points[0] == points[-1]:
        points.pop()
    return points


def rings_of(geojson: dict) -> Iterable[Sequence[Sequence[float]]]:
    """Yield every ring of every Polygon or MultiPolygon feature."""
    features = geojson.get("features")
    if not isinstance(features, list):
        raise ValueError("input is not a GeoJSON FeatureCollection")
    for feature in features:
        geometry = feature.get("geometry") or {}
        kind = geometry.get("type")
        coordinates = geometry.get("coordinates")
        if kind == "Polygon":
            polygons = [coordinates]
        elif kind == "MultiPolygon":
            polygons = coordinates
        else:
            raise ValueError(f"unsupported geometry type: {kind!r}")
        for polygon in polygons:
            yield from polygon


def encode(rings: Iterable[Sequence[Sequence[float]]]) -> bytes:
    """Encode rings in the asset format; rings that collapse below three points are dropped."""
    quantized = [points for points in (quantize_ring(ring) for ring in rings) if len(points) >= MIN_RING_POINTS]
    if len(quantized) > MAX_U16:
        raise ValueError("too many rings for the format")
    out = bytearray(MAGIC)
    out += struct.pack(">HH", VERSION, len(quantized))
    for points in quantized:
        if len(points) > MAX_U16:
            raise ValueError("ring has too many points for the format")
        out += struct.pack(">H", len(points))
        for lon, lat in points:
            out += struct.pack(">hh", lon, lat)
    return bytes(out)


def main(argv: list[str]) -> int:
    if len(argv) not in (2, 3):
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    source = pathlib.Path(argv[1])
    output = pathlib.Path(argv[2]) if len(argv) == 3 else DEFAULT_OUTPUT
    with source.open(encoding="utf-8") as handle:
        payload = encode(rings_of(json.load(handle)))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(payload)
    print(f"wrote {output} ({len(payload)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
