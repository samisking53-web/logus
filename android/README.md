# LOG US 안드로이드 앱 (Kotlin)

카카오·구글 로그인과 Firebase(로그인·Firestore)를 연결한 네이티브 안드로이드 앱이다.
지금은 **로그인 연동을 확인하는 화면 하나**만 있다. 화면은 Jetpack Compose로 그린다.

웹앱과 같은 Firebase 프로젝트(`logus-80f21`)를 쓴다. 그래서 같은 사람이 웹·안드로이드 어디서 로그인해도 **같은 계정**이다.

## 폴더 구조
```
android/
├─ app/build.gradle.kts        앱 설정(패키지 이름 com.logus.app, 라이브러리)
├─ gradle/libs.versions.toml   라이브러리 버전 모음
└─ app/src/main/java/com/logus/app/
   ├─ MainActivity.kt           앱 시작 지점
   ├─ auth/AuthRepository.kt    카카오·구글 로그인, users/{uid} 프로필 만들기
   ├─ auth/AuthViewModel.kt     로그인 상태(로그아웃·로딩·로그인됨·오류) 관리
   ├─ ui/LoginScreen.kt         로그인 화면
   └─ ui/theme/                 팔레트 색(웹과 같은 값)과 다크 모드
```

## 처음 실행하는 순서
1. **Android Studio**를 설치하고 `android/` 폴더를 연다(저장소 루트가 아니라 `android/`).
   처음 열면 Gradle이 라이브러리를 받느라 몇 분 걸린다.
2. **Firebase 콘솔에 안드로이드 앱 등록** (팀에서 한 번만)
   - 프로젝트 설정 → 일반 → 앱 추가 → Android
   - Android 패키지 이름: `com.logus.app`
3. **SHA-1 지문 등록** (팀원 각자, 자기 컴퓨터마다)
   - Android Studio 오른쪽 Gradle 탭 → `app > Tasks > android > signingReport` 실행
     (또는 터미널에서 `./gradlew signingReport`)
   - 결과의 `Variant: debug` 아래 `SHA1:` 값을 복사한다
   - Firebase 콘솔 → 프로젝트 설정 → 내 앱(Android) → 디지털 지문 추가에 붙여 넣는다
   - SHA-1이 없으면 구글 로그인과 카카오 로그인이 모두 실패한다
4. **google-services.json 받기**
   - 3번까지 끝낸 뒤 같은 화면에서 `google-services.json`을 내려받아 `android/app/` 에 넣는다
   - 구글 로그인을 켜거나 SHA-1을 추가한 뒤에는 **다시 내려받아야** 한다
   - 이 파일은 `.gitignore`에 있어서 커밋되지 않는다. 팀원 각자 콘솔에서 받는다
5. **로그인 설정**: `docs/login-setup.txt`의 3~5단계(구글·카카오·Firebase 연결)와 7단계(안드로이드 추가 설정)를 마친다
6. 폰을 USB로 연결하고(개발자 옵션 → USB 디버깅 켜기) Android Studio에서 ▶ Run

## 확인할 것
- 카카오 로그인: 버튼을 누르면 카카오 로그인 화면이 열리고, 로그인 후 "○○님, 환영해요!"가 나온다
- 구글 로그인: 폰에 있는 구글 계정을 고르는 창이 뜬다
- Firebase 콘솔 → Authentication → 사용자에 계정이 생긴다
- Firestore → `users/{uid}` 문서가 생긴다. 안 생기면 Logcat에서 `AuthRepository`를 검색해 경고를 확인한다
  (보안 규칙을 아직 배포하지 않았으면 프로필 만들기가 거부된다)

## 알아 둘 것
- **카카오 로그인은 카카오 SDK가 아니라 Firebase가 여는 카카오 로그인 화면(브라우저)을 쓴다.**
  카카오 SDK로 받은 토큰은 안드로이드 앱 키로 발급돼서, 웹용으로 등록한 Firebase 카카오 설정(REST API 키)과 맞지 않는다.
  그러면 같은 사람이라도 웹과 안드로이드 계정이 따로 생길 수 있다.
  그래서 웹과 같은 `oidc.kakao` 방식을 쓴다.
  대신 카카오톡 앱으로 바로 로그인하는 기능은 없고, 카카오 계정 로그인 화면이 열린다.
- 로그인 버튼 색(카카오 노랑 #FEE500, 구글 흰색)은 각 회사 디자인 가이드를 따라야 해서 팔레트의 예외다.
- 라이브러리 버전은 2024년 말 기준 안정 버전이다. Android Studio가 업데이트를 제안하면 올려도 된다.
