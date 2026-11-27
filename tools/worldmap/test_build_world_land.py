"""Tests for the Natural Earth base-map asset generator."""

from __future__ import annotations

import pathlib
import struct
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import build_world_land as builder  # noqa: E402


def square(size: float) -> list[list[float]]:
    return [[0.0, 0.0], [size, 0.0], [size, size], [0.0, size], [0.0, 0.0]]


class QuantizeRingTest(unittest.TestCase):
    def test_rounds_to_centidegrees_and_drops_the_closing_point(self) -> None:
        self.assertEqual(
            builder.quantize_ring([[1.234, -5.678], [2.0, 3.0], [4.0, 5.0], [1.234, -5.678]]),
            [(123, -568), (200, 300), (400, 500)],
        )

    def test_drops_consecutive_points_that_round_to_the_same_value(self) -> None:
        self.assertEqual(
            builder.quantize_ring([[0.0, 0.0], [0.001, 0.001], [1.0, 0.0], [1.0, 1.0]]),
            [(0, 0), (100, 0), (100, 100)],
        )

    def test_rejects_out_of_range_coordinates(self) -> None:
        with self.assertRaises(ValueError):
            builder.quantize_ring([[181.0, 0.0], [0.0, 0.0], [1.0, 1.0]])


class EncodeTest(unittest.TestCase):
    def test_writes_header_rings_and_points_big_endian(self) -> None:
        payload = builder.encode([square(1.0)])
        self.assertEqual(payload[:4], b"NSKL")
        self.assertEqual(struct.unpack(">HH", payload[4:8]), (1, 1))
        self.assertEqual(struct.unpack(">H", payload[8:10]), (4,))
        points = struct.unpack(">8h", payload[10:26])
        self.assertEqual(points, (0, 0, 100, 0, 100, 100, 0, 100))
        self.assertEqual(len(payload), 26)

    def test_drops_rings_that_collapse_below_three_points(self) -> None:
        payload = builder.encode([square(0.001), square(2.0)])
        self.assertEqual(struct.unpack(">HH", payload[4:8]), (1, 1))

    def test_reads_polygons_and_multipolygons(self) -> None:
        geojson = {
            "type": "FeatureCollection",
            "features": [
                {"geometry": {"type": "Polygon", "coordinates": [square(1.0), square(0.5)]}},
                {"geometry": {"type": "MultiPolygon", "coordinates": [[square(3.0)]]}},
            ],
        }
        self.assertEqual(len(list(builder.rings_of(geojson))), 3)

    def test_rejects_other_geometry_types(self) -> None:
        geojson = {"type": "FeatureCollection", "features": [{"geometry": {"type": "LineString", "coordinates": []}}]}
        with self.assertRaises(ValueError):
            list(builder.rings_of(geojson))


if __name__ == "__main__":
    unittest.main()
