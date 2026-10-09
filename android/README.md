# LOG US 안드로이드 앱 (Kotlin)

LOG US는 **안드로이드 앱으로만** 만든다(웹 앱 개발은 중지).
지금 있는 기능은 **앱 시작 화면 → 구글 로그인 → 회원가입(약관·닉네임·프로필 사진) → 홈(S01·S01-A) + 하단 탭** 이다. 화면은 Jetpack Compose로 그린다.

백엔드는 Firebase다(프로젝트 `logus-80f21`).
- Firebase Authentication: 구글 계정으로 회원 만들기·로그인
- Cloud Firestore: 회원 정보 `users/{uid}`
- 보안 규칙·서버 함수는 저장소 루트(`firestore.rules`, `functions/`)에 있고 이 앱과 함께 쓴다

## 폴더 구조
```
android/
├─ app/build.gradle.kts        앱 설정(패키지 이름 com.logus.app, 라이브러리)
├─ gradle/libs.versions.toml   라이브러리 버전 모음
├─ app/src/main/res/drawable-nodpi/intro_*.webp  첫 화면 소개 사진
└─ app/src/main/java/com/logus/app/
   ├─ MainActivity.kt           앱 시작 지점. 상태에 따라 로그인·회원가입·홈 화면을 고른다
   ├─ auth/AuthRepository.kt    Firebase와 이야기하는 곳(구글 로그인, users/{uid} 읽기·만들기)
   ├─ auth/AuthViewModel.kt     화면 상태(확인 중·로그인·회원가입·홈·오류) 관리
   ├─ auth/Nickname.kt          닉네임 검사(1~20자)
   ├─ auth/Agreements.kt        약관 동의 항목과 약관 버전(TERMS_VERSION)
   ├─ auth/ProfilePhoto.kt      앨범 사진을 줄여서 JPEG로 바꾸기
   ├─ legal/LegalDocs.kt        약관·개인정보 처리방침 문구(캡스톤용 예시)
   ├─ ui/WelcomeScreen.kt       첫 화면(겹친 소개 사진 3장 + Google 계정으로 계속하기)
   ├─ ui/signup/TermsScreen.kt  약관 동의(1/2 단계), LegalDocScreen.kt 약관 전문
   ├─ ui/signup/ProfileSetupScreen.kt  P01 프로필 설정(2/2 단계)
   ├─ ui/signup/ProfilePhotoScreen.kt  P02 프로필 사진
   ├─ journey/JourneyRepository.kt  진행 중인 내 여정·구성원 읽기(Firestore journeys·users), 내 초대 코드(getInviteCode 함수)
   ├─ home/HomeViewModel.kt     홈 상태(찾는 중·여정 없음 S01·진행 중 S01-A·오류)
   ├─ home/JoinViewModel.kt     초대 코드 입력 팝업 → 코드 확인(previewInvite) → I01 → 참여(joinJourney) → 수락 완료 팝업 상태
   ├─ journey/NewJourneyViewModel.kt  S02·S03 입력 상태, 저장(createJourney 함수)
   ├─ journey/Cities.kt         도시 추천용 내장 목록(한글·영문 검색)
   ├─ journey/CityRepository.kt 현재 위치의 도시·지도에서 도시 찾기(오픈스트리트맵)
   ├─ ui/SplashScreen.kt        앱 시작 화면(켤 때마다 잠깐)
   ├─ ui/MainScreen.kt          가입 후 메인: 탭 화면 + 하단 탭
   ├─ ui/home/                  S01·S01-A 홈, 프로필 상자, 여정 카드, 초대 코드 입력 팝업
   ├─ ui/feed/JourneyFeedScreen.kt  N01~N03 기록 보기(오늘 기록을 시간대별 카드로, 여정 마치기)
   ├─ feed/JourneyFeedViewModel.kt  기록 보기 상태(30개씩 읽기, 시간대 나누기, 여정 마치기)
   ├─ ui/record/                S04 앱 내 카메라(16:9 미리보기·10초 영상 촬영), S05 기록 올리기, L01 위치 확인 지도
   ├─ record/CaptureViewModel.kt 촬영 상태(촬영 전·녹화 중·완료), 10초 제한, 공통 알림 간격
   ├─ record/LogUploadViewModel.kt S05 상태(위치 없이 저장·테마 태그 3개·올리기)
   ├─ record/LogRepository.kt   영상을 Storage 에 올리고 logs 문서 만들기
   ├─ record/LocationPickerViewModel.kt  L01 위치 확인 상태(GPS·지도 가운데·주소)
   ├─ record/PlaceRepository.kt GPS 현재 위치, 핀 자리의 장소 이름·주소(오픈스트리트맵)
   ├─ journey/Nominatim.kt      오픈스트리트맵 검색 서버 요청(앱 전체 1초에 1번 이하)
   ├─ ui/journey/               S02 새 여정 만들기, S03 여행 기간 달력, 초대 코드 팝업, 여정에 초대하기 팝업(S01-A)
   ├─ ui/invite/                I01 초대 확인(초대 코드를 확인한 뒤 화면), 초대 수락 완료 팝업
   ├─ ui/explore/, ui/mylog/    탐색·마이로그(준비 중. 로그아웃은 마이로그에)
   ├─ ui/components/            동그란 프로필 사진, 하단 탭, 오류 문구 등
   └─ ui/theme/                 팔레트 색(CLAUDE.md "UI 규칙")과 다크 모드
```

## 처음 실행하는 순서
0. **Gradle용 Java를 21로 맞춘다(중요).** 최신 Android Studio에는 Java 25가 들어 있는데, 이 프로젝트의 빌드 도구(Gradle 8.11·Kotlin 2.1)는 Java 21까지만 지원한다.
   Java 25로 빌드하면 `What went wrong: 25.0.3`처럼 버전 숫자만 나오고 실패한다.
   - Android Studio: Settings(맥은 Android Studio → Settings) → Build, Execution, Deployment → Build Tools → Gradle
     → **Gradle JDK** → Download JDK… → Version **21**, Vendor 아무거나(예: JetBrains Runtime 또는 Eclipse Temurin) → Download → 선택 → OK
   - 터미널에서 `./gradlew`를 쓸 때(맥): `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` 를 먼저 실행한다
`docs/login-setup.txt`를 처음부터 따라 한다. 요약은 다음과 같다.
1. Firebase에 **안드로이드 앱** 등록(패키지 `com.logus.app`). 웹 앱은 등록하지 않는다
2. 팀원마다 SHA-1 지문 등록
3. Authentication에서 구글 로그인 켜기
4. `google-services.json`을 받아 `android/app/`에 넣기(커밋하지 않음)
5. Firestore 만들기(서울) + Storage 시작하기 + 보안 규칙 배포(`firebase deploy --only firestore:rules,storage`)
6. 폰을 USB로 연결하고 ▶ Run

## 확인할 것
- "Google로 시작하기" → 계정 선택 → 회원가입 화면 → "가입 완료" → 홈에 닉네임과 "보유 0 코인"
- Firebase 콘솔 Authentication(사용자)·Firestore(`users/{uid}`)에 생겼는지
- 로그아웃 후 다시 로그인하면 회원가입 없이 바로 홈
- 영상을 위치와 함께 여정에 올리면 홈으로 돌아왔을 때 "보유 코인"이 바로 10 올라 있는지(앱을 다시 켜지 않아도)
- 새 계정으로 가입할 때 사진을 고르지 않으면 P01·홈 프로필이 지구본 로고인지(구글 계정에 사진이 있어도)
- 친구가 내 초대 코드(S01-A 친구 추가 버튼 → 여정에 초대하기 팝업의 코드)로 여정에 들어오면 내 홈 "보유 코인"이 30 오르는지(코드는 구성원마다 다르다)
- 여정을 만들면 카메라 대신 홈(오늘 시작이면 S01-A)으로 가는지
- S01-A 여정 카드를 누르면 N01 기록 보기가 열리고, 오늘 올린 영상이 시간대별 카드(첫 장면·작은 프로필·시각)로 보이는지. 다운로드 모양 버튼 → "저장하시겠습니까?" 팝업(여정 이름·날짜·기록 수·인원) → "취소"면 그대로, "저장"이면 마이로그로 가고 홈이 S01 이 되는지
- 폰 홈 화면의 앱 아이콘이 연보라 바탕 + 보라 지구 로고인지(바뀐 아이콘이 안 보이면 앱을 지웠다가 다시 설치)

## 알아 둘 것
- 로그인은 구글 계정만 쓴다(카카오는 쓰지 않기로 함).
- 콘솔에 "Web client (auto created…)"가 보이는데 웹 앱 등록이 아니다.
  구글 로그인을 켜면 Firebase가 자동으로 만드는 서버 확인용 ID이고, 안드로이드 구글 로그인에 꼭 필요하다.
- 구글 로그인 버튼 색(흰 배경·회색 테두리)은 구글 디자인 가이드를 따라야 해서 팔레트의 예외다.
- 프로필 사진(인터넷 주소)은 Coil 라이브러리로 띄운다. 못 불러오면 기본 프로필 그림을 보여 준다.
- 라이브러리 버전은 2024년 말 기준 안정 버전이다. Android Studio가 업데이트를 제안하면 올려도 된다.
