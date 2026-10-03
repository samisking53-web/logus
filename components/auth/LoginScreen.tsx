"use client";

// 로그인 화면: 구글 계정으로 시작한다. 처음이면 로그인 뒤 회원가입(/signup)으로 이어진다.
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { checkRedirectResult, loginErrorMessage, signInWithGoogle } from "@/lib/auth/google";
import { chromeIntentUrl, detectInAppBrowser, type InAppBrowser } from "@/lib/auth/inAppBrowser";
import { safeNext } from "@/lib/auth/safeNext";

export default function LoginScreen() {
  const { state } = useAuth();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [inApp, setInApp] = useState<InAppBrowser>(null);
  const [copied, setCopied] = useState(false);

  // 인앱 브라우저 감지는 브라우저에서만 할 수 있다.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 서버에는 브라우저 정보가 없어 화면이 뜬 뒤 한 번 읽는다
    setInApp(detectInAppBrowser(navigator.userAgent));
  }, []);

  // 구글 페이지에서 돌아온 경우 오류가 있었는지 확인한다.
  useEffect(() => {
    if (state.status === "unconfigured") return;
    checkRedirectResult().catch((e: unknown) => setError(loginErrorMessage(e)));
  }, [state.status]);

  // 로그인이 끝나면 다음 화면으로 보낸다.
  useEffect(() => {
    if (state.status === "ready") router.replace(next);
    if (state.status === "needsProfile") router.replace(`/signup?next=${encodeURIComponent(next)}`);
  }, [state.status, next, router]);

  async function handleGoogle() {
    setBusy(true);
    setError(null);
    try {
      await signInWithGoogle();
    } catch (e) {
      setError(loginErrorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(location.href);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  const signingIn = busy || state.status === "loading" || state.status === "ready" || state.status === "needsProfile";

  return (
    <div className="flex min-h-[70dvh] flex-col justify-center gap-8">
      <div className="text-center">
        <p className="text-3xl font-bold tracking-wide text-primary-strong">LOG EARTH</p>
        <h1 className="mt-3 text-xl font-bold text-text">함께한 여정을 함께 기록해요</h1>
      </div>

      {inApp ? (
        // 인앱 브라우저: 구글이 로그인을 막으므로 브라우저로 열도록 안내한다.
        <section className="rounded-3xl border border-border bg-surface p-5 text-text" aria-live="polite">
          <p className="font-bold">{inApp.name} 안에서는 구글 로그인을 할 수 없어요.</p>
          <p className="mt-2 text-text-secondary">
            {inApp.isAndroid
              ? "아래 버튼으로 크롬에서 열어 주세요."
              : "오른쪽 아래(또는 위) ⋯ 메뉴에서 'Safari로 열기'를 눌러 주세요."}
          </p>
          <div className="mt-4 flex flex-col gap-2">
            {inApp.isAndroid && (
              <a
                href={chromeIntentUrl(location.href)}
                className="rounded-full bg-primary px-5 py-3 text-center font-bold text-on-primary"
              >
                크롬으로 열기
              </a>
            )}
            <button
              type="button"
              onClick={copyLink}
              className="rounded-full border border-border bg-background px-5 py-3 font-bold text-primary-strong"
            >
              {copied ? "주소를 복사했어요" : "주소 복사하기"}
            </button>
          </div>
        </section>
      ) : state.status === "unconfigured" ? (
        <p className="rounded-2xl border-2 border-error bg-surface p-5 text-text" role="alert">
          Firebase 설정값이 없어서 로그인할 수 없어요. (.env.local 또는 Vercel 환경변수 확인)
        </p>
      ) : (
        <button
          type="button"
          onClick={handleGoogle}
          disabled={signingIn}
          // 구글 로그인 버튼은 구글 브랜드 가이드 색을 쓴다 — 팔레트 예외(CLAUDE.md, globals.css의 google-* 토큰)
          className="flex h-14 items-center justify-center gap-3 rounded-full border border-google-outline bg-google-surface px-6 text-lg font-semibold text-google-label disabled:cursor-wait"
        >
          <GoogleLogo />
          {signingIn ? "로그인하는 중…" : "Google로 시작하기"}
        </button>
      )}

      {error && (
        <p role="alert" className="rounded-2xl border-2 border-error bg-surface p-4 text-text">
          {error}
        </p>
      )}

      <p className="text-center text-sm text-text-weak">처음이면 로그인 후 닉네임만 정하면 가입이 끝나요.</p>
    </div>
  );
}

function GoogleLogo() {
  // 구글 공식 "G" 로고 색 (브랜드 가이드)
  return (
    <svg viewBox="0 0 48 48" className="size-6" aria-hidden="true">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
    </svg>
  );
}
