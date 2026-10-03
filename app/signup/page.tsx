// P01 회원가입 (구글 계정으로 가입: 프로필 사진·닉네임만 정한다)
import type { Metadata } from "next";
import { Suspense } from "react";
import SignupScreen from "@/components/auth/SignupScreen";

export const metadata: Metadata = { title: "회원가입 | LOG US" };

export default function SignupPage() {
  return (
    <Suspense>
      <SignupScreen />
    </Suspense>
  );
}
