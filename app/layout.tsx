import type { Metadata, Viewport } from "next";
import { Geist } from "next/font/google";
import { ThemeProvider } from "next-themes";
import { SplashScreen, SplashScreenScript } from "@/components/splash-screen";
import "./globals.css";

const defaultUrl = process.env.VERCEL_URL
  ? `https://${process.env.VERCEL_URL}`
  : "http://localhost:3000";

export const metadata: Metadata = {
  metadataBase: new URL(defaultUrl),
  // 화면마다 title을 정하면 "탐색 · LOG US"처럼 뒤에 앱 이름이 붙는다
  title: {
    default: "LOG US",
    template: "%s · LOG US",
  },
  description:
    "여행과 행사를 함께 기록하고, 추억 지도와 영상으로 다시 보는 LOG US",
  applicationName: "LOG US",
  // 아이폰에서 '홈 화면에 추가'했을 때 앱처럼 열리게 한다 (PWA 설정은 app/manifest.ts)
  appleWebApp: {
    capable: true,
    title: "LOG US",
    statusBarStyle: "default",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  // 아이폰 노치·홈 바 영역까지 화면을 쓰고, 안쪽 여백은 env(safe-area-inset-*)로 맞춘다
  viewportFit: "cover",
  // 폰 브라우저 상단 막대 색. 화면 바탕색(globals.css의 --background)과 맞춘다
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#F7F5FB" },
    { media: "(prefers-color-scheme: dark)", color: "#141220" },
  ],
};

const geistSans = Geist({
  variable: "--font-geist-sans",
  display: "swap",
  subsets: ["latin"],
});

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <body className={`${geistSans.className} antialiased`}>
        {/* 앱 시작화면을 이 창에서 이미 봤는지 확인한다. 다른 내용보다 먼저 실행되도록 맨 앞에 둔다 */}
        <SplashScreenScript />
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          {children}
          {/* 앱을 열면 잠깐 보였다가 사라지는 시작화면 (LOG EARTH). 로그인 화면이든 홈이든 가장 먼저 보인다 */}
          <SplashScreen />
        </ThemeProvider>
      </body>
    </html>
  );
}
