import type { Metadata, Viewport } from "next";
import BottomTabBar from "@/components/BottomTabBar";
import SplashScreen from "@/components/SplashScreen";
import "./globals.css";

export const metadata: Metadata = {
  title: "LOG US",
  description: "여정별로 함께 남기는 공동 기록",
  applicationName: "LOG US",
  appleWebApp: {
    capable: true,
    title: "LOG US",
    statusBarStyle: "default",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover", // 아이폰 노치·홈 바 영역까지 화면을 쓰고, 여백은 safe-area로 맞춘다
  // 브라우저 주소창 색: 팔레트의 바탕색(라이트/다크)
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f7f5fb" },
    { media: "(prefers-color-scheme: dark)", color: "#141220" },
  ],
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="min-h-full bg-background">
        <SplashScreen />
        {/* 기준 폭 390px의 모바일 화면. 넓은 화면에서는 가운데에 놓인다. */}
        <div className="mx-auto flex min-h-dvh w-full max-w-[390px] flex-col bg-background shadow-sm">
          <main className="flex-1 px-5 pt-[calc(env(safe-area-inset-top)+1.5rem)] pb-28">
            {children}
          </main>
          <BottomTabBar />
        </div>
      </body>
    </html>
  );
}
