<!-- 팀 메모: Claude가 같은 실수를 두 번 하면 여기에 규칙을 한 줄씩 추가하세요. 200줄 이하로 유지합니다. 이 주석은 Claude에게 전달되지 않습니다. -->

# LOG US (로그어스)

여정(여행·행사) 단위로 친구·연인·가족이 사진·영상·글을 함께 기록하고, 추억 지도와 DAY RECAP 영상으로 다시 보는 모바일 웹앱(PWA). 대학 캡스톤 프로젝트이고 팀원 3명 모두 개발 초보다. 백엔드는 Firebase다.

## 작업 방식
- 한 세션에서는 화면이나 기능 하나만 다룬다. 요청 범위 밖의 파일은 고치지 말고 제안만 한다.
- 화면 작업 전에 `docs-storyboard-v4.pdf`에서 해당 화면 번호(아래 목록)를 먼저 확인한다.
- 코드를 쓰기 전에 만들거나 바꿀 파일, 사용할 컬렉션·함수, 완료 기준을 계획으로 보여준다.
- 끝내기 전에 `npm run lint`와 `npm run build`를 통과시킨다. 함수를 고쳤다면 `npm --prefix functions run build`도 통과시킨다.
- 보안 규칙을 바꾸면 에뮬레이터 규칙 테스트(`tests/rules/`)도 함께 고치고 통과시킨다.
- 커밋 메시지와 PR 설명은 한국어로 쓴다. PR 설명에는 (1) 바뀐 점 (2) 폰에서 확인하는 순서 (3) 개발 초보도 이해할 수 있는 코드 설명 (4) 따로 배포해야 하는 것(함수·규칙·색인)을 넣는다.
- 요구사항이 모호하면 추측하지 말고 질문한다.

## 기술 스택 (임의로 바꾸지 않는다)
- 프론트엔드: Next.js App Router + TypeScript + Tailwind CSS. Vercel에 배포
- Firebase JS SDK는 모듈식 API만 쓴다(`firebase/compat` 금지)
- 백엔드: Firebase Authentication, Cloud Firestore, Cloud Storage, Cloud Functions(2세대, TypeScript)
- 지도: MapLibre GL JS + OpenFreeMap 타일. 카카오맵·구글맵 SDK는 추가하지 않는다
- 추억 영상: Remotion Player로 앱 안에서 재생. mp4 렌더링은 나중에
- AI(제목·캡션 생성): LLM API는 Cloud Functions에서만 호출한다
- PWA(manifest)
- 새 라이브러리를 추가할 때는 계획에 이유를 적는다

## 명령어
- `npm run dev` 개발 서버 / `npm run build` 빌드 / `npm run lint` 린트
- `npm --prefix functions run build` 함수 빌드
- `firebase emulators:start` 로컬 에뮬레이터(Auth·Firestore·Functions·Storage)
- `npm run test:rules` 에뮬레이터를 띄워 `tests/rules/`의 보안 규칙·함수 테스트를 실행한다(처음 한 번 `npm --prefix functions install` 필요)
- `firebase deploy`는 사람이 승인한 뒤 로컬 세션에서만 실행하고, 대상은 `--only`로 지정한다. 클라우드 세션에서는 배포하지 않는다

## 폴더·설정 규칙
- 화면은 `app/`, 공통 UI는 `components/`, Firebase 초기화는 `lib/firebase/`(initializeApp은 한 곳에서만)
- 서버 코드는 `functions/src/`에 기능별 파일로 나누고 `index.ts`에서 export 한다
- `firebase.json`, `firestore.rules`, `firestore.indexes.json`, `storage.rules`는 저장소 루트에 둔다
- 화면 파일 맨 위에 스토리보드 번호를 주석으로 적는다 (예: `// S02 새 여정 만들기`)
- 프론트 환경변수: `NEXT_PUBLIC_FIREBASE_API_KEY`, `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN`, `NEXT_PUBLIC_FIREBASE_PROJECT_ID`, `NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET`, `NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID`, `NEXT_PUBLIC_FIREBASE_APP_ID`. 이 값들은 공개돼도 되고, 권한은 보안 규칙으로 지킨다
- 비밀 값(LLM API 키 등)은 `firebase functions:secrets:set`으로 저장하고 `defineSecret`으로 읽는다. 코드·커밋·`.env` 파일에 넣지 않는다
- 서비스 계정 키(JSON)는 만들지도, 커밋하지도 않는다
- 함수 공통 옵션: 리전 `asia-northeast3`(서울, Firestore와 같은 위치), `maxInstances: 10`. `minInstances`는 설정하지 않는다(상시 과금)

## UI 규칙
- 모바일 우선, 기준 폭 390px. 하단 탭: 홈 / 탐색 / 마이로그
- 화면 문구는 모두 한국어
- 글자와 배경의 명도 대비는 4.5:1 이상 (WCAG AA)

## 화면 번호 (스토리보드 v3)
- S01 홈 / S01-A 여행 기간 중 홈(지금 기록하기)
- S02 새 여정 만들기 / S03 여행 기간 달력 / 초대하기(링크 복사·카카오톡·공유 창)
- S04 앱 내 카메라 / S05 기록 올리기(짧은 글·태그·테마)
- S06 우리 기록(시간순 공동 피드) / S07 위치 저장(+30코인, 탐색 공개 선택)
- S08 탐색(세로 스와이프 영상) / S09 검색 결과(지역·테마)
- S10 마이로그(여정 목록) / S11 여정 상세(기록·추억 지도·추억 영상 탭)
- S12 추억 지도 / S13 추억 타임라인
- S14 추억 영상 만들기(하루·전체·기간) / S15 재생·저장·공유
- I01 초대 확인 / I02 참여 완료·내 알림 설정 / I03 참여한 여정 / I04 공동 피드의 첫 기록

## 데이터 모델 (Firestore 초안. 바꾸면 이 파일도 함께 고친다)
- `users/{uid}`: nickname, photoURL, coins(서버만 수정), createdAt
- `journeys/{journeyId}`: name, city, country, startDate, endDate, ownerId, memberIds(배열), memberCount, inviteCode, createdAt. startDate·endDate는 현지 달력 날짜 문자열 `"YYYY-MM-DD"`
- `journeys/{journeyId}/members/{uid}`: role(owner|member), notifyIntervalHours(1|2|3|null), joinedAt
- `journeys/{journeyId}/logs/{logId}`: authorId, mediaType(photo|video|text), mediaPath, body, theme, taggedUids, capturedAt(Timestamp), capturedTz, location({lat, lng} 또는 null), placeName, isPublic(기본 false), createdAt. mediaPath는 `journeys/{journeyId}/{작성자 uid}/{파일 이름}`(글 기록은 null)
- `.../logs/{logId}/comments/{commentId}`: authorId, body, createdAt
- `.../logs/{logId}/reactions/{uid}`: emoji, createdAt (1인 1반응)
- `journeys/{journeyId}/recaps/{recapId}`: rangeType(day|journey|custom), startDate, endDate, title, captions, createdAt
- `invites/{inviteCode}`: journeyId, name, city, startDate, endDate, memberCount, inviterName. 로그인 전 초대 화면용 요약만 담는다
- `publicLogs/{logId}`: 탐색용 공개 사본(journeyId, city, theme, mediaPath, placeName, location, createdAt)
- `sharedRecaps/{slug}`: 공유를 누른 리캡의 공개 사본
- `users/{uid}/coinLedger/{logId}`: reason, amount, journeyId, createdAt. 문서 ID가 logId라 기록당 한 번만 생긴다

## 보안 규칙 (기본 거부)
- 모든 경로는 거부에서 시작해 필요한 것만 허용한다
- 여정과 그 하위 문서는 `memberIds`에 있는 사람만 읽고 쓴다
- 여정 생성과 참여는 서버 함수(`createJourney`, `joinJourney`)로만 한다. 클라이언트는 `journeys` 문서를 직접 만들거나 `memberIds`를 고치지 못한다
- 기록 수정·삭제는 작성자만. `location`, `placeName`, `isPublic`은 클라이언트가 직접 바꾸지 못하고 `saveLogLocation` 함수로만 바꾼다
- `users/{uid}`는 로그인한 사용자가 읽을 수 있고, 본인은 nickname·photoURL만 고칠 수 있다
- `coins`, `coinLedger`, `publicLogs`, `sharedRecaps`, `invites`는 클라이언트가 쓰지 못한다
- 비로그인 읽기는 `invites`·`sharedRecaps`의 문서 단건 읽기(get)와 `publicLogs` 목록만 허용한다
- 초대 코드와 공유 slug는 추측하기 어려운 12자 이상 무작위 문자열로 만든다
- Storage: `journeys/{journeyId}/...`는 여정 구성원만 읽고, 올리기·지우기는 본인 폴더 `journeys/{journeyId}/{uid}/`에서만 한다(Firestore 구성원 정보로 확인). 이미지 10MB·영상 50MB 미만, `image/*`·`video/*`만 허용. `public/...`은 읽기만 공개하고 쓰기는 서버만

## 서버 함수 (functions/src)
- 모든 callable은 로그인 여부와 여정 구성원 여부를 먼저 확인한다
- `createJourney`: 여정 문서, owner 멤버 문서, `invites` 요약을 한 트랜잭션으로 만든다
- `joinJourney`: 초대 코드로 참여한다. memberIds·members·memberCount·invites 요약을 함께 갱신한다
- `saveLogLocation`: 위치 저장과 30코인 지급을 한 트랜잭션으로 처리한다. coinLedger 문서가 이미 있으면 코인은 주지 않는다
- `syncPublicLog`(Firestore 트리거): 공개이고 위치가 있는 기록만 publicLogs 사본과 `public/` 미디어 사본을 만들고, 조건이 깨지면 지운다
- `generateRecapCaptions`: 썸네일·시간·장소를 LLM에 보내 제목·장면 순서·캡션을 JSON으로 받아 저장한다. 사용자당 하루 호출 횟수를 제한한다
- `shareRecap`: sharedRecaps 사본과 공유 링크를 만든다

## 인증
- 로그인 수단: 카카오(OpenID Connect, provider ID `oidc.kakao`, issuer `https://kauth.kakao.com`, 코드 흐름)가 기본, 구글이 보조
- 모바일은 `signInWithRedirect`를 쓴다. 앱이 Firebase Hosting이 아닌 Vercel에 있으므로, Next.js rewrites로 `/__/auth/:path*`를 `https://<프로젝트ID>.firebaseapp.com/__/auth/:path*`에 프록시하고 `authDomain`을 앱 도메인으로 둔다 (Firebase 문서 "redirect best practices"의 Option 3)
- 초대 링크 `/invite/[code]`는 로그인 전에도 `invites` 요약을 보여주고, 로그인 후 같은 주소로 돌아와 `joinJourney`로 참여를 끝낸다

## 데이터 원칙
- 카카오·구글 장소 검색 결과는 DB에 저장하지 않는다(이용약관). 장소 이름은 사용자가 입력한 텍스트, 좌표는 기기 위치나 사용자가 지도에서 고른 점만 저장한다
- 위치는 사용자가 허용했을 때만 저장한다. 좌표 (0,0)은 결측으로 처리한다
- 시간은 Timestamp(UTC)로 저장하고 촬영지 시간대(capturedTz)를 함께 저장한다. DAY RECAP의 '하루'는 현지 시간 기준으로 나눈다
- 사진은 업로드 전에 브라우저에서 줄이고 HEIC는 JPEG/WebP로 바꾼다. 영상은 15초 이하

## 비용 관리 (Blaze 요금제)
- 목록 화면은 `limit()`과 페이지 나누기를 쓴다. 한 화면에 실시간 리스너(onSnapshot)를 여러 개 붙이지 않는다
- 외부 API를 부르는 함수에는 호출 횟수 제한을 둔다
- Storage 기본 버킷은 무료 한도가 적용되는 US 리전에 있다. 미디어는 줄여서 올린다

## 알려진 함정
- 구글 로그인은 카카오톡·인스타그램 인앱 브라우저에서 막힌다. 인앱 브라우저를 감지하면 '브라우저로 열기' 안내를 보여준다
- Vercel 미리보기 주소는 PR마다 바뀐다. 로그인은 Firebase 승인 도메인과 카카오 리다이렉트 URI에 등록해 둔 고정 주소(실서비스·staging)에서만 테스트한다
- 위치·푸시는 HTTPS에서만 동작한다. 폰 테스트는 Vercel 주소로 한다
- 아이폰 웹 푸시(FCM 포함)는 홈 화면에 추가한 PWA에서만 동작한다

## 지금 범위 밖 (요청이 있을 때만 만든다)
- 촬영 알림(FCM·예약 함수), mp4 렌더링·저장, 월별·연도별 타임라인, '1년 전 오늘'
