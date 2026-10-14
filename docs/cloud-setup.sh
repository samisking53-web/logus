#!/bin/bash
# LOG US · Claude Code 클라우드 환경 Setup script
#
# 쓰는 곳: Claude 앱 세션 제목 줄의 클라우드 환경 메뉴 → Edit → Setup script 칸에 이 내용을 그대로 붙여 넣는다.
# 하는 일: 안드로이드 SDK(API 35)를 /opt/android-sdk 에 설치해서, Claude 가 클라우드에서
#          `cd android && ./gradlew assembleDebug` 로 빌드를 확인할 수 있게 한다. (Java 21 은 기본으로 들어 있다)
# 함께 할 일: 같은 Edit 화면에서
#   1) Network access → Allowed domains 에 아래 주소를 추가("Allow package managers"는 체크 유지)
#      dl.google.com, maven.google.com, services.gradle.org, github.com, objects.githubusercontent.com,
#      repo.maven.apache.org, plugins.gradle.org, plugins-artifacts.gradle.org
#   2) Environment variables 에 ANDROID_HOME=/opt/android-sdk 추가
# 이미 설치돼 있으면 다시 받지 않는다(여러 번 실행해도 괜찮다).
set -euo pipefail

SDK=/opt/android-sdk
TOOLS_ZIP=commandlinetools-linux-11076708_latest.zip   # Android SDK Command-line Tools 12.0

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[LOG US] 안드로이드 SDK 도구 받는 중…"
  mkdir -p "$SDK/cmdline-tools"
  curl -fsSLo /tmp/cmdtools.zip "https://dl.google.com/android/repository/$TOOLS_ZIP"
  rm -rf "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/cmdline-tools"
  unzip -q /tmp/cmdtools.zip -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -f /tmp/cmdtools.zip
fi

# 라이선스 동의 → 빌드에 필요한 것만 설치(앱 compileSdk 35, AGP 8.7 기본 build-tools 34)
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0" "build-tools;34.0.0" >/dev/null

# Claude 가 쓰는 셸에서도 SDK 위치를 알도록
echo "export ANDROID_HOME=$SDK" > /etc/profile.d/android-sdk.sh 2>/dev/null || true
grep -qs "ANDROID_HOME=$SDK" ~/.bashrc || echo "export ANDROID_HOME=$SDK" >> ~/.bashrc

echo "[LOG US] 안드로이드 SDK 준비 완료: $SDK"
