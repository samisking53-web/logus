// S04 앱 내 카메라 (기본형). 주소 예: /journeys/여정번호/camera
import type { Metadata } from "next";
import { Suspense } from "react";
import { JourneyCamera } from "@/components/journey/journey-camera";
import { JourneyHeader } from "@/components/journey/journey-header";

export const metadata: Metadata = {
  title: "카메라",
};

export default function JourneyCameraPage() {
  return (
    // 여정 번호는 주소에서 읽기 때문에 화면을 연 뒤에 알 수 있다. 그 전까지는 제목 줄만 먼저 보여준다
    <Suspense fallback={<JourneyHeader title="카메라" />}>
      <JourneyCamera />
    </Suspense>
  );
}
