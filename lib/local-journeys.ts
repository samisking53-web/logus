// 실습용 여정 저장소 (서버 없음). 계정(lib/local-auth.ts)과 같이 이 브라우저의 localStorage에 저장한다.
// 저장 모양은 CLAUDE.md 데이터 모델의 journeys·journey_members를 따른다. 나중에 Supabase로 옮기기 쉽게 하기 위해서다.
import { isDateKey } from "@/lib/dates";

const JOURNEYS_KEY = "logus-journeys-v1"; // localStorage: 모든 계정의 여정
const DRAFT_KEY = "logus-journey-draft"; // sessionStorage: 새 여정 만들기에서 입력 중인 내용
const AUTO_CAMERA_KEY = "logus-camera-auto-opened-v1"; // localStorage: 시작일에 카메라를 이미 자동으로 연 여정
const FLASH_KEY = "logus-flash"; // sessionStorage: 다음 화면에 한 번 보여줄 안내 문구

export const JOURNEY_NAME_MAX_LENGTH = 20;
export const NOTIFY_INTERVALS = [1, 2, 3] as const;
export type NotifyInterval = (typeof NOTIFY_INTERVALS)[number];
export const DEFAULT_NOTIFY_INTERVAL: NotifyInterval = 2;

export const JOURNEY_STORAGE_ERROR = "이 브라우저에 저장하지 못했어요. 시크릿(비공개) 모드라면 일반 모드에서 다시 해 주세요.";

export type JourneyMember = {
  userId: string;
  role: "owner" | "member";
  notifyIntervalHours: NotifyInterval | null; // null이면 촬영 알림을 받지 않는다
  joinedAt: string;
};

export type Journey = {
  id: string;
  name: string;
  city: string;
  country: string; // 목록에서 고르지 않고 직접 쓴 도시는 빈 글자
  startDate: string; // "2026-09-24" (현지 날짜)
  endDate: string;
  ownerId: string;
  inviteCode: string; // 초대 링크 /invite/[code]에 쓸 값. 초대 화면은 아직 없다
  createdAt: string; // UTC 시각
  members: JourneyMember[];
};

export type JourneyStatus = "upcoming" | "ongoing" | "past";

// 예정 · 진행 중 · 지난 여정
export function journeyStatus(journey: Pick<Journey, "startDate" | "endDate">, today: string): JourneyStatus {
  if (today < journey.startDate) return "upcoming";
  if (today > journey.endDate) return "past";
  return "ongoing";
}

// ─────────────────────────────────────────────
// 여정 목록 (localStorage)
// ─────────────────────────────────────────────
function loadAll(): Journey[] {
  try {
    const list = JSON.parse(localStorage.getItem(JOURNEYS_KEY) ?? "[]");
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

// 내가 구성원인 여정. 최근에 만든 여정이 앞에 온다
export function listMyJourneys(userId: string) {
  return loadAll()
    .filter((j) => j.members?.some((m) => m.userId === userId))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export function getMyJourney(userId: string, journeyId: string) {
  return listMyJourneys(userId).find((j) => j.id === journeyId) ?? null;
}

// 오늘 진행 중인 여정 (여러 개면 가장 최근에 만든 것)
export function findOngoingJourney(userId: string, today: string) {
  return listMyJourneys(userId).find((j) => journeyStatus(j, today) === "ongoing") ?? null;
}

// 초대 코드: 헷갈리는 글자(0·o·1·l·i)를 뺀 8글자
function makeInviteCode(taken: Set<string>) {
  const alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
  for (;;) {
    const bytes = crypto.getRandomValues(new Uint8Array(8));
    const code = Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
    if (!taken.has(code)) return code;
  }
}

export type NewJourneyInput = {
  name: string;
  city: string;
  country: string;
  startDate: string;
  endDate: string;
  notifyIntervalHours: NotifyInterval | null;
};

type CreateJourneyResult = { ok: true; journey: Journey } | { ok: false; message: string };

export function createJourney(ownerId: string, input: NewJourneyInput): CreateJourneyResult {
  const name = input.name.trim();
  const city = input.city.trim();
  if (!name || Array.from(name).length > JOURNEY_NAME_MAX_LENGTH) {
    return { ok: false, message: `여정 이름은 1~${JOURNEY_NAME_MAX_LENGTH}글자로 정해 주세요.` };
  }
  if (!city) return { ok: false, message: "도시를 정해 주세요." };
  if (!isDateKey(input.startDate) || !isDateKey(input.endDate) || input.startDate > input.endDate) {
    return { ok: false, message: "여행 기간을 정해 주세요." };
  }

  const all = loadAll();
  const now = new Date().toISOString();
  const journey: Journey = {
    id: crypto.randomUUID(),
    name,
    city,
    country: input.country.trim(),
    startDate: input.startDate,
    endDate: input.endDate,
    ownerId,
    inviteCode: makeInviteCode(new Set(all.map((j) => j.inviteCode))),
    createdAt: now,
    members: [{ userId: ownerId, role: "owner", notifyIntervalHours: input.notifyIntervalHours, joinedAt: now }],
  };
  try {
    localStorage.setItem(JOURNEYS_KEY, JSON.stringify([...all, journey]));
  } catch {
    return { ok: false, message: JOURNEY_STORAGE_ERROR };
  }
  return { ok: true, journey };
}

// ─────────────────────────────────────────────
// 시작일 자동 카메라: 시작일에 앱을 처음 열 때 한 번만 카메라를 연다
// ─────────────────────────────────────────────
function loadAutoOpened(): string[] {
  try {
    const list = JSON.parse(localStorage.getItem(AUTO_CAMERA_KEY) ?? "[]");
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

export function markCameraAutoOpened(journeyId: string) {
  try {
    const list = loadAutoOpened();
    if (!list.includes(journeyId)) localStorage.setItem(AUTO_CAMERA_KEY, JSON.stringify([...list, journeyId]));
  } catch {
    // 저장하지 못해도 카메라는 연다. 다음에 한 번 더 열릴 수 있을 뿐이다
  }
}

// 오늘 시작하는데 아직 카메라를 자동으로 열지 않은 여정. 찾으면 '열었음'으로 표시하고 돌려준다
export function takeStartDayJourney(userId: string, today: string) {
  const opened = new Set(loadAutoOpened());
  const journey = listMyJourneys(userId).find((j) => j.startDate === today && !opened.has(j.id));
  if (journey) markCameraAutoOpened(journey.id);
  return journey ?? null;
}

// ─────────────────────────────────────────────
// 새 여정 만들기에서 입력 중인 내용 (sessionStorage: 달력 화면에 다녀와도 남아 있게)
// ─────────────────────────────────────────────
export type JourneyDraft = {
  name: string;
  cityText: string; // 도시 칸에 보이는 글자
  city: { name: string; country: string } | null; // 목록에서 고른 도시
  startDate: string | null;
  endDate: string | null;
  notifyInterval: NotifyInterval;
  noNotify: boolean; // 촬영 알림 받지 않기
};

export const EMPTY_JOURNEY_DRAFT: JourneyDraft = {
  name: "",
  cityText: "",
  city: null,
  startDate: null,
  endDate: null,
  notifyInterval: DEFAULT_NOTIFY_INTERVAL,
  noNotify: false,
};

export function readJourneyDraft(): JourneyDraft {
  try {
    return { ...EMPTY_JOURNEY_DRAFT, ...JSON.parse(sessionStorage.getItem(DRAFT_KEY) ?? "{}") };
  } catch {
    return EMPTY_JOURNEY_DRAFT;
  }
}

export function saveJourneyDraft(draft: JourneyDraft) {
  try {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  } catch {
    // 임시 저장에 실패해도 화면의 입력은 그대로 남아 있다
  }
}

export function clearJourneyDraft() {
  try {
    sessionStorage.removeItem(DRAFT_KEY);
  } catch {}
}

// ─────────────────────────────────────────────
// 다음 화면에 한 번만 보여줄 안내 문구 (예: 여정 저장 후 홈에서 "저장했어요")
// ─────────────────────────────────────────────
export function setFlashMessage(message: string) {
  try {
    sessionStorage.setItem(FLASH_KEY, message);
  } catch {}
}

export function takeFlashMessage() {
  try {
    const message = sessionStorage.getItem(FLASH_KEY);
    sessionStorage.removeItem(FLASH_KEY);
    return message;
  } catch {
    return null;
  }
}
