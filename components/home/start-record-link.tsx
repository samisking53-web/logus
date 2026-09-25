"use client";

// 홈 ① 기록 시작하기: 오늘 진행 중인 여정이 있으면 그 여정의 카메라로, 없으면 새 여정 만들기로 간다
// (진행 중인 여정이 여러 개면 가장 최근에 만든 여정. 고르는 화면은 나중에 만든다)
import Link from "next/link";
import { useEffect, useState } from "react";
import { todayKey } from "@/lib/dates";
import { getCurrentAccount } from "@/lib/local-auth";
import { findOngoingJourney } from "@/lib/local-journeys";

export function StartRecordLink({ className, children }: { className?: string; children: React.ReactNode }) {
  const [href, setHref] = useState("/journeys/new");

  useEffect(() => {
    const account = getCurrentAccount();
    const ongoing = account ? findOngoingJourney(account.id, todayKey()) : null;
    setHref(ongoing ? `/journeys/${ongoing.id}/camera` : "/journeys/new");
  }, []);

  return (
    <Link href={href} className={className}>
      {children}
    </Link>
  );
}
