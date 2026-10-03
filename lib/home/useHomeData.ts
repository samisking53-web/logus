"use client";

// 홈(S01·S01-A)에 필요한 데이터
// - 프로필(이름·사진·코인)은 AuthProvider가 이미 읽어 둔 것을 쓴다.
// - 진행 중인 여정: 내가 구성원이고 startDate ≤ 오늘 ≤ endDate 인 여정 1개
import { useEffect, useState } from "react";
import type { Profile } from "@/lib/auth/AuthProvider";

export type HomeProfile = Profile;

export type ActiveJourney = {
  id: string;
  name: string;
  city: string;
  startDate: string; // "YYYY-MM-DD"
  endDate: string;
  memberCount: number;
  // 여정 대표 사진 주소. 지금은 항상 null(자리 표시 상자)이고, 기록 기능을 만든 뒤 실제 대표 사진을 넣는다.
  coverUrl: string | null;
};

/** undefined = 불러오는 중, null = 진행 중인 여정 없음 */
export type ActiveJourneyResult = ActiveJourney | null | undefined;

/** 화면 확인용 미리보기. 주소 끝에 ?preview=idle 또는 ?preview=active 를 붙인다(로그인 없이 볼 수 있음). */
export type HomePreview = "idle" | "active";

export const SAMPLE_PROFILE: HomeProfile = { nickname: "성연", photoURL: null, coins: 100 };
export const SAMPLE_JOURNEY: ActiveJourney = {
  id: "sample",
  name: "우리의 포르투",
  city: "포르투",
  startDate: "2026-09-24",
  endDate: "2026-09-26",
  memberCount: 4,
  coverUrl: null,
};

/** 이 기기의 오늘 날짜를 "YYYY-MM-DD"로 만든다. 여행 중인 사람의 폰 날짜가 곧 현지 날짜다. */
export function todayLocal(now = new Date()): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

/** 로그인한 사용자(uid)의 진행 중 여정을 찾는다. */
export function useActiveJourney(uid: string): ActiveJourneyResult {
  const [result, setResult] = useState<{ uid: string; journey: ActiveJourney | null } | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [{ db }, { collection, getDocs, limit, orderBy, query, where }] = await Promise.all([
        import("@/lib/firebase/client"),
        import("firebase/firestore"),
      ]);
      const today = todayLocal();
      try {
        // 시작일이 오늘 이전인 내 여정을 최근 시작한 순서로 몇 개만 가져와, 끝나지 않은 것을 고른다.
        // (색인: memberIds + startDate 내림차순, firestore.indexes.json)
        const snap = await getDocs(
          query(
            collection(db, "journeys"),
            where("memberIds", "array-contains", uid),
            where("startDate", "<=", today),
            orderBy("startDate", "desc"),
            limit(5),
          ),
        );
        const active = snap.docs.find((d) => (d.get("endDate") as string) >= today);
        const journey: ActiveJourney | null = active
          ? {
              id: active.id,
              name: active.get("name") as string,
              city: active.get("city") as string,
              startDate: active.get("startDate") as string,
              endDate: active.get("endDate") as string,
              memberCount: active.get("memberCount") as number,
              coverUrl: null,
            }
          : null;
        if (!cancelled) setResult({ uid, journey });
      } catch (error) {
        // 색인을 아직 배포하지 않았거나 네트워크 문제: 진행 중 여정이 없는 것으로 보여 준다.
        console.error("진행 중인 여정을 불러오지 못했어요", error);
        if (!cancelled) setResult({ uid, journey: null });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [uid]);

  return result && result.uid === uid ? result.journey : undefined;
}
