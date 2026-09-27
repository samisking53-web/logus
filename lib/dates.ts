// 날짜 도우미. 여행 기간 같은 '날짜'는 "2026-09-24" 모양의 글자(날짜 키)로 다룬다.
// 날짜 키는 폰의 현지 날짜 기준이고, 글자 그대로 크기 비교("2026-09-24" < "2026-09-26")가 된다.

export const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

function pad(n: number) {
  return String(n).padStart(2, "0");
}

export function toDateKey(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

// 오늘 날짜 키 (폰의 현지 날짜)
export function todayKey() {
  return toDateKey(new Date());
}

export function fromDateKey(key: string) {
  const [y, m, d] = key.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function isDateKey(value: unknown): value is string {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value) && toDateKey(fromDateKey(value)) === value;
}

// 두 날짜 키 사이의 일 수 (같은 날이면 0)
export function daysBetween(fromKey: string, toKey: string) {
  const ms = fromDateKey(toKey).getTime() - fromDateKey(fromKey).getTime();
  return Math.round(ms / 86_400_000);
}

// "9.24" (올해가 아니면 "2027.1.2")
export function formatDot(key: string, thisYear = new Date().getFullYear()) {
  const d = fromDateKey(key);
  const md = `${d.getMonth() + 1}.${d.getDate()}`;
  return d.getFullYear() === thisYear ? md : `${d.getFullYear()}.${md}`;
}

// "9월 24일" (올해가 아니면 "2027년 1월 2일")
export function formatKorean(key: string, thisYear = new Date().getFullYear()) {
  const d = fromDateKey(key);
  const md = `${d.getMonth() + 1}월 ${d.getDate()}일`;
  return d.getFullYear() === thisYear ? md : `${d.getFullYear()}년 ${md}`;
}

// 여행 기간 한 줄: "9.24 ~ 9.26", 하루짜리면 "9.24"
export function formatRangeDot(start: string, end: string) {
  return start === end ? formatDot(start) : `${formatDot(start)} ~ ${formatDot(end)}`;
}

// 마이로그 카드의 짧은 기간: "9.24~26", 달이 바뀌면 "9.28~10.2", 하루짜리면 "9.24"
// 올해가 아닌 여정은 연도를 붙인다: "2025.8.10~12", "2025.12.30~2026.1.2"
export function formatRangeShort(start: string, end: string, thisYear = new Date().getFullYear()) {
  if (start === end) return formatDot(start, thisYear);
  const s = fromDateKey(start);
  const e = fromDateKey(end);
  const inThisYear = s.getFullYear() === thisYear && e.getFullYear() === thisYear;
  const head = `${inThisYear ? "" : `${s.getFullYear()}.`}${s.getMonth() + 1}.${s.getDate()}`;
  let tail = `${e.getDate()}`;
  if (s.getFullYear() !== e.getFullYear()) tail = `${e.getFullYear()}.${e.getMonth() + 1}.${e.getDate()}`;
  else if (s.getMonth() !== e.getMonth()) tail = `${e.getMonth() + 1}.${e.getDate()}`;
  return `${head}~${tail}`;
}

// "2박 3일", 하루짜리면 "당일"
export function formatNights(start: string, end: string) {
  const nights = daysBetween(start, end);
  return nights === 0 ? "당일" : `${nights}박 ${nights + 1}일`;
}

// 달력 한 달치 칸. 한 줄에 7칸(일~토)이고, 1일 앞의 빈칸은 null
export function monthCells(year: number, monthIndex: number): (string | null)[] {
  const firstWeekday = new Date(year, monthIndex, 1).getDay();
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
  const cells: (string | null)[] = Array(firstWeekday).fill(null);
  for (let day = 1; day <= daysInMonth; day++) cells.push(toDateKey(new Date(year, monthIndex, day)));
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}
