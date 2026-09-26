"use client";

// S10 마이로그의 여정 목록: 필터(전체·진행 중·예정·지난 여정)와 여정 카드
// - 여정은 이 기기(브라우저)에 저장되어 있어서, 화면을 연 뒤에 읽는다
// - 기간이 끝난 여정도 지우지 않고 여기에 '지난 여정'으로 남는다 (홈은 기본 홈으로 돌아간다)
// - 카드: 대표 사진, 여정 제목, 기간·인원, 진행 상태. 누르면 S11 여정 상세(/journeys/여정번호)로 간다
// - 테두리 색: 진행 중=보라(brand), 예정=연보라(brand-line), 지난 여정=앰버(reward)
import Link from "next/link";
import { useEffect, useState } from "react";
import { ChevronRight, ImageIcon } from "lucide-react";
import { formatRangeShort, todayKey } from "@/lib/dates";
import { getCurrentAccount } from "@/lib/local-auth";
import { journeyStatus, listMyJourneys, type Journey, type JourneyStatus } from "@/lib/local-journeys";
import { cn } from "@/lib/utils";

type Filter = "all" | JourneyStatus;
type Item = { journey: Journey; status: JourneyStatus };

// 필터 버튼. 배열 순서대로 왼쪽부터 놓인다
const FILTERS: { value: Filter; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "ongoing", label: "진행 중" },
  { value: "upcoming", label: "예정" },
  { value: "past", label: "지난 여정" },
];

const STATUS: Record<JourneyStatus, { label: string; border: string; empty: string; order: number }> = {
  ongoing: { label: "진행 중", border: "border-brand", empty: "진행 중인 여정이 없어요.", order: 0 },
  upcoming: { label: "예정", border: "border-brand-line", empty: "예정된 여정이 없어요.", order: 1 },
  past: { label: "지난 여정", border: "border-reward", empty: "지난 여정이 없어요.", order: 2 },
};

// 진행 중 → 예정 → 지난 여정 순서.
// 같은 상태 안에서는 진행 중은 최근에 시작한 것, 예정은 곧 시작하는 것, 지난 여정은 최근에 끝난 것부터
function sortItems(items: Item[]) {
  return [...items].sort((a, b) => {
    if (a.status !== b.status) return STATUS[a.status].order - STATUS[b.status].order;
    if (a.status === "ongoing") return b.journey.startDate.localeCompare(a.journey.startDate);
    if (a.status === "upcoming") return a.journey.startDate.localeCompare(b.journey.startDate);
    return b.journey.endDate.localeCompare(a.journey.endDate);
  });
}

export function JourneyList() {
  const [items, setItems] = useState<Item[] | null>(null); // null: 아직 읽는 중
  const [filter, setFilter] = useState<Filter>("all");

  // 다른 화면에서 돌아올 때도 다시 읽는다 (그사이 날짜가 바뀌었거나 대표 화면을 새로 골랐을 수 있다)
  useEffect(() => {
    const account = getCurrentAccount();
    const today = todayKey();
    const mine = account ? listMyJourneys(account.id) : [];
    setItems(sortItems(mine.map((journey) => ({ journey, status: journeyStatus(journey, today) }))));
  }, []);

  const shown = items?.filter((item) => filter === "all" || item.status === filter) ?? [];

  return (
    <section className="flex flex-1 flex-col gap-4 break-keep px-4 pb-6 pt-2">
      <div role="group" aria-label="여정 골라 보기" className="grid grid-cols-4 gap-1 rounded-2xl border border-brand-line bg-brand-soft p-1">
        {FILTERS.map(({ value, label }) => (
          <button
            key={value}
            type="button"
            onClick={() => setFilter(value)}
            aria-pressed={filter === value}
            className={cn(
              "h-10 rounded-xl text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
              filter === value ? "bg-brand text-brand-foreground" : "text-muted-foreground",
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {/* 읽기 전에는 목록을 그리지 않는다 ('여정이 없어요'가 잠깐 보였다가 바뀌지 않게) */}
      {items && items.length === 0 && (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 py-12 text-center">
          <p className="text-lg font-semibold">아직 여정이 없어요</p>
          <p className="text-sm text-muted-foreground">내가 만들거나 초대받은 여정이 여기에 모여요.</p>
          <Link
            href="/journeys/new"
            className="mt-3 flex h-11 items-center rounded-xl bg-brand px-5 text-base font-semibold text-brand-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:bg-brand-pressed"
          >
            새 여정 만들기
          </Link>
        </div>
      )}
      {items && items.length > 0 && filter !== "all" && shown.length === 0 && (
        <p className="py-12 text-center text-base text-muted-foreground">{STATUS[filter].empty}</p>
      )}
      {shown.length > 0 && (
        <ul className="flex flex-col gap-4">
          {shown.map((item) => (
            <li key={item.journey.id}>
              <JourneyCard {...item} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function JourneyCard({ journey, status }: Item) {
  const { label, border } = STATUS[status];
  return (
    <Link
      href={`/journeys/${journey.id}`}
      className={cn(
        "flex items-center gap-4 rounded-3xl border-2 bg-brand-soft p-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:opacity-80",
        border,
      )}
    >
      <div className="aspect-[3/4] w-24 shrink-0 overflow-hidden rounded-2xl bg-card">
        {journey.coverImage ? (
          // eslint-disable-next-line @next/next/no-img-element -- 이 기기에 저장한 이미지 글자라 next/image를 쓸 수 없다
          <img src={journey.coverImage} alt={`${journey.name} 대표 사진`} className="size-full object-cover" />
        ) : (
          <div className="flex size-full items-center justify-center text-muted-foreground">
            <ImageIcon className="size-7" aria-hidden="true" />
          </div>
        )}
      </div>
      <div className="min-w-0 flex-1">
        {/* 이름이 길면 두 줄까지 보여준다. 띄어쓰기 없는 긴 이름도 칸 밖으로 나가지 않게 break-words */}
        <h2 className="line-clamp-2 break-words text-lg font-bold">{journey.name}</h2>
        <p className="mt-1 text-base text-muted-foreground">
          {formatRangeShort(journey.startDate, journey.endDate)} · {journey.members.length}명
        </p>
        <p className="mt-3 flex items-center gap-0.5 text-base font-semibold text-brand-strong">
          {label}
          <ChevronRight className="size-4" aria-hidden="true" />
        </p>
      </div>
    </Link>
  );
}
