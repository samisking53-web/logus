"use client";

// 이름표 + 입력칸 + 아래 안내 문구를 한 번에 그리는 입력칸. 가입·로그인 화면에서 함께 쓴다
import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { FormError } from "@/components/form-error";
import { cn } from "@/lib/utils";

type TextFieldProps = Omit<React.InputHTMLAttributes<HTMLInputElement>, "className" | "id"> & {
  id: string;
  label: string;
  hint?: React.ReactNode; // 입력칸 아래 안내 문구
  error?: string | null; // 있으면 안내 문구 대신 오류를 보여준다
  counter?: string; // 오른쪽 아래 글자 수 (예: "2 / 12")
  trailing?: React.ReactNode; // 입력칸 오른쪽 안에 넣을 버튼
};

export function TextField({ id, label, hint, error, counter, trailing, ...inputProps }: TextFieldProps) {
  const helpId = `${id}-help`;
  return (
    <div className="flex flex-col">
      <label htmlFor={id} className="text-sm font-semibold">
        {label}
      </label>
      <div className="relative mt-2">
        <input
          id={id}
          aria-invalid={Boolean(error)}
          aria-describedby={helpId}
          className={cn(
            "h-14 w-full rounded-2xl border bg-card px-4 text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[invalid=true]:border-destructive",
            trailing && "pr-14",
          )}
          {...inputProps}
        />
        {trailing && <div className="absolute inset-y-0 right-2 flex items-center">{trailing}</div>}
      </div>
      <div id={helpId} className="mt-2 flex items-start justify-between gap-3 text-xs text-muted-foreground">
        <div className="min-w-0">{error ? <FormError>{error}</FormError> : hint}</div>
        {counter && <span className="shrink-0">{counter}</span>}
      </div>
    </div>
  );
}

// 비밀번호 입력칸. 오른쪽 눈 모양 버튼으로 입력한 비밀번호를 보거나 숨길 수 있다
export function PasswordField(props: Omit<TextFieldProps, "type" | "trailing">) {
  const [visible, setVisible] = useState(false);
  return (
    <TextField
      {...props}
      type={visible ? "text" : "password"}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? "비밀번호 숨기기" : "비밀번호 보기"}
          aria-pressed={visible}
          className="flex size-10 items-center justify-center rounded-full text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {visible ? <EyeOff className="size-5" aria-hidden="true" /> : <Eye className="size-5" aria-hidden="true" />}
        </button>
      }
    />
  );
}
