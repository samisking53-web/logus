// 카카오·구글 로그인을 마치고 돌아오는 주소.
// 받은 code를 로그인 세션(쿠키)으로 바꾼 뒤 next 주소로 보낸다.
// 가입을 아직 안 끝낸 사람은 proxy.ts가 가입 단계로 다시 보낸다.
import { NextResponse } from "next/server";
import { createClient } from "@/lib/supabase/server";
import { safeNextPath } from "@/lib/safe-next";

export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url);
  const code = searchParams.get("code");
  const next = safeNextPath(searchParams.get("next"));

  if (code) {
    const supabase = await createClient();
    const { error } = await supabase.auth.exchangeCodeForSession(code);
    if (!error) {
      // Vercel에서는 사용자가 연 원래 주소가 x-forwarded-host·x-forwarded-proto에 들어 있다
      const forwardedHost = request.headers.get("x-forwarded-host");
      const forwardedProto = request.headers.get("x-forwarded-proto") ?? "https";
      const base = forwardedHost ? `${forwardedProto}://${forwardedHost}` : origin;
      return NextResponse.redirect(`${base}${next}`);
    }
  }

  // 로그인을 취소했거나 실패하면 로그인 화면에 안내 문구를 띄운다
  return NextResponse.redirect(`${origin}/login?error=1`);
}
