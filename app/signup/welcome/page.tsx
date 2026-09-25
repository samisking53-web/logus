// 회원가입 4/4 준비 완료 (스토리보드 번호 없음: 가입 흐름 화면)
import type { Metadata } from "next";
import { Welcome } from "@/components/signup/welcome";

export const metadata: Metadata = {
  title: "준비 완료",
};

export default function SignupWelcomePage() {
  return <Welcome />;
}
