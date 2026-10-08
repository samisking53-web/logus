<!-- 팀 메모: Claude가 같은 실수를 두 번 하면 여기에 규칙을 한 줄씩 추가하세요. 200줄 이하로 유지합니다. 이 주석은 Claude에게 전달되지 않습니다. -->

# LOG US (로그어스)

여정(여행·행사) 단위로 친구·연인·가족이 사진·영상·글을 함께 기록하고, 추억 지도와 DAY RECAP 영상으로 다시 보는 **안드로이드 앱**(`android/`, Kotlin). 대학 캡스톤 프로젝트이고 팀원 3명 모두 개발 초보다. 백엔드는 Firebase다.

> 처음에 만들던 웹 앱(Next.js)은 그만두고 코드도 지웠다. 화면·기능은 모두 `android/`에 만든다. 저장소 루트에는 Firebase 백엔드(보안 규칙·서버 함수·규칙 테스트)만 있다.

## 작업 방식
- 한 세션에서는 화면이나 기능 하나만 다룬다. 요청 범위 밖의 파일은 고치지 말고 제안만 한다.
- 화면 작업 전에 `docs-storyboard-v4.pdf`에서 해당 화면 번호(아래 목록)를 먼저 확인한다.
- 코드를 쓰기 전에 만들거나 바꿀 파일, 사용할 컬렉션·함수, 완료 기준을 계획으로 보여준다.
- 끝내기 전에 안드로이드 빌드(`cd android && ./gradlew assembleDebug`)를 통과시킨다. 함수를 고쳤다면 `npm --prefix functions run build`도 통과시킨다. 빌드할 수 없는 환경이면 그 사실을 PR과 답변에 분명히 적는다.
- 보안 규칙을 바꾸면 에뮬레이터 규칙 테스트(`tests/rules/`)도 함께 고치고 통과시킨다.
- 커밋 메시지와 PR 설명은 한국어로 쓴다. PR 설명에는 (1) 바뀐 점 (2) 폰(안드로이드)에서 확인하는 순서 (3) 개발 초보도 이해할 수 있는 코드 설명 (4) 따로 배포해야 하는 것(함수·규칙·색인)을 넣는다.
- 요구사항이 모호하면 추측하지 말고 질문한다.

## 기술 스택 (임의로 바꾸지 않는다)
- 앱: 안드로이드 네이티브(`android/`, Kotlin + Jetpack Compose, Firebase Android SDK·BoM, 패키지 `com.logus.app`, minSdk 26). 빌드는 Android Studio에서 한다
- 백엔드: Firebase Authentication, Cloud Firestore, Cloud Storage, Cloud Functions(2세대, TypeScript)
- 지도: MapLibre(안드로이드는 MapLibre Native Android) + OpenFreeMap 타일. 카카오맵·구글맵 SDK는 추가하지 않는다
- 카메라: CameraX(camera-camera2·lifecycle·video·view). S04 앱 내 카메라에 쓴다
- 추억 영상: 앱 안에서 재생. 안드로이드에서 쓸 방식은 그 기능을 만들 때 정한다. mp4 렌더링은 나중에
- AI(제목·캡션 생성): LLM API는 Cloud Functions에서만 호출한다
- Firebase에는 안드로이드 앱만 등록한다(웹 앱은 등록하지 않음)
- 새 라이브러리를 추가할 때는 계획에 이유를 적는다

## 명령어
- `cd android && ./gradlew assembleDebug` 안드로이드 빌드(`android/app/google-services.json` 필요) / Android Studio ▶ Run 으로 폰에 설치
- `npm --prefix functions run build` 함수 빌드
- `firebase emulators:start` 로컬 에뮬레이터(Auth·Firestore·Functions·Storage)
- `npm run test:rules` 에뮬레이터를 띄워 `tests/rules/`의 보안 규칙·함수 테스트를 실행한다(처음 한 번 저장소 루트에서 `npm install`, `npm --prefix functions install` 필요)
- `firebase deploy`는 사람이 승인한 뒤 로컬 세션에서만 실행하고, 대상은 `--only`로 지정한다. 클라우드 세션에서는 배포하지 않는다

## 폴더·설정 규칙
- 안드로이드: 화면은 `android/app/src/main/java/com/logus/app/ui/`, 공통 UI는 `ui/components/`, Firebase와 이야기하는 코드는 기능별 `*Repository.kt`(예: `auth/AuthRepository.kt`), 화면 상태는 `*ViewModel.kt`
- Firebase 안드로이드는 `google-services.json`으로 자동 초기화된다. `FirebaseApp.initializeApp`을 따로 부르지 않는다
- 서버 코드는 `functions/src/`에 기능별 파일로 나누고 `index.ts`에서 export 한다
- `firebase.json`, `firestore.rules`, `firestore.indexes.json`, `storage.rules`는 저장소 루트에 둔다
- `android/app/google-services.json`과 서명 키(`*.jks`)는 커밋하지 않는다
- 화면 파일(Composable) 위에 스토리보드 번호를 주석으로 적는다 (예: `// S02 새 여정 만들기`)
- 비밀 값(LLM API 키 등)은 `firebase functions:secrets:set`으로 저장하고 `defineSecret`으로 읽는다. 코드·커밋·`.env` 파일에 넣지 않는다
- 서비스 계정 키(JSON)는 만들지도, 커밋하지도 않는다
- 함수 공통 옵션: 리전 `asia-northeast3`(서울, Firestore와 같은 위치), `maxInstances: 10`. `minInstances`는 설정하지 않는다(상시 과금)

## UI 규칙
- 안드로이드 폰 세로 화면 기준. 하단 탭: 홈 / 탐색 / 마이로그(글자 없이 그림만, `ui/components/BottomTabBar.kt`)
- 앱을 켤 때마다 앱 시작 화면(`ui/SplashScreen.kt`, 스토리보드 v4 1쪽)을 잠깐 보여 준 뒤 홈(또는 첫 화면)으로 간다
- 홈은 오늘이 여행 기간(startDate~endDate) 안인 내 여정이 있으면 S01-A, 없으면 S01(`ui/home/HomeScreen.kt`, `home/HomeViewModel.kt`). S01-A에도 "지금 기록하기"(primary) 아래에 S01과 같은 "새 여정 시작하기"(primary-light, → S02)·"초대 코드로 참여"(흰 카드, → 초대 코드 입력 팝업)를 같은 크기로 둔다. S01-A 여정 카드의 여정 이름 오른쪽 위에는 친구 추가(사람+) 버튼을 둔다 → 여정에 초대하기 팝업(`ui/journey/JourneyInviteDialog.kt`, 뒤는 S01-A): 여정 이름·현재 함께하는 사람 수 → 여정의 초대 코드(`journeys.inviteCode`, 같은 여정은 늘 같은 코드)·복사 → "초대 코드 공유"(안드로이드 공유 창으로 Gmail·메시지·카카오톡 등에 여정 이름·코드·참여 방법을 보낸다. 링크 아님). 팝업을 열 때 여정 문서를 다시 읽어 인원수를 새로 고친다(`HomeViewModel.refreshOngoing`)
- S02 새 여정 만들기·S03 기간 달력은 `ui/journey/`(상태는 `journey/NewJourneyViewModel.kt`). 저장은 위쪽 오른쪽 "저장 →" 또는 "친구 초대하기"(저장 후 초대 코드 팝업)로 `createJourney` 함수를 부른다. 도시 추천은 ① 현재 위치(대략적인 위치 권한, play-services-location)의 도시 ② 글자를 칠 때 내장 목록(`journey/Cities.kt`) ③ "지도에서 찾기" 버튼으로 오픈스트리트맵 Nominatim 검색(`journey/CityRepository.kt`) 순서다. 어디에도 없으면 입력한 글자 그대로 저장한다
- 여정 시작일에는 S04 앱 내 카메라를 자동으로 한 번 연다(오늘 시작하는 여정을 만들었을 때, 미래 여정은 시작일에 앱을 열 때. 여정마다 이 폰에서 한 번, `journey/StartDayCamera.kt`)
- S04 앱 내 카메라(`ui/record/CaptureScreen.kt`, 상태는 `record/CaptureViewModel.kt`, CameraX): S01-A "지금 기록하기"로 연다(하단 탭 숨김). 위에서부터 뒤로 가기+여정 이름 → "● 여정 진행 중 · 지금 시각" → 16:9 가로 미리보기(화면 위쪽 절반 안, 앱은 세로 고정, 미리보기 영역대로 잘라 녹화) → "영상 · 10초까지 담겨요"·"00:04 / 00:10"·진행 막대 → 촬영 버튼(빨간 동그라미, 녹화 중 깜빡임·"촬영 멈추기", 끝나면 "다시 촬영") → 맨 아래 "공통 알림 · N시간마다"(여정을 만든 사람의 members 문서 notifyIntervalHours). 영상은 최대 10초(FHD, 마이크 거절 시 소리 없이)로 앱 전용 임시 폴더 `cacheDir/captures`에 저장하고, 뒤로 가기로 나가면 지운다. 기록 올리기(S05)는 다음 작업
- 화면 문구는 모두 한국어
- 글자와 배경의 명도 대비는 4.5:1 이상 (WCAG AA)
- 색은 `android/app/src/main/java/com/logus/app/ui/theme/Color.kt`의 팔레트 값만 쓴다(`MaterialTheme.colorScheme`, `LogUsColors`). 화면 코드에 임의 색을 쓰지 않는다. 새 색이 필요하면 팔레트에서 골라 Color.kt에 추가하고 이 목록도 고친다
  - 브랜드: primary `#5B3DD6`(버튼·핀·활성 탭 채움, 위 글자는 흰색), primary-strong `#4A2FB8`(눌림·강조 글자), primary-light `#6B4FE0`(조금 밝은 보라 채움, S01-A "새 여정 시작하기". 위 글자는 흰색, 대비 5.5:1. 다크도 같은 값), surface `#EEEAFB`(카드), border `#D8CFF5`(테두리)
  - 기록·보상(코인·하이라이트): amber `#E0930F`(채움, 위 글자는 본문색. 흰 글자 금지), amber-text `#8A5606`, amber-soft `#FDF0DC`
  - 멤버 구분: `#5B3DD6` `#C63F33` `#0B7A6B` `#9C6408`. 색만으로 구분하지 않게 이름 첫 글자를 함께 넣는다
  - 뉴트럴: text `#1A1725`, text-secondary `#5A5570`, text-weak `#6E6A85`(바탕 위에서만, 카드 위에서는 대비 부족), line `#E3E0EC`, background `#F7F5FB`
  - 시맨틱: success `#17795A`, warning `#A8620A`, error `#C0392B`
  - 다크 모드(폰 설정을 따름, `isSystemInDarkTheme()`): background `#141220`, surface `#1E1B2E`, primary `#6B4FE0`, primary-strong `#B3A0FF`. 글자는 `#F7F5FB`·`#E3E0EC`·`#D8CFF5`
  - 글자색으로는 primary 대신 primary-strong을 쓴다(다크 모드에서 primary 글자는 대비 부족)
  - 오류 빨강은 다크 바탕에서 글자 대비가 3.4라 부족하다. 빨강은 테두리·아이콘에만 쓰고 오류 글자는 본문색으로 쓴다
  - 흰 카드: `#FFFFFF`(홈의 코인 상자·여정 카드·흰 버튼·하단 탭 바탕. 라이트 전용이고 다크는 surface `#1E1B2E`. Color.kt의 `Card`, `LogUsColors.card`)
  - 사진 테두리: `#FFFFFF`(첫 화면 소개 사진 카드의 흰 테두리, 라이트·다크 같음. Color.kt의 `PhotoFrame`). 글자색으로는 쓰지 않는다
  - 예외: 구글 로그인 버튼은 구글 디자인 가이드 색(흰 배경 `#FFFFFF`, 테두리 `#747775`, 글자 `#1F1F1F`)을 쓴다(Color.kt의 `Google*`)

## 화면 번호 (스토리보드 v3)
- S01 홈 / S01-A 여행 기간 중 홈(지금 기록하기)
- S02 새 여정 만들기 / S03 여행 기간 달력 / 초대 코드 팝업(S02 "친구 초대하기" → 6자리 코드·코드 복사) / 여정에 초대하기 팝업(S01-A 여정 카드의 친구 추가 버튼 → 코드 복사·초대 코드 공유)
- S04 앱 내 카메라(S01-A "지금 기록하기", 16:9 영상 10초) / S05 기록 올리기(짧은 글·태그·테마)
- S06 우리 기록(시간순 공동 피드) / S07 위치 저장(+30코인, 탐색 공개 선택)
- S08 탐색(세로 스와이프 영상) / S09 검색 결과(지역·테마)
- S10 마이로그(여정 목록) / S11 여정 상세(기록·추억 지도·추억 영상 탭)
- S12 추억 지도 / S13 추억 타임라인
- S14 추억 영상 만들기(하루·전체·기간) / S15 재생·저장·공유
- 초대 코드 입력 팝업(S01 "초대 코드로 참여" → 6자리 입력 → 여정 확인하기, `ui/home/JoinCodeDialog.kt`)
- I01 초대 확인("○○ 님이 초대했습니다" + 여정 카드 + 초대 수락하기, `ui/invite/InvitePreviewScreen.kt`) / 초대 수락 완료 팝업(I01 위에 "초대가 수락되었습니다!" + 홈으로 가기 → 참여한 여정의 S01-A, `ui/invite/InviteAcceptedDialog.kt`) / I02 참여 완료·내 알림 설정 / I03 참여한 여정 / I04 공동 피드의 첫 기록

## 데이터 모델 (Firestore 초안. 바꾸면 이 파일도 함께 고친다)
- `users/{uid}`: nickname, photoURL, coins(서버만 수정), createdAt
- `users/{uid}/agreements/{termsVersion}`: ageOver14·terms·location(필수, true), notifyNewLogs, agreedAt. 문서 ID는 약관 버전 `YYYY-MM-DD`(`auth/Agreements.kt`의 `TERMS_VERSION`). 본인만 읽고, 한 번 만들면 고치거나 지울 수 없다
- `journeys/{journeyId}`: name, city, country, startDate, endDate, ownerId, memberIds(배열), memberCount, inviteCode, createdAt. startDate·endDate는 현지 달력 날짜 문자열 `"YYYY-MM-DD"`
- `journeys/{journeyId}/members/{uid}`: role(owner|member), notifyIntervalHours(1|2|3|null), joinedAt
- `journeys/{journeyId}/logs/{logId}`: authorId, mediaType(photo|video|text), mediaPath, body, theme, taggedUids, capturedAt(Timestamp), capturedTz, location({lat, lng} 또는 null), placeName, isPublic(기본 false), createdAt. mediaPath는 `journeys/{journeyId}/{작성자 uid}/{파일 이름}`(글 기록은 null)
- `.../logs/{logId}/comments/{commentId}`: authorId, body, createdAt
- `.../logs/{logId}/reactions/{uid}`: emoji, createdAt (1인 1반응)
- `journeys/{journeyId}/recaps/{recapId}`: rangeType(day|journey|custom), startDate, endDate, title, captions, createdAt
- `invites/{inviteCode}`: journeyId, name, city, startDate, endDate, memberCount, inviterName, expiresAt(여정 종료일이 끝나는 때 = 종료일 다음 날 12:00 UTC. 기록용이고 실제 만료 확인은 여정 문서의 endDate로 계산한다). 문서 ID가 6자리 초대 코드. 서버 함수만 읽고 쓴다
- `inviteAttempts/{uid}`: date(UTC `YYYY-MM-DD`), count. 초대 코드 입력 횟수(하루 20번 제한). 서버만 읽고 쓴다
- `publicLogs/{logId}`: 탐색용 공개 사본(journeyId, city, theme, mediaPath, placeName, location, createdAt)
- `sharedRecaps/{slug}`: 공유를 누른 리캡의 공개 사본
- `users/{uid}/coinLedger/{logId}`: reason, amount, journeyId, createdAt. 문서 ID가 logId라 기록당 한 번만 생긴다

## 보안 규칙 (기본 거부)
- 모든 경로는 거부에서 시작해 필요한 것만 허용한다
- 여정과 그 하위 문서는 `memberIds`에 있는 사람만 읽고 쓴다
- 여정 생성과 참여는 서버 함수(`createJourney`, `joinJourney`)로만 한다. 클라이언트는 `journeys` 문서를 직접 만들거나 `memberIds`를 고치지 못한다
- 기록 수정·삭제는 작성자만. `location`, `placeName`, `isPublic`은 클라이언트가 직접 바꾸지 못하고 `saveLogLocation` 함수로만 바꾼다
- `users/{uid}`는 로그인한 사용자가 읽을 수 있고, 본인은 nickname·photoURL만 고칠 수 있다
- `coins`, `coinLedger`, `publicLogs`, `sharedRecaps`, `invites`, `inviteAttempts`는 클라이언트가 쓰지 못한다. `invites`·`inviteAttempts`는 읽지도 못한다
- 비로그인 읽기는 `sharedRecaps`의 문서 단건 읽기(get)와 `publicLogs` 목록만 허용한다
- 공유 slug는 추측하기 어려운 12자 이상 무작위 문자열로 만든다
- 초대 코드는 헷갈리는 글자(0·O·1·I)를 뺀 영문 대문자·숫자 6자리(32글자, 약 10억 가지)다. 같은 여정은 처음 만든 코드를 계속 쓴다(바뀌지 않음). 짧은 대신 ① 여정이 끝나면 만료(종료일이 세계 어디서나 끝나는 종료일 다음 날 12:00 UTC까지) ② 로그인한 사람만 `joinJourney`로 사용(앱이 `invites`를 직접 읽지 못함) ③ 한 사람당 하루 20번까지 입력으로 보완한다(2026-10-04 팀 결정, 2026-10-07에 "만든 뒤 7일 만료"를 "여정이 끝날 때까지"로 바꿈)
- Storage: 프로필 사진 `users/{uid}/{파일}`은 로그인한 사람이 읽고 본인만 올린다(이미지 5MB 미만). `journeys/{journeyId}/...`는 여정 구성원만 읽고, 올리기·지우기는 본인 폴더 `journeys/{journeyId}/{uid}/`에서만 한다(Firestore 구성원 정보로 확인). 이미지 10MB·영상 50MB 미만, `image/*`·`video/*`만 허용. `public/...`은 읽기만 공개하고 쓰기는 서버만

## 서버 함수 (functions/src)
- 모든 callable은 로그인 여부와 여정 구성원 여부를 먼저 확인한다
- `createJourney`: 여정 문서, owner 멤버 문서, `invites` 요약(6자리 코드, 여정이 끝나면 만료)을 한 트랜잭션으로 만든다. 코드는 여정 문서 `inviteCode`에도 저장해 구성원이 언제 열어도 같은 코드를 본다. 코드가 겹치면 새 코드로 다시 만든다. 돌려주는 값: journeyId, inviteCode, inviteExpiresAt
- `previewInvite`: 6자리 초대 코드로 여정 요약(여정 이름·도시·기간·현재 인원·초대한 사람·만든 사람의 알림 간격·이미 구성원인지)만 돌려준다(참여하지 않음). 앱은 `invites`를 읽지 못하므로 I01 화면은 이 함수로 채운다. `joinJourney`와 하루 입력 횟수를 함께 세고, 여정이 끝났으면 거부한다
- `joinJourney`: 6자리 초대 코드로 참여한다(대소문자·공백 무시). 하루 입력 횟수와 만료(여정 문서의 endDate로 계산, `common.ts`의 `requireInviteOpen`)를 확인하고, memberIds·members·memberCount·invites 요약을 함께 갱신한다
- `saveLogLocation`: 위치 저장과 30코인 지급을 한 트랜잭션으로 처리한다. coinLedger 문서가 이미 있으면 코인은 주지 않는다
- `syncPublicLog`(Firestore 트리거): 공개이고 위치가 있는 기록만 publicLogs 사본과 `public/` 미디어 사본을 만들고, 조건이 깨지면 지운다
- `generateRecapCaptions`: 썸네일·시간·장소를 LLM에 보내 제목·장면 순서·캡션을 JSON으로 받아 저장한다. 사용자당 하루 호출 횟수를 제한한다
- `shareRecap`: sharedRecaps 사본과 공유 링크를 만든다

## 인증
- 로그인 수단: 구글 계정만 쓴다(카카오는 쓰지 않기로 함). 콘솔 설정 순서는 `docs/login-setup.txt`
- 로그인: Credential Manager(`GetSignInWithGoogleOption`)로 받은 구글 ID 토큰을 `GoogleAuthProvider`로 Firebase Auth에 넘긴다. `R.string.default_web_client_id`(구글 로그인을 켜면 자동으로 생기는 OAuth 클라이언트, 웹 앱 등록과 무관)가 필요하다. Firebase 콘솔에 팀원별 SHA-1 등록이 필요하다
- 회원가입 흐름: 첫 화면(`ui/WelcomeScreen.kt`, Google 계정으로 계속하기) → 로그인 후 `users/{uid}`가 없으면 약관 동의(1/2 단계) → P01 프로필 설정(2/2 단계, 닉네임 1~20자) ↔ P02 프로필 사진(앨범·구글 사진·기본) → 홈. 가입 완료 때 `users/{uid}`와 `users/{uid}/agreements/{약관 버전}`을 한 배치로 저장하고, 앨범 사진은 줄여서 Storage에 올린다. 흐름과 상태는 `auth/AuthViewModel.kt`(Checking·SignedOut·Signup(step)·Ready·Failed)
- 약관 문구는 `legal/LegalDocs.kt`(캡스톤용 예시, 출시 전 법률 검토 필요). 문구를 바꾸면 `TERMS_VERSION`도 바꾼다
- 초대는 링크 없이 6자리 초대 코드로만 한다. S02 "친구 초대하기"를 누르면 여정을 저장하고 코드 팝업(`ui/journey/InviteCodeDialog.kt`)에서 코드를 복사한다. 받은 사람은 로그인·가입 후 홈(S01)의 "초대 코드로 참여" 팝업에 코드를 입력 → "여정 확인하기"(`previewInvite`) → I01 초대 확인 → "초대 수락하기"(`joinJourney`)로 참여 → 수락 완료 팝업 → "홈으로 가기"(방금 참여한 여정이 오늘 여행 기간 안이면 그 여정의 S01-A, 아니면 홈 규칙대로)(상태는 `home/JoinViewModel.kt`)(로그인 전 미리보기 없음). 코드 글자 규칙은 앱 `journey/InviteCode.kt`와 서버 `functions/src/common.ts`가 같아야 한다

## 데이터 원칙
- 카카오·구글 장소 검색 결과는 DB에 저장하지 않는다(이용약관). 장소 이름은 사용자가 입력한 텍스트, 좌표는 기기 위치나 사용자가 지도에서 고른 점만 저장한다
- 도시 이름은 오픈스트리트맵(OSM) 데이터만 쓴다(안드로이드 기본 Geocoder는 구글 데이터라 쓰지 않는다). Nominatim 규칙: 1초에 1번 이하, User-Agent에 앱 이름, 글자마다 자동 검색 금지(버튼을 누를 때만), 화면에 "© OpenStreetMap" 출처 표시. 위치 좌표는 소수 둘째 자리로 줄여 보내고 저장하지 않는다
- 위치는 사용자가 허용했을 때만 저장한다. 좌표 (0,0)은 결측으로 처리한다
- 시간은 Timestamp(UTC)로 저장하고 촬영지 시간대(capturedTz)를 함께 저장한다. DAY RECAP의 '하루'는 현지 시간 기준으로 나눈다
- 사진은 업로드 전에 앱에서 줄이고 JPEG/WebP로 올린다. 영상은 15초 이하

## 비용 관리 (Blaze 요금제)
- 목록 화면은 `limit()`과 페이지 나누기를 쓴다. 한 화면에 실시간 리스너(onSnapshot)를 여러 개 붙이지 않는다
- 외부 API를 부르는 함수에는 호출 횟수 제한을 둔다
- Storage 기본 버킷은 무료 한도가 적용되는 US 리전에 있다. 미디어는 줄여서 올린다

## 알려진 함정
- 빌드는 Java 21(Gradle JDK)로 한다. 최신 Android Studio 기본 Java 25로는 Gradle 8.11·Kotlin 2.1이 버전을 읽지 못해 `What went wrong: 25.0.3`처럼 실패한다. Java 25로 올리려면 Gradle·AGP·Kotlin을 함께 올려야 한다
- 구글 로그인은 SHA-1이 등록된 컴퓨터에서 빌드한 앱에서만 된다. 팀원이 바뀌면 그 컴퓨터의 SHA-1을 추가하고 `google-services.json`을 다시 받는다
- 구글 로그인은 Google Play 서비스가 있는 기기·에뮬레이터에서만 된다
- 플레이스토어 배포 시 Play 앱 서명 키의 SHA-1도 Firebase에 추가해야 한다

## 지금 범위 밖 (요청이 있을 때만 만든다)
- 촬영 알림(FCM·예약 함수), mp4 렌더링·저장, 월별·연도별 타임라인, '1년 전 오늘'
