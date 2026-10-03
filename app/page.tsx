// S01 홈 / S01-A 여행 기간 중 홈
import { Suspense } from "react";
import HomeScreen from "@/components/home/HomeScreen";

export default function HomePage() {
  // 주소의 ?preview= 값을 읽기 때문에 Suspense로 감싼다(Next.js 규칙).
  return (
    <Suspense>
      <HomeScreen />
    </Suspense>
  );
}
