"use client";

// 홈 위쪽 안내 한 줄 (예: 여정을 저장한 뒤 "저장했어요"). 한 번 보여주고 나면 다시 나오지 않는다
import { useEffect, useState } from "react";
import { CheckCircle2, X } from "lucide-react";
import { takeFlashMessage } from "@/lib/local-journeys";

export function FlashBanner() {
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const flash = takeFlashMessage();
    if (flash) setMessage(flash);
  }, []);

  if (!message) return null;
  return (
    <div role="status" className="flex items-start gap-3 break-keep rounded-2xl border border-brand-line bg-brand-soft p-4 text-sm leading-relaxed">
      <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-success" aria-hidden="true" />
      <p className="flex-1">{message}</p>
      <button
        type="button"
        onClick={() => setMessage(null)}
        aria-label="안내 닫기"
        className="-m-2 flex size-9 shrink-0 items-center justify-center rounded-full text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <X className="size-4" aria-hidden="true" />
      </button>
    </div>
  );
}
