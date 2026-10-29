// 촬영 알림(sendCaptureReminders) 테스트: 시각 계산은 그대로, 알림 보내기는 가짜로 바꿔 끼워 어떤 폰에 보내는지 확인한다.
// (에뮬레이터에는 FCM 이 없어서 실제로 보내지는 않는다)
import { randomUUID } from "node:crypto";
import type { Message, SendResponse } from "firebase-admin/messaging";
import { describe, expect, it } from "vitest";
import { db } from "../../functions/src/admin";
import { isReminderTime, localTimeIn, runCaptureReminders } from "../../functions/src/sendCaptureReminders";

const id = (prefix: string) => `${prefix}_${randomUUID().slice(0, 8)}`;

// 다른 테스트의 여정과 섞이지 않게 먼 미래 날짜를 쓴다(여정 조회가 "종료일이 지금 - 1일 이후"라서 예전 여정은 빠진다)
const START = "2031-05-09";
const END = "2031-05-11";
/** 2031-05-10 의 한국 시각 hour 시 정각 */
const seoulAt = (hour: number) => new Date(Date.UTC(2031, 4, 10, hour - 9, 0, 0));

/** 여정 + 구성원 + 기기를 만든다. 돌려주는 값: 여정 ID 와 구성원별 토큰 */
async function seed(options: {
  interval: number | null;
  members: { finished?: boolean; timeZone?: string }[];
}) {
  const journeyId = id("j");
  const uids = options.members.map(() => id("u"));
  await db.doc(`journeys/${journeyId}`).set({
    name: "알림 여정",
    city: "포르투",
    startDate: START,
    endDate: END,
    ownerId: uids[0],
    memberIds: uids,
    memberCount: uids.length,
  });
  const tokens: string[] = [];
  await Promise.all(
    options.members.map(async (m, i) => {
      await db.doc(`journeys/${journeyId}/members/${uids[i]}`).set({
        role: i === 0 ? "owner" : "member",
        notifyIntervalHours: i === 0 ? options.interval : 2,
        ...(m.finished ? { finishedAt: new Date() } : {}),
      });
      const token = id("tok");
      tokens[i] = token;
      await db.doc(`users/${uids[i]}/devices/${token}`).set({
        token,
        timeZone: m.timeZone ?? "Asia/Seoul",
        platform: "android",
        updatedAt: new Date(),
      });
    }),
  );
  return { journeyId, uids, tokens };
}

/** 보내려던 메시지를 모으는 가짜 보내기(모두 성공) */
function fakeSend() {
  const sent: Message[] = [];
  const send = async (messages: Message[]): Promise<SendResponse[]> => {
    sent.push(...messages);
    return messages.map(() => ({ success: true, messageId: "fake" }));
  };
  const tokens = () => sent.map((m) => (m as { token: string }).token);
  return { sent, send, tokens };
}

describe("촬영 알림 시각 계산", () => {
  it("폰 시간대의 현지 날짜·시를 구한다", () => {
    expect(localTimeIn("Asia/Seoul", new Date(Date.UTC(2031, 4, 9, 15, 0)))).toEqual({ date: "2031-05-10", hour: 0 });
    expect(localTimeIn("Europe/Lisbon", new Date(Date.UTC(2031, 4, 10, 10, 0)))).toEqual({ date: "2031-05-10", hour: 11 });
    expect(localTimeIn("없는/시간대", new Date())).toBeNull();
  });

  it("여정 기간 안, 09~23시, 9시부터 간격마다만 보낸다", () => {
    const at = (date: string, hour: number) => ({ date, hour });
    // 2시간: 9·11·…·23시
    expect(isReminderTime(at("2031-05-10", 9), START, END, 2)).toBe(true);
    expect(isReminderTime(at("2031-05-10", 23), START, END, 2)).toBe(true);
    expect(isReminderTime(at("2031-05-10", 10), START, END, 2)).toBe(false);
    // 3시간: 9·12·15·18·21시
    expect(isReminderTime(at("2031-05-10", 12), START, END, 3)).toBe(true);
    expect(isReminderTime(at("2031-05-10", 23), START, END, 3)).toBe(false);
    // 밤·새벽, 여정 기간 밖, 알림 받지 않기는 보내지 않는다
    expect(isReminderTime(at("2031-05-10", 8), START, END, 1)).toBe(false);
    expect(isReminderTime(at("2031-05-10", 0), START, END, 1)).toBe(false);
    expect(isReminderTime(at("2031-05-08", 9), START, END, 1)).toBe(false);
    expect(isReminderTime(at("2031-05-12", 9), START, END, 1)).toBe(false);
    expect(isReminderTime(at("2031-05-10", 9), START, END, null)).toBe(false);
  });
});

describe("촬영 알림 보내기", () => {
  it("간격에 맞는 시각에 여정을 마치지 않은 구성원 폰으로 보낸다(누르면 앱 홈)", async () => {
    const { journeyId, tokens } = await seed({ interval: 2, members: [{}, {}, { finished: true }] });
    const fake = fakeSend();
    await runCaptureReminders(seoulAt(11), fake.send);
    const mine = fake.sent.filter((m) => m.data?.journeyId === journeyId);
    expect(mine.map((m) => (m as { token: string }).token).sort()).toEqual([tokens[0], tokens[1]].sort());
    expect(mine[0].notification?.title).toBe("지금 기록할 시간이에요");
    expect(mine[0].android?.notification?.channelId).toBe("capture_reminder");
    expect(mine[0].android?.notification?.clickAction).toBe("com.logus.app.CAPTURE_REMINDER");
  });

  it("간격에 맞지 않는 시각·밤에는 보내지 않는다", async () => {
    const { tokens } = await seed({ interval: 2, members: [{}] });
    for (const hour of [10, 12, 8]) {
      const fake = fakeSend();
      await runCaptureReminders(seoulAt(hour), fake.send);
      expect(fake.tokens()).not.toContain(tokens[0]);
    }
  });

  it("만든 사람이 '촬영 알림 받지 않기'면 아무에게도 보내지 않는다", async () => {
    const { tokens } = await seed({ interval: null, members: [{}, {}] });
    const fake = fakeSend();
    await runCaptureReminders(seoulAt(11), fake.send);
    expect(fake.tokens()).not.toContain(tokens[0]);
    expect(fake.tokens()).not.toContain(tokens[1]);
  });

  it("폰마다 그 폰의 시간대로 시각을 계산한다", async () => {
    // 한국 11시 = 리스본 3시(밤) → 리스본 폰은 받지 않는다
    const { tokens } = await seed({ interval: 1, members: [{}, { timeZone: "Europe/Lisbon" }] });
    const fake = fakeSend();
    await runCaptureReminders(seoulAt(11), fake.send);
    expect(fake.tokens()).toContain(tokens[0]);
    expect(fake.tokens()).not.toContain(tokens[1]);
  });

  it("더는 받을 수 없는 토큰(앱 삭제)은 기기 문서를 지운다", async () => {
    const { uids, tokens } = await seed({ interval: 1, members: [{}] });
    const send = async (messages: Message[]): Promise<SendResponse[]> =>
      messages.map((m) =>
        (m as { token: string }).token === tokens[0]
          ? ({
              success: false,
              error: { code: "messaging/registration-token-not-registered", message: "gone" },
            } as unknown as SendResponse)
          : { success: true, messageId: "fake" },
      );
    await runCaptureReminders(seoulAt(10), send);
    expect((await db.doc(`users/${uids[0]}/devices/${tokens[0]}`).get()).exists).toBe(false);
  });
});
