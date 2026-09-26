"use client";

// 이전 화면으로 돌아가는 버튼. 이 앱 안에서 넘어온 기록이 없으면 fallbackHref로 간다
import { useRouter } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { cn } from "@/lib/utils";

export function BackButton({ fallbackHref = "/", className }: { fallbackHref?: string; className?: string }) {
  const router = useRouter();

  function goBack() {
    if (window.history.length > 1) {
      router.back();
    } else {
      router.push(fallbackHref);
    }
  }

  return (
    <button
      type="button"
      onClick={goBack}
      aria-label="이전으로"
      className={cn(
        "-ml-2 flex size-10 items-center justify-center rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        className,
      )}
    >
      <ChevronLeft className="size-6" aria-hidden="true" />
    </button>
  );
}
