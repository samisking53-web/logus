// 오류 문구. 빨간 느낌표 아이콘과 함께 보여준다.
// 다크 모드는 팔레트에 어두운 배경용 빨간 글자색이 없어서, 글자는 본문 색으로 두고 아이콘만 빨갛게 한다
// (어두운 배경에서 오류색 글자는 명도 대비 4.5:1이 안 나온다)
import { AlertCircle } from "lucide-react";

export function FormError({ children, alert = false }: { children: React.ReactNode; alert?: boolean }) {
  return (
    <p
      // alert: 버튼을 누른 뒤 생긴 오류는 화면 읽기 프로그램이 바로 읽어 주게 한다
      role={alert ? "alert" : undefined}
      className="flex items-start gap-1.5 text-sm text-destructive dark:text-foreground"
    >
      <AlertCircle className="mt-0.5 size-4 shrink-0 text-destructive" aria-hidden="true" />
      <span>{children}</span>
    </p>
  );
}
