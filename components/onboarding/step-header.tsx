"use client";

// 가입 단계 화면 맨 위: 뒤로 버튼 + "1 / 3 단계"
import { ChevronLeft } from "lucide-react";

export function StepHeader({ step, onBack }: { step: 1 | 2 | 3; onBack?: () => void }) {
  return (
    <header className="flex h-14 items-center gap-1">
      {onBack && (
        <button
          type="button"
          onClick={onBack}
          aria-label="이전으로"
          className="-ml-2 flex size-10 items-center justify-center rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ChevronLeft className="size-6" aria-hidden="true" />
        </button>
      )}
      <p className="text-sm text-muted-foreground">{step} / 3 단계</p>
    </header>
  );
}
