"use client";

// 로그아웃 버튼 (설정 화면이 생기기 전까지 마이로그에 임시로 둔다)
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";

export default function SignOutButton() {
  const { state, signOut } = useAuth();
  const router = useRouter();
  if (state.status !== "ready" && state.status !== "needsProfile") return null;

  return (
    <button
      type="button"
      onClick={async () => {
        await signOut();
        router.replace("/login");
      }}
      className="mt-6 w-full rounded-full border border-border bg-surface py-3 font-bold text-primary-strong"
    >
      로그아웃
    </button>
  );
}
