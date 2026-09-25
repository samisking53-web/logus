import type { MetadataRoute } from "next";

// PWA 설정 파일. 브라우저가 /manifest.webmanifest 주소로 읽어서
// '홈 화면에 추가'했을 때의 앱 이름·아이콘·색을 정한다.
export default function manifest(): MetadataRoute.Manifest {
  return {
    id: "/",
    name: "LOG US",
    short_name: "LOG US",
    description:
      "여행과 행사를 함께 기록하고, 추억 지도와 영상으로 다시 보는 LOG US",
    lang: "ko",
    start_url: "/",
    scope: "/",
    // standalone: 주소창 없이 앱처럼 열린다
    display: "standalone",
    // 설치한 앱을 열 때 안드로이드가 먼저 보여주는 화면의 배경색.
    // 앱 시작화면(components/splash-screen.tsx)의 연보라 배경(--brand-soft)과 맞춘다
    background_color: "#f2ebff",
    theme_color: "#ffffff",
    // 임시 아이콘이다. 디자인이 정해지면 public/icons/ 안의 파일을 같은 이름·크기의 PNG로 바꾼다.
    // 아이폰 홈 화면 아이콘은 app/apple-icon.png(180x180), 브라우저 탭 아이콘은 app/favicon.ico
    icons: [
      {
        src: "/icons/icon-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "any",
      },
      {
        // maskable: 안드로이드가 원·둥근 사각형 등으로 잘라 쓰는 아이콘.
        // 가장자리가 잘려도 되도록 로고는 가운데 80% 안에 둔다
        src: "/icons/icon-maskable-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}
