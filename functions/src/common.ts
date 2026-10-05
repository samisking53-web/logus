// 여러 함수가 함께 쓰는 확인·검증 도우미
import { randomInt } from "node:crypto";
import { HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { db } from "./admin";

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

/**
 * 초대 코드: 영문 대문자·숫자 6자리 (예: K7PQ2M)
 * 헷갈리는 글자(0·O·1·I)를 빼서 32가지 글자 × 6자리 = 약 10억 가지.
 * 짧은 대신 7일 뒤 만료, 로그인한 사람만 사용(joinJourney), 한 사람당 하루 입력 횟수 제한으로 보완한다.
 */
export const INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
export const INVITE_CODE_LENGTH = 6;
/** 초대 코드를 쓸 수 있는 기간(일) */
export const INVITE_VALID_DAYS = 7;
/** 한 사람이 하루(UTC)에 초대 코드를 입력할 수 있는 횟수 */
export const INVITE_ATTEMPTS_PER_DAY = 20;

export function createInviteCode(): string {
  let code = "";
  for (let i = 0; i < INVITE_CODE_LENGTH; i++) {
    code += INVITE_CODE_ALPHABET[randomInt(INVITE_CODE_ALPHABET.length)];
  }
  return code;
}

/** 초대 코드 형식 확인. 앞뒤 공백을 지우고 대문자로 바꿔서 본다(소문자로 입력해도 된다) */
export function requireInviteCode(value: unknown): string {
  const code = typeof value === "string" ? value.trim().toUpperCase() : "";
  if (!/^[A-HJ-NP-Z2-9]{6}$/.test(code)) {
    throw new HttpsError("invalid-argument", "초대 코드는 영문·숫자 6자리예요.");
  }
  return code;
}

/**
 * 초대 코드 입력 횟수를 센다(맞든 틀리든, 코드 확인·참여 모두). 오늘(UTC) 횟수를 넘으면 거부한다.
 * inviteAttempts/{uid}: date("YYYY-MM-DD"), count — 서버만 읽고 쓴다(보안 규칙 기본 거부).
 */
export async function countInviteAttempt(uid: string): Promise<void> {
  const ref = db.collection("inviteAttempts").doc(uid);
  const today = new Date().toISOString().slice(0, 10);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = snap.get("date") === today ? ((snap.get("count") as number | undefined) ?? 0) : 0;
    if (count >= INVITE_ATTEMPTS_PER_DAY) {
      throw new HttpsError("resource-exhausted", "오늘은 초대 코드를 너무 많이 입력했어요. 내일 다시 시도해 주세요.");
    }
    tx.set(ref, { date: today, count: count + 1 });
  });
}
