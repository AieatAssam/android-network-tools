# Natural Earth 1:110m land

The Traceroute route map draws its base map from Natural Earth's 1:110m
physical "land" polygons (`ne_110m_land`), <https://www.naturalearthdata.com>.

**Licence: public domain.** Natural Earth states: "All versions of Natural
Earth raster + vector map data found on this website are in the public domain.
You may use the maps in any manner, including modifying the content and
design, electronic dissemination, and offset printing." No permission or
attribution is required; the app credits Natural Earth anyway, under the map
and in Settings > Data Source Attributions.

## Bundled form

`app/src/main/assets/worldmap/land-110m.bin` (about 20 KB) is generated from the
GeoJSON with `tools/worldmap/build_world_land.py`, which documents the binary
format. It holds 128 rings and about 5,000 points, rounded to 0.01 degree. The
app parses it with `WorldLandParser` and never downloads map data.

To regenerate:

```bash
python3 -I tools/worldmap/build_world_land.py path/to/ne_110m_land.geojson
python3 -I -B -m unittest discover -s tools/worldmap -p 'test_*.py'
```
