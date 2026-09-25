// 여정 화면(새 여정 만들기·여행 기간 선택·카메라)의 공통 레이아웃. 하단 탭 없이 폰 폭으로 보여준다
// break-keep: 한글 문장이 줄을 바꿀 때 단어 중간이 아니라 띄어쓰기에서 끊기게 한다
import { AccountCheck } from "@/components/account-check";

export default function JourneysLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[430px] flex-col break-keep px-4 pb-[calc(env(safe-area-inset-bottom)+1rem)]">
      {children}
      {/* 이 기기에 로그인한 계정이 실제로 있는지 확인한다 */}
      <AccountCheck />
    </main>
  );
}
