// 회원가입 3/4 이름 정하기 (스토리보드 번호 없음: 가입 흐름 화면)
import type { Metadata } from "next";
import { ProfileForm } from "@/components/signup/profile-form";

export const metadata: Metadata = {
  title: "이름 정하기",
};

export default function SignupProfilePage() {
  return <ProfileForm />;
}
