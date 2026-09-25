"use client";

// 로그인 화면 아래쪽 버튼들.
// 지금은 실습 단계라 Google·카카오 버튼도 실제 구글·카카오 로그인 대신 앱 전용 회원가입으로 간다.
// (나중에 서버를 붙이면 두 버튼만 실제 구글·카카오 로그인으로 바꾼다: docs/login-setup.md)
import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import { UserRound } from "lucide-react";
import { readNextParam, withNext } from "@/lib/next-param";

const buttonBase = "flex h-14 w-full items-center justify-center gap-3 rounded-2xl text-base font-semibold";

export function LoginActions() {
  // 로그인 전에 가려던 주소(next)를 가입·로그인 화면으로 이어서 넘긴다
  const [next, setNext] = useState("/");
  useEffect(() => {
    setNext(readNextParam());
  }, []);
  const signupHref = withNext("/signup/terms", next);

  return (
    <div className="flex flex-col gap-3">
      <Link href={signupHref} className={`${buttonBase} border border-google-line bg-google text-google-foreground`}>
        <Image src="/brand/google-g.svg" alt="" width={20} height={20} />
        Google 계정으로 계속하기
      </Link>
      {/* 카카오 로그인 버튼 가이드: 노란 바탕, 검은 심볼, 85% 불투명도의 검은 글자 */}
      <Link href={signupHref} className={`${buttonBase} bg-kakao text-kakao-foreground/85`}>
        <Image src="/brand/kakao-symbol.svg" alt="" width={20} height={20} />
        카카오로 계속하기
      </Link>
      <Link href={signupHref} className={`${buttonBase} bg-brand text-brand-foreground active:bg-brand-pressed`}>
        <UserRound className="size-5" aria-hidden="true" />
        아이디로 시작하기
      </Link>

      <p className="mt-1 text-center text-xs leading-relaxed text-muted-foreground">
        실습 버전이라 어느 버튼을 눌러도 앱 전용 아이디로 가입해요.
        <br />
        계속하면 이용약관과 개인정보 처리방침에 동의하는 절차로 넘어가요.
      </p>
      <p className="py-2 text-center text-sm text-muted-foreground">
        이미 계정이 있어요?{" "}
        <Link href={withNext("/login/id", next)} className="font-semibold text-brand-strong underline underline-offset-2">
          로그인
        </Link>
      </p>
    </div>
  );
}
