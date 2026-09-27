"use client";

// 대표 화면 고르기: 영상을 찍는 동안 1초마다 남긴 장면 중 하나를 눌러 여정의 대표 화면(여행 중 홈에 크게 보임)으로 정한다
import { useState } from "react";
import { Check } from "lucide-react";
import { getCurrentAccount } from "@/lib/local-auth";
import { setJourneyCover } from "@/lib/local-journeys";
import { cn } from "@/lib/utils";

export function CoverPicker({ journeyId, frames }: { journeyId: string; frames: string[] }) {
  const [chosen, setChosen] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  function choose(index: number) {
    const account = getCurrentAccount();
    if (!account) return;
    const result = setJourneyCover(account.id, journeyId, frames[index]);
    setChosen(result.ok ? index : null);
    setMessage(result.ok ? "대표 화면으로 정했어요. 홈에서 보여요." : result.message);
  }

  if (frames.length === 0) return null;
  return (
    <div className="mt-4" role="group" aria-labelledby="cover-picker-title">
      <p id="cover-picker-title" className="text-sm font-semibold">
        대표 화면 고르기
      </p>
      <p className="mt-0.5 text-xs text-muted-foreground">여행 중 홈에 크게 보일 장면을 눌러 주세요.</p>
      <ul className="-mx-4 mt-2 flex gap-2 overflow-x-auto px-4 pb-1">
        {frames.map((frame, i) => (
          <li key={i} className="shrink-0">
            <button
              type="button"
              onClick={() => choose(i)}
              aria-pressed={chosen === i}
              aria-label={`${i}초 장면`}
              className={cn(
                "relative block h-20 w-14 overflow-hidden rounded-xl border-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                chosen === i ? "border-brand" : "border-transparent",
              )}
            >
              {/* eslint-disable-next-line @next/next/no-img-element -- 방금 찍은 장면(이미지 글자)이라 next/image를 쓸 수 없다 */}
              <img src={frame} alt="" className="size-full object-cover" />
              {chosen === i && (
                <span className="absolute right-1 top-1 flex size-5 items-center justify-center rounded-full bg-brand text-brand-foreground">
                  <Check className="size-3.5" aria-hidden="true" />
                </span>
              )}
            </button>
          </li>
        ))}
      </ul>
      {message && (
        <p role="status" className="mt-1 text-sm text-brand-strong">
          {message}
        </p>
      )}
    </div>
  );
}
