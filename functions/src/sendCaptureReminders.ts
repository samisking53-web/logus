// sendCaptureReminders: 촬영 알림(공통 알림). 매시 정각에 진행 중인 여정의 구성원 폰으로 FCM 푸시를 보낸다(2026-10-29 팀 결정).
// - 공통 알림 간격 = 여정을 만든 사람의 멤버 문서 notifyIntervalHours(1|2|3). null 이면 그 여정은 보내지 않는다("촬영 알림 받지 않기").
// - 폰마다 그 폰의 시간대(users/{uid}/devices/{토큰}.timeZone)로 지금 시각을 계산해서,
//   여정 기간(startDate~endDate, 현지 달력 날짜) 안이고 09:00~23:00 사이이며 9시부터 간격마다인 시각일 때만 보낸다
//   (2시간이면 9·11·13·15·17·19·21·23시, 3시간이면 9·12·15·18·21시).
// - 여정을 마친 사람(내 멤버 문서 finishedAt)에게는 보내지 않는다. 앱을 지워 더는 받을 수 없는 토큰은 지운다.
// - 알림을 누르면 앱이 홈(S01-A, 진행 중 여정 카드들)을 연다(2026-10-29 팀 결정). clickAction 으로 앱 MainActivity 를 연다.
import type { Message, SendResponse } from "firebase-admin/messaging";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { db } from "./admin";

/** 하루 중 첫 알림 시각과 마지막 알림 시각(폰 시각, 시) */
export const REMINDER_FIRST_HOUR = 9;
export const REMINDER_LAST_HOUR = 23;

/** 앱의 알림 채널 ID·누르면 여는 동작(앱 notify/CaptureReminder.kt 의 CHANNEL_ID·ACTION_OPEN_HOME 과 같아야 한다) */
export const REMINDER_CHANNEL_ID = "capture_reminder";
export const REMINDER_CLICK_ACTION = "com.logus.app.CAPTURE_REMINDER";

/** 한 번에 읽을 여정 수 상한(비용 관리) */
const MAX_JOURNEYS = 500;

export type LocalTime = { date: string; hour: number };

/** 이 순간(instant)을 시간대 timeZone 의 현지 날짜 "YYYY-MM-DD"와 시(0~23)로 바꾼다. 시간대 이름이 틀리면 null */
export function localTimeIn(timeZone: string, instant: Date): LocalTime | null {
  try {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      hourCycle: "h23",
    }).formatToParts(instant);
    const get = (type: string) => parts.find((p) => p.type === type)?.value;
    const [y, m, d, h] = [get("year"), get("month"), get("day"), get("hour")];
    if (!y || !m || !d || h === undefined) return null;
    return { date: `${y}-${m}-${d}`, hour: Number(h) % 24 };
  } catch {
    return null;
  }
}

/** 이 현지 시각에 촬영 알림을 보낼 차례인지(여정 기간 안 · 09~23시 · 9시부터 간격마다) */
export function isReminderTime(local: LocalTime, startDate: string, endDate: string, intervalHours: unknown): boolean {
  if (intervalHours !== 1 && intervalHours !== 2 && intervalHours !== 3) return false;
  // "YYYY-MM-DD" 글자 순서 = 날짜 순서
  if (local.date < startDate || local.date > endDate) return false;
  if (local.hour < REMINDER_FIRST_HOUR || local.hour > REMINDER_LAST_HOUR) return false;
  return (local.hour - REMINDER_FIRST_HOUR) % intervalHours === 0;
}

/** 보낼 알림 하나(어느 기기 문서로 보내는지 함께 기억해 두었다가, 실패한 토큰을 지운다) */
type Outgoing = { devicePath: string; message: Message };

/** 알림 보내기(테스트에서는 가짜로 바꿔 끼운다) */
export type SendFn = (messages: Message[]) => Promise<SendResponse[]>;

const defaultSend: SendFn = async (messages) => (await getMessaging().sendEach(messages)).responses;

/** 더는 쓸 수 없는 토큰이라 지워야 하는 오류 */
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/**
 * 지금(now) 보낼 촬영 알림을 모아 보낸다. 돌려주는 값: 보낸 수·실패 수·지운 토큰 수.
 * 함수 본체와 나눠 두어 테스트에서 시각과 보내기를 바꿔 넣을 수 있다.
 */
export async function runCaptureReminders(
  now: Date,
  send: SendFn = defaultSend,
): Promise<{ sent: number; failed: number; removed: number }> {
  // 세계 어느 시간대에서든 아직 종료일일 수 있는 여정만: 종료일이 (지금 - 1일, UTC 날짜) 이후
  const minEndDate = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  // 시작일이 (지금 + 1일, UTC 날짜) 보다 늦은 여정은 어느 시간대에서도 아직 시작 전이다
  const maxStartDate = new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

  const journeys = await db.collection("journeys").where("endDate", ">=", minEndDate).limit(MAX_JOURNEYS).get();
  const outgoing: Outgoing[] = [];

  await Promise.all(
    journeys.docs.map(async (journey) => {
      const startDate = journey.get("startDate");
      const endDate = journey.get("endDate");
      const ownerId = journey.get("ownerId");
      const name = journey.get("name");
      if (typeof startDate !== "string" || typeof endDate !== "string" || typeof ownerId !== "string") return;
      if (startDate > maxStartDate) return;
      const memberIds = (journey.get("memberIds") as string[] | undefined) ?? [];

      // 구성원 문서를 한 번에 읽는다: 공통 알림 간격(만든 사람) + 여정을 마쳤는지(각자)
      const members = await journey.ref.collection("members").get();
      const interval = members.docs.find((m) => m.id === ownerId)?.get("notifyIntervalHours");
      if (interval !== 1 && interval !== 2 && interval !== 3) return; // 촬영 알림 받지 않기

      const active = members.docs
        .filter((m) => memberIds.includes(m.id) && m.get("finishedAt") == null)
        .map((m) => m.id);

      await Promise.all(
        active.map(async (uid) => {
          const devices = await db.collection("users").doc(uid).collection("devices").get();
          for (const device of devices.docs) {
            const token = device.get("token");
            const timeZone = device.get("timeZone");
            if (typeof token !== "string" || typeof timeZone !== "string") continue;
            const local = localTimeIn(timeZone, now);
            if (!local || !isReminderTime(local, startDate, endDate, interval)) continue;
            outgoing.push({
              devicePath: device.ref.path,
              message: {
                token,
                notification: {
                  title: "지금 기록할 시간이에요",
                  body: `${typeof name === "string" ? name : "우리 여정"} · 함께 3초 영상을 남겨 보세요`,
                },
                data: { type: "captureReminder", journeyId: journey.id },
                android: {
                  priority: "high",
                  notification: {
                    channelId: REMINDER_CHANNEL_ID,
                    clickAction: REMINDER_CLICK_ACTION,
                    tag: `capture_${journey.id}`,
                  },
                },
              },
            });
          }
        }),
      );
    }),
  );

  let sent = 0;
  let failed = 0;
  let removed = 0;
  // FCM 은 한 번에 500개까지 보낼 수 있다
  for (let i = 0; i < outgoing.length; i += 500) {
    const chunk = outgoing.slice(i, i + 500);
    const responses = await send(chunk.map((o) => o.message));
    await Promise.all(
      responses.map(async (res, j) => {
        if (res.success) {
          sent++;
          return;
        }
        failed++;
        const code = res.error?.code;
        if (code && DEAD_TOKEN_CODES.has(code)) {
          await db.doc(chunk[j].devicePath).delete();
          removed++;
        } else {
          logger.warn("촬영 알림 보내기 실패", { code, message: res.error?.message });
        }
      }),
    );
  }
  logger.info("촬영 알림", { journeys: journeys.size, sent, failed, removed });
  return { sent, failed, removed };
}

// 매시 정각(UTC)에 실행한다. 다시 시도하지 않는다(같은 알림이 두 번 가지 않게).
export const sendCaptureReminders = onSchedule(
  { schedule: "0 * * * *", timeZone: "Etc/UTC", retryCount: 0 },
  async () => {
    await runCaptureReminders(new Date());
  },
);
