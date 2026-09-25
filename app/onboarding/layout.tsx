// 가입 단계(1~3단계) 화면들의 공통 레이아웃. 하단 탭 없이 폰 폭으로 보여준다
export default function OnboardingLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[430px] flex-col px-5 pb-[calc(env(safe-area-inset-bottom)+1rem)] pt-[env(safe-area-inset-top)]">
      {children}
    </main>
  );
}
