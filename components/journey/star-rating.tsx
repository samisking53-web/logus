"use client";

// 별점 (5점 만점, 0.5점 단위). 별 위를 누르거나 손가락·마우스로 좌우로 끌어서 매긴다.
// 누른 곳이 별의 왼쪽 절반이면 0.5점, 오른쪽 절반이면 1점이 더해진다.
// 키보드: ←→ 0.5점씩, Home 0점, End 5점 (화면 읽기 프로그램에는 '슬라이더'로 알려 준다)
import { useRef } from "react";
import { Star } from "lucide-react";

const MAX = 5;
const STEP = 0.5;

export function StarRating({
  value,
  onChange,
  labelledBy,
}: {
  value: number;
  onChange: (value: number) => void;
  labelledBy: string;
}) {
  const areaRef = useRef<HTMLDivElement>(null);
  const dragging = useRef(false);

  // 손가락 위치 → 점수 (0.5 ~ 5)
  function valueAt(clientX: number) {
    const rect = areaRef.current!.getBoundingClientRect();
    const ratio = (clientX - rect.left) / rect.width;
    const raw = Math.ceil(ratio * MAX * 2) / 2;
    return Math.min(MAX, Math.max(STEP, raw));
  }

  function onPointerDown(event: React.PointerEvent<HTMLDivElement>) {
    if (event.pointerType === "mouse" && event.button !== 0) return;
    dragging.current = true;
    event.currentTarget.setPointerCapture(event.pointerId);
    event.currentTarget.focus();
    onChange(valueAt(event.clientX));
  }

  function onPointerMove(event: React.PointerEvent<HTMLDivElement>) {
    if (dragging.current) onChange(valueAt(event.clientX));
  }

  function onKeyDown(event: React.KeyboardEvent) {
    const next =
      event.key === "ArrowRight" || event.key === "ArrowUp"
        ? value + STEP
        : event.key === "ArrowLeft" || event.key === "ArrowDown"
          ? value - STEP
          : event.key === "Home"
            ? 0
            : event.key === "End"
              ? MAX
              : null;
    if (next === null) return;
    event.preventDefault();
    onChange(Math.min(MAX, Math.max(0, next)));
  }

  return (
    <div
      ref={areaRef}
      role="slider"
      tabIndex={0}
      aria-labelledby={labelledBy}
      aria-valuemin={0}
      aria-valuemax={MAX}
      aria-valuenow={value}
      aria-valuetext={value === 0 ? "별점 없음" : `${value}점`}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={() => (dragging.current = false)}
      onPointerCancel={() => (dragging.current = false)}
      onKeyDown={onKeyDown}
      // touch-none: 별 위에서 좌우로 끄는 동안 화면이 움직이지 않게 한다
      className="flex w-fit touch-none select-none rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {Array.from({ length: MAX }, (_, i) => {
        const fill = Math.min(1, Math.max(0, value - i)); // 0, 0.5, 1
        return (
          <span key={i} className="relative block size-10 p-0.5" aria-hidden="true">
            <Star className="size-full text-muted-foreground" strokeWidth={1.5} />
            {fill > 0 && (
              <span className="absolute inset-y-0 left-0 overflow-hidden p-0.5" style={{ width: `${fill * 100}%` }}>
                <Star className="size-9 fill-reward text-reward-strong" strokeWidth={1.5} />
              </span>
            )}
          </span>
        );
      })}
    </div>
  );
}
