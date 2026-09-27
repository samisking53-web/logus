// 로그인·가입을 마친 뒤 "원래 가려던 주소(next)"로 돌려보내는 도우미.
// 예: 초대 링크로 들어온 사람이 가입을 마치면 그 초대 링크로 돌아간다.

// next가 우리 앱 안의 주소인지 확인한다.
// "//다른사이트.com" 같은 값으로 다른 사이트로 빠져나가는 것을 막고, 이상하면 홈("/")으로 보낸다.
// 탭·줄바꿈 같은 보이지 않는 글자도 막는다. 브라우저는 주소에서 이런 글자를 지워서
// "/(탭)/다른사이트.com"이 "//다른사이트.com"이 되기 때문이다
export function safeNextPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || /[\\\s\u0000-\u001f\u007f]/.test(next)) {
    return "/";
  }
  return next;
}

// 지금 주소의 next 값을 읽는다 (없으면 "/"). 브라우저에서만 쓴다
export function readNextParam() {
  return safeNextPath(new URLSearchParams(window.location.search).get("next"));
}

// 다음 화면 주소에 next를 붙인다. next가 홈("/")이면 붙이지 않는다
export function withNext(path: string, next: string) {
  return next === "/" ? path : `${path}?next=${encodeURIComponent(next)}`;
}
