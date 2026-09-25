// 화면 맨 위 제목 영역. 탭 화면마다 같은 모양으로 쓴다.
export function PageHeader({ title }: { title: string }) {
  return (
    <header
      // safe-area-inset-top: 홈 화면에 추가한 앱에서 아이폰 상단(노치·상태 표시줄)에 글자가 가리지 않게 한다
      className="sticky top-0 z-30 bg-background px-4 pt-[env(safe-area-inset-top)]"
    >
      <p className="pt-3 text-xs font-bold tracking-wider text-brand">LOG US</p>
      <h1 className="pb-3 text-2xl font-bold">{title}</h1>
    </header>
  );
}
