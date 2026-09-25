<!-- 팀 메모: Claude가 같은 실수를 두 번 하면 여기에 규칙을 한 줄씩 추가하세요. 200줄 이하로 유지합니다. 이 주석은 Claude에게 전달되지 않습니다. -->

# LOG US (로그어스)

여정(여행·행사) 단위로 친구·연인·가족이 사진·영상·글을 함께 기록하고, 추억 지도와 DAY RECAP 영상으로 다시 보는 모바일 웹앱(PWA). 대학 캡스톤 프로젝트이고 팀원 3명 모두 개발 초보다.

## 작업 방식
- 한 세션에서는 화면이나 기능 하나만 다룬다. 요청 범위 밖의 파일은 고치지 말고 제안만 한다.
- 화면 작업 전에 `docs/storyboard-v3.pdf`에서 해당 화면 번호(아래 목록)를 먼저 확인한다.
- 코드를 쓰기 전에 만들거나 바꿀 파일, 사용할 테이블, 완료 기준을 계획으로 보여준다.
- 끝내기 전에 `npm run lint`와 `npm run build`를 통과시킨다.
- 커밋 메시지와 PR 설명은 한국어로 쓴다. PR 설명에는 (1) 바뀐 점 (2) 폰에서 확인하는 순서 (3) 개발 초보도 이해할 수 있는 코드 설명을 넣는다.
- 요구사항이 모호하면 추측하지 말고 질문한다.

## 기술 스택 (임의로 바꾸지 않는다)
- Next.js App Router + TypeScript + Tailwind CSS. Supabase 공식 `with-supabase` 템플릿 기반
- Supabase: Auth(카카오 로그인 기본, 구글 보조), Postgres, Storage
- 지도: MapLibre GL JS + OpenFreeMap 타일. 카카오맵·구글맵 SDK는 추가하지 않는다
- 추억 영상: Remotion Player로 앱 안에서 재생. mp4 렌더링은 나중에
- AI(제목·캡션 생성): LLM API는 서버 코드(`app/api/`)에서만 호출한다
- 배포: Vercel. PWA(manifest)
- 새 라이브러리를 추가할 때는 계획에 이유를 적는다

## 명령어
- `npm run dev` 개발 서버
- `npm run build` 빌드 확인
- `npm run lint` 린트

## 폴더·설정 규칙
- 화면은 `app/` 라우트, 공통 UI는 `components/`, Supabase 클라이언트는 `lib/supabase/`
- 화면 파일 맨 위에 스토리보드 번호를 주석으로 적는다 (예: `// S02 새 여정 만들기`)
- DB 변경은 `supabase/migrations/NNNN_이름.sql` 파일로만 한다. 팀원이 Supabase 대시보드 SQL Editor에서 직접 실행하므로 파일 맨 위에 실행 순서와 한 줄 설명을 주석으로 단다
- 환경변수: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`
- 비밀 키(Supabase secret·service_role 키, AI API 키)는 코드·커밋·브라우저 코드에 넣지 않는다. `.env*` 파일은 커밋하지 않는다

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

## 데이터 모델 (초안. 바꾸면 이 파일도 함께 고친다)
- `profiles`: id(= auth.users.id), nickname, avatar_url
- `journeys`: id, name, city, country, start_date, end_date, owner_id, invite_code(unique), created_at
- `journey_members`: journey_id, user_id, role(owner|member), notify_interval_hours(1|2|3|null), joined_at. PK (journey_id, user_id)
- `logs`: id, journey_id, author_id, media_type(photo|video|text), media_path, body, theme, captured_at(UTC), captured_tz, lat, lng, place_name, is_public(기본 false), created_at
- `log_tags`: log_id, user_id
- `comments`: id, log_id, author_id, body, created_at
- `reactions`: log_id, user_id, emoji. unique (log_id, user_id, emoji)
- `coin_ledger`: id, user_id, log_id, reason, amount, created_at. unique (log_id, reason). 코인 잔액은 이 표의 합계로 계산한다
- `recaps`: id, journey_id, range_type(day|journey|custom), start_date, end_date, title, captions(jsonb), share_slug(unique, 공유할 때만 생성), created_at

## 권한·보안 (RLS 필수)
- 모든 테이블에 RLS를 켜고, 테이블을 만드는 migration에 정책도 함께 넣는다
- 여정과 그 기록·댓글·반응·리캡은 `journey_members`에 있는 사람만 읽고 쓴다
- 탐색에는 `is_public = true`이고 좌표가 있는 기록만 노출한다
- 미디어 Storage 버킷은 비공개. 경로는 `journeys/{journey_id}/...`, 여정 구성원만 접근한다
- 코인은 브라우저 코드에서 지급하지 않는다. 위치 저장과 30코인 지급은 DB 함수(RPC) 하나에서 처리하고, 기록당 1회는 `coin_ledger`의 unique 제약으로 막는다
- 초대 링크 `/invite/[code]`는 로그인 전에도 여정 요약(이름·도시·기간·인원)을 보여주고, 로그인 후 같은 주소로 돌아와 참여를 끝낸다

## 데이터 원칙
- 카카오·구글 장소 검색 결과는 DB에 저장하지 않는다(이용약관). 장소 이름은 사용자가 입력한 텍스트, 좌표는 기기 위치나 사용자가 지도에서 고른 점만 저장한다
- 위치는 사용자가 허용했을 때만 저장한다. 좌표 (0,0)은 결측으로 처리한다
- 시간은 UTC로 저장하고 촬영지 시간대(captured_tz)를 함께 저장한다. DAY RECAP의 '하루'는 현지 시간 기준으로 나눈다
- 사진은 업로드 전에 브라우저에서 줄이고 HEIC는 JPEG/WebP로 바꾼다. 영상은 15초 이하, 파일 하나 50MB 미만(Supabase 무료 플랜 한도)

## 알려진 함정
- 구글 로그인은 카카오톡·인스타그램 인앱 브라우저에서 막힌다. 인앱 브라우저를 감지하면 '브라우저로 열기' 안내를 보여준다
- 위치·푸시 같은 기능은 HTTPS에서만 동작한다. 폰 테스트는 Vercel 미리보기 주소로 한다
- 아이폰 웹 푸시는 홈 화면에 추가한 PWA에서만 동작한다

## 지금 범위 밖 (요청이 있을 때만 만든다)
- 촬영 알림 웹 푸시, mp4 렌더링·저장, 월별·연도별 타임라인, '1년 전 오늘'
