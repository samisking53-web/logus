// 문지기: 모든 화면 요청이 도착하기 전에 여기를 거쳐, 로그인 상태에 맞는 화면으로 보낸다.
// 지금은 실습 단계라 로그인 상태를 쿠키(lib/session-cookie.ts)가 있는지로만 판단한다.
// 계정 자체는 브라우저에 저장되어 있다 (lib/local-auth.ts)
import { NextResponse, type NextRequest } from "next/server";
import { safeNextPath } from "@/lib/next-param";
import { SESSION_COOKIE } from "@/lib/session-cookie";

// 로그인하지 않아도 볼 수 있는 주소 (초대 링크 /invite는 CLAUDE.md 규칙에 따라 로그인 전에도 보인다)
const PUBLIC_PATHS = ["/login", "/signup/terms", "/signup/account", "/signup/profile", "/terms", "/invite"];

// 로그인한 사람이 오면 홈(또는 원래 가려던 주소)으로 보내는 주소.
// 가입 마지막 '준비 완료'(/signup/welcome) 화면은 가입 직후에 봐야 하므로 넣지 않는다
const SIGNED_OUT_ONLY_PATHS = ["/login", "/signup/terms", "/signup/account", "/signup/profile"];

function matches(pathname: string, paths: string[]) {
  return paths.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

export function proxy(request: NextRequest) {
  const { pathname, search, searchParams } = request.nextUrl;
  const signedIn = Boolean(request.cookies.get(SESSION_COOKIE)?.value);

  // 1) 로그인 안 한 사람: 로그인 없이 볼 수 있는 곳이 아니면 로그인 화면으로.
  //    로그인·가입을 마치면 원래 가려던 주소(next)로 돌아온다
  if (!signedIn) {
    if (matches(pathname, PUBLIC_PATHS)) return NextResponse.next();
    const next = pathname === "/" ? "" : `?next=${encodeURIComponent(pathname + search)}`;
    return NextResponse.redirect(new URL(`/login${next}`, request.url));
  }

  // 2) 로그인한 사람: 로그인·가입 화면 대신 홈(또는 원래 가려던 주소)으로
  if (matches(pathname, SIGNED_OUT_ONLY_PATHS)) {
    return NextResponse.redirect(new URL(safeNextPath(searchParams.get("next")), request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * 아래는 문지기를 거치지 않는다:
     * - _next/static, _next/image: Next.js가 만든 파일
     * - favicon.ico, manifest.webmanifest: 아이콘과 PWA 설정 (로그인 전에도 읽혀야 한다)
     * - 이미지 파일 (.svg .png .jpg .jpeg .gif .webp)
     */
    "/((?!_next/static|_next/image|favicon.ico|manifest.webmanifest|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)",
  ],
};
