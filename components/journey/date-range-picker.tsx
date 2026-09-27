"use client";

// S03 여행 기간 선택: 달력에서 기간을 고르고 "기간 선택 완료"를 누르면 새 여정 만들기로 돌아간다.
// 고른 기간은 새 여정 만들기의 임시 저장 내용(lib/local-journeys.ts)에 넣는다.
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { JourneyHeader } from "@/components/journey/journey-header";
import { RangeCalendar } from "@/components/journey/range-calendar";
import { NextButton } from "@/components/signup/next-button";
import { formatKorean, formatNights } from "@/lib/dates";
import { readJourneyDraft, saveJourneyDraft } from "@/lib/local-journeys";

export function DateRangePicker() {
  const router = useRouter();
  const [range, setRange] = useState<{ start: string | null; end: string | null }>({ start: null, end: null });
  const [loaded, setLoaded] = useState(false);

  // 이미 고른 기간이 있으면 그 기간을 보여준다
  useEffect(() => {
    const draft = readJourneyDraft();
    setRange({ start: draft.startDate, end: draft.endDate });
    setLoaded(true);
  }, []);

  function done() {
    if (!range.start || !range.end) return;
    saveJourneyDraft({ ...readJourneyDraft(), startDate: range.start, endDate: range.end });
    if (window.history.length > 1) router.back();
    else router.replace("/journeys/new");
  }

  return (
    <>
      <JourneyHeader title="여행 기간 선택" fallbackHref="/journeys/new" />
      <div className="mt-8">
        {/* 임시 저장한 기간을 읽은 뒤에 달력을 그려서, 고른 기간이 있는 달부터 보이게 한다 */}
        {loaded && (
          <RangeCalendar start={range.start} end={range.end} onChange={(start, end) => setRange({ start, end })} />
        )}
      </div>
      <p className="mt-3 text-balance text-center text-sm text-muted-foreground">
        날짜를 누른 채 끌거나, 시작일과 종료일을 차례로 누르세요.
      </p>

      <div className="min-h-8 flex-1" />
      <p
        aria-live="polite"
        className="flex h-14 items-center justify-center gap-2 rounded-2xl border border-brand-line bg-brand-soft px-4 text-base font-semibold"
      >
        {range.start && range.end ? (
          <>
            {range.start === range.end
              ? formatKorean(range.start)
              : `${formatKorean(range.start)} ~ ${formatKorean(range.end)}`}
            <span className="text-sm font-normal text-muted-foreground">{formatNights(range.start, range.end)}</span>
          </>
        ) : (
          <span className="font-normal text-muted-foreground">여행 기간을 골라 주세요</span>
        )}
      </p>
      <NextButton type="button" className="mt-3" disabled={!range.start || !range.end} onClick={done}>
        기간 선택 완료
      </NextButton>
    </>
  );
}
