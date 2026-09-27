// S11 여정 상세 (기록·추억 지도·추억 영상). 주소 예: /journeys/여정번호
// 마이로그(S10)의 여정 카드를 누르거나, 기록 올리기(S05)에서 게시하면 이 화면으로 온다
import type { Metadata } from "next";
import { Suspense } from "react";
import { JourneyDetail } from "@/components/journey/journey-detail";

export const metadata: Metadata = {
  title: "여정 상세",
};

export default function JourneyDetailPage() {
  return (
    // 여정 번호는 주소에서 읽기 때문에 화면을 연 뒤에 알 수 있다
    <Suspense fallback={null}>
      <JourneyDetail />
    </Suspense>
  );
}
