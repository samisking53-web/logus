// S10 마이로그
import type { Metadata } from "next";
import ScreenHeader from "@/components/ScreenHeader";

export const metadata: Metadata = { title: "마이로그 | LOG US" };

export default function MyLogPage() {
  return (
    <>
      <ScreenHeader title="마이로그" description="여정별 기록과 추억을 다시 봐요." />
      <p className="rounded-2xl bg-brand-soft p-5 text-muted">
        내가 만들거나 초대받은 여정이 여기에 모일 예정이에요.
      </p>
    </>
  );
}
