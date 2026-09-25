# 로그인 설정 안내 (Supabase · 카카오 · 구글)

카카오·구글 로그인이 실제로 동작하려면 아래 설정을 **한 번만** 해 두면 됩니다. 위에서부터 순서대로 진행하세요.
비밀 값(DB 비밀번호, secret·service_role 키, 카카오·구글 Client Secret)은 코드나 채팅에 붙여 넣지 않습니다. 각 서비스의 설정 화면에만 입력합니다.

> 메뉴 이름은 각 서비스 화면이 바뀌면서 조금씩 달라질 수 있습니다. 비슷한 이름을 찾아 들어가면 됩니다.

## 1. Supabase 프로젝트 만들기

1. [supabase.com](https://supabase.com)에 로그인 → **New project**
   - 이름: `logus`
   - 지역(Region): **Northeast Asia (Seoul)**
   - Database Password: 안전한 곳에 따로 보관합니다 (코드에 넣지 않음)
2. 프로젝트가 만들어지면 **Project Settings → API Keys**에서 두 값을 복사해 둡니다.
   - **Project URL** (예: `https://abcdefgh.supabase.co`)
   - **Publishable key** (`sb_publishable_…`로 시작)
   - `secret`·`service_role` 키는 이 앱에서 쓰지 않습니다. 복사하지 마세요.

## 2. Vercel에 환경변수 넣기

1. Vercel → `logus` 프로젝트 → **Settings → Environment Variables**
2. 두 개를 추가합니다. **Production**과 **Preview**를 모두 체크합니다.

   | 이름 | 값 |
   |---|---|
   | `NEXT_PUBLIC_SUPABASE_URL` | 1번의 Project URL |
   | `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | 1번의 Publishable key |

3. **Deployments** 탭 → 맨 위 배포 → **⋯ → Redeploy**. 환경변수는 다시 배포해야 반영됩니다.
4. 내 컴퓨터에서 `npm run dev`로 확인하려면 저장소 맨 위에 `.env.local` 파일을 만들고 같은 두 줄을 넣습니다.
   이 파일은 `.gitignore`에 있어 커밋되지 않습니다.

   ```
   NEXT_PUBLIC_SUPABASE_URL=https://abcdefgh.supabase.co
   NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
   ```

## 3. DB 표 만들기

1. Supabase → **SQL Editor → New query**
2. 저장소의 `supabase/migrations/0001_profiles_and_agreements.sql` 내용을 전부 붙여 넣고 **Run**
3. `Success. No rows returned`가 나오면 성공입니다. **Table Editor**에 `profiles`, `user_agreements` 표가 보입니다.
4. 이 파일은 **한 번만** 실행합니다. 다시 실행하면 "이미 있다"는 오류가 납니다.

## 4. 로그인 후 돌아올 주소 허용하기

Supabase → **Authentication → URL Configuration**

- **Site URL**: 앱의 정식 주소 (Vercel 프로젝트 → Settings → Domains에 보이는 주소, 예: `https://logus-xxxx.vercel.app`)
- **Redirect URLs**에 아래를 추가합니다.
  - `https://*-samisking53-web.vercel.app/**`: Vercel 미리보기 주소 전부
  - `https://정식주소/**`: Site URL과 같은 주소 뒤에 `/**`
  - `http://localhost:3000/**`: 내 컴퓨터에서 개발할 때

여기에 없는 주소에서 로그인하면, 로그인을 마친 뒤 앱으로 돌아오지 못합니다.

## 5. 카카오 로그인 연결

**먼저 Supabase에서 Callback URL 복사**: Authentication → **Sign In / Providers** → **Kakao** 펼치기 → **Callback URL** 복사
(모양: `https://abcdefgh.supabase.co/auth/v1/callback`)

**[Kakao Developers](https://developers.kakao.com)**

1. 로그인 → **내 애플리케이션 → 애플리케이션 추가하기** (앱 이름: LOG US)
2. **앱 설정 → 앱 → 플랫폼 키**에서 **REST API 키**를 확인합니다. 이 값이 **Client ID**입니다.
3. 같은 REST API 키를 눌러 편집 화면으로 들어갑니다.
   - **카카오 로그인 리다이렉트 URI**에 위에서 복사한 Supabase Callback URL을 넣고 저장합니다.
   - **카카오 로그인 클라이언트 시크릿 코드**를 확인하고 **사용 설정**을 켭니다. 이 값이 **Client Secret**입니다.
4. **제품 설정 → 카카오 로그인 → 일반**: 사용 설정을 **ON**으로 바꿉니다.
5. **제품 설정 → 카카오 로그인 → 동의항목**
   - **닉네임**(`profile_nickname`), **프로필 사진**(`profile_image`)을 설정합니다.
   - **카카오계정(이메일)**(`account_email`)도 설정합니다. Supabase는 카카오에 이메일을 **항상** 함께 요청합니다(Supabase 로그인 서버 소스에서 확인). 그래서 이 항목이 없으면 카카오가 `KOE205` 오류를 낼 수 있습니다.
   - 이메일 항목은 **비즈 앱**에서만 설정할 수 있습니다. 사업자가 없으면 **앱 설정 → 일반 → 비즈니스 정보**에서 **개인 개발자 비즈 앱**으로 전환합니다(본인 인증 필요).
6. Supabase → Authentication → **Sign In / Providers → Kakao**
   - **Enable** 켜기 → Client ID(REST API 키), Client Secret(클라이언트 시크릿) 입력 → **Save**

## 6. 구글 로그인 연결

**[Google Cloud Console](https://console.cloud.google.com)**

1. 새 프로젝트를 만듭니다 (이름: LOG US).
2. **Google Auth Platform**
   - **Branding**: 앱 이름, 지원 이메일을 입력합니다.
   - **Audience**: External을 고릅니다. 앱을 게시하기 전에는 **테스트 사용자**로 등록한 계정만 로그인할 수 있으니, 팀원 구글 계정을 추가합니다.
   - **Data Access (Scopes)**: `openid`를 추가합니다. `email`, `profile`은 기본으로 들어 있습니다.
3. **Clients → Create client → Web application**
   - **Authorized JavaScript origins**: 정식 주소와 `http://localhost:3000`
   - **Authorized redirect URIs**: 5번에서 복사한 것과 같은 Supabase Callback URL
   - 만들고 나면 **Client ID**, **Client Secret**을 복사합니다.
4. Supabase → Authentication → **Sign In / Providers → Google**
   - **Enable** 켜기 → Client ID, Client Secret 입력 → **Save**

## 7. 폰으로 확인하기

1. Vercel 미리보기 주소를 폰 브라우저(Safari·Chrome)의 **새 탭**에서 엽니다.
2. 시작화면(LOG EARTH)이 나온 뒤 로그인 화면이 나오는지 봅니다.
3. **카카오로 계속하기**를 누릅니다. 카카오 동의가 끝나면 가입 1단계(약관 동의) → 2단계(이름 정하기) → 3단계(준비 완료) → 홈으로 갑니다.
4. 앱을 닫았다가 다시 열었을 때, 로그인 화면 없이 바로 홈이 나오는지 봅니다.
5. **Google 계정으로 계속하기**도 같은 순서로 확인합니다. 다른 구글 계정이면 새로 가입하게 됩니다.
6. 카카오톡으로 링크를 보내 카카오톡 안에서 엽니다. 구글 버튼을 누르면 "다른 브라우저로 열기" 안내가 나와야 합니다.

## 문제가 생기면

| 보이는 증상 | 확인할 곳 |
|---|---|
| 로그인 화면에 "Supabase 연결 설정이 아직 없어…" | 2번 환경변수를 넣고 Redeploy |
| 로그인 없이 홈이 바로 보임 | 환경변수가 없는 배포입니다. 2번 확인 |
| 카카오 `KOE006` | 5-3 카카오 리다이렉트 URI에 Supabase Callback URL이 있는지 |
| 카카오 `KOE205` | 5-5 동의항목(특히 이메일) |
| 구글 `redirect_uri_mismatch` | 6-3 Authorized redirect URIs |
| 구글 `403 disallowed_useragent` | 카카오톡 같은 앱 안에서 연 경우. 다른 브라우저로 열기 |
| 로그인 후 "로그인하지 못했어요"가 반복됨 | 4번 Redirect URLs에 지금 주소가 있는지 |
| 약관 동의 후 "저장하지 못했어요" | 3번 SQL을 실행했는지 |
| 미리보기 주소에 Vercel 로그인 화면이 뜸 | Vercel → Settings → Deployment Protection → Vercel Authentication |

## 참고

- 템플릿에 있던 이메일·비밀번호 로그인 화면(`/auth/login` 등)은 아직 남아 있지만 앱에서는 쓰지 않습니다.
- 로그인·가입 흐름 코드: `app/login`, `app/auth/callback`, `app/onboarding`, `lib/supabase/proxy.ts`
- 약관 본문이 확정되면 `app/terms/[slug]/page.tsx`에 넣고 `lib/terms.ts`의 `TERMS_VERSION`을 올립니다.
