"use client";

// 가입 1단계: 약관 동의. 필수 항목을 모두 체크해야 "다음" 버튼이 켜진다.
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { ChevronRight, FileText } from "lucide-react";
import { Checkbox } from "@/components/ui/checkbox";
import { StepHeader } from "@/components/onboarding/step-header";
import { NextButton } from "@/components/onboarding/next-button";
import { createClient } from "@/lib/supabase/client";
import { readNextParam, withNext } from "@/lib/onboarding";
import { AGREEMENTS, TERMS_VERSION, type AgreementKey } from "@/lib/terms";

// 체크박스 모양: 체크 전에는 회색 테두리, 체크하면 보라색으로 채워진다
const checkboxClass =
  "size-6 rounded-md border-2 border-muted-foreground data-[state=checked]:border-brand data-[state=checked]:bg-brand data-[state=checked]:text-brand-foreground";

const NONE_CHECKED = Object.fromEntries(AGREEMENTS.map((a) => [a.key, false])) as Record<
  AgreementKey,
  boolean
>;

export function TermsForm() {
  const router = useRouter();
  const [checked, setChecked] = useState(NONE_CHECKED);
  const [saving, setSaving] = useState(false);
  // 다음 화면으로 넘어가는 동안 true. 넘어가고 나면 저절로 false가 된다
  // (Next.js는 떠난 화면을 숨겨서 보관하므로, 뒤로 돌아왔을 때 버튼이 '저장하는 중'에 멈추지 않게 한다)
  const [moving, startMoving] = useTransition();
  const [error, setError] = useState<string | null>(null);
  const busy = saving || moving;

  const allChecked = AGREEMENTS.every((a) => checked[a.key]);
  const requiredChecked = AGREEMENTS.filter((a) => a.required).every((a) => checked[a.key]);

  function setAll(value: boolean) {
    setChecked(Object.fromEntries(AGREEMENTS.map((a) => [a.key, value])) as Record<AgreementKey, boolean>);
  }

  // 1단계에서 뒤로 가면 로그인을 취소하고 로그인 화면으로 돌아간다
  async function goBack() {
    await createClient().auth.signOut();
    router.replace("/login");
  }

  async function submit() {
    setSaving(true);
    setError(null);
    const supabase = createClient();
    const {
      data: { user },
    } = await supabase.auth.getUser();
    if (!user) {
      router.replace("/login");
      return;
    }

    // 체크한 항목과 안 한 항목(선택 항목)을 모두 약관 버전과 함께 저장한다. 동의 시각은 DB가 채운다
    const { error } = await supabase.from("user_agreements").insert(
      AGREEMENTS.map((a) => ({
        user_id: user.id,
        agreement: a.key,
        version: TERMS_VERSION,
        agreed: checked[a.key],
      })),
    );
    setSaving(false);
    if (error) {
      setError("저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
      return;
    }
    startMoving(() => router.push(withNext("/onboarding/profile", readNextParam())));
  }

  return (
    <>
      <StepHeader step={1} onBack={goBack} />
      <h1 className="mt-4 text-2xl font-bold leading-snug">
        시작하기 전에
        <br />
        확인할 내용이 있어요
      </h1>
      <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
        로그어스는 사진에 붙은 장소를 지도에 기록해요. 그래서 위치정보 약관 동의가 필요합니다.
      </p>

      <div className="mt-6 flex items-center gap-4 rounded-2xl border border-brand-line bg-brand-soft px-5 py-4">
        <Checkbox
          id="agree-all"
          checked={allChecked}
          onCheckedChange={(value) => setAll(value === true)}
          className={checkboxClass}
        />
        <label htmlFor="agree-all" className="flex-1 text-base font-bold">
          약관 전체에 동의합니다
        </label>
      </div>

      <ul className="mt-2">
        {AGREEMENTS.map((a) => (
          <li key={a.key} className="flex items-center gap-4 border-b py-3 pl-2">
            <Checkbox
              id={a.key}
              checked={checked[a.key]}
              onCheckedChange={(value) => setChecked((prev) => ({ ...prev, [a.key]: value === true }))}
              className={checkboxClass}
            />
            <label htmlFor={a.key} className="flex-1 py-1 text-base">
              <span className={a.required ? "text-brand-strong" : "text-muted-foreground"}>
                ({a.required ? "필수" : "선택"})
              </span>{" "}
              {a.label}
            </label>
            {a.termsSlug && (
              <Link
                href={`/terms/${a.termsSlug}`}
                aria-label={`${a.label} 보기`}
                className="flex size-10 items-center justify-center text-muted-foreground"
              >
                <ChevronRight className="size-5" aria-hidden="true" />
              </Link>
            )}
          </li>
        ))}
        <li>
          <Link href="/terms/privacy" className="flex items-center gap-4 py-3 pl-2 text-muted-foreground">
            <FileText className="size-5" aria-hidden="true" />
            <span className="flex-1 py-1 text-base">개인정보 처리방침 확인</span>
            <span className="flex size-10 items-center justify-center">
              <ChevronRight className="size-5" aria-hidden="true" />
            </span>
          </Link>
        </li>
      </ul>

      <div className="min-h-8 flex-1" />
      <p className="text-xs leading-relaxed text-muted-foreground">
        동의한 시각과 약관 버전을 함께 저장해 두어, 나중에 어떤 내용에 동의했는지 확인할 수 있어요.
      </p>
      {error && (
        <p role="alert" className="mt-2 text-sm text-destructive">
          {error}
        </p>
      )}
      <NextButton className="mt-3" disabled={!requiredChecked || busy} onClick={submit}>
        {!requiredChecked ? "필수 항목에 동의해 주세요" : busy ? "저장하는 중…" : "다음"}
      </NextButton>
    </>
  );
}
