// S01-A 진행 중인 여정: 여정 이름, 대표 사진, 기간·인원, '지금 기록하기' 버튼
import Link from "next/link";
import type { ActiveJourney } from "@/lib/home/useHomeData";

type Props = {
  journey: ActiveJourney;
  myNickname: string;
};

/** "2026-09-24" → "9.24" */
function shortDate(date: string): string {
  const [, m, d] = date.split("-");
  return `${Number(m)}.${Number(d)}`;
}

export default function ActiveJourneyCard({ journey, myNickname }: Props) {
  const others = journey.memberCount - 1;
  const members = others > 0 ? `${myNickname} 외 ${others}명` : myNickname;

  return (
    <section aria-label="진행 중인 여정" className="flex flex-col gap-4">
      <p className="rounded-full bg-surface px-5 py-3 text-lg font-bold text-primary-strong">
        진행 중 · {journey.name}
      </p>

      {/* 여정 대표 사진. 지금은 자리만 잡아 두고, 나중에 기록 중 대표로 고른 장면이 들어간다. */}
      <div className="aspect-[16/9] overflow-hidden rounded-2xl bg-surface">
        {journey.coverUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={journey.coverUrl} alt={`${journey.name} 대표 사진`} className="size-full object-cover" />
        ) : (
          <div className="flex size-full flex-col items-center justify-center gap-2 border-2 border-dashed border-border text-text-secondary">
            <svg viewBox="0 0 24 24" className="size-9" fill="none" stroke="currentColor" strokeWidth={1.6} aria-hidden="true">
              <rect x="3" y="5" width="18" height="14" rx="2" />
              <circle cx="9" cy="10" r="1.8" />
              <path d="m4 18 5-5 4 4 3-3 4 4" />
            </svg>
            <p className="text-sm">여정 대표 사진이 여기에 보여요</p>
          </div>
        )}
      </div>

      <p className="text-lg font-bold text-text">
        {shortDate(journey.startDate)} ~ {shortDate(journey.endDate)} · {members}
      </p>

      <div>
        {/* S04 앱 내 카메라로 이동 (화면은 다음 작업에서 만든다) */}
        <Link
          href={`/journeys/${journey.id}/camera`}
          className="block rounded-full bg-primary px-6 py-4 text-xl font-bold text-on-primary active:bg-primary-strong"
        >
          지금 기록하기
        </Link>
        <p className="mt-3 px-2 font-semibold text-primary-strong">누르면 {journey.city} 여정 카메라가 열려요</p>
      </div>
    </section>
  );
}
