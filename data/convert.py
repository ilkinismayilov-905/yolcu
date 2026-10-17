#!/usr/bin/env python3
"""data/raw → data/processed (docs/04_DATA.md §2). Stdlib only: no ogr2ogr/geopandas needed.

Usage (repo root):  python3 -I data/convert.py          # convert everything
                    python3 -I data/convert.py --test   # self-check of the KML parser
"""
import csv
import html
import io
import json
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RAW, OUT = ROOT / "raw", ROOT / "processed"


def local(tag):
    return tag.rsplit("}", 1)[-1]


def coords(text):
    pts = []
    for tok in (text or "").split():
        parts = tok.split(",")
        if len(parts) >= 2:
            pts.append([round(float(parts[0]), 6), round(float(parts[1]), 6)])
    return pts


def geometry(el):
    """KML geometry element → GeoJSON geometry (None if unsupported/empty)."""
    t = local(el.tag)
    if t == "Point":
        pts = coords(child_text(el, "coordinates"))
        return {"type": "Point", "coordinates": pts[0]} if pts else None
    if t == "LineString":
        pts = coords(child_text(el, "coordinates"))
        return {"type": "LineString", "coordinates": pts} if len(pts) >= 2 else None
    if t == "LinearRing":
        return {"type": "LinearRing", "coordinates": coords(child_text(el, "coordinates"))}
    if t == "Polygon":
        rings = [coords(r.text) for r in el.iter() if local(r.tag) == "coordinates"]
        return {"type": "Polygon", "coordinates": [r for r in rings if r]} if rings else None
    if t in ("Track", "gx:Track"):
        pts = [[round(float(v), 6) for v in c.text.split()[:2]] for c in el if local(c.tag) == "coord"]
        return {"type": "LineString", "coordinates": pts} if len(pts) >= 2 else None
    if t in ("MultiGeometry", "MultiTrack"):
        parts = [g for g in (geometry(c) for c in el) if g]
        kinds = {g["type"] for g in parts}
        if not parts:
            return None
        if len(parts) == 1:
            return parts[0]
        if kinds == {"LineString"}:
            return {"type": "MultiLineString", "coordinates": [g["coordinates"] for g in parts]}
        if kinds == {"Point"}:
            return {"type": "MultiPoint", "coordinates": [g["coordinates"] for g in parts]}
        if kinds == {"Polygon"}:
            return {"type": "MultiPolygon", "coordinates": [g["coordinates"] for g in parts]}
        return {"type": "GeometryCollection", "geometries": parts}
    return None


def child_text(el, name):
    for c in el.iter():
        if local(c.tag) == name:
            return (c.text or "").strip()
    return ""


GEOM_TAGS = {"Point", "LineString", "Polygon", "MultiGeometry", "Track", "MultiTrack"}


def placemarks(kml_bytes):
    root = ET.fromstring(kml_bytes)
    for pm in root.iter():
        if local(pm.tag) != "Placemark":
            continue
        props, geom, has_when = {}, None, False
        for c in pm:
            t = local(c.tag)
            if t in ("name", "description", "styleUrl"):
                props[t] = (c.text or "").strip()
            elif t == "ExtendedData":
                for d in c.iter():
                    if local(d.tag) == "Data":
                        props[d.get("name")] = child_text(d, "value")
                    elif local(d.tag) == "SimpleData":
                        props[d.get("name")] = (d.text or "").strip()
            elif t in GEOM_TAGS and geom is None:
                geom = geometry(c)
        has_when = any(local(e.tag) in ("when", "TimeStamp", "TimeSpan") for e in pm.iter())
        if geom:
            yield {"type": "Feature", "properties": props, "geometry": geom}, has_when


def in_baku(geom):
    """Rough Absheron box; drops Google Earth sample placemarks left in the source KMZ."""
    c = geom["coordinates"]
    while isinstance(c[0], list):
        c = c[0]
    return 40.0 < c[1] < 41.0 and 49.0 < c[0] < 51.0


def html_table_props(props):
    """ArcGIS exports put attributes in an HTML <td>key</td><td>value</td> table inside description."""
    desc = props.get("description")
    if isinstance(desc, dict):  # GeoJSON variant: {"@type": "html", "value": "..."}
        desc = desc.get("value", "")
    if not desc or "<td" not in desc:
        return props
    cells = [html.unescape(re.sub(r"<[^>]+>", "", c)).strip() for c in re.findall(r"<td[^>]*>(.*?)</td>", desc, re.S)]
    out = {k: v for k, v in props.items() if k != "description"}
    # first cell is the title row; then key/value pairs
    for k, v in zip(cells[1::2], cells[2::2]):
        if k and k not in out:
            out[k] = v
    return out


def kmz_to_geojson(src, dst, keep=lambda f: True):
    with zipfile.ZipFile(src) as z:
        kml = next(n for n in z.namelist() if n.lower().endswith(".kml"))
        data = z.read(kml)
    feats, when = [], False
    for f, w in placemarks(data):
        f["properties"] = html_table_props(f["properties"])
        if keep(f):
            feats.append(f)
            when |= w
    write(dst, feats)
    return when, len(feats)


def csv_rows(path):
    raw = path.read_bytes()
    for enc in ("utf-8-sig", "cp1254", "latin-1"):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    dialect = csv.Sniffer().sniff(text[:4096], delimiters=",;\t")
    return list(csv.DictReader(io.StringIO(text), dialect=dialect))


def find_col(cols, *patterns):
    for p in patterns:
        for c in cols:
            if re.search(p, c, re.I):
                return c
    return None


def num(v):
    return float(str(v).strip().replace(",", "."))


def metro_exits(src, dst):
    rows = csv_rows(src)
    cols = list(rows[0].keys())
    lat = find_col(cols, r"^lat", r"latitude", r"enlik", r"^y$")
    lon = find_col(cols, r"^lon", r"^lng", r"longitude", r"uzunluq", r"^x$")
    station = find_col(cols, r"stan", r"station", r"stop_name", r"name", r"ad")
    exit_ = find_col(cols, r"exit", r"çıxış", r"cixis", r"pathway_id", r"id")
    feats = []
    for r in rows:
        try:
            la, lo = num(r[lat]), num(r[lon])
        except (TypeError, ValueError, KeyError):
            continue
        if abs(la) > 1000:  # source stores degrees × 10^6 as integers (40421499 → 40.421499)
            la, lo = la / 1e6, lo / 1e6
        if not (40.0 < la < 41.0 and 49.0 < lo < 51.0):  # Baku sanity box
            la, lo = lo, la
            if not (40.0 < la < 41.0 and 49.0 < lo < 51.0):
                continue
        props = {k: v for k, v in r.items() if k not in (lat, lon)}
        props = {k.strip(): (v or "").strip() for k, v in props.items()}
        props["station"] = (r.get(station) or "").strip() if station else ""
        if re.match(r"\d{2}\.\d{2}\.\d{4}", props["station"]):  # Excel turned "28 May" into a date
            props["station"] = props["Stansiya"] = props.get("Station", "")
        props["exit"] = (r.get(exit_) or "").strip() if exit_ else ""
        feats.append({"type": "Feature", "properties": props,
                      "geometry": {"type": "Point", "coordinates": [round(lo, 6), round(la, 6)]}})
    write(dst, feats)
    return cols, (lat, lon, station, exit_)


# ridership CSV spellings (2025: Azerbaijani, 2026: Latin transliteration) → metro_exits station names
STATION_ALIASES = {
    "Koroglu": "Koroğlu", "Xətai": "Şah İsmayıl Xətai", "Memar Əcəmi-2": "Memar Əcəmi 2",
    "Azadlig Prospekti": "Azadlıq prospekti", "Elmlar Akademiyasi": "Elmlər Akademiyası",
    "Inshaatchilar": "İnşaatçılar", "Jafar Jabbarli": "Cəfər Cabbarlı", "Khatai": "Şah İsmayıl Xətai",
    "Khocasen": "Xocəsən", "Koroghlu": "Koroğlu",
}


def norm(name):
    return re.sub(r"[^0-9a-z]", "", name.lower())


def metro_ridership(exits_path, csv_paths, dst):
    """One Point per station (centroid of its exits) with average daily ridership per year and month."""
    pts, by_latin = {}, {}
    for f in json.loads(exits_path.read_text(encoding="utf-8"))["features"]:
        pts.setdefault(f["properties"]["station"], []).append(f["geometry"]["coordinates"])
        by_latin[norm(f["properties"].get("Station", ""))] = f["properties"]["station"]
    sums = {}
    for p in csv_paths:
        for r in csv_rows(p):
            vals = list(r.values())
            day, name, count = vals[0][:10], vals[1].strip(), vals[2]
            name = STATION_ALIASES.get(name) or (name if name in pts else by_latin.get(norm(name), name))
            try:
                n = int(float(count))
            except ValueError:
                continue
            for key in (day[:4], day[:7]):
                s, c = sums.setdefault(name, {}).get(key, (0, 0))
                sums[name][key] = (s + n, c + 1)
    feats, unmatched = [], []
    for name, by in sorted(sums.items()):
        if name not in pts:
            unmatched.append(name)
            continue
        xs = pts[name]
        lon, lat = sum(x[0] for x in xs) / len(xs), sum(x[1] for x in xs) / len(xs)
        props = {"station": name, "exits": len(xs)}
        props.update({f"avgDaily{k}": round(s / c) for k, (s, c) in sorted(by.items()) if len(k) == 4})
        props["monthlyAvgDaily"] = {k: round(s / c) for k, (s, c) in sorted(by.items()) if len(k) == 7}
        feats.append({"type": "Feature", "properties": props,
                      "geometry": {"type": "Point", "coordinates": [round(lon, 6), round(lat, 6)]}})
    write(dst, feats)
    return unmatched


def write(dst, feats):
    dst.write_text(json.dumps({"type": "FeatureCollection", "features": feats},
                              ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def summary(path):
    fc = json.loads(path.read_text(encoding="utf-8"))
    feats = fc["features"]
    types, keys = {}, {}
    for f in feats:
        g = (f.get("geometry") or {}).get("type", "null")
        types[g] = types.get(g, 0) + 1
        for k in (f.get("properties") or {}):
            keys[k] = keys.get(k, 0) + 1
    return len(feats), types, keys, feats[:1]


def main():
    OUT.mkdir(exist_ok=True)
    report = {}
    keep = {
        "routes": lambda f: True,
        # parkings KMZ also holds 44 bike-lane lines (duplicates of bike_lanes) and Google sample points
        "bike_parkings": lambda f: f["geometry"]["type"] == "Point" and in_baku(f["geometry"]),
        "bike_lanes": lambda f: in_baku(f["geometry"]),
    }
    for name, k in keep.items():
        src = RAW / f"{name}.kmz"
        if src.exists():
            when, n = kmz_to_geojson(src, OUT / f"{name}.geojson", k)
            report[name] = {"when": when}
        else:
            print(f"MISSING {src}")
    routes = OUT / "routes.geojson"
    if routes.exists():  # route number lives in the Placemark <name> (== table "Name")
        fc = json.loads(routes.read_text(encoding="utf-8"))
        for f in fc["features"]:
            f["properties"]["route"] = f["properties"].get("name", "")
        write(routes, fc["features"])
    src = RAW / "terminals.geojson"
    if src.exists():
        fc = json.loads(src.read_text(encoding="utf-8-sig"))
        for f in fc["features"]:
            props = html_table_props(f.get("properties") or {})
            f["properties"] = {k: v for k, v in props.items() if not re.match(r"(fill|stroke|label)", k)}
        write(OUT / "terminals.geojson", fc["features"])
    src = RAW / "metro_exits.csv"
    if src.exists():
        cols, picked = metro_exits(src, OUT / "metro_exits.geojson")
        report["metro_exits"] = {"csv_columns": cols, "lat,lon,station,exit": picked}
    ridership = sorted(RAW.glob("metro_ridership*.csv"))
    for p in ridership:
        rows = csv_rows(p)
        report[p.stem] = {"rows": len(rows), "columns": list(rows[0].keys()), "first": rows[0]}
    if ridership and (OUT / "metro_exits.geojson").exists():
        report["metro_ridership"] = {"unmatched": metro_ridership(OUT / "metro_exits.geojson", ridership,
                                                                  OUT / "metro_ridership.geojson")}
    for p in sorted(OUT.glob("*.geojson")):
        n, types, keys, first = summary(p)
        report.setdefault(p.stem, {}).update({"features": n, "geometry": types, "properties": keys})
    print(json.dumps(report, ensure_ascii=False, indent=1))


def self_test():
    kml = b"""<?xml version="1.0"?><kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2"><Document>
      <Placemark><name>88</name><ExtendedData><Data name="route"><value>88</value></Data></ExtendedData>
        <LineString><coordinates>49.8,40.37,0 49.81,40.38,0</coordinates></LineString></Placemark>
      <Placemark><name>P1</name><Point><coordinates>49.85,40.4</coordinates></Point></Placemark>
      <Placemark><name>T</name><gx:Track><when>2024-01-01T00:00:00Z</when><gx:coord>49.8 40.3 0</gx:coord>
        <when>2024-01-01T00:01:00Z</when><gx:coord>49.9 40.4 0</gx:coord></gx:Track></Placemark>
      <Placemark><name>M</name><MultiGeometry><LineString><coordinates>1,2 3,4</coordinates></LineString>
        <LineString><coordinates>5,6 7,8</coordinates></LineString></MultiGeometry></Placemark>
    </Document></kml>"""
    out = list(placemarks(kml))
    assert [f["geometry"]["type"] for f, _ in out] == ["LineString", "Point", "LineString", "MultiLineString"], out
    assert out[0][0]["properties"] == {"name": "88", "route": "88"}
    assert out[1][0]["geometry"]["coordinates"] == [49.85, 40.4]
    assert [w for _, w in out] == [False, False, True, False]
    props = html_table_props({"name": "135", "description": "<table><tr><td>135</td></tr><tr><td>FID</td><td>0</td></tr><tr><td>Name</td><td>135</td></tr></table>"})
    assert props == {"name": "135", "FID": "0", "Name": "135"}, props
    assert in_baku({"type": "Point", "coordinates": [49.85, 40.4]}) and not in_baku({"type": "Point", "coordinates": [2.29, 48.86]})
    assert norm("28-May") == norm("28 May")
    print("self-test ok")


if __name__ == "__main__":
    self_test() if "--test" in sys.argv else main()
