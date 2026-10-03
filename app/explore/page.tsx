// S08 탐색
import type { Metadata } from "next";
import ScreenHeader from "@/components/ScreenHeader";

export const metadata: Metadata = { title: "탐색 | LOG US" };

export default function ExplorePage() {
  return (
    <>
      <ScreenHeader title="탐색" description="다른 여행자의 순간을 발견해요." />
      <p className="rounded-2xl bg-surface p-5 text-text-secondary">
        공개된 추억 영상이 여기에 나올 예정이에요.
      </p>
    </>
  );
}
