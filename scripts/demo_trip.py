"""
에뮬레이터에 GPS 경로를 주입해 동해안 여행을 압축 재현한다.

경로는 scripts/routes/east_coast.json(실제 도로 모양, fetch_route.py로 생성)을 따라간다.
서울 → 홍천 → 인제 → 속초(잠시 머묾) → 양양 → 강릉

준비: 에뮬레이터 실행, 앱에서 '여행 시작'
사용: python scripts/demo_trip.py [점 간격 km, 기본 0.5] [주입 간격 초, 기본 0.25]
"""
import json
import math
import subprocess
import sys
import time
from pathlib import Path

ROUTE_FILE = Path(__file__).resolve().parent / "routes" / "east_coast.json"
STAY_SECONDS = 40


def km(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


def resample(points, step_km):
    """도로 좌표열을 따라 step_km 간격의 점을 만든다(굽은 길 모양을 유지)."""
    out = [points[0]]
    carry = 0.0
    for a, b in zip(points, points[1:]):
        seg = km(a, b)
        pos = step_km - carry
        while pos <= seg:
            t = pos / seg
            out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
            pos += step_km
        carry = seg - (pos - step_km)
    if out[-1] != tuple(points[-1]):
        out.append(tuple(points[-1]))
    return out


def fix(lat, lon):
    # geo fix는 경도, 위도 순서
    subprocess.run(["adb", "emu", "geo", "fix", f"{lon:.6f}", f"{lat:.6f}"], check=True, capture_output=True)


def main():
    step_km = float(sys.argv[1]) if len(sys.argv) > 1 else 0.5
    interval = float(sys.argv[2]) if len(sys.argv) > 2 else 0.25
    route = json.loads(ROUTE_FILE.read_text(encoding="utf-8"))
    total = 0.0
    for leg in route["legs"]:
        points = resample([tuple(p) for p in leg["points"]], step_km)
        print(f"{leg['from']} → {leg['to']}  {leg['km']:.0f}km", flush=True)
        for lat, lon in points:
            fix(lat, lon)
            time.sleep(interval)
        total += leg["km"]
        if leg["to"] in route["stay_at"]:
            print(f"{leg['to']}에서 {STAY_SECONDS}초 머묾", flush=True)
            lat, lon = points[-1]
            end = time.time() + STAY_SECONDS
            while time.time() < end:
                fix(lat, lon)
                time.sleep(2)
    print(f"완료: 약 {total:.0f}km", flush=True)


if __name__ == "__main__":
    main()
