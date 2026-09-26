"use client";

// 가입 2단계: 로그인에 쓸 아이디와 비밀번호 만들기.
// 아이디 규칙·비밀번호 규칙·비밀번호 확인이 모두 맞아야 "다음" 버튼이 켜진다.
import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";
import { CheckCircle2, Circle } from "lucide-react";
import { FormError } from "@/components/form-error";
import { StepHeader } from "@/components/signup/step-header";
import { NextButton } from "@/components/signup/next-button";
import { PasswordField, TextField } from "@/components/signup/text-field";
import {
  USERNAME_RULE,
  hasRequiredAgreements,
  isUsernameTaken,
  isValidUsername,
  normalizeUsername,
  passwordChecks,
  readDraft,
  saveCredentialsToDraft,
} from "@/lib/local-auth";
import { readNextParam, withNext } from "@/lib/next-param";
import { cn } from "@/lib/utils";

// 비밀번호 규칙 한 줄: 맞으면 초록 체크, 아니면 빈 동그라미 (색만으로 구분하지 않도록 아이콘도 바뀐다)
function RuleItem({ ok, children }: { ok: boolean; children: React.ReactNode }) {
  const Icon = ok ? CheckCircle2 : Circle;
  return (
    <li className={cn("flex items-center gap-1", ok ? "text-foreground" : "text-muted-foreground")}>
      <Icon className={cn("size-4", ok && "text-success")} aria-hidden="true" />
      {children}
      <span className="sr-only">{ok ? "(충족)" : "(아직)"}</span>
    </li>
  );
}

export function AccountForm() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [usernameTouched, setUsernameTouched] = useState(false);
  const [confirmTouched, setConfirmTouched] = useState(false);
  const [saving, setSaving] = useState(false);
  const [moving, startMoving] = useTransition();
  const [error, setError] = useState<string | null>(null);

  // 1단계(약관 동의)를 건너뛰고 들어오면 1단계로 돌려보낸다. 적어 둔 아이디가 있으면 다시 채운다
  useEffect(() => {
    const draft = readDraft();
    if (!hasRequiredAgreements(draft)) {
      router.replace(withNext("/signup/terms", readNextParam()));
      return;
    }
    if (draft.username) setUsername((prev) => prev || draft.username!);
  }, [router]);

  const normalized = normalizeUsername(username);
  const usernameValid = isValidUsername(normalized);
  // 같은 아이디가 이 기기에 이미 있는지 (입력한 뒤에만 확인하므로 서버에서 미리 그릴 때는 건너뛴다)
  const taken = usernameValid && isUsernameTaken(normalized);
  const checks = passwordChecks(password);
  const passwordValid = checks.length && checks.letterAndNumber;
  const confirmMatches = confirm.length > 0 && confirm === password;
  const busy = saving || moving;
  const canSubmit = usernameValid && !taken && passwordValid && confirmMatches && !busy;

  let usernameError: string | null = null;
  if (taken) usernameError = "이 기기에서 이미 쓰고 있는 아이디예요.";
  else if (usernameTouched && username && !usernameValid) usernameError = `아이디는 ${USERNAME_RULE}로 만들어 주세요.`;

  const confirmError = confirmTouched && confirm && !confirmMatches ? "비밀번호가 서로 달라요." : null;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;
    setSaving(true);
    setError(null);
    try {
      // 비밀번호는 되돌릴 수 없는 값(해시)으로 바꿔서 적어 둔다
      await saveCredentialsToDraft(normalized, password);
    } catch (err) {
      setSaving(false);
      setError(err instanceof Error ? err.message : "저장하지 못했어요. 다시 시도해 주세요.");
      return;
    }
    setSaving(false);
    startMoving(() => router.push(withNext("/signup/profile", readNextParam())));
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-1 flex-col">
      <StepHeader step={2} onBack={() => router.push(withNext("/signup/terms", readNextParam()))} />
      <h1 className="mt-4 text-2xl font-bold leading-snug">
        로그인에 쓸
        <br />
        아이디와 비밀번호를 만들어요
      </h1>
      <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
        이 앱에서만 쓰는 계정이에요. 계정 정보는 이 기기에 저장돼요.
      </p>

      <div className="mt-8 flex flex-col gap-6">
        <TextField
          id="username"
          label="아이디"
          value={username}
          // 아이디는 소문자로만 쓰므로 대문자를 입력해도 소문자로 바꾼다
          onChange={(e) => setUsername(e.target.value.toLowerCase())}
          onBlur={() => setUsernameTouched(true)}
          autoComplete="username"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          maxLength={20}
          error={usernameError}
          hint={usernameValid ? "쓸 수 있는 아이디예요." : USERNAME_RULE}
        />
        <PasswordField
          id="password"
          label="비밀번호"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
          maxLength={64}
          hint={
            <ul className="flex gap-4">
              <RuleItem ok={checks.length}>8자 이상</RuleItem>
              <RuleItem ok={checks.letterAndNumber}>영문과 숫자 섞기</RuleItem>
            </ul>
          }
        />
        <PasswordField
          id="password-confirm"
          label="비밀번호 확인"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          onBlur={() => setConfirmTouched(true)}
          autoComplete="new-password"
          maxLength={64}
          error={confirmError}
          hint={
            confirmMatches ? (
              <ul>
                <RuleItem ok>비밀번호가 같아요</RuleItem>
              </ul>
            ) : (
              "한 번 더 똑같이 입력해 주세요."
            )
          }
        />
      </div>

      <div className="min-h-8 flex-1" />
      {error && (
        <div className="mb-2">
          <FormError alert>{error}</FormError>
        </div>
      )}
      <NextButton type="submit" disabled={!canSubmit}>
        {busy ? "저장하는 중…" : "다음"}
      </NextButton>
    </form>
  );
}
