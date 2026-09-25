// 회원가입 2/4 아이디·비밀번호 만들기 (스토리보드 번호 없음: 가입 흐름 화면)
import type { Metadata } from "next";
import { AccountForm } from "@/components/signup/account-form";

export const metadata: Metadata = {
  title: "아이디 만들기",
};

export default function SignupAccountPage() {
  return <AccountForm />;
}
