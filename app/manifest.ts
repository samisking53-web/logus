import type { MetadataRoute } from "next";

// PWA 설정: 홈 화면에 추가하면 앱처럼 전체 화면으로 열린다.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "LOG US",
    short_name: "LOG US",
    description: "여정별로 함께 남기는 공동 기록",
    lang: "ko",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#f7f5fb",
    theme_color: "#5b3dd6",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
