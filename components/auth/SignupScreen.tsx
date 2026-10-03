"use client";

// P01 회원가입: 구글로 로그인한 뒤 프로필(사진·닉네임)을 정하면 가입이 끝난다.
// 이메일·비밀번호는 구글 계정이 대신하므로 받지 않는다.
// 직접 사진 올리기(P02 사진 미리보기)는 '사진 수정' 화면과 함께 다음 작업에서 만든다.
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import Avatar from "@/components/home/Avatar";
import { useAuth } from "@/lib/auth/AuthProvider";
import { createProfile, safePhotoURL, validateNickname } from "@/lib/auth/google";
import { safeNext } from "@/lib/auth/safeNext";

export default function SignupScreen() {
  const { state, refreshProfile, signOut } = useAuth();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));

  const googleName = state.status === "needsProfile" ? (state.user.displayName ?? "") : "";
  const googlePhoto = state.status === "needsProfile" ? safePhotoURL(state.user.photoURL) : null;

  const [nickname, setNickname] = useState<string | null>(null); // null = 아직 안 고침 → 구글 이름을 보여 줌
  const [useGooglePhoto, setUseGooglePhoto] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const nicknameValue = nickname ?? Array.from(googleName).slice(0, 20).join("");
  const photoURL = useGooglePhoto ? googlePhoto : null;

  // 로그인 전이면 로그인으로, 이미 가입했으면 원래 가려던 화면으로 보낸다.
  useEffect(() => {
    if (state.status === "signedOut" || state.status === "unconfigured") {
      router.replace(`/login?next=${encodeURIComponent(next)}`);
    }
    if (state.status === "ready") router.replace(next);
  }, [state.status, next, router]);

  if (state.status !== "needsProfile") {
    return (
      <p role="status" className="py-20 text-center text-text-secondary">
        불러오는 중이에요…
      </p>
    );
  }
  const uid = state.user.uid;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const result = validateNickname(nicknameValue);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await createProfile(uid, result.value, photoURL);
      await refreshProfile(); // 상태가 ready가 되면 위의 useEffect가 다음 화면으로 보낸다
    } catch (e) {
      console.error("회원가입 실패", e);
      setError("가입하지 못했어요. 잠시 후 다시 시도해 주세요.");
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6" noValidate>
      <p className="text-center text-2xl font-bold tracking-wide text-primary-strong">LOG EARTH</p>
      <h1 className="rounded-full bg-surface py-3 text-center text-xl font-bold text-text">회원가입</h1>

      <section aria-label="프로필 사진" className="flex flex-col items-center gap-3">
        <Avatar photoURL={photoURL} size={112} />
        <p className="font-semibold text-primary-strong">프로필 사진 설정(선택)</p>
        {googlePhoto && (
          <label className="flex items-center gap-2 text-text-secondary">
            <input
              type="checkbox"
              checked={!useGooglePhoto}
              onChange={(e) => setUseGooglePhoto(!e.target.checked)}
              className="size-5 accent-primary"
            />
            기본 프로필 사용
          </label>
        )}
        <p className="text-sm text-text-weak">사진은 가입 후 홈의 &lsquo;사진 수정&rsquo;에서 바꿀 수 있어요.</p>
      </section>

      <label className="flex flex-col gap-2">
        <span className="font-semibold text-text">닉네임</span>
        <input
          type="text"
          value={nicknameValue}
          onChange={(e) => setNickname(e.target.value)}
          maxLength={40}
          autoComplete="nickname"
          placeholder="여정에서 보일 이름"
          aria-invalid={error ? true : undefined}
          className="rounded-2xl border border-border bg-surface px-5 py-4 text-lg text-text placeholder:text-text-secondary"
        />
        <span className="text-sm text-text-weak">1~20자. 함께 여행하는 사람들에게 보여요.</span>
      </label>

      <p className="text-sm text-text-secondary">
        구글 계정 <span className="font-semibold text-text">{state.user.email}</span>(으)로 가입해요.
      </p>

      {error && (
        <p role="alert" className="rounded-2xl border-2 border-error bg-surface p-4 text-text">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={saving}
        className="rounded-full bg-primary py-4 text-xl font-bold text-on-primary active:bg-primary-strong disabled:cursor-wait"
      >
        {saving ? "가입하는 중…" : "가입 완료"}
      </button>
      <button type="button" onClick={() => void signOut()} className="text-text-secondary underline">
        다른 구글 계정으로 하기
      </button>
    </form>
  );
}
