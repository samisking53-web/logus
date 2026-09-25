// 로그인 뒤 돌아갈 주소(next)가 우리 앱 안의 주소인지 확인한다.
// "//다른사이트.com" 같은 값으로 다른 사이트로 빠져나가는 것을 막고, 이상하면 홈("/")으로 보낸다.
export function safeNextPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.includes("\\")) {
    return "/";
  }
  return next;
}
