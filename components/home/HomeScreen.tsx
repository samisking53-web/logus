"use client";

// S01 기본 홈 / S01-A 여행 기간 중 홈
// 진행 중인 여정이 있으면 S01-A, 없으면 S01을 보여 준다.
// 로그인·회원가입을 마친 사람만 볼 수 있다(AuthGate). ?preview= 일 때는 예시 데이터로 로그인 없이 보여 준다.
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import AuthGate from "@/components/AuthGate";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  SAMPLE_JOURNEY,
  SAMPLE_PROFILE,
  useActiveJourney,
  type ActiveJourneyResult,
  type HomePreview,
  type HomeProfile,
} from "@/lib/home/useHomeData";
import ActiveJourneyCard from "./ActiveJourneyCard";
import ProfileCard from "./ProfileCard";

function parsePreview(value: string | null): HomePreview | null {
  return value === "idle" || value === "active" ? value : null;
}

export default function HomeScreen() {
  const preview = parsePreview(useSearchParams().get("preview"));

  if (preview) {
    return <HomeView profile={SAMPLE_PROFILE} journey={preview === "active" ? SAMPLE_JOURNEY : null} />;
  }
  return (
    <AuthGate>
      <SignedInHome />
    </AuthGate>
  );
}

function SignedInHome() {
  const { state } = useAuth();
  // AuthGate 안이라 항상 ready 상태다.
  if (state.status !== "ready") return null;
  return <SignedInHomeContent uid={state.user.uid} profile={state.profile} />;
}

function SignedInHomeContent({ uid, profile }: { uid: string; profile: HomeProfile }) {
  const journey = useActiveJourney(uid);
  return <HomeView profile={profile} journey={journey} />;
}

function HomeView({ profile, journey }: { profile: HomeProfile; journey: ActiveJourneyResult }) {
  return (
    <>
      <p className="mb-5 text-center text-2xl font-bold tracking-wide text-primary-strong">LOG EARTH</p>

      {journey === undefined ? (
        <p className="py-20 text-center text-text-secondary" role="status">
          불러오는 중이에요…
        </p>
      ) : journey ? (
        // S01-A 여행 기간 중 홈
        <div className="flex flex-col gap-5">
          <ProfileCard profile={profile} variant="compact" />
          <ActiveJourneyCard journey={journey} myNickname={profile.nickname} />
        </div>
      ) : (
        // S01 기본 홈
        <div className="flex flex-col gap-6">
          <h1 className="text-3xl font-bold text-text">어떤 순간을 남길까요?</h1>
          <ProfileCard profile={profile} variant="large" />
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
