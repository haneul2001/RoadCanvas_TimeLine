# RoadCanvas : TimeLine

달리면 자동으로 채워지는 나만의 여행 지도, 말 한마디로 완성되는 다구간 여행 내비.

차가 실제로 달린 길을 기록해 방문한 시·군·구를 지도에 색칠하고, 지나온 경로를 타임랩스로 다시 보여 주며, 여러 목적지를 말하면 들를 순서를 정해 기본 내비에 이어서 안내하는 Pleos Connect 차량용 여행 앱입니다.

> 개발 중입니다. 현재는 Pleos SDK(Vehicle·NaviHelper·TextToSpeech) 연결 상태를 보여 주는 단계입니다.

## 주요 기능 (계획)

| 기능 | 설명 |
|---|---|
| Auto Canvas | 주행 위치를 시·군·구 경계와 대조해 방문 지역을 자동 색칠 |
| Canvas Quest | 아직 안 가 본 지역을 찾아 다음 여행지로 추천 |
| Smart Detour | 지금 경로에서 조금만 돌아가면 들를 수 있는 새 지역 추천 |
| Route Replay | 지나온 경로를 지도 위에 시간순으로 재생 |
| Collection | 방문 확정 장소 스티커와 지역 탐험 미션 |

주행 중에는 음성만 쓰고, 지도 꾸미기·타임랩스 같은 화면 조작은 기어 P 상태에서만 열립니다.

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

## APK

제출용 release APK는 `apk/` 폴더에 올릴 예정입니다.
