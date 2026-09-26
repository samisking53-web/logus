"use client";

// 아이디로 로그인. 이 기기에서 가입한 계정의 아이디·비밀번호가 맞으면 홈(또는 원래 가려던 주소)으로 간다.
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";
import { BackButton } from "@/components/back-button";
import { FormError } from "@/components/form-error";
import { NextButton } from "@/components/signup/next-button";
import { PasswordField, TextField } from "@/components/signup/text-field";
import { signIn } from "@/lib/local-auth";
import { readNextParam, withNext } from "@/lib/next-param";

export function IdLoginForm() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [checking, setChecking] = useState(false);
  const [moving, startMoving] = useTransition();
  const [error, setError] = useState<string | null>(null);
  const [next, setNext] = useState("/");

  useEffect(() => {
    setNext(readNextParam());
  }, []);

  const busy = checking || moving;
  const canSubmit = username.trim().length > 0 && password.length > 0 && !busy;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;
    setChecking(true);
    setError(null);
    try {
      const account = await signIn(username, password);
      if (!account) {
        setError("아이디 또는 비밀번호가 맞지 않아요. 이 기기에서 가입한 계정으로만 로그인할 수 있어요.");
        return;
      }
      startMoving(() => router.replace(next));
    } catch (err) {
      setError(err instanceof Error ? err.message : "로그인하지 못했어요. 다시 시도해 주세요.");
    } finally {
      setChecking(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-1 flex-col">
      <header className="flex h-14 items-center">
        <BackButton fallbackHref="/login" />
      </header>
      <h1 className="mt-4 text-2xl font-bold">아이디로 로그인</h1>
      <p className="mt-3 text-sm leading-relaxed text-muted-foreground">이 기기에서 가입한 계정으로 로그인해요.</p>

      <div className="mt-8 flex flex-col gap-6">
        <TextField
          id="login-username"
          label="아이디"
          value={username}
          onChange={(e) => setUsername(e.target.value.toLowerCase())}
          autoComplete="username"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          maxLength={20}
        />
        <PasswordField
          id="login-password"
          label="비밀번호"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          maxLength={64}
        />
      </div>
      {error && (
        <div className="mt-4">
          <FormError alert>{error}</FormError>
        </div>
      )}

      <div className="min-h-8 flex-1" />
      <NextButton type="submit" disabled={!canSubmit}>
        {busy ? "확인하는 중…" : "로그인"}
      </NextButton>
      <p className="py-4 text-center text-sm text-muted-foreground">
        처음이에요?{" "}
        <Link href={withNext("/signup/terms", next)} className="font-semibold text-brand-strong underline underline-offset-2">
          회원가입
        </Link>
      </p>
    </form>
  );
}
