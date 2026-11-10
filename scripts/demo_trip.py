"""
에뮬레이터에 GPS 경로를 주입해 동해안 여행을 압축 재현한다.

서울(광화문) → 가평 → 홍천 → 인제 → 속초(잠시 머묾) → 양양 → 강릉

준비: 에뮬레이터 실행, 앱에서 '여행 기록 시작'
사용: python scripts/demo_trip.py [점 간격 km, 기본 1.5] [주입 간격 초, 기본 0.4]
"""
import math
import subprocess
import sys
import time

ROUTE = [
    ("광화문", 37.5759, 126.9768),
    ("구리", 37.5943, 127.1296),
    ("화도", 37.6500, 127.3000),
    ("청평", 37.7350, 127.4200),
    ("홍천", 37.6970, 127.8888),
    ("인제", 38.0697, 128.1704),
    ("속초", 38.2070, 128.5918),
    ("양양", 38.0754, 128.6190),
    ("강릉", 37.7519, 128.8761),
]
STAY_AT = "속초"
STAY_SECONDS = 40


def km(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


def fix(lat, lon):
    # geo fix는 경도, 위도 순서
    subprocess.run(["adb", "emu", "geo", "fix", f"{lon:.6f}", f"{lat:.6f}"], check=True, capture_output=True)


def main():
    step_km = float(sys.argv[1]) if len(sys.argv) > 1 else 1.5
    interval = float(sys.argv[2]) if len(sys.argv) > 2 else 0.4
    total = 0.0
    for (name_a, *a), (name_b, *b) in zip(ROUTE, ROUTE[1:]):
        dist = km(a, b)
        steps = max(1, round(dist / step_km))
        print(f"{name_a} → {name_b}  {dist:.0f}km", flush=True)
        for i in range(1, steps + 1):
            t = i / steps
            fix(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
            time.sleep(interval)
        total += dist
        if name_b == STAY_AT:
            print(f"{STAY_AT}에서 {STAY_SECONDS}초 머묾", flush=True)
            end = time.time() + STAY_SECONDS
            while time.time() < end:
                fix(b[0], b[1])
                time.sleep(2)
    print(f"완료: 약 {total:.0f}km", flush=True)


if __name__ == "__main__":
    main()
