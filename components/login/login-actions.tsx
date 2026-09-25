"use client";

// 로그인 화면 아래쪽: Google·카카오 버튼과 인앱 브라우저 안내
import Image from "next/image";
import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import { hasEnvVars } from "@/lib/utils";
import { safeNextPath } from "@/lib/safe-next";
import { isInAppBrowser, isKakaoTalkBrowser } from "@/lib/in-app-browser";

type Provider = "google" | "kakao";

export function LoginActions() {
  const [pending, setPending] = useState<Provider | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [helpOpen, setHelpOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [inApp, setInApp] = useState({ any: false, kakaoTalk: false });

  // 브라우저 종류와 주소의 error 표시는 화면이 뜬 뒤에 한 번 읽는다
  useEffect(() => {
    const ua = navigator.userAgent;
    setInApp({ any: isInAppBrowser(ua), kakaoTalk: isKakaoTalkBrowser(ua) });
    if (new URLSearchParams(window.location.search).get("error")) {
      setError("로그인하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
    }
    // 카카오·구글 화면에서 뒤로 가기로 돌아오면 브라우저가 이 화면을 그대로 복원한다.
    // 이때 버튼이 '연결하는 중…'에 멈추지 않도록 되돌린다
    const resetPending = (event: PageTransitionEvent) => {
      if (event.persisted) setPending(null);
    };
    window.addEventListener("pageshow", resetPending);
    return () => window.removeEventListener("pageshow", resetPending);
  }, []);

  async function signIn(provider: Provider) {
    if (!hasEnvVars) {
      setError("Supabase 연결 설정이 아직 없어 로그인할 수 없어요. (docs/login-setup.md 참고)");
      return;
    }
    // 구글은 인앱 브라우저에서 로그인을 막으므로, 시도하기 전에 다른 브라우저로 열도록 안내한다
    if (provider === "google" && inApp.any) {
      setHelpOpen(true);
      return;
    }

    setPending(provider);
    setError(null);
    // 로그인을 마치면 /auth/callback을 거쳐 원래 가려던 주소(next)로 돌아온다
    const next = safeNextPath(new URLSearchParams(window.location.search).get("next"));
    const supabase = createClient();
    const { error } = await supabase.auth.signInWithOAuth({
      provider,
      options: {
        redirectTo: `${window.location.origin}/auth/callback?next=${encodeURIComponent(next)}`,
      },
    });
    // 성공하면 카카오·구글 로그인 화면으로 넘어가므로, 여기까지 오면 실패한 것이다
    if (error) {
      setPending(null);
      setError("로그인을 시작하지 못했어요. 다시 시도해 주세요.");
    }
  }

  function openExternalBrowser() {
    // 카카오톡 인앱 브라우저에서 기본 브라우저(Safari·Chrome)로 지금 주소를 연다
    window.location.href = `kakaotalk://web/openExternal?url=${encodeURIComponent(window.location.href)}`;
  }

  async function copyAddress() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      {helpOpen && (
        <div id="in-app-help" className="rounded-2xl border bg-card p-4 text-sm">
          <p className="font-bold">앱 안에서 열면 구글 로그인이 막혀요</p>
          <p className="mt-1 text-muted-foreground">
            카카오톡·인스타그램 같은 앱 안의 브라우저에서는 구글이 로그인을 막아요.
            Safari나 Chrome에서 열면 로그인할 수 있어요. 카카오 로그인은 앱 안에서도 돼요.
          </p>
          {inApp.kakaoTalk ? (
            <button
              type="button"
              onClick={openExternalBrowser}
              className="mt-3 h-11 w-full rounded-xl bg-brand font-semibold text-brand-foreground active:bg-brand-pressed"
            >
              다른 브라우저로 열기
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={copyAddress}
                className="mt-3 h-11 w-full rounded-xl bg-brand font-semibold text-brand-foreground active:bg-brand-pressed"
              >
                주소 복사하기
              </button>
              <p className="mt-2 text-muted-foreground" aria-live="polite">
                {copied
                  ? "복사했어요. Safari나 Chrome 주소창에 붙여 넣어 주세요."
                  : "화면 구석의 ⋯ 메뉴에서 '다른 브라우저로 열기'를 눌러도 돼요."}
              </p>
            </>
          )}
        </div>
      )}

      {error && (
        <p role="alert" className="text-center text-sm text-destructive">
          {error}
        </p>
      )}

      <button
        type="button"
        onClick={() => signIn("google")}
        disabled={pending !== null}
        className="flex h-14 w-full items-center justify-center gap-3 rounded-2xl border border-google-line bg-google text-base font-semibold text-google-foreground disabled:opacity-60"
      >
        <Image src="/brand/google-g.svg" alt="" width={20} height={20} />
        {pending === "google" ? "연결하는 중…" : "Google 계정으로 계속하기"}
      </button>
      <button
        type="button"
        onClick={() => signIn("kakao")}
        disabled={pending !== null}
        // 카카오 로그인 버튼 가이드: 노란 바탕, 검은 심볼, 85% 불투명도의 검은 글자
        className="flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-kakao text-base font-semibold text-kakao-foreground/85 disabled:opacity-60"
      >
        <Image src="/brand/kakao-symbol.svg" alt="" width={20} height={20} />
        {pending === "kakao" ? "연결하는 중…" : "카카오로 계속하기"}
      </button>

      <p className="mt-1 text-center text-xs leading-relaxed text-muted-foreground">
        계속하면 이용약관과 개인정보 처리방침에
        <br />
        동의하는 절차로 넘어가요
      </p>
      <button
        type="button"
        onClick={() => setHelpOpen((open) => !open)}
        aria-expanded={helpOpen}
        aria-controls="in-app-help"
        className="mx-auto py-2 text-xs text-muted-foreground underline underline-offset-2"
      >
        카카오톡에서 열어 로그인이 안 되나요?
      </button>
    </div>
  );
}
