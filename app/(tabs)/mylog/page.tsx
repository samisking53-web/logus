// S10 마이로그 (나의 여정 목록)
// 홈의 ③ 마이로그 카드와 하단 탭의 마이로그에서 들어온다
import type { Metadata } from "next";
import { AccountBox } from "@/components/account-box";
import { JourneyList } from "@/components/mylog/journey-list";
import { PageHeader } from "@/components/page-header";

export const metadata: Metadata = {
  title: "마이로그",
};

export default function MyLogPage() {
  return (
    <>
      <PageHeader title="마이로그" />
      {/* 필터(전체·진행 중·예정·지난 여정)와 여정 카드 */}
      <JourneyList />
      {/* 로그인한 계정과 로그아웃 버튼 */}
      <AccountBox />
    </>
  );
}
