// 아이디로 로그인 (스토리보드 번호 없음: 이미 가입한 사람이 쓰는 화면)
import type { Metadata } from "next";
import { IdLoginForm } from "@/components/login/id-login-form";

export const metadata: Metadata = {
  title: "아이디로 로그인",
};

export default function IdLoginPage() {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[430px] flex-col break-keep px-5 pb-[calc(env(safe-area-inset-bottom)+1rem)] pt-[env(safe-area-inset-top)]">
      <IdLoginForm />
    </main>
  );
}
