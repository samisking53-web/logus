"use client";

// 가입 3단계: 준비 완료. "시작하기"를 누르면 홈(또는 원래 가려던 주소)으로 간다.
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { StepHeader } from "@/components/onboarding/step-header";
import { NextButton } from "@/components/onboarding/next-button";
import { AvatarPreview } from "@/components/onboarding/avatar-preview";
import { createClient } from "@/lib/supabase/client";
import { readNextParam } from "@/lib/onboarding";

export function Welcome() {
  const router = useRouter();
  const [profile, setProfile] = useState<{ nickname: string; avatar_url: string | null } | null>(null);

  // 2단계에서 저장한 닉네임과 사진을 불러온다
  useEffect(() => {
    const supabase = createClient();
    supabase.auth.getUser().then(async ({ data: { user } }) => {
      if (!user) return;
      const { data } = await supabase
        .from("profiles")
        .select("nickname, avatar_url")
        .eq("id", user.id)
        .maybeSingle();
      setProfile(data);
    });
  }, []);

  return (
    <>
      <StepHeader step={3} />
      <div className="flex flex-1 flex-col items-center justify-center text-center">
        <AvatarPreview nickname={profile?.nickname ?? ""} avatarUrl={profile?.avatar_url ?? null} />
        <h1 className="mt-6 text-2xl font-bold leading-snug">
          {profile && (
            <>
              {profile.nickname}님,
              <br />
            </>
          )}
          준비됐어요!
        </h1>
        <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
          여정을 만들고 친구·연인·가족을 초대해
          <br />
          함께 기록해 보세요.
        </p>
      </div>
      <NextButton onClick={() => router.replace(readNextParam())}>시작하기</NextButton>
    </>
  );
}
