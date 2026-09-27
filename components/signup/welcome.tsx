"use client";

// 가입 4단계: 준비 완료. "시작하기"를 누르면 홈(또는 원래 가려던 주소)으로 간다.
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { InitialAvatar } from "@/components/signup/initial-avatar";
import { NextButton } from "@/components/signup/next-button";
import { StepHeader } from "@/components/signup/step-header";
import { getCurrentAccount } from "@/lib/local-auth";
import { readNextParam } from "@/lib/next-param";

export function Welcome() {
  const router = useRouter();
  const [nickname, setNickname] = useState<string | null>(null);

  // 방금 만든 계정의 닉네임을 불러온다. 로그인 상태가 아니면 로그인 화면으로 보낸다
  useEffect(() => {
    const account = getCurrentAccount();
    if (!account) {
      router.replace("/login");
      return;
    }
    setNickname(account.nickname);
  }, [router]);

  return (
    <>
      <StepHeader step={4} />
      <div className="flex flex-1 flex-col items-center justify-center text-center">
        <InitialAvatar nickname={nickname ?? ""} />
        <h1 className="mt-6 text-2xl font-bold leading-snug">
          {nickname && (
            <>
              {nickname}님,
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
