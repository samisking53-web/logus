"use client";

// 가입 2단계: 그룹에서 보일 닉네임 정하기. 로그인한 계정의 이름과 사진을 미리 채운다.
import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";
import { Info } from "lucide-react";
import { StepHeader } from "@/components/onboarding/step-header";
import { NextButton } from "@/components/onboarding/next-button";
import { AvatarPreview } from "@/components/onboarding/avatar-preview";
import { createClient } from "@/lib/supabase/client";
import { readNextParam, withNext } from "@/lib/onboarding";

const MAX_NICKNAME_LENGTH = 12;

const PROVIDER_NAMES: Record<string, string> = { google: "Google", kakao: "카카오" };

// 글자 수 세기. 한글 한 글자, 이모지 하나를 모두 1로 센다 (DB의 char_length와 같은 방식)
function countChars(text: string) {
  return Array.from(text).length;
}

// 카카오는 사진 주소를 http로 주기도 해서 https로 바꾼다. 그 밖의 주소는 쓰지 않는다
function toHttpsUrl(url: unknown) {
  if (typeof url !== "string") return null;
  if (url.startsWith("https://")) return url;
  if (url.startsWith("http://")) return `https://${url.slice("http://".length)}`;
  return null;
}

export function ProfileForm() {
  const router = useRouter();
  const [userId, setUserId] = useState<string | null>(null);
  const [providerName, setProviderName] = useState("로그인한");
  const [nickname, setNickname] = useState("");
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  // 다음 화면으로 넘어가는 동안 true (terms-form.tsx와 같은 이유)
  const [moving, startMoving] = useTransition();
  const [error, setError] = useState<string | null>(null);

  // 로그인한 계정 정보(이름·사진)를 불러와 미리 채운다
  useEffect(() => {
    createClient()
      .auth.getUser()
      .then(({ data: { user } }) => {
        if (!user) {
          router.replace("/login");
          return;
        }
        const meta = user.user_metadata ?? {};
        const name = String(meta.full_name ?? meta.name ?? meta.nickname ?? "").trim();
        setUserId(user.id);
        setProviderName(PROVIDER_NAMES[user.app_metadata?.provider ?? ""] ?? "로그인한");
        // 이미 입력해 둔 닉네임이 있으면(1단계에 다녀온 경우 등) 덮어쓰지 않는다
        setNickname((prev) => prev || Array.from(name).slice(0, MAX_NICKNAME_LENGTH).join(""));
        setAvatarUrl(toHttpsUrl(meta.avatar_url ?? meta.picture));
      });
  }, [router]);

  const trimmed = nickname.trim();
  const length = countChars(trimmed);
  const tooLong = length > MAX_NICKNAME_LENGTH;
  const busy = saving || moving;
  const canSubmit = userId !== null && length > 0 && !tooLong && !busy;

  async function submit() {
    if (!userId) return;
    setSaving(true);
    setError(null);
    const { error } = await createClient()
      .from("profiles")
      .insert({ id: userId, nickname: trimmed, avatar_url: avatarUrl });

    setSaving(false);
    // 23505: 이미 프로필이 있음(두 번 누른 경우) → 그대로 다음 단계로
    if (error && error.code !== "23505") {
      // 42501: 필수 약관 동의 기록이 없어서 DB가 막은 경우 → 1단계로 돌려보낸다
      if (error.code === "42501") {
        router.replace(withNext("/onboarding/terms", readNextParam()));
        return;
      }
      setError("저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
      return;
    }
    startMoving(() => router.push(withNext("/onboarding/welcome", readNextParam())));
  }

  return (
    <>
      <StepHeader step={2} onBack={() => router.push(withNext("/onboarding/terms", readNextParam()))} />
      <h1 className="mt-4 text-2xl font-bold leading-snug">
        그룹에서 보일
        <br />
        이름을 정해요
      </h1>
      <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
        {providerName} 계정의 이름과 사진을 미리 채워 뒀어요. 그대로 두거나 바꿔도 괜찮아요.
      </p>

      <div className="mt-8 flex justify-center">
        <AvatarPreview nickname={trimmed} avatarUrl={avatarUrl} />
      </div>

      <label htmlFor="nickname" className="mt-8 text-sm font-semibold">
        닉네임
      </label>
      <input
        id="nickname"
        value={nickname}
        onChange={(e) => setNickname(e.target.value)}
        autoComplete="nickname"
        aria-describedby="nickname-help"
        aria-invalid={tooLong}
        className="mt-2 h-14 w-full rounded-2xl border bg-card px-4 text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[invalid=true]:border-destructive"
      />
      <div id="nickname-help" className="mt-2 flex justify-between text-xs text-muted-foreground">
        <span>{tooLong ? `${MAX_NICKNAME_LENGTH}글자까지 쓸 수 있어요` : "그룹 멤버에게만 보여요"}</span>
        <span className={tooLong ? "text-destructive" : undefined}>
          {length} / {MAX_NICKNAME_LENGTH}
        </span>
      </div>

      <div className="mt-6 flex gap-3 rounded-2xl bg-brand-soft p-4">
        <Info className="mt-0.5 size-5 shrink-0 text-brand-strong" aria-hidden="true" />
        <div className="text-sm">
          <p className="font-semibold">위치와 사진 권한은 나중에 물어봐요</p>
          <p className="mt-1 text-muted-foreground">첫 기록을 남길 때 무엇에 쓰는지 설명하고 요청해요.</p>
        </div>
      </div>

      <div className="min-h-8 flex-1" />
      {error && (
        <p role="alert" className="mb-2 text-sm text-destructive">
          {error}
        </p>
      )}
      <NextButton disabled={!canSubmit} onClick={submit}>
        {busy ? "저장하는 중…" : "다음"}
      </NextButton>
    </>
  );
}
