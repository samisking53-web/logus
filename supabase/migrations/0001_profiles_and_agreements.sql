-- 실행 순서: 0001 (가장 먼저 실행)
-- 설명: 회원 프로필(profiles)과 약관 동의 기록(user_agreements) 표를 만들고, 각자 자기 것만 읽고 쓰도록 권한(RLS)을 건다
-- 실행 방법: Supabase 대시보드 → SQL Editor → New query에 이 파일 전체를 붙여 넣고 Run. 한 번만 실행한다

-- ─────────────────────────────────────────────
-- 1) 약관 동의 기록 (user_agreements)
--    가입 1단계에서 체크한 항목을 약관 버전·동의 시각과 함께 쌓아 둔다.
--    나중에 어떤 내용에 동의했는지 확인할 수 있도록 고치거나 지우지 않고 새 줄을 추가만 한다.
-- ─────────────────────────────────────────────
create table public.user_agreements (
  user_id uuid not null references auth.users (id) on delete cascade,
  -- age_14: 만 14세 이상, service_terms: 서비스 이용약관,
  -- location_terms: 위치기반서비스 이용약관, new_log_notice: 새 기록 알림 받기(선택)
  agreement text not null
    check (agreement in ('age_14', 'service_terms', 'location_terms', 'new_log_notice')),
  -- 동의한 약관의 버전 (lib/terms.ts의 TERMS_VERSION)
  version text not null check (char_length(version) between 1 and 40),
  agreed boolean not null,
  -- 동의 시각은 서버 시간으로만 채운다 (아래 GRANT에서 이 칸은 앱이 직접 쓰지 못하게 막음)
  agreed_at timestamptz not null default now(),
  primary key (user_id, agreement, agreed_at)
);

alter table public.user_agreements enable row level security;

create policy "본인 동의 기록 보기" on public.user_agreements
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy "본인 동의 기록 추가" on public.user_agreements
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

-- 수정·삭제 정책은 일부러 만들지 않는다 (기록은 추가만 한다)
revoke all on table public.user_agreements from anon, authenticated;
grant select on table public.user_agreements to authenticated;
grant insert (user_id, agreement, version, agreed) on table public.user_agreements to authenticated;

-- ─────────────────────────────────────────────
-- 2) 회원 프로필 (profiles)
--    가입 2단계에서 정한 닉네임과 계정 사진. 이 줄이 있으면 가입을 마친 사람으로 본다.
-- ─────────────────────────────────────────────
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  -- 앞뒤 공백 없이 1~12글자
  nickname text not null
    check (nickname = btrim(nickname) and char_length(nickname) between 1 and 12),
  -- 구글·카카오 계정 사진 주소. https 주소만 저장한다
  avatar_url text check (avatar_url is null or avatar_url ~ '^https://'),
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- 지금은 자기 프로필만 본다. 여정 기능을 만들 때 같은 여정 멤버끼리 볼 수 있는 정책을 추가한다
create policy "본인 프로필 보기" on public.profiles
  for select to authenticated
  using ((select auth.uid()) = id);

-- 필수 약관 3개(만 14세, 서비스, 위치기반)에 동의한 기록이 있어야 프로필을 만들 수 있다
create policy "필수 약관 동의 후 본인 프로필 만들기" on public.profiles
  for insert to authenticated
  with check (
    (select auth.uid()) = id
    and (
      select count(distinct a.agreement)
      from public.user_agreements a
      where a.user_id = (select auth.uid())
        and a.agreed
        and a.agreement in ('age_14', 'service_terms', 'location_terms')
    ) = 3
  );

create policy "본인 프로필 고치기" on public.profiles
  for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

-- 삭제 정책은 만들지 않는다 (계정을 지우면 on delete cascade로 함께 지워진다)
revoke all on table public.profiles from anon, authenticated;
grant select on table public.profiles to authenticated;
grant insert (id, nickname, avatar_url) on table public.profiles to authenticated;
grant update (nickname, avatar_url) on table public.profiles to authenticated;
