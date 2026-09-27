// 새 여정 만들기의 한 줄: 왼쪽 이름표(연보라) + 오른쪽 입력칸
import { cn } from "@/lib/utils";

// 오른쪽 입력칸들이 함께 쓰는 모양
export const VALUE_BOX = "h-14 w-full rounded-2xl border bg-card px-4 text-base";

export function FormRow({
  label,
  labelId,
  htmlFor,
  children,
  className,
}: {
  label: string;
  labelId: string;
  htmlFor?: string; // 입력칸이 input이면 id를 넣는다. 버튼·선택 묶음이면 비워 두고 aria-labelledby로 연결한다
  children: React.ReactNode;
  className?: string;
}) {
  const labelClass =
    "flex h-14 w-24 shrink-0 items-center rounded-2xl border border-brand-line bg-brand-soft px-3 text-[15px] font-semibold";
  return (
    <div className={cn("relative flex items-start gap-2", className)}>
      {htmlFor ? (
        <label id={labelId} htmlFor={htmlFor} className={labelClass}>
          {label}
        </label>
      ) : (
        <span id={labelId} className={labelClass}>
          {label}
        </span>
      )}
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
