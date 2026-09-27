// S05 기록 올리기 (새 기록 남기기). 주소 예: /journeys/여정번호/record
// 카메라(S04)에서 영상을 찍고 멈추면 이 화면으로 온다
import type { Metadata } from "next";
import { Suspense } from "react";
import { NewLogForm } from "@/components/journey/new-log-form";

export const metadata: Metadata = {
  title: "새 기록 남기기",
};

export default function NewLogPage() {
  return (
    // 여정 번호는 주소에서 읽기 때문에 화면을 연 뒤에 알 수 있다
    <Suspense fallback={null}>
      <NewLogForm />
    </Suspense>
  );
}
