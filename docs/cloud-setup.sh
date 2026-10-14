#!/bin/bash
# LOG US · Claude Code 클라우드 환경 Setup script (2026-10-14)
#
# 쓰는 곳: Claude 앱 세션 제목 줄의 클라우드 환경 메뉴 → Edit → Setup script 칸에 이 내용을 그대로 붙여 넣는다.
# 하는 일
#   1) 안드로이드 SDK(API 35)를 /opt/android-sdk 에 설치 → Claude 가 `cd android && ./gradlew assembleDebug` 로 빌드 확인
#      (Java 21 은 클라우드에 기본으로 들어 있다)
#   2) Firebase CLI 설치 → 보안 규칙을 고친 사람이 `npm run test:rules`(에뮬레이터 테스트)를 돌릴 수 있게
#   3) 저장소가 이미 받아져 있으면 android/local.properties 와 빌드 확인용 가짜 google-services.json 을 만든다
#      (진짜 파일이 있으면 건드리지 않는다. 둘 다 .gitignore 에 있어 커밋되지 않는다)
# 함께 할 일: 같은 Edit 화면에서
#   - Network access → Allowed domains 에 아래 주소 추가("Allow package managers"는 체크 유지)
#       dl.google.com, maven.google.com, services.gradle.org, github.com, objects.githubusercontent.com,
#       repo.maven.apache.org, plugins.gradle.org, plugins-artifacts.gradle.org, storage.googleapis.com
#   - Environment variables 에 ANDROID_HOME=/opt/android-sdk 추가
# 이미 설치돼 있으면 다시 받지 않는다(여러 번 실행해도 괜찮다).
set -euo pipefail

SDK=/opt/android-sdk
TOOLS_ZIP=commandlinetools-linux-11076708_latest.zip   # Android SDK Command-line Tools 12.0

# ---- 1) 안드로이드 SDK ----
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

# ---- 2) Firebase CLI(규칙 테스트용). 실패해도 앱 빌드에는 지장 없으니 계속 ----
if ! command -v firebase >/dev/null 2>&1; then
  echo "[LOG US] Firebase CLI 설치 중…"
  npm install -g firebase-tools >/dev/null 2>&1 || echo "[LOG US] Firebase CLI 설치 실패(규칙 테스트만 못 돌림)"
fi

# ---- 3) 저장소가 있으면 빌드 준비 파일 만들기 ----
for settings in /home/user/*/android/settings.gradle.kts; do
  [ -f "$settings" ] || continue
  android_dir=$(dirname "$settings")
  [ -f "$android_dir/local.properties" ] || echo "sdk.dir=$SDK" > "$android_dir/local.properties"
  json="$android_dir/app/google-services.json"
  if [ -d "$android_dir/app" ] && [ ! -f "$json" ]; then
    # 빌드 확인용 가짜 값(진짜 Firebase 에 연결되지 않는다). 진짜 파일은 각자 컴퓨터에만 둔다
    cat > "$json" <<'JSON'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "logus-build-check",
    "storage_bucket": "logus-build-check.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
        "android_client_info": { "package_name": "com.logus.app" }
      },
      "oauth_client": [
        { "client_id": "000000000000-buildcheck.apps.googleusercontent.com", "client_type": 3 }
      ],
      "api_key": [ { "current_key": "BUILD_CHECK_ONLY_NOT_A_REAL_KEY" } ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            { "client_id": "000000000000-buildcheck.apps.googleusercontent.com", "client_type": 3 }
          ]
        }
      }
    }
  ]
}
JSON
    echo "[LOG US] 빌드 확인용 가짜 google-services.json 을 만들었어요: $json"
  fi
done

echo "[LOG US] 준비 완료: 안드로이드 SDK $SDK"
