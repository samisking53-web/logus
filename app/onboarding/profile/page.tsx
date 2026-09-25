// 가입 2/3 이름 정하기 (스토리보드 번호 없음: 가입 흐름 화면)
import type { Metadata } from "next";
import { ProfileForm } from "@/components/onboarding/profile-form";

export const metadata: Metadata = {
  title: "이름 정하기",
};

export default function OnboardingProfilePage() {
  return <ProfileForm />;
}
