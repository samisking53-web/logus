"use client";

// 여행 기간 달력 (한 달 전체). 기간을 고르는 방법은 두 가지다.
// 1) 날짜를 누른 채 끌기: 누른 날부터 손가락(마우스)을 뗀 날까지
// 2) 두 번 누르기: 시작일을 누르고, 종료일을 누른다 (다른 달로 넘어가서 눌러도 된다)
// 끌기 도중 화면이 스크롤되지 않도록 날짜 칸 영역에는 touch-none을 준다.
import { useRef, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { WEEKDAYS, fromDateKey, monthCells, todayKey } from "@/lib/dates";
import { cn } from "@/lib/utils";

type Range = { start: string | null; end: string | null };

function ordered(a: string, b: string): [string, string] {
  return a <= b ? [a, b] : [b, a];
}

function dayLabel(key: string) {
  const d = fromDateKey(key);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 ${WEEKDAYS[d.getDay()]}요일`;
}

export function RangeCalendar({
  start,
  end,
  onChange,
}: Range & {
  onChange: (start: string, end: string) => void;
}) {
  const initial = fromDateKey(start ?? todayKey());
  const [month, setMonth] = useState({ year: initial.getFullYear(), index: initial.getMonth() });
  // 시작일만 누르고 종료일을 기다리는 중인지
  const [waitingForEnd, setWaitingForEnd] = useState(false);
  const drag = useRef<{ anchor: string; moved: boolean } | null>(null);
  const today = todayKey();

  function tap(day: string) {
    if (waitingForEnd && start) {
      const [s, e] = ordered(start, day);
      onChange(s, e);
      setWaitingForEnd(false);
    } else {
      onChange(day, day);
      setWaitingForEnd(true);
    }
  }

  // 손가락·마우스 아래에 있는 날짜 칸
  function dayAt(x: number, y: number) {
    const el = document.elementFromPoint(x, y)?.closest<HTMLElement>("[data-date]");
    return el?.dataset.date ?? null;
  }

  function onPointerDown(event: React.PointerEvent<HTMLDivElement>) {
    const day = dayAt(event.clientX, event.clientY);
    if (!day || (event.pointerType === "mouse" && event.button !== 0)) return;
    drag.current = { anchor: day, moved: false };
    // 칸 밖으로 끌고 나가도 손을 뗀 것을 알 수 있게 이 영역이 포인터를 붙잡아 둔다
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function onPointerMove(event: React.PointerEvent<HTMLDivElement>) {
    const current = drag.current;
    if (!current) return;
    const day = dayAt(event.clientX, event.clientY);
    if (!day || (day === current.anchor && !current.moved)) return;
    current.moved = true;
    const [s, e] = ordered(current.anchor, day);
    onChange(s, e);
    setWaitingForEnd(false);
  }

  function onPointerUp() {
    const current = drag.current;
    drag.current = null;
    if (current && !current.moved) tap(current.anchor);
  }

  function moveMonth(step: number) {
    setMonth(({ year, index }) => {
      const d = new Date(year, index + step, 1);
      return { year: d.getFullYear(), index: d.getMonth() };
    });
  }

  const cells = monthCells(month.year, month.index);

  return (
    <div>
      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={() => moveMonth(-1)}
          aria-label="이전 달"
          className="flex size-11 items-center justify-center rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:bg-brand-soft"
        >
          <ChevronLeft className="size-6" aria-hidden="true" />
        </button>
        <h2 aria-live="polite" className="text-2xl font-bold">
          {month.year}년 {month.index + 1}월
        </h2>
        <button
          type="button"
          onClick={() => moveMonth(1)}
          aria-label="다음 달"
          className="flex size-11 items-center justify-center rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:bg-brand-soft"
        >
          <ChevronRight className="size-6" aria-hidden="true" />
        </button>
      </div>

      <div className="mt-4 grid grid-cols-7 text-center text-sm text-muted-foreground" aria-hidden="true">
        {WEEKDAYS.map((w) => (
          <span key={w} className="py-2">
            {w}
          </span>
        ))}
      </div>

      <div
        className="grid touch-none select-none grid-cols-7 [-webkit-touch-callout:none]"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={() => (drag.current = null)}
      >
        {cells.map((day, i) => {
          if (!day) return <span key={`blank-${i}`} className="h-12" />;
          const inRange = Boolean(start && end && day >= start && day <= end);
          const isStart = day === start;
          const isEnd = day === end;
          const isEndpoint = isStart || isEnd;
          return (
            <div key={day} className="relative flex h-12 items-center justify-center">
              {/* 기간 안의 날짜를 잇는 연보라 띠 */}
              {inRange && !(isStart && isEnd) && (
                <span
                  aria-hidden="true"
                  className={cn(
                    "absolute inset-y-1 bg-brand-soft",
                    isStart ? "left-1/2 right-0" : isEnd ? "left-0 right-1/2" : "inset-x-0",
                  )}
                />
              )}
              <button
                type="button"
                data-date={day}
                aria-label={dayLabel(day)}
                aria-pressed={inRange}
                aria-current={day === today ? "date" : undefined}
                // 손가락·마우스는 위의 끌기 처리에서 다루고, 여기서는 키보드(Enter·Space)만 처리한다
                onClick={(e) => e.detail === 0 && tap(day)}
                className={cn(
                  "relative flex size-10 flex-col items-center justify-center rounded-full text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                  isEndpoint
                    ? "bg-brand font-bold text-brand-foreground"
                    : inRange
                      ? "font-semibold text-brand-strong"
                      : "text-foreground",
                )}
              >
                {fromDateKey(day).getDate()}
                {/* 오늘 표시: 숫자 아래 작은 점 */}
                {day === today && (
                  <span
                    aria-hidden="true"
                    className={cn(
                      "absolute bottom-1 size-1 rounded-full",
                      isEndpoint ? "bg-brand-foreground" : "bg-brand",
                    )}
                  />
                )}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
