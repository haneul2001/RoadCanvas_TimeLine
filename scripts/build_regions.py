"""
시·군·구 경계 GeoJSON을 앱용 경량 JSON으로 변환한다.

입력: 통계청 2018 시·군·구 경계(GeoJSON, southkorea-maps 저장소의 kostat/2018 데이터)
출력: app/src/main/assets/regions.json
  [{"code": "11010", "name": "종로구", "province": "서울특별시",
    "rings": [[lon, lat, lon, lat, ...], ...]}, ...]

사용: python scripts/build_regions.py <입력 GeoJSON> [허용 오차(도), 기본 0.001]
"""
import json
import sys
from pathlib import Path

PROVINCES = {
    "11": "서울특별시", "21": "부산광역시", "22": "대구광역시", "23": "인천광역시",
    "24": "광주광역시", "25": "대전광역시", "26": "울산광역시", "29": "세종특별자치시",
    "31": "경기도", "32": "강원특별자치도", "33": "충청북도", "34": "충청남도",
    "35": "전북특별자치도", "36": "전라남도", "37": "경상북도", "38": "경상남도",
    "39": "제주특별자치도",
}


def simplify(points, tolerance):
    """Douglas-Peucker. points: [(x, y), ...]"""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    tol2 = tolerance * tolerance
    while stack:
        start, end = stack.pop()
        ax, ay = points[start]
        bx, by = points[end]
        dx, dy = bx - ax, by - ay
        length2 = dx * dx + dy * dy
        best, best_i = -1.0, -1
        for i in range(start + 1, end):
            px, py = points[i]
            if length2 == 0:
                d2 = (px - ax) ** 2 + (py - ay) ** 2
            else:
                t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2))
                d2 = (px - ax - t * dx) ** 2 + (py - ay - t * dy) ** 2
            if d2 > best:
                best, best_i = d2, i
        if best > tol2:
            keep[best_i] = True
            stack.append((start, best_i))
            stack.append((best_i, end))
    return [p for p, k in zip(points, keep) if k]


def main():
    src = Path(sys.argv[1])
    tolerance = float(sys.argv[2]) if len(sys.argv) > 2 else 0.001
    out = Path(__file__).resolve().parent.parent / "app/src/main/assets/regions.json"

    data = json.loads(src.read_text(encoding="utf-8"))
    regions = []
    total = 0
    for feature in data["features"]:
        props = feature["properties"]
        geom = feature["geometry"]
        polygons = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
        rings = []
        # 바깥 경계만 쓴다(구멍은 판정·표시에 영향이 작다).
        for polygon in polygons:
            ring = simplify([tuple(p[:2]) for p in polygon[0]], tolerance)
            if len(ring) < 4:
                continue
            rings.append([round(v, 5) for p in ring for v in p])
            total += len(ring)
        if rings:
            code = props["code"]
            regions.append({
                "code": code,
                "name": props["name"],
                "province": PROVINCES.get(code[:2], ""),
                "rings": rings,
            })

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(regions, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(regions)} regions, {total} points, {out.stat().st_size // 1024} KB -> {out}")


if __name__ == "__main__":
    main()
