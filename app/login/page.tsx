// 로그인 (스토리보드 v4 2쪽 "앱 시작 화면" 로그인 예시를 구글 로그인으로 단순화)
import type { Metadata } from "next";
import { Suspense } from "react";
import LoginScreen from "@/components/auth/LoginScreen";

export const metadata: Metadata = { title: "로그인 | LOG US" };

export default function LoginPage() {
  return (
    <Suspense>
      <LoginScreen />
    </Suspense>
  );
}
