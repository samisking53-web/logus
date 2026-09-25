"use client";

// 가입 3단계: 그룹에서 보일 닉네임 정하기. "가입하기"를 누르면 계정이 만들어지고 바로 로그인된다.
import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";
import { Info } from "lucide-react";
import { FormError } from "@/components/form-error";
import { InitialAvatar } from "@/components/signup/initial-avatar";
import { NextButton } from "@/components/signup/next-button";
import { StepHeader } from "@/components/signup/step-header";
import { TextField } from "@/components/signup/text-field";
import {
  NICKNAME_MAX_LENGTH,
  countChars,
  createAccount,
  hasCredentials,
  hasRequiredAgreements,
  readDraft,
} from "@/lib/local-auth";
import { readNextParam, withNext } from "@/lib/next-param";

export function ProfileForm() {
  const router = useRouter();
  const [nickname, setNickname] = useState("");
  const [error, setError] = useState<string | null>(null);
  // 다음 화면으로 넘어가는 동안 true (terms-form.tsx와 같은 이유)
  const [moving, startMoving] = useTransition();

  // 앞 단계를 건너뛰고 들어오면 빠진 단계로 돌려보낸다
  useEffect(() => {
    const draft = readDraft();
    if (!hasRequiredAgreements(draft)) {
      router.replace(withNext("/signup/terms", readNextParam()));
    } else if (!hasCredentials(draft)) {
      router.replace(withNext("/signup/account", readNextParam()));
    }
  }, [router]);

  const trimmed = nickname.trim();
  const length = countChars(trimmed);
  const tooLong = length > NICKNAME_MAX_LENGTH;
  const canSubmit = length > 0 && !tooLong && !moving;

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;
    setError(null);
    const result = createAccount(trimmed);
    if (!result.ok) {
      setError(result.message);
      // 약관이나 아이디 단계에 문제가 있으면 그 단계로 돌려보낸다
      if (result.step !== "profile") {
        const path = result.step === "terms" ? "/signup/terms" : "/signup/account";
        startMoving(() => router.replace(withNext(path, readNextParam())));
      }
      return;
    }
    startMoving(() => router.push(withNext("/signup/welcome", readNextParam())));
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-1 flex-col">
      <StepHeader step={3} onBack={() => router.push(withNext("/signup/account", readNextParam()))} />
      <h1 className="mt-4 text-2xl font-bold leading-snug">
        그룹에서 보일
        <br />
        이름을 정해요
      </h1>
      <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
        여정을 함께하는 친구·연인·가족에게 보이는 이름이에요.
      </p>

      <div className="mt-8 flex justify-center">
        <InitialAvatar nickname={trimmed} />
      </div>

      <div className="mt-8">
        <TextField
          id="nickname"
          label="닉네임"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          autoComplete="nickname"
          error={tooLong ? `${NICKNAME_MAX_LENGTH}글자까지 쓸 수 있어요.` : null}
          hint="그룹 멤버에게만 보여요"
          counter={`${length} / ${NICKNAME_MAX_LENGTH}`}
        />
      </div>

      <div className="mt-6 flex gap-3 rounded-2xl bg-brand-soft p-4">
        <Info className="mt-0.5 size-5 shrink-0 text-brand-strong" aria-hidden="true" />
        <div className="text-sm">
          <p className="font-semibold">위치와 사진 권한은 나중에 물어봐요</p>
          <p className="mt-1 text-muted-foreground">첫 기록을 남길 때 무엇에 쓰는지 설명하고 요청해요.</p>
        </div>
      </div>

      <div className="min-h-8 flex-1" />
      {error && (
        <div className="mb-2">
          <FormError alert>{error}</FormError>
        </div>
      )}
      <NextButton type="submit" disabled={!canSubmit}>
        {moving ? "가입하는 중…" : "가입하기"}
      </NextButton>
    </form>
  );
}
