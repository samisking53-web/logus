"use client";

// 앱 시작 화면 (스토리보드 1쪽)
// 앱을 열면 잠깐 보였다가 사라진다. 탭을 옮겨 다닐 때는 레이아웃이 그대로라 다시 나오지 않는다.
import { useEffect, useState } from "react";

const SHOW_MS = 1500; // 보여 주는 시간
const FADE_MS = 400; // 사라지는 시간

export default function SplashScreen() {
  const [phase, setPhase] = useState<"show" | "fade" | "gone">("show");

  useEffect(() => {
    const fadeTimer = setTimeout(() => setPhase("fade"), SHOW_MS);
    const goneTimer = setTimeout(() => setPhase("gone"), SHOW_MS + FADE_MS);
    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(goneTimer);
    };
  }, []);

  if (phase === "gone") return null;

  return (
    <div
      role="status"
      aria-label="LOG EARTH 앱을 여는 중"
      className={`fixed inset-0 z-50 flex items-center justify-center bg-surface transition-opacity duration-[400ms] motion-reduce:transition-none ${
        phase === "fade" ? "pointer-events-none opacity-0" : "opacity-100"
      }`}
    >
      <p className="text-3xl font-bold tracking-wide text-primary-strong">LOG EARTH</p>
    </div>
  );
}
