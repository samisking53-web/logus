// 여정 화면(새 여정 만들기·여행 기간 선택·카메라) 맨 위: LOG EARTH 글자 + 뒤로 버튼 + 제목
import { Globe } from "lucide-react";
import { BackButton } from "@/components/back-button";

export function JourneyHeader({ title, fallbackHref = "/" }: { title: string; fallbackHref?: string }) {
  return (
    <header className="pt-[env(safe-area-inset-top)]">
      <p className="flex items-center justify-center gap-2 pt-3 text-sm font-bold tracking-widest text-brand-strong">
        <Globe className="size-4" aria-hidden="true" />
        LOG EARTH
      </p>
      <div className="mt-3 flex items-center gap-2">
        <BackButton fallbackHref={fallbackHref} className="ml-0 size-11 shrink-0 border bg-card" />
        <h1 className="flex h-11 min-w-0 flex-1 items-center truncate rounded-full border bg-card px-5 text-lg font-bold">
          {title}
        </h1>
      </div>
    </header>
  );
}
