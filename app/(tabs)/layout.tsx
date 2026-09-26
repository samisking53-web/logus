// 하단 탭(홈/탐색/마이로그)이 있는 화면들의 공통 레이아웃
// 폴더 이름의 괄호 (tabs)는 주소에 나타나지 않는다. 예: app/(tabs)/explore → /explore
import { BottomTabBar } from "@/components/bottom-tab-bar";
import { AccountCheck } from "@/components/account-check";
import { StartDayCamera } from "@/components/journey/start-day-camera";

export default function TabsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    // 모바일 기준 폭 390px로 디자인하고, 큰 폰(최대 430px)까지는 화면을 꽉 채운다.
    // 컴퓨터에서 열면 가운데에 폰 폭으로 보인다.
    <div className="mx-auto flex min-h-dvh w-full max-w-[430px] flex-col sm:border-x">
      <main className="flex flex-1 flex-col">{children}</main>
      <BottomTabBar />
      {/* 이 기기에 로그인한 계정이 실제로 있는지 확인한다 */}
      <AccountCheck />
      {/* 미리 등록한 여정의 시작일이면 카메라를 바로 연다 (시작일에 처음 열 때 한 번) */}
      <StartDayCamera />
    </div>
  );
}
