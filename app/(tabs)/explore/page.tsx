// S08 탐색
import type { Metadata } from "next";
import { PageHeader } from "@/components/page-header";

export const metadata: Metadata = {
  title: "탐색",
};

export default function ExplorePage() {
  return (
    <>
      <PageHeader title="탐색" />
      {/* 빈 화면: 지역·테마 검색창과 세로 스와이프 영상 피드가 들어갈 자리 */}
      <section className="flex flex-1 flex-col items-center justify-center gap-2 px-4 text-center">
        <p className="text-lg font-semibold">다른 여행자의 순간을 발견해요</p>
        <p className="text-sm text-muted-foreground">
          공개된 여행 영상을 준비하고 있어요.
        </p>
      </section>
    </>
  );
}
