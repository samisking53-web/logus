"use client";

// 홈(S01·S01-A)에 필요한 데이터를 불러온다.
// - 로그인한 사용자의 프로필: users/{uid} (nickname, photoURL, coins)
// - 진행 중인 여정: 내가 구성원이고 startDate ≤ 오늘 ≤ endDate 인 여정 1개
import { useEffect, useState } from "react";

export type HomeProfile = {
  nickname: string;
  photoURL: string | null;
  coins: number;
};

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

export type HomeData =
  | { status: "loading" }
  | { status: "ready"; profile: HomeProfile; activeJourney: ActiveJourney | null };

/** 화면 확인용 미리보기. 주소 끝에 ?preview=idle 또는 ?preview=active 를 붙인다. */
export type HomePreview = "idle" | "active";

const GUEST_PROFILE: HomeProfile = { nickname: "게스트", photoURL: null, coins: 0 };

const SAMPLE_PROFILE: HomeProfile = { nickname: "성연", photoURL: null, coins: 100 };
const SAMPLE_JOURNEY: ActiveJourney = {
  id: "sample",
  name: "우리의 포르투",
  city: "포르투",
  startDate: "2026-09-24",
  endDate: "2026-09-26",
  memberCount: 4,
  coverUrl: null,
};

// Firebase 설정값(.env.local / Vercel 환경변수)이 없으면 Firebase를 부르지 않고 게스트로 보여 준다.
const firebaseConfigured = Boolean(process.env.NEXT_PUBLIC_FIREBASE_API_KEY);

/** 이 기기의 오늘 날짜를 "YYYY-MM-DD"로 만든다. 여행 중인 사람의 폰 날짜가 곧 현지 날짜다. */
export function todayLocal(now = new Date()): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function useHomeData(preview: HomePreview | null): HomeData {
  const [data, setData] = useState<HomeData>({ status: "loading" });
  const skipFirebase = preview !== null || !firebaseConfigured;

  useEffect(() => {
    if (skipFirebase) return;

    let cancelled = false;
    let unsubscribe: (() => void) | undefined;

    (async () => {
      // Firebase 코드는 필요할 때만 불러온다(첫 화면을 가볍게).
      const [{ auth, db }, { onAuthStateChanged }, firestore] = await Promise.all([
        import("@/lib/firebase/client"),
        import("firebase/auth"),
        import("firebase/firestore"),
      ]);
      const { collection, doc, getDoc, getDocs, limit, orderBy, query, where } = firestore;

      unsubscribe = onAuthStateChanged(auth, async (user) => {
        if (!user) {
          // 회원가입·로그인 화면은 나중에 이 화면 앞에 붙인다. 그때까지는 게스트로 보여 준다.
          if (!cancelled) setData({ status: "ready", profile: GUEST_PROFILE, activeJourney: null });
          return;
        }
        try {
          const today = todayLocal();
          const [userSnap, journeySnap] = await Promise.all([
            getDoc(doc(db, "users", user.uid)),
            // 시작일이 오늘 이전인 내 여정을 최근 시작한 순서로 몇 개만 가져와, 끝나지 않은 것을 고른다.
            // (색인: memberIds + startDate 내림차순, firestore.indexes.json)
            getDocs(
              query(
                collection(db, "journeys"),
                where("memberIds", "array-contains", user.uid),
                where("startDate", "<=", today),
                orderBy("startDate", "desc"),
                limit(5),
              ),
            ),
          ]);

          const u = userSnap.data();
          const profile: HomeProfile = {
            nickname: (u?.nickname as string | undefined) ?? user.displayName ?? "여행자",
            photoURL: (u?.photoURL as string | null | undefined) ?? user.photoURL ?? null,
            coins: (u?.coins as number | undefined) ?? 0,
          };

          const active = journeySnap.docs.find((d) => (d.get("endDate") as string) >= today);
          const activeJourney: ActiveJourney | null = active
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

          if (!cancelled) setData({ status: "ready", profile, activeJourney });
        } catch (error) {
          console.error("홈 데이터를 불러오지 못했어요", error);
          if (!cancelled) setData({ status: "ready", profile: GUEST_PROFILE, activeJourney: null });
        }
      });
    })();

    return () => {
      cancelled = true;
      unsubscribe?.();
    };
  }, [skipFirebase]);

  if (preview === "active") {
    return { status: "ready", profile: SAMPLE_PROFILE, activeJourney: SAMPLE_JOURNEY };
  }
  if (preview === "idle") {
    return { status: "ready", profile: SAMPLE_PROFILE, activeJourney: null };
  }
  if (!firebaseConfigured) {
    return { status: "ready", profile: GUEST_PROFILE, activeJourney: null };
  }
  return data;
}
