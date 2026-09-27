"use client";

// 홈 화면 두 가지 중 하나를 고른다.
// - 오늘 진행 중인 여정이 있으면: S01-A 여행 기간 중 홈 (여러 개면 가장 최근에 만든 여정)
// - 없으면: S01 기본 홈 (children으로 받은 메뉴 카드)
// 여정은 이 기기(브라우저)에 저장되어 있어서, 화면을 연 뒤에 확인한다
import { useEffect, useState } from "react";
import { OngoingHome } from "@/components/home/ongoing-home";
import { todayKey } from "@/lib/dates";
import { getCurrentAccount } from "@/lib/local-auth";
import { findOngoingJourney, type Journey } from "@/lib/local-journeys";

export function HomeSwitch({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<{ checked: boolean; ongoing: Journey | null }>({ checked: false, ongoing: null });

  // 다른 화면(카메라 등)에서 돌아올 때도 다시 확인해서, 새로 고른 대표 화면이 바로 보이게 한다
  useEffect(() => {
    const account = getCurrentAccount();
    setState({ checked: true, ongoing: account ? findOngoingJourney(account.id, todayKey()) : null });
  }, []);

  // 확인하기 전에는 아무것도 그리지 않는다 (기본 홈이 잠깐 보였다가 바뀌지 않게)
  if (!state.checked) return null;
  return state.ongoing ? <OngoingHome journey={state.ongoing} /> : children;
}
