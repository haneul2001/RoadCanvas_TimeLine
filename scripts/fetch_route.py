"""
시연용 주행 경로를 실제 도로 모양으로 받아 저장한다(한 번만 실행, 결과는 저장소에 포함).

OpenStreetMap 기반 OSRM 공개 서버에서 구간별 도로 경로를 받아
scripts/routes/east_coast.json 으로 저장한다. 보내는 정보는 아래 도시 좌표뿐이다.

사용: python scripts/fetch_route.py
"""
import json
import time
import urllib.request
from pathlib import Path

STOPS = [
    ("서울", 37.5759, 126.9768),
    ("홍천", 37.6970, 127.8888),
    ("인제", 38.0697, 128.1704),
    ("속초", 38.1906, 128.6025),  # 속초 해변(숙소에서 머무는 장면)
    ("양양", 38.0754, 128.6190),
    ("강릉", 37.7519, 128.8761),
]
STAY_AT = ["속초"]
OSRM = "https://router.project-osrm.org/route/v1/driving/{a_lon},{a_lat};{b_lon},{b_lat}?overview=full&geometries=geojson"


def main():
    legs = []
    for (name_a, a_lat, a_lon), (name_b, b_lat, b_lon) in zip(STOPS, STOPS[1:]):
        url = OSRM.format(a_lon=a_lon, a_lat=a_lat, b_lon=b_lon, b_lat=b_lat)
        req = urllib.request.Request(url, headers={"User-Agent": "road-canvas-demo"})
        with urllib.request.urlopen(req, timeout=30) as res:
            data = json.load(res)
        route = data["routes"][0]
        # GeoJSON은 [경도, 위도] 순서
        coords = [[round(lat, 6), round(lon, 6)] for lon, lat in route["geometry"]["coordinates"]]
        legs.append({"from": name_a, "to": name_b, "km": round(route["distance"] / 1000, 1), "points": coords})
        print(f"{name_a} → {name_b}: {route['distance'] / 1000:.0f}km, {len(coords)}점", flush=True)
        time.sleep(1)  # 공개 서버 예의상 간격
    out = Path(__file__).resolve().parent / "routes" / "east_coast.json"
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps({"stay_at": STAY_AT, "legs": legs}, ensure_ascii=False), encoding="utf-8")
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
