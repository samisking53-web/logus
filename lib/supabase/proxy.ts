import { createServerClient } from "@supabase/ssr";
import type { SupabaseClient } from "@supabase/supabase-js";
import { REQUIRED_AGREEMENT_KEYS } from "@/lib/terms";
import { safeNextPath } from "@/lib/safe-next";
import { NextResponse, type NextRequest } from "next/server";
import { hasEnvVars } from "../utils";

export async function updateSession(request: NextRequest) {
  let supabaseResponse = NextResponse.next({
    request,
  });

  // If the env vars are not set, skip proxy check. You can remove this
  // once you setup the project.
  if (!hasEnvVars) {
    return supabaseResponse;
  }

  // With Fluid compute, don't put this client in a global environment
  // variable. Always create a new one on each request.
  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
        setAll(cookiesToSet) {
          cookiesToSet.forEach(({ name, value }) =>
            request.cookies.set(name, value),
          );
          supabaseResponse = NextResponse.next({
            request,
          });
          cookiesToSet.forEach(({ name, value, options }) =>
            supabaseResponse.cookies.set(name, value, options),
          );
        },
      },
    },
  );

  // Do not run code between createServerClient and
  // supabase.auth.getClaims(). A simple mistake could make it very hard to debug
  // issues with users being randomly logged out.

  // IMPORTANT: If you remove getClaims() and you use server-side rendering
  // with the Supabase client, your users may be randomly logged out.
  const { data } = await supabase.auth.getClaims();
  const userId = data?.claims?.sub;
  const { pathname, search } = request.nextUrl;

  // 1) 로그인 안 한 사람: 로그인 없이 볼 수 있는 곳이 아니면 로그인 화면으로 보낸다.
  //    로그인을 마치면 원래 가려던 주소(next)로 돌아온다
  if (!userId) {
    if (isPublicPath(pathname)) return supabaseResponse;
    const next = pathname === "/" ? "" : `?next=${encodeURIComponent(pathname + search)}`;
    return redirectKeepingCookies(request, supabaseResponse, `/login${next}`);
  }

  // 2) 로그인한 사람: 로그인을 마무리하는 주소와 약관 보기는 그대로 보여준다
  if (pathname.startsWith("/auth") || pathname.startsWith("/terms")) {
    return supabaseResponse;
  }

  // 3) 가입을 어디까지 했는지 보고 알맞은 화면으로 보낸다
  const step = await getOnboardingStep(supabase, userId);
  if (step === "unknown") {
    // DB를 읽지 못했을 때(표를 아직 안 만든 경우 등)는 막지 않고 그대로 보여준다
    return supabaseResponse;
  }
  if (step !== "done") {
    // 약관까지 마친 사람은 1단계로 돌아가 다시 볼 수 있다
    const allowed = step === "terms" ? [ONBOARDING_TERMS] : [ONBOARDING_TERMS, ONBOARDING_PROFILE];
    if (allowed.includes(pathname)) return supabaseResponse;
    const target = step === "terms" ? ONBOARDING_TERMS : ONBOARDING_PROFILE;
    // 가입을 마치면 원래 가려던 주소로 갈 수 있게 next를 넘긴다
    const original = request.nextUrl.searchParams.get("next") ?? pathname + search;
    const keepNext = isAppPath(original) ? `?next=${encodeURIComponent(original)}` : "";
    return redirectKeepingCookies(request, supabaseResponse, `${target}${keepNext}`);
  }

  // 4) 가입을 마친 사람: 로그인·가입 1~2단계 화면 대신 홈(또는 원래 가려던 주소)으로 보낸다
  //    3단계 '준비 완료' 화면은 가입 직후에 봐야 하므로 그대로 둔다
  if (pathname === "/login" || pathname === "/onboarding" || pathname === ONBOARDING_TERMS || pathname === ONBOARDING_PROFILE) {
    const next = safeNextPath(request.nextUrl.searchParams.get("next"));
    return redirectKeepingCookies(request, supabaseResponse, next);
  }

  // IMPORTANT: You *must* return the supabaseResponse object as it is.
  // If you're creating a new response object with NextResponse.next() make sure to:
  // 1. Pass the request in it, like so:
  //    const myNewResponse = NextResponse.next({ request })
  // 2. Copy over the cookies, like so:
  //    myNewResponse.cookies.setAll(supabaseResponse.cookies.getAll())
  // 3. Change the myNewResponse object to fit your needs, but avoid changing
  //    the cookies!
  // 4. Finally:
  //    return myNewResponse
  // If this is not done, you may be causing the browser and server to go out
  // of sync and terminate the user's session prematurely!

  return supabaseResponse;
}

const ONBOARDING_TERMS = "/onboarding/terms";
const ONBOARDING_PROFILE = "/onboarding/profile";

// 로그인하지 않아도 볼 수 있는 주소 (초대 링크 /invite는 CLAUDE.md 규칙에 따라 로그인 전에도 보인다)
const PUBLIC_PATHS = ["/login", "/auth", "/terms", "/invite"];

function isPublicPath(pathname: string) {
  return PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

// 가입을 마친 뒤 돌아갈 만한 주소인지 (홈, 로그인·가입 화면, 이상한 주소는 제외)
function isAppPath(path: string) {
  const safe = safeNextPath(path);
  return safe !== "/" && !/^\/(login|onboarding|auth)(\/|\?|$)/.test(safe);
}

// 가입 단계: terms(약관 동의 전) → profile(이름 정하기 전) → done(가입 완료)
async function getOnboardingStep(
  supabase: SupabaseClient,
  userId: string,
): Promise<"terms" | "profile" | "done" | "unknown"> {
  const { data: profile, error } = await supabase.from("profiles").select("id").eq("id", userId).maybeSingle();
  if (error) {
    console.error("가입 상태 확인 실패(profiles)", error.message);
    return "unknown";
  }
  if (profile) return "done";

  const { data: agreements, error: agreementsError } = await supabase
    .from("user_agreements")
    .select("agreement")
    .eq("user_id", userId)
    .eq("agreed", true)
    .in("agreement", REQUIRED_AGREEMENT_KEYS);
  if (agreementsError) {
    console.error("가입 상태 확인 실패(user_agreements)", agreementsError.message);
    return "unknown";
  }
  const agreed = new Set(agreements.map((a) => a.agreement));
  return REQUIRED_AGREEMENT_KEYS.every((key) => agreed.has(key)) ? "profile" : "terms";
}

// 다른 주소로 보내면서, Supabase가 방금 갱신한 로그인 쿠키도 함께 넘긴다 (아래 IMPORTANT 설명 참고)
function redirectKeepingCookies(request: NextRequest, response: NextResponse, pathWithQuery: string) {
  const url = new URL(pathWithQuery, request.nextUrl.origin);
  const redirect = NextResponse.redirect(url);
  response.cookies.getAll().forEach((cookie) => redirect.cookies.set(cookie));
  return redirect;
}
