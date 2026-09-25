// 회원가입 1/4 약관 동의 (스토리보드 번호 없음: 가입 흐름 화면)
import type { Metadata } from "next";
import { TermsForm } from "@/components/signup/terms-form";

export const metadata: Metadata = {
  title: "약관 동의",
};

export default function SignupTermsPage() {
  return <TermsForm />;
}
