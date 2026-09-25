// S01 홈
import { PageHeader } from "@/components/page-header";

export default function HomePage() {
  return (
    <>
      <PageHeader title="홈" />
      {/* 빈 화면: 기록 시작하기 · 탐색 · 마이로그 메뉴가 들어갈 자리 */}
      <section className="flex flex-1 flex-col items-center justify-center gap-2 px-4 text-center">
        <p className="text-lg font-semibold">어떤 순간을 남길까요?</p>
        <p className="text-sm text-muted-foreground">
          기록 시작하기 화면을 준비하고 있어요.
        </p>
      </section>
    </>
  );
}
