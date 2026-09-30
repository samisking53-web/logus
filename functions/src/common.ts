// 여러 함수가 함께 쓰는 확인·검증 도우미
import { randomBytes } from "node:crypto";
import { HttpsError, type CallableRequest } from "firebase-functions/v2/https";

/** 로그인하지 않았으면 거부하고, 로그인한 사용자의 uid를 돌려준다. */
export function requireAuth(request: CallableRequest<unknown>): string {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요해요.");
  }
  return request.auth.uid;
}

/** 요청 데이터가 객체인지 확인한다. */
export function requireObject(data: unknown): Record<string, unknown> {
  if (typeof data !== "object" || data === null || Array.isArray(data)) {
    throw new HttpsError("invalid-argument", "요청 형식이 올바르지 않아요.");
  }
  return data as Record<string, unknown>;
}

/** 앞뒤 공백을 지운 문자열이 길이 제한 안에 있는지 확인한다. */
export function requireString(
  value: unknown,
  field: string,
  minLen: number,
  maxLen: number,
): string {
  if (typeof value !== "string") {
    throw new HttpsError("invalid-argument", `${field} 값이 올바르지 않아요.`);
  }
  const trimmed = value.trim();
  if (trimmed.length < minLen || trimmed.length > maxLen) {
    throw new HttpsError("invalid-argument", `${field}은(는) ${minLen}~${maxLen}자로 입력해 주세요.`);
  }
  return trimmed;
}

/** 여행 날짜는 현지 달력 날짜라서 "YYYY-MM-DD" 문자열로 저장한다. */
export function requireDate(value: unknown, field: string): string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new HttpsError("invalid-argument", `${field}은(는) YYYY-MM-DD 형식이어야 해요.`);
  }
  // 2월 31일처럼 없는 날짜를 거른다.
  const date = new Date(`${value}T00:00:00Z`);
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) {
    throw new HttpsError("invalid-argument", `${field}이(가) 없는 날짜예요.`);
  }
  return value;
}

/** 알림 주기: 1·2·3시간 또는 받지 않음(null) */
export function requireNotifyInterval(value: unknown): 1 | 2 | 3 | null {
  if (value === undefined || value === null) return null;
  if (value === 1 || value === 2 || value === 3) return value;
  throw new HttpsError("invalid-argument", "알림 주기는 1·2·3시간 중에서 골라 주세요.");
}

/** 초대 코드: 추측하기 어려운 16자 무작위 문자열 (영문 대소문자·숫자·-·_) */
export function createInviteCode(): string {
  return randomBytes(12).toString("base64url");
}

/** 초대 코드 형식 확인 (12자 이상) */
export function requireInviteCode(value: unknown): string {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{12,64}$/.test(value)) {
    throw new HttpsError("invalid-argument", "초대 코드가 올바르지 않아요.");
  }
  return value;
}
