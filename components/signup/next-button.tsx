// 가입 단계 화면 맨 아래의 큰 버튼. 누를 수 없을 때는 회색으로 보인다.
import { Button, type ButtonProps } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function NextButton({ className, ...props }: ButtonProps) {
  return (
    <Button
      className={cn(
        "h-14 w-full rounded-2xl text-base font-semibold active:bg-brand-pressed disabled:bg-border disabled:text-muted-foreground disabled:opacity-100",
        className,
      )}
      {...props}
    />
  );
}
