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
   ├─ ads/                      광고 보고 코인 받기(2026-11-11): AdReward.kt 규칙(하루 5번·2코인), RewardedAds.kt 구글 AdMob 보상형 광고, AdRewardRepository.kt 오늘 횟수·claimAdReward 함수, AdRewardViewModel.kt 팝업 상태
   ├─ ui/home/AdRewardDialog.kt 보유 코인 상자를 누르면 뜨는 "광고 보고 코인 받을까요?"·"+2 코인을 받았어요" 팝업
   ├─ journeydetail/PremiumExport*.kt  DAY RECAP 프리미엄 다운로드(2026-11-11): 50코인으로 워터마크 없이, 한 번 낸 날은 다시 무료(unlockPremiumExport 함수)
   ├─ ui/journeydetail/PremiumExportDialog.kt "프리미엄 추출 · 50코인으로 워터마크 없이 저장할까요?" 확인 팝업
   ├─ home/HomeViewModel.kt     홈 상태(찾는 중·여정 없음 S01·진행 중 여정 여러 개 S01-A·오류), 여정 삭제·나가기(5초 되돌리기)
   ├─ home/CoverPhoto*.kt       S01-A 여정 카드 대표 사진 선택 팝업 상태·저장(내 멤버 문서 coverPath, 내 홈에서만)
   ├─ home/GalleryRepository.kt 대표 사진 팝업의 폰 사진첩 목록(MediaStore)
   ├─ home/JoinViewModel.kt     초대 코드 입력 팝업 → 코드 확인(previewInvite) → I01 → 참여(joinJourney) → 수락 완료 팝업 상태
   ├─ journey/NewJourneyViewModel.kt  S02·S03 입력 상태, 저장(createJourney 함수)
   ├─ journey/Cities.kt         도시 추천용 내장 목록(한글·영문 검색)
   ├─ journey/CityRepository.kt 현재 위치의 도시·지도에서 도시 찾기(오픈스트리트맵)
   ├─ ui/SplashScreen.kt        앱 시작 화면(켤 때마다 잠깐)
   ├─ ui/MainScreen.kt          가입 후 메인: 탭 화면 + 하단 탭
   ├─ ui/home/                  S01·S01-A 홈, 프로필 상자, 여정 카드(여러 개), 초대 코드 입력 팝업, 대표 사진 선택 팝업,
   │                            여정 삭제·나가기 팝업(RemoveJourneyDialog.kt, 여정 카드를 길게 누름)
   ├─ ui/feed/JourneyFeedScreen.kt  N01~N03 기록 보기(오늘 기록을 시간대별 카드로, 여정 마치기)
   ├─ ui/feed/LogVideoDialog.kt  원본 영상 팝업(기록 카드를 누르면 16:9 원본을 소리까지 재생, ‹ › 로 그날 영상 넘기기. N01·S15 공통)
   ├─ ui/feed/DeleteLogDialog.kt  기록 삭제 팝업(N01 카드를 길게 누름: 내 기록은 삭제 확인, 남의 기록은 지울 수 없다는 안내)
   ├─ feed/JourneyFeedViewModel.kt  기록 보기 상태(30개씩 읽기, 시간대 나누기, 여정 마치기)
   ├─ ui/record/                S04 앱 내 카메라(16:9 미리보기·3초 고정 영상 촬영), S05 기록 올리기, L01 위치 확인 지도
   ├─ record/CaptureViewModel.kt 촬영 상태(촬영 전·녹화 중·완료), 3초 고정 촬영, 공통 알림 간격
   ├─ record/LogUploadViewModel.kt S05 상태(위치 없이 저장·테마 태그 3개·올리기)
   ├─ record/LogRepository.kt   영상을 Storage 에 올리고 logs 문서 만들기
   ├─ record/VideoCache.kt      폰 안 영상 캐시(최대 200MB): 한 번 본 영상은 다시 받지 않음(원본 영상 팝업·DAY RECAP 재생·영상 추출 공용)
   ├─ record/PendingLogs.kt     오프라인 기록 보관함(앱 전용 폴더) + "인터넷이 연결되면 올리기" 예약(WorkManager)
   ├─ record/LogUploadWorker.kt 보관한 기록을 인터넷이 연결되면 올리기(앱이 꺼져 있어도), 다 올리면 알림(notify/UploadNotification.kt)
   ├─ record/LocationPickerViewModel.kt  L01 위치 확인 상태(GPS·지도 가운데·주소)
   ├─ record/PlaceRepository.kt GPS 현재 위치, 핀 자리의 장소 이름·주소(오픈스트리트맵)
   ├─ journey/Nominatim.kt      오픈스트리트맵 검색 서버 요청(앱 전체 1초에 1번 이하)
   ├─ ui/journey/               S02 새 여정 만들기, S03 여행 기간 달력, 초대 코드 팝업, 여정에 초대하기 팝업(S01-A)
   ├─ ui/invite/                I01 초대 확인(초대 코드를 확인한 뒤 화면), 초대 수락 완료 팝업
   ├─ ui/explore/, ui/mylog/    탐색·마이로그(준비 중. 로그아웃은 마이로그에)
   ├─ ui/notify/NotificationsScreen.kt  알림 화면(홈 오른쪽 위 종 버튼 → 오늘·어제 알림 목록·모두 읽음)
   ├─ notify/Notification*.kt   알림 목록 읽기·안 읽은 수 세기·읽음 표시(users/{uid}/notifications)
   ├─ notify/                   촬영 알림: FCM 푸시 받기(LogUsMessagingService), 이 폰 등록(DeviceRepository), 알림 채널·권한(CaptureReminder)
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
- 홈(S01·S01-A) "사진 수정 ›" → 프로필 사진 수정 팝업에서 사진 찍기·앨범에서·기본 이미지와 닉네임을 바꾸고 "저장"하면 홈 프로필이 바로 바뀌는지
- 새 계정으로 가입할 때 사진을 고르지 않으면 P01·홈 프로필이 지구본 로고인지(구글 계정에 사진이 있어도)
- 친구가 내 초대 코드(S01-A 친구 추가 버튼 → 여정에 초대하기 팝업의 코드)로 여정에 들어오면 내 홈 "보유 코인"이 5 오르는지(코드는 구성원마다 다르다)
- 새 여정: S02 기간 칸 → S03 달력에서 오늘이 시작일로 고정되고 끝나는 날만 고를 수 있는지(지난 날짜·다른 시작일은 고를 수 없음) → S01-A 여정 카드 이름 아래에 구성원 프로필 사진이 겹쳐 보이는지(사진이 없는 사람은 지구본)
- 하루 50코인 제한(함수 배포 뒤): 위치와 함께 기록을 5번 올려 오늘 50코인을 받은 뒤 6번째를 올리면 "오늘 받을 수 있는 코인 50개를 다 받아서 코인은 쌓이지 않았어요"가 뜨고 보유 코인이 그대로인지(하루는 세계 표준시 기준이라 한국 시간 오전 9시에 바뀐다)
- 여정을 만들면 카메라 대신 홈(오늘 시작이면 S01-A)으로 가는지
- S01-A 여정 카드를 누르면 N01 기록 보기가 열리고, 오늘 올린 영상이 시간대별 카드(첫 장면·작은 프로필·시각)로 보이는지. 다운로드 모양 버튼 → "저장하시겠습니까?" 팝업(여정 이름·날짜·기록 수·인원) → "취소"면 그대로, "저장"이면 마이로그로 가고 홈이 S01 이 되는지
- 폰 홈 화면의 앱 아이콘이 연보라 바탕 + 보라 지구 로고인지(바뀐 아이콘이 안 보이면 앱을 지웠다가 다시 설치)
- 오늘이 기간 안인 여정이 둘 이상이면 S01-A 에 여정 카드가 모두 보이고(스크롤), "지금 기록하기" 버튼 없이 "새 여정 시작하기"·"초대 코드로 참여"만 있는지
- 여정 카드 사진 오른쪽 위 연필 버튼(흰 동그라미) → "대표 사진 선택" 팝업(사진 접근 허용) → 사진 고르고 "이 사진으로 변경" → 내 홈 카드 사진만 바뀌는지(다른 구성원 홈은 그대로)
- N01 날짜 상자 〈 〉로 전날·다음날 기록이 보이는지(여정 첫날·오늘에서는 화살표가 흐려짐)
- S05 에서 영상을 누르면 소리까지 재생되는지, "+5코인" 문구(2026-11-11 위치 코인 10 → 5)
- N01(또는 마이로그 여정 상세 [기록] 탭)에서 기록 카드를 누르면 원본 영상 팝업이 뜨고 소리까지 바로 재생되는지(가로 16:9, 잘리지 않음) → 영상을 누르면 멈춤·다시 재생 → ‹ › 로 그날 다른 영상으로 넘어가는지("2 / 8 · 원본 영상") → X·바깥·뒤로 가기로 닫히는지. 작은 프로필을 누르면 예전처럼 프로필 팝업인지
- N01 기록 삭제(색인 배포 뒤): 내 기록이 있는 날 위에 "내가 올린 기록을 길게 누르면 삭제할 수 있어요" 안내가 보이는지(카드에 따로 "내 기록" 표시는 없음) → 내 카드를 길게 누르면 "이 기록을 삭제할까요?" → "삭제" → 카드가 사라지고 아래 "내 기록을 삭제했어요 · 되돌리기"(5초) → 되돌리기로 돌아오는지 / 5초 뒤 Firestore·Storage 에서 지워졌는지 → 날짜 상자 "기록 N개 · 내 기록 N개"가 1씩 줄었는지 → 남의 카드를 길게 누르면 "다른 사람의 기록이에요"와 [확인]만 나오는지
- S01-A 여정 삭제·나가기(함수 배포 뒤): (안내 띠는 없음) 내가 만든 여정 카드를 길게 누르면(진동) "이 여정을 삭제할까요?"와 기록 수 → "여정 삭제" → 카드가 사라지고 아래 "여정을 삭제했어요 · 되돌리기"(5초) → 되돌리기로 돌아오는지 / 5초 뒤 다른 구성원의 홈에서도 사라졌는지(Firestore·Storage 의 여정 폴더도 없어짐) → 초대받은 여정 카드를 길게 누르면 "이 여정에서 나갈까요?" → "여정 나가기" → 내 홈에서만 사라지고 다른 구성원은 내 기록을 그대로 보는지(이름은 "구성원"으로 보임) → 같은 초대 코드로 다시 들어올 수 있는지
- S04 카메라 전환: "여정 진행 중" 상자 오른쪽 버튼을 누르면 앞(셀카)·뒤 카메라가 바뀌는지 → 앞 카메라로 3초 찍은 영상이 S05 에서 미리보기와 같은 방향(거울처럼)으로 보이는지 → 촬영 중에는 버튼이 흐리게 눌리지 않는지 → 다시 촬영·다음 촬영 때 고른 카메라가 그대로인지
- 오프라인 기록: 비행기 모드에서 3초 찍기 → S05 "위치 없이 저장" → "여정에 올리기" → "인터넷이 없어서 폰에 보관했어요…" 안내와 함께 N01 맨 위에 "올리기를 기다리는 기록 1개"와 흐린 카드가 보이는지 → 비행기 모드를 끄면 "올리는 중…" 뒤 카드가 보통 기록으로 바뀌고 "폰에 보관했던 기록을 여정에 올렸어요" 안내가 뜨는지 → 다시 비행기 모드로 찍어 보관한 뒤 앱을 완전히 끄고 비행기 모드를 끄면 "기록 1개를 여정에 올렸어요" 폰 알림이 오는지(몇 분 걸릴 수 있음)
- 영상 캐시: 원본 영상 팝업에서 영상을 끝까지 본 뒤, 앱을 끄지 않은 채 비행기 모드로 바꾸고 같은 영상을 다시 열어도 재생되는지(한 번 본 영상은 폰에서 재생). 처음 보는 영상은 비행기 모드에서 "영상을 불러오지 못했어요". 앱을 다시 켜면 영상 주소를 새로 받아야 해서 인터넷이 필요하다(영상 파일은 캐시에서 읽음)
- 알림 화면(함수·규칙 배포 뒤): S01·S01-A 오른쪽 위 종 버튼에 안 읽은 수(빨간 숫자)가 보이는지 → 누르면 "알림 ③"·오늘·어제로 나뉜 목록 → 다른 폰으로 같은 여정에 기록을 올리면 "○○ 님이 기록을 올렸어요"가 생기는지(종 숫자는 홈에 다시 들어오거나 앱을 다시 열 때 바뀜) → 알림을 누르면 보라 점이 사라지고 그 여정 기록 보기가 열리는지 → 뒤로 가면 알림 화면 → "모두 읽음" → 종 숫자가 사라지는지
- 광고 보고 코인 받기(함수·규칙 배포 뒤): S01·S01-A 보유 코인 상자 왼쪽 위에 보라 "+"가 보이는지 → 상자를 누르면 "광고 보고 코인 받을까요?"·"오늘 0 / 5회 사용 · 하루 최대 10코인" → "광고 보기" → "Test Ad" 표시가 있는 테스트 광고가 전체 화면으로 나오는지 → 끝까지 보고 닫으면 "+2 코인을 받았어요"·"오늘 1 / 5회 사용"·보유 코인 상자 숫자가 2 늘었는지 → "한 번 더 보기"로 5번까지 받고, 5번째 뒤에는 "오늘은 다 받았어요"로 버튼이 꺼지는지 → 광고를 중간에 닫으면 "광고를 끝까지 보면 코인을 받을 수 있어요"인지
- DAY RECAP 프리미엄 다운로드(함수·규칙 배포 뒤): 마이로그 → 여정 → DAY RECAP → "영상 추출" → [영상 다운로드](무료) 영상 오른쪽 아래에 LOG EARTH 워터마크가 있는지 → [프리미엄 다운로드](50코인) → "프리미엄 추출" 팝업에 "현재 보유 N 코인"·"사용하면 N-50 코인이 남아요" → "50코인 사용" → 만든 영상에 워터마크가 없는지·홈 보유 코인이 50 줄었는지 → 같은 날 다시 "영상 추출"을 열면 배지가 "이날 무료"이고 팝업 없이 바로 만들어지는지(코인이 줄지 않음) → 다른 날은 다시 50코인인지 → 코인이 50개보다 적으면 버튼이 "코인이 부족해요"로 꺼지는지
- 촬영 알림(서버 함수 `sendCaptureReminders` 배포 뒤): 홈에 처음 들어올 때 "알림 허용"을 묻는지(안드로이드 13 이상) → 공통 알림 간격이 1시간인 여정을 만들고(예: 14:23) 만든 시각에서 1시간 뒤 10분 단위 시각(15:20, 09~23시)에 "지금 기록할 시간이에요" 알림이 오는지(앱을 꺼 두어도·보고 있어도, 몇십 초 늦을 수 있음) → N01 시간대가 "14:20 ~ 15:20"처럼 같은 칸으로 나뉘는지 → 알림을 누르면 홈(S01-A)이 열리는지

## 알아 둘 것
- 로그인은 구글 계정만 쓴다(카카오는 쓰지 않기로 함).
- 콘솔에 "Web client (auto created…)"가 보이는데 웹 앱 등록이 아니다.
  구글 로그인을 켜면 Firebase가 자동으로 만드는 서버 확인용 ID이고, 안드로이드 구글 로그인에 꼭 필요하다.
- 구글 로그인 버튼 색(흰 배경·회색 테두리)은 구글 디자인 가이드를 따라야 해서 팔레트의 예외다.
- 프로필 사진(인터넷 주소)은 Coil 라이브러리로 띄운다. 못 불러오면 기본 프로필 그림을 보여 준다.
- 촬영 알림은 서버가 10분마다 확인해서, 여정을 만든 시각(10분 단위로 내림)부터 공통 알림 간격마다 보낸다(19:23에 만든 2시간 여정 → 21:20·23:20, 다음 날 09:20·11:20…). 폰 시각 09:00~23:59, 여정 기간 안에만 온다.
  여정을 만든 사람이 "촬영 알림 받지 않기"를 골랐거나, 내가 그 여정을 저장(마침)했으면 오지 않는다.
  알림이 안 오면: 폰 설정 → 앱 → LOG US → 알림이 켜져 있는지, Firestore `users/{uid}/devices`에 이 폰 문서가 있는지(홈에 들어오면 생긴다), 함수 로그(`firebase functions:log --only sendCaptureReminders`)를 본다.
- 광고는 구글 AdMob 테스트 ID(app/build.gradle.kts 의 `admobAppId`·`ADMOB_REWARDED_UNIT_ID`)라서 "Test Ad" 광고만 나오고 수익은 없다. 출시 전에 AdMob 콘솔에서 만든 실제 ID 로 바꾸고, 서버 측 보상 확인(SSV)·플레이 콘솔 "광고 포함"·광고 ID 신고를 한다(CLAUDE.md "지금 범위 밖").
- 라이브러리 버전은 2024년 말 기준 안정 버전이다. Android Studio가 업데이트를 제안하면 올려도 된다.
