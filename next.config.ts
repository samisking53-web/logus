import type { NextConfig } from "next";

const FIREBASE_PROJECT_ID = "logus-80f21";

const nextConfig: NextConfig = {
  // 모바일 로그인(signInWithRedirect)을 위해 Firebase 인증 도우미 주소를 앱 도메인으로 프록시한다.
  // Firebase 문서 "redirect best practices"의 Option 3. authDomain은 앱 도메인으로 둔다.
  async rewrites() {
    return [
      {
        source: "/__/auth/:path*",
        destination: `https://${FIREBASE_PROJECT_ID}.firebaseapp.com/__/auth/:path*`,
      },
    ];
  },
};

export default nextConfig;
