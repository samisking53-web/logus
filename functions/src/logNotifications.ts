// 새 기록 알림(2026-11-09 팀 그림): 구성원이 기록을 올리면 다른 구성원의 알림함에 "○○ 님이 기록을 올렸어요"를 넣는다.
// 폰 알림(푸시)은 보내지 않고 앱 안 알림 목록에만 남긴다(2026-11-09 팀 결정). 여정을 저장(마침)한 사람은 빼고 보낸다.
// 내용: "우리의 포르투 · 11:20 시간대" — N01 기록 보기와 같은 시간대(여정을 만든 시각에서 공통 알림 간격마다, 찍은 곳 시간대 기준).
import { Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { db } from "./admin";
import { addToInboxes } from "./notifications";

/** 그 시간대(timeZone)에서 그 순간이 그날 몇 분째인지(0~1439). 시간대 이름이 틀리면 null */
export function minuteOfDayIn(timeZone: string, millis: number): number | null {
  try {
    const parts = new Intl.DateTimeFormat("en-GB", {
      timeZone, hour: "2-digit", minute: "2-digit", hourCycle: "h23",
    }).formatToParts(new Date(millis));
    const hour = Number(parts.find((p) => p.type === "hour")?.value);
    const minute = Number(parts.find((p) => p.type === "minute")?.value);
    return Number.isFinite(hour) && Number.isFinite(minute) ? (hour % 24) * 60 + minute : null;
  } catch {
    return null;
  }
}

/** 560 → "09:20". 0보다 작거나 24시를 넘으면 그날 안의 시각으로 바꾼다(-40 → "23:20") */
export function minuteLabel(minute: number): string {
  const m = ((minute % 1440) + 1440) % 1440;
  return `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
}

/**
 * 기록이 들어가는 N01 시간대의 시작 시각 "11:20"(앱 groupByWindows 와 같은 계산).
 * 시작점 = 여정을 만든 시각(10분 단위로 내림)에서 간격(시간)마다. 만든 시각을 모르면 null.
 */
export function windowStartLabel(
  capturedMillis: number, anchorMillis: number | null, intervalHours: number, timeZone: string,
): string | null {
  if (anchorMillis === null) return null;
  const length = Math.min(Math.max(intervalHours, 1), 24) * 60;
  const captured = minuteOfDayIn(timeZone, capturedMillis);
  const anchorMinute = minuteOfDayIn(timeZone, anchorMillis);
  if (captured === null || anchorMinute === null) return null;
  const phase = (((Math.floor(anchorMinute / 10) * 10) % length) + length) % length;
  return minuteLabel(phase + Math.floor((captured - phase) / length) * length);
}

/**
 * 새 기록 하나를 다른 구성원의 알림함에 알린다(트리거 본체, 테스트에서 바로 부른다). 돌려주는 값: 알린 사람 수.
 * 알림 ID 는 log_{기록 ID}라서 같은 기록으로 두 번 생기지 않는다.
 */
export async function notifyNewLog(journeyId: string, logId: string, log: Record<string, unknown>): Promise<number> {
  const authorId = log.authorId;
  if (typeof authorId !== "string") return 0;
  const journeyRef = db.collection("journeys").doc(journeyId);
  const [journey, members, author] = await Promise.all([
    journeyRef.get(),
    journeyRef.collection("members").get(),
    db.collection("users").doc(authorId).get(),
  ]);
  if (!journey.exists) return 0;
  const memberIds = (journey.get("memberIds") as string[] | undefined) ?? [];
  const targets = members.docs
    .filter((m) => m.id !== authorId && memberIds.includes(m.id) && m.get("finishedAt") == null)
    .map((m) => m.id);
  if (targets.length === 0) return 0;

  const interval = members.docs.find((m) => m.id === journey.get("ownerId"))?.get("notifyIntervalHours");
  const hours = interval === 1 || interval === 2 || interval === 3 ? interval : 2; // 알림 받지 않기면 N01 처럼 2시간
  const createdAt = journey.get("createdAt");
  const capturedAt = log.capturedAt instanceof Timestamp ? log.capturedAt.toMillis() : Date.now();
  const timeZone = typeof log.capturedTz === "string" ? log.capturedTz : "Asia/Seoul";
  const window = windowStartLabel(
    capturedAt, createdAt instanceof Timestamp ? createdAt.toMillis() : null, hours, timeZone,
  );
  const journeyName = (journey.get("name") as string | undefined) ?? "우리 여정";
  const nickname = (author.get("nickname") as string | undefined) ?? "구성원";
  await addToInboxes(targets, {
    type: "newLog",
    title: `${nickname} 님이 기록을 올렸어요`,
    body: window ? `${journeyName} · ${window} 시간대` : journeyName,
    journeyId,
  }, `log_${logId}`);
  return targets.length;
}

export const onLogCreated = onDocumentCreated("journeys/{journeyId}/logs/{logId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  await notifyNewLog(event.params.journeyId, event.params.logId, data);
});
