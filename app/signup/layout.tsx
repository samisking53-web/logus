// 회원가입 단계(1~4단계) 화면들의 공통 레이아웃. 하단 탭 없이 폰 폭으로 보여준다
// break-keep: 한글 문장이 줄을 바꿀 때 단어 중간('저 / 장돼요')이 아니라 띄어쓰기에서 끊기게 한다
export default function SignupLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[430px] flex-col break-keep px-5 pb-[calc(env(safe-area-inset-bottom)+1rem)] pt-[env(safe-area-inset-top)]">
      {children}
    </main>
  );
}
