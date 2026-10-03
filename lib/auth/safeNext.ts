/** ?next= 값이 우리 앱 안의 주소일 때만 쓴다(다른 사이트로 보내는 악용 방지). */
export function safeNext(value: string | null): string {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "/";
}
