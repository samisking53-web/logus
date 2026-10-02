"use client";

// S01 기본 홈 / S01-A 여행 기간 중 홈
// 진행 중인 여정이 있으면 S01-A, 없으면 S01을 보여 준다.
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useHomeData, type HomePreview } from "@/lib/home/useHomeData";
import ActiveJourneyCard from "./ActiveJourneyCard";
import ProfileCard from "./ProfileCard";

function parsePreview(value: string | null): HomePreview | null {
  return value === "idle" || value === "active" ? value : null;
}

export default function HomeScreen() {
  const preview = parsePreview(useSearchParams().get("preview"));
  const data = useHomeData(preview);

  return (
    <>
      <p className="mb-5 text-center text-2xl font-bold tracking-wide text-primary-strong">LOG EARTH</p>

      {data.status === "loading" ? (
        <p className="py-20 text-center text-text-secondary" role="status">
          불러오는 중이에요…
        </p>
      ) : data.activeJourney ? (
        // S01-A 여행 기간 중 홈
        <div className="flex flex-col gap-5">
          <ProfileCard profile={data.profile} variant="compact" />
          <ActiveJourneyCard journey={data.activeJourney} myNickname={data.profile.nickname} />
        </div>
      ) : (
        // S01 기본 홈
        <div className="flex flex-col gap-6">
          <h1 className="text-3xl font-bold text-text">어떤 순간을 남길까요?</h1>
          <ProfileCard profile={data.profile} variant="large" />
          <p className="px-2 text-xl font-bold text-primary-strong">현재 진행중인 여정이 없어요!</p>
          {/* S02 새 여정 만들기로 이동 (화면은 다음 작업에서 만든다) */}
          <Link
            href="/journeys/new"
            className="flex flex-col items-center gap-2 rounded-3xl border border-border bg-surface px-6 py-8"
          >
            <span className="text-2xl font-bold text-text">기록 시작하기</span>
            <span className="text-text-secondary">새로운 여정 기록하기</span>
          </Link>
        </div>
      )}
    </>
  );
}
