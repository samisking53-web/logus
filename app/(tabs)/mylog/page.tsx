// S10 마이로그
import type { Metadata } from "next";
import { PageHeader } from "@/components/page-header";

export const metadata: Metadata = {
  title: "마이로그",
};

export default function MyLogPage() {
  return (
    <>
      <PageHeader title="마이로그" />
      {/* 빈 화면: 내가 만들거나 초대받은 여정 목록이 들어갈 자리 */}
      <section className="flex flex-1 flex-col items-center justify-center gap-2 px-4 text-center">
        <p className="text-lg font-semibold">아직 여정이 없어요</p>
        <p className="text-sm text-muted-foreground">
          내가 만들거나 초대받은 여정이 여기에 모여요.
        </p>
      </section>
    </>
  );
}
