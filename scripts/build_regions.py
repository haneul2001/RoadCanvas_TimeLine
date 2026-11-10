"""
행정동 경계 GeoJSON에서 3단계(시·도 / 시·군·구 / 읍·면·동) 경계를 만들어 앱 assets에 넣는다.

입력: vuski/admdongkor 의 HangJeongDong_verYYYYMMDD.geojson
      (통계청 SGIS 행정동 경계, 공공누리 제1유형 · 가공 CC BY 4.0)
출력: app/src/main/assets/regions_sido.json, regions_sgg.json, regions_dong.json
  [{"code", "name", "province", "parent", "rings": [[lon, lat, ...], ...]}, ...]

시·군·구와 시·도 경계는 읍·면·동을 합쳐서 만든다(shapely 필요).
사용: python scripts/build_regions.py <행정동 GeoJSON>
"""
import json
import sys
from collections import defaultdict
from pathlib import Path

from shapely.geometry import MultiPolygon, Polygon, shape
from shapely.ops import unary_union

# 단계별 단순화 허용 오차(도). 작은 단계일수록 촘촘하게.
TOLERANCE = {"sido": 0.004, "sgg": 0.0015, "dong": 0.0004}


def rings_of(geom, tolerance):
    geom = geom.simplify(tolerance, preserve_topology=True)
    polygons = geom.geoms if isinstance(geom, MultiPolygon) else [geom] if isinstance(geom, Polygon) else []
    out = []
    for poly in polygons:
        coords = list(poly.exterior.coords)
        if len(coords) < 4:
            continue
        out.append([round(v, 5) for c in coords for v in c[:2]])
    return out


def main():
    src = Path(sys.argv[1])
    assets = Path(__file__).resolve().parent.parent / "app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)

    data = json.loads(src.read_text(encoding="utf-8"))
    dongs, sgg_geoms, sido_geoms = [], defaultdict(list), defaultdict(list)
    sgg_info, sido_name = {}, {}
    for f in data["features"]:
        p = f["properties"]
        geom = shape(f["geometry"]).buffer(0)  # 잘못 꼬인 폴리곤 정리
        name = p["adm_nm"].split()[-1]
        dongs.append((p["adm_cd2"], name, p["sidonm"], p["sgg"], geom))
        sgg_geoms[p["sgg"]].append(geom)
        sgg_info[p["sgg"]] = (p["sggnm"], p["sidonm"], p["sido"])
        sido_geoms[p["sido"]].append(geom)
        sido_name[p["sido"]] = p["sidonm"]

    levels = {"dong": [], "sgg": [], "sido": []}
    for code, name, province, parent, geom in dongs:
        levels["dong"].append({"code": code, "name": name, "province": province, "parent": parent,
                               "rings": rings_of(geom, TOLERANCE["dong"])})
    for code, geoms in sgg_geoms.items():
        name, province, sido = sgg_info[code]
        levels["sgg"].append({"code": code, "name": name, "province": province, "parent": sido,
                              "rings": rings_of(unary_union(geoms), TOLERANCE["sgg"])})
    for code, geoms in sido_geoms.items():
        levels["sido"].append({"code": code, "name": sido_name[code], "province": sido_name[code], "parent": "",
                               "rings": rings_of(unary_union(geoms), TOLERANCE["sido"])})

    for level, regions in levels.items():
        regions = [r for r in regions if r["rings"]]
        regions.sort(key=lambda r: r["code"])
        out = assets / f"regions_{level}.json"
        out.write_text(json.dumps(regions, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        points = sum(len(ring) // 2 for r in regions for ring in r["rings"])
        print(f"{level}: {len(regions)} regions, {points} points, {out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
