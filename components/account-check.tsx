"use client";

// 하단 탭 화면(홈/탐색/마이로그)에 들어올 때 로그인 상태를 확인한다.
// - 계정이 있으면: 로그인 유지 기간을 다시 센다
// - 로그인 쿠키는 남아 있는데 이 기기에 계정이 없으면(브라우저 저장 데이터가 지워진 경우 등):
//   로그아웃하고 로그인 화면으로 보낸다
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { extendSession, getCurrentAccount, signOut } from "@/lib/local-auth";

export function AccountCheck() {
  const router = useRouter();

  useEffect(() => {
    const account = getCurrentAccount();
    if (account) {
      extendSession(account);
      return;
    }
    signOut();
    router.replace("/login");
  }, [router]);

  return null;
}
