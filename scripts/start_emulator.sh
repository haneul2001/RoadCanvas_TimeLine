#!/usr/bin/env bash
# Pleos 에뮬레이터를 시연 설정으로 켠다.
# -allow-host-audio: 노트북 마이크 소리를 에뮬레이터로 보낸다(없으면 녹음이 무음이 된다).
set -euo pipefail

AVD="${AVD:-Pleos_Connect_v2.1.0}"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"

"$SDK/emulator/emulator" -avd "$AVD" -allow-host-audio -netdelay none -netspeed full &

echo "부팅 대기 중..."
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done

# Virtual Console (브라우저로 http://127.0.0.1:8088/ 열어 두기)
adb forward tcp:8088 tcp:8088 > /dev/null
echo "준비 완료. Virtual Console: http://127.0.0.1:8088/"
