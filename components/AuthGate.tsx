"use client";

// 로그인과 회원가입을 마친 사람만 안쪽 화면을 보게 한다.
// - 로그인 전 → /login, 구글 로그인만 하고 프로필이 없으면 → /signup 으로 보낸다.
// - 원래 가려던 주소는 ?next= 로 넘겨서, 끝나면 그 화면으로 돌아온다(초대 링크 등).
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";

export default function AuthGate({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const next = encodeURIComponent(pathname);
    if (state.status === "signedOut" || state.status === "unconfigured") {
      router.replace(`/login?next=${next}`);
    } else if (state.status === "needsProfile") {
      router.replace(`/signup?next=${next}`);
    }
  }, [state.status, pathname, router]);

  if (state.status === "ready") return <>{children}</>;
  if (state.status === "error") {
    return (
      <p role="alert" className="mt-10 rounded-2xl border-2 border-error bg-surface p-5 text-text">
        {state.message}
      </p>
    );
  }
  return (
    <p role="status" className="py-20 text-center text-text-secondary">
      불러오는 중이에요…
    </p>
  );
}
