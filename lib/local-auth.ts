// 실습용 "앱 전용 계정" (서버 없음)
// - 계정은 이 브라우저의 localStorage에만 저장한다. 다른 기기·다른 브라우저에서는 이 계정이 없고,
//   브라우저 데이터를 지우면 계정도 사라진다.
// - 비밀번호는 그대로 저장하지 않고, 되돌릴 수 없는 값(PBKDF2-SHA256 해시)으로 바꿔 저장한다.
// - 로그인 상태는 쿠키(lib/session-cookie.ts)에 계정 번호를 넣어 표시한다. proxy.ts가 이 쿠키를 보고 길을 안내한다.
// 나중에 서버(Supabase)를 붙이면 이 파일 대신 Supabase Auth를 쓴다. (docs/login-setup.md 참고)
import { SESSION_COOKIE } from "@/lib/session-cookie";
import { AGREEMENTS, REQUIRED_AGREEMENT_KEYS, TERMS_VERSION, type AgreementKey } from "@/lib/terms";

const ACCOUNTS_KEY = "logus-accounts-v1"; // localStorage: 가입한 계정 목록
const DRAFT_KEY = "logus-signup-draft"; // sessionStorage: 가입하는 동안 단계별로 적어 둔 내용
const PBKDF2_ITERATIONS = 100_000;
const SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 365; // 로그인 유지 1년

export type AgreementRecord = {
  key: AgreementKey;
  version: string; // 동의한 약관 버전
  agreed: boolean;
  agreedAt: string; // 동의한 시각 (ISO 형식)
};

export type LocalAccount = {
  id: string;
  username: string; // 아이디
  passwordHash: string; // 비밀번호를 바꾼 값 (원래 비밀번호는 저장하지 않는다)
  salt: string; // 해시를 만들 때 섞은 무작위 값
  nickname: string;
  createdAt: string;
  agreements: AgreementRecord[];
};

export type SignupDraft = {
  agreements?: AgreementRecord[]; // 1단계
  username?: string; // 2단계
  passwordHash?: string; // 2단계
  salt?: string; // 2단계
};

// ─────────────────────────────────────────────
// 규칙: 아이디·비밀번호·닉네임
// ─────────────────────────────────────────────
export const USERNAME_RULE = "영문 소문자로 시작, 영문 소문자·숫자·밑줄(_) 4~20자";
export const NICKNAME_MAX_LENGTH = 12;

export function normalizeUsername(raw: string) {
  return raw.trim().toLowerCase();
}

export function isValidUsername(username: string) {
  return /^[a-z][a-z0-9_]{3,19}$/.test(username);
}

export function passwordChecks(password: string) {
  return {
    length: password.length >= 8 && password.length <= 64,
    letterAndNumber: /[A-Za-z]/.test(password) && /\d/.test(password),
  };
}

// 글자 수 세기. 한글 한 글자, 이모지 하나를 모두 1로 센다
export function countChars(text: string) {
  return Array.from(text).length;
}

// ─────────────────────────────────────────────
// 계정 목록 (localStorage)
// ─────────────────────────────────────────────
function loadAccounts(): LocalAccount[] {
  try {
    const list = JSON.parse(localStorage.getItem(ACCOUNTS_KEY) ?? "[]");
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function saveAccounts(accounts: LocalAccount[]) {
  localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(accounts));
}

export function isUsernameTaken(username: string) {
  const normalized = normalizeUsername(username);
  return loadAccounts().some((a) => a.username === normalized);
}

// ─────────────────────────────────────────────
// 비밀번호 해시 (브라우저에 들어 있는 Web Crypto 사용)
// ─────────────────────────────────────────────
function toBase64(bytes: Uint8Array) {
  return btoa(String.fromCharCode(...bytes));
}

function fromBase64(text: string) {
  return Uint8Array.from(atob(text), (c) => c.charCodeAt(0));
}

async function hashPassword(password: string, saltBase64?: string) {
  if (!globalThis.crypto?.subtle) {
    // Web Crypto는 https 주소(또는 내 컴퓨터의 localhost)에서만 쓸 수 있다
    throw new Error("https 주소에서만 비밀번호를 만들 수 있어요.");
  }
  const salt = saltBase64 ? fromBase64(saltBase64) : crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, [
    "deriveBits",
  ]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations: PBKDF2_ITERATIONS },
    key,
    256,
  );
  return { hash: toBase64(new Uint8Array(bits)), salt: toBase64(salt) };
}

// ─────────────────────────────────────────────
// 가입하는 동안 적어 두는 내용 (sessionStorage: 탭을 닫으면 지워진다)
// ─────────────────────────────────────────────
export function readDraft(): SignupDraft {
  try {
    return JSON.parse(sessionStorage.getItem(DRAFT_KEY) ?? "{}");
  } catch {
    return {};
  }
}

const STORAGE_ERROR = "이 브라우저에 저장하지 못했어요. 시크릿(비공개) 모드라면 일반 모드에서 다시 해 주세요.";

function updateDraft(patch: SignupDraft) {
  try {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ ...readDraft(), ...patch }));
  } catch {
    throw new Error(STORAGE_ERROR);
  }
}

function clearDraft() {
  sessionStorage.removeItem(DRAFT_KEY);
}

export function hasRequiredAgreements(draft: SignupDraft) {
  const agreed = new Set(draft.agreements?.filter((a) => a.agreed).map((a) => a.key));
  return REQUIRED_AGREEMENT_KEYS.every((key) => agreed.has(key));
}

export function hasCredentials(draft: SignupDraft) {
  return Boolean(draft.username && draft.passwordHash && draft.salt);
}

// 1단계: 체크한 항목과 안 한 항목(선택 항목)을 약관 버전·동의 시각과 함께 적어 둔다
export function saveAgreementsToDraft(checked: Record<AgreementKey, boolean>) {
  const agreedAt = new Date().toISOString();
  updateDraft({
    agreements: AGREEMENTS.map((a) => ({ key: a.key, version: TERMS_VERSION, agreed: checked[a.key], agreedAt })),
  });
}

// 2단계: 아이디와 비밀번호 해시를 적어 둔다 (원래 비밀번호는 적지 않는다)
export async function saveCredentialsToDraft(username: string, password: string) {
  const { hash, salt } = await hashPassword(password);
  updateDraft({ username: normalizeUsername(username), passwordHash: hash, salt });
}

// ─────────────────────────────────────────────
// 가입 완료·로그인·로그아웃
// ─────────────────────────────────────────────
type CreateAccountResult =
  | { ok: true; account: LocalAccount }
  | { ok: false; step: "terms" | "account" | "profile"; message: string };

// 3단계: 적어 둔 내용과 닉네임으로 계정을 만들고 바로 로그인한다
export function createAccount(nickname: string): CreateAccountResult {
  const draft = readDraft();
  if (!hasRequiredAgreements(draft)) {
    return { ok: false, step: "terms", message: "필수 약관에 먼저 동의해 주세요." };
  }
  if (!hasCredentials(draft)) {
    return { ok: false, step: "account", message: "아이디와 비밀번호를 먼저 만들어 주세요." };
  }
  const name = nickname.trim();
  if (countChars(name) < 1 || countChars(name) > NICKNAME_MAX_LENGTH) {
    return { ok: false, step: "profile", message: `닉네임은 1~${NICKNAME_MAX_LENGTH}글자로 정해 주세요.` };
  }

  const accounts = loadAccounts();
  if (accounts.some((a) => a.username === draft.username)) {
    return { ok: false, step: "account", message: "그새 누군가 같은 아이디로 가입했어요. 다른 아이디를 골라 주세요." };
  }

  const account: LocalAccount = {
    id: crypto.randomUUID(),
    username: draft.username!,
    passwordHash: draft.passwordHash!,
    salt: draft.salt!,
    nickname: name,
    createdAt: new Date().toISOString(),
    agreements: draft.agreements!,
  };
  try {
    saveAccounts([...accounts, account]);
  } catch {
    return { ok: false, step: "profile", message: STORAGE_ERROR };
  }
  clearDraft();
  setSessionCookie(account.id);
  return { ok: true, account };
}

// 아이디·비밀번호가 맞으면 로그인하고 계정을 돌려준다. 틀리면 null
export async function signIn(username: string, password: string) {
  const account = loadAccounts().find((a) => a.username === normalizeUsername(username));
  if (!account) return null;
  const { hash } = await hashPassword(password, account.salt);
  if (hash !== account.passwordHash) return null;
  setSessionCookie(account.id);
  return account;
}

export function signOut() {
  document.cookie = `${SESSION_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax${secureFlag()}`;
}

// 지금 로그인한 계정 (없으면 null)
export function getCurrentAccount() {
  const id = readSessionCookie();
  if (!id) return null;
  return loadAccounts().find((a) => a.id === id) ?? null;
}

// 이 기기에 있는 계정의 닉네임 (없으면 null). 여정 구성원·기록한 사람의 이름을 보여줄 때 쓴다
export function findNickname(accountId: string) {
  return loadAccounts().find((a) => a.id === accountId)?.nickname ?? null;
}

// 로그인 유지 기간(1년)을 오늘부터 다시 센다. 앱을 열 때마다 부른다 (components/account-check.tsx).
// 아이폰 Safari는 화면 코드가 만든 쿠키를 최대 7일만 남겨 두므로, 이렇게 다시 써 두어야
// 일주일에 한 번 이상 앱을 여는 사람이 로그인 화면을 다시 보지 않는다
export function extendSession(account: LocalAccount) {
  setSessionCookie(account.id);
}

// ─────────────────────────────────────────────
// 로그인 쿠키
// ─────────────────────────────────────────────
function secureFlag() {
  return window.location.protocol === "https:" ? "; Secure" : "";
}

function setSessionCookie(accountId: string) {
  document.cookie = `${SESSION_COOKIE}=${encodeURIComponent(accountId)}; Path=/; Max-Age=${SESSION_MAX_AGE_SECONDS}; SameSite=Lax${secureFlag()}`;
}

function readSessionCookie() {
  const found = document.cookie.split("; ").find((c) => c.startsWith(`${SESSION_COOKIE}=`));
  return found ? decodeURIComponent(found.slice(SESSION_COOKIE.length + 1)) : null;
}
