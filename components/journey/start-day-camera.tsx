"use client";

// 미리 등록한 여정의 시작일에 앱을 처음 열면 그 여정의 카메라를 바로 연다 (시작일에 한 번만).
// 하단 탭 화면(홈/탐색/마이로그)에 들어올 때 확인한다
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { todayKey } from "@/lib/dates";
import { getCurrentAccount } from "@/lib/local-auth";
import { takeStartDayJourney } from "@/lib/local-journeys";

export function StartDayCamera() {
  const router = useRouter();

  useEffect(() => {
    const account = getCurrentAccount();
    if (!account) return;
    const journey = takeStartDayJourney(account.id, todayKey());
    if (journey) router.push(`/journeys/${journey.id}/camera`);
  }, [router]);

  return null;
}
