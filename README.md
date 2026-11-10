# Road Canvas : TimeLine

달리면 자동으로 채워지는 나만의 여행 지도, 말 한마디로 완성되는 다구간 여행 내비.

차가 실제로 달린 길을 기록해 방문한 시·군·구를 지도에 색칠하고, 지나온 경로를 타임랩스로 다시 보여 주며, 여러 목적지를 말하면 들를 순서를 정해 기본 내비에 이어서 안내하는 Pleos Connect 차량용 여행 앱입니다.

> 개발 중입니다. 현재 **Auto Canvas**(위치 기록 → 시·군·구 판정 → 지도 색칠)가 에뮬레이터에서 동작합니다.

## 주요 기능

| 기능 | 설명 | 상태 |
|---|---|---|
| Auto Canvas | 주행 위치를 시·군·구 경계와 대조해 방문 지역을 자동 색칠 | 동작 |
| Canvas Quest | 아직 안 가 본 지역을 찾아 다음 여행지로 추천 | 계획 |
| Smart Detour | 지금 경로에서 조금만 돌아가면 들를 수 있는 새 지역 추천 | 계획 |
| Route Replay | 지나온 경로를 지도 위에 시간순으로 재생 | 동작 |
| Collection | 방문 확정 장소 스티커와 지역 탐험 미션 | 계획 |

주행 중에는 음성만 쓰고, 지도 꾸미기·타임랩스 같은 화면 조작은 기어 P 상태에서만 열립니다.

### Auto Canvas 동작 방식

- 사용자가 **여행 시작**을 누르고 위치 권한을 허용해야 기록합니다. 기록은 기기 안에만 저장합니다.
- Pleos FusedLocation으로 받은 좌표를 시·군·구 경계와 대조해 처음 지나간 곳은 **통과**, 한곳에 머문 곳은 **머문 곳(방문 후보)** 으로 칠합니다. 통과만으로 방문을 확정하지 않습니다.
- 색칠 기준을 **시·도 / 시·군·구 / 읍·면·동** 세 단계로 바꿔 볼 수 있습니다. 판정은 늘 세 단계를 함께 하고, 진행률은 단계에 맞춰(전국 시·도 중 / 지금 시·도 안 / 지금 시·군·구 안) 보여 줍니다.
- 지도는 방문한 순서대로 무지개색으로 칠하고(머문 곳은 진하게), 이번 여행 범위로 자동 확대합니다. 경로선은 차가 실제로 지나간 위치 기록을 그대로 잇습니다.
- 정차 중에는 지도를 끌어서 옮기고 두 손가락으로 확대할 수 있으며, 두 번 탭하면 여행 범위로 돌아옵니다. 주행 중에는 지도 조작이 잠깁니다.
- 여행 타임라인에 출발 · 첫 방문 · 머문 곳 · 여행 종료 요약이 시간순으로 쌓입니다.
- 처음 들어온 시·도와 처음 지나는 시·군·구마다 한 번, 짧은 음성으로 알려 줍니다(읍·면·동은 너무 잦아 화면에만 기록).
- 내비가 화면 앞에 있어도 기록이 이어지도록 포그라운드 서비스로 동작합니다.

### Route Replay 동작 방식

- **타임랩스 보기**를 누르거나, 주행하다 기어를 **P(주차)** 로 바꾸면 이번 여행을 약 14초 동안 다시 보여 줍니다.
- 경로선이 시간순으로 그려지고, 처음 지난 지역이 그 순간에 칠해지며, 거리·새 방문 지역·진행률·타임라인도 그 시점 값으로 함께 움직입니다.
- 이번 여행 전에 다녀온 지역은 처음부터 칠해진 채로 시작합니다.

## 테스트 환경

- Pleos Connect Emulator 2.x (Android 14 / API 34, x86_64)
- Android Studio, JDK 17 이상 (Gradle 실행용 JVM은 17~22)
- 일반 휴대폰 에뮬레이터에는 Pleos 차량 서비스가 없어 동작하지 않습니다.

## 실행 방법

1. Android Studio → Settings → Android SDK → SDK Update Sites에 아래 주소를 추가하고, SDK Platforms에서 **Pleos Connect v2.1.0 APIs (API 34)** 이미지를 설치합니다.
   ```
   https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
   ```
2. Device Manager에서 Automotive → Pleos Connect v2.1.0 기기를 만들고 실행합니다.
   마이크 입력이 필요하면 `scripts/start_emulator.sh`로 실행합니다(`-allow-host-audio` 포함).
3. Pleos Playground에서 발급받은 키를 `local.properties`에 넣습니다(저장소에는 올리지 않습니다).
   ```
   pleos.clientId=...
   pleos.clientSecret=...
   ```
4. 빌드·설치
   ```bash
   ./gradlew installDebug
   ```

에뮬레이터 차량 상태(기어 등)는 Virtual Console로 바꿀 수 있습니다: `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/`.

## 시연: 동해안 주행 재현

앱에서 **여행 시작**을 누른 뒤, 실제 도로 모양의 GPS 경로를 주입합니다(서울 → 홍천 → 인제 → 속초(머묾) → 양양 → 강릉, 약 267km를 3분 안팎으로 압축).

```bash
python scripts/demo_trip.py
```

경로 파일 `scripts/routes/east_coast.json`은 `scripts/fetch_route.py`로 한 번 받아 저장해 둔 것이라 시연 때 인터넷이 필요 없습니다.

디버그 빌드는 시간을 압축한 시연을 위해 체류 판정 시간(30초)과 위치 점프 허용 범위를 넓혀 두었습니다. 릴리스 빌드는 실제 주행 기준(체류 5분, 시속 약 250km 이상 점프 무시)을 씁니다.

## 데이터 출처

- 행정구역 경계(시·도 / 시·군·구 / 읍·면·동, 2026-10-01 기준): 본 데이터는 통계청 통계지리정보서비스(SGIS, https://sgis.kostat.go.kr)에서 공공누리 제1유형으로 개방한 행정동 경계를 가공한 것이며(가공: [vuski/admdongkor](https://github.com/vuski/admdongkor)), CC BY 4.0으로 배포됩니다. `scripts/build_regions.py`로 단계별로 합치고 단순화해 `app/src/main/assets/regions_*.json`에 포함했습니다.
- 시연용 도로 경로: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors(ODbL), [OSRM](https://project-osrm.org/) 길찾기 결과

## APK

제출용 release APK는 `apk/` 폴더에 올릴 예정입니다.
