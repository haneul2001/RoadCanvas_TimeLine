"""
테스트 주행용 도시 간 도로 경로를 받아 앱 assets에 넣는다(개발 PC에서 한 번 실행, 결과는 저장소에 포함).

내비 SDK는 경로 좌표를 주지 않고, 앱이 실행 중에 외부 길찾기 서버를 부르면 실제 차 위치가 밖으로 나가므로,
주요 도시 사이의 도로 모양을 미리 받아 둔다. OpenStreetMap 기반 OSRM 공개 서버에 보내는 정보는 아래 도시 좌표뿐이다.

결과: app/src/main/assets/city_roads.json
  {"cities": [[이름, 위도, 경도], ...], "routes": [{"a": i, "b": j, "km": 거리, "points": [[위도, 경도], ...]}, ...]}
  points는 약 250m 간격으로 줄인 도로 좌표(a → b 방향). b → a는 뒤집어 쓴다.

사용: python scripts/fetch_city_roads.py
"""
import itertools
import json
import math
import time
import urllib.request
from pathlib import Path

CITIES = [
    ("서울", 37.5663, 126.9779),
    ("춘천", 37.8813, 127.7298),
    ("홍천", 37.6970, 127.8888),
    ("인제", 38.0697, 128.1704),
    ("속초", 38.2070, 128.5918),
    ("양양", 38.0754, 128.6190),
    ("강릉", 37.7519, 128.8761),
    ("대전", 36.3504, 127.3845),
    ("전주", 35.8242, 127.1480),
    ("광주", 35.1601, 126.8514),
    ("여수", 34.7604, 127.6622),
    ("대구", 35.8714, 128.6014),
    ("경주", 35.8562, 129.2247),
    ("부산", 35.1798, 129.0750),
]
OSRM = "https://router.project-osrm.org/route/v1/driving/{a_lon},{a_lat};{b_lon},{b_lat}?overview=full&geometries=geojson"
STEP_KM = 0.25


def km(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


def thin(points):
    out = [points[0]]
    for p in points[1:-1]:
        if km(out[-1], p) >= STEP_KM:
            out.append(p)
    out.append(points[-1])
    return out


def main():
    routes = []
    for (i, a), (j, b) in itertools.combinations(enumerate(CITIES), 2):
        url = OSRM.format(a_lon=a[2], a_lat=a[1], b_lon=b[2], b_lat=b[1])
        req = urllib.request.Request(url, headers={"User-Agent": "road-canvas-demo"})
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=30) as res:
                    data = json.load(res)
                break
            except Exception as e:  # 공개 서버가 가끔 끊긴다
                print(f"  retry {a[0]}-{b[0]}: {e}", flush=True)
                time.sleep(3)
        else:
            raise SystemExit(f"실패: {a[0]} → {b[0]}")
        route = data["routes"][0]
        coords = [(round(lat, 5), round(lon, 5)) for lon, lat in route["geometry"]["coordinates"]]
        pts = thin(coords)
        routes.append({"a": i, "b": j, "km": round(route["distance"] / 1000, 1), "points": [list(p) for p in pts]})
        print(f"{a[0]} → {b[0]}: {route['distance'] / 1000:.0f}km, {len(pts)}점", flush=True)
        time.sleep(1)  # 공개 서버 예의상 간격
    out = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "city_roads.json"
    payload = {
        "source": "OpenStreetMap contributors (ODbL), OSRM",
        "cities": [list(c) for c in CITIES],
        "routes": routes,
    }
    out.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"저장: {out} ({out.stat().st_size // 1024}KB)")


if __name__ == "__main__":
    main()
