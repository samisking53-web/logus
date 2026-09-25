// 가입 1단계에서 동의받는 항목과 약관 정보.
// 약관 내용이 바뀌면 TERMS_VERSION을 올린다. 동의할 때 이 버전이 DB(user_agreements)에 함께 저장된다.
export const TERMS_VERSION = "2026-09-25";

export type AgreementKey = "age_14" | "service_terms" | "location_terms" | "new_log_notice";
export type TermsSlug = "service" | "location" | "privacy";

// 항목을 바꾸면 supabase/migrations의 user_agreements 검사 조건(check)도 함께 바꾼다
export const AGREEMENTS: {
  key: AgreementKey;
  label: string;
  required: boolean;
  termsSlug?: TermsSlug; // 있으면 오른쪽 › 를 눌러 약관 본문을 볼 수 있다
}[] = [
  { key: "age_14", label: "만 14세 이상입니다", required: true },
  { key: "service_terms", label: "서비스 이용약관", required: true, termsSlug: "service" },
  { key: "location_terms", label: "위치기반서비스 이용약관", required: true, termsSlug: "location" },
  { key: "new_log_notice", label: "새 기록 알림 받기", required: false },
];

export const REQUIRED_AGREEMENT_KEYS = AGREEMENTS.filter((a) => a.required).map((a) => a.key);

export const TERMS_DOCUMENTS: Record<TermsSlug, { title: string }> = {
  service: { title: "서비스 이용약관" },
  location: { title: "위치기반서비스 이용약관" },
  privacy: { title: "개인정보 처리방침" },
};
