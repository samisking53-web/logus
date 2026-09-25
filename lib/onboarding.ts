// 가입 단계 사이에서 "원래 가려던 주소(next)"를 이어 붙이는 도우미.
// 예: 초대 링크로 들어온 사람이 가입을 마치면 그 초대 링크로 돌아간다.
import { safeNextPath } from "@/lib/safe-next";

// 지금 주소의 next 값을 읽는다 (없으면 "/")
export function readNextParam() {
  return safeNextPath(new URLSearchParams(window.location.search).get("next"));
}

// 다음 단계 주소에 next를 붙인다. next가 홈("/")이면 붙이지 않는다
export function withNext(path: string, next: string) {
  return next === "/" ? path : `${path}?next=${encodeURIComponent(next)}`;
}
