// 로그인 (스토리보드 번호 없음: 가입 흐름 화면. 처음 여는 사람이 보는 첫 화면)
import type { Metadata } from "next";
import { Globe } from "lucide-react";
import { LoginActions } from "@/components/login/login-actions";

export const metadata: Metadata = {
  title: "로그인",
};

export default function LoginPage() {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[430px] flex-col break-keep px-4 pb-[calc(env(safe-area-inset-bottom)+1.5rem)] pt-[calc(env(safe-area-inset-top)+1.5rem)]">
      <h1 className="sr-only">LOG EARTH 로그인</h1>
      <p className="flex items-center justify-center gap-2 text-sm font-bold tracking-widest text-brand-strong">
        <Globe className="size-4" aria-hidden="true" />
        LOG EARTH
      </p>
      {/* 가운데는 비워 두고 버튼은 엄지가 닿는 아래쪽에 둔다 */}
      <div className="flex-1" />
      <LoginActions />
    </main>
  );
}
