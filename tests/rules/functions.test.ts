// 서버 함수 테스트: 함수를 에뮬레이터 Firestore에 직접 실행해 결과를 확인한다.
// (서버 함수는 보안 규칙을 거치지 않으므로 권한 확인과 코인 중복 방지를 함수 안에서 검사한다)
import { randomUUID } from "node:crypto";
import type { CallableRequest } from "firebase-functions/v2/https";
import { describe, expect, it } from "vitest";
import { db, storage } from "../../functions/src/admin";
import {
  DAILY_COIN_LIMIT,
  INVITE_REWARD_COINS,
  inviteExpiresAtMillis,
  localDateIn,
  utcToday,
} from "../../functions/src/common";
import { AD_REWARD_COINS, AD_REWARD_DAILY_LIMIT, claimAdReward } from "../../functions/src/adReward";
import { createJourney } from "../../functions/src/createJourney";
import { deleteJourney } from "../../functions/src/deleteJourney";
import { getInviteCode } from "../../functions/src/getInviteCode";
import { joinJourney } from "../../functions/src/joinJourney";
import { leaveJourney } from "../../functions/src/leaveJourney";
import { notifyNewLog, windowStartLabel } from "../../functions/src/logNotifications";
import { previewInvite } from "../../functions/src/previewInvite";
import { LOCATION_REWARD_COINS, saveLogLocation } from "../../functions/src/saveLogLocation";

/** 로그인한 사용자가 callable을 부른 것처럼 요청을 만든다. uid가 없으면 비로그인 */
function req<T>(data: T, uid?: string): CallableRequest<T> {
  return {
    data,
    auth: uid ? { uid, token: { name: uid } as never, rawToken: "" } : undefined,
    rawRequest: {} as never,
    acceptsStreaming: false,
  } as CallableRequest<T>;
}

// 테스트끼리 데이터가 섞이지 않도록 사용자 ID를 매번 새로 만든다.
const newUid = () => `u_${randomUUID().slice(0, 8)}`;

/** 오늘(UTC)에서 days 일 뒤의 날짜 "YYYY-MM-DD" (음수면 그만큼 전) */
function dateFromToday(days: number): string {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

// 초대 코드는 여정이 끝나면 못 쓰므로, 테스트 여정은 오늘 시작해서 2일 뒤에 끝나게 만든다.
const START = dateFromToday(0);
const END = dateFromToday(2);

async function makeJourney(ownerId: string) {
  return createJourney.run(req({
    name: "우리의 포르투", city: "포르투", country: "포르투갈",
    startDate: START, endDate: END, notifyIntervalHours: 2,
  }, ownerId));
}

/** 여정을 이미 끝난 여정으로 바꾼다(초대 코드 만료 확인용) */
async function endJourney(journeyId: string) {
  await db.doc(`journeys/${journeyId}`).update({ startDate: "2020-01-01", endDate: "2020-01-03" });
}

async function makeLog(journeyId: string, authorId: string) {
  const ref = db.collection("journeys").doc(journeyId).collection("logs").doc();
  await ref.set({
    authorId, mediaType: "photo", mediaPath: `journeys/${journeyId}/${authorId}/a.jpg`,
    body: "", themes: [], taggedUids: [], capturedAt: new Date(), capturedTz: "Europe/Lisbon",
    location: null, placeName: null, isPublic: false, createdAt: new Date(),
  });
  return ref.id;
}

const porto = { lat: 41.1413, lng: -8.6110 };

/** 가입한 사람처럼 프로필(users/{uid})을 만든다(초대 코인은 프로필이 있는 사람에게만 준다) */
async function makeUser(uid: string, nickname: string) {
  await db.doc(`users/${uid}`).set({ nickname, photoURL: null, coins: 0, createdAt: new Date() });
}

async function coinsOf(uid: string): Promise<number> {
  return ((await db.doc(`users/${uid}`).get()).get("coins") as number | undefined) ?? 0;
}

/** 오늘(또는 date 날) 이미 받은 코인 합을 정해 둔다(하루 50코인 제한 확인용) */
async function setDailyCoins(uid: string, earned: number, date = utcToday()) {
  await db.doc(`coinDaily/${uid}`).set({ date, earned });
}

/** 에뮬레이터 Storage 에 파일을 하나 올린다(서버가 지우는지 확인용) */
async function putFile(path: string) {
  await storage.bucket().file(path).save(Buffer.from("x"), { contentType: "video/mp4", resumable: false });
}

async function fileExists(path: string): Promise<boolean> {
  const [exists] = await storage.bucket().file(path).exists();
  return exists;
}

describe("createJourney", () => {
  it("여정·owner 멤버·초대 요약을 함께 만든다", async () => {
    const owner = newUid();
    const { journeyId, inviteCode } = await makeJourney(owner);

    expect(inviteCode).toMatch(/^[A-HJ-NP-Z2-9]{6}$/); // 6자리, 헷갈리는 글자(0·O·1·I) 없음
    const journey = (await db.doc(`journeys/${journeyId}`).get()).data();
    expect(journey).toMatchObject({ ownerId: owner, memberIds: [owner], memberCount: 1, inviteCode });
    const member = (await db.doc(`journeys/${journeyId}/members/${owner}`).get()).data();
    expect(member).toMatchObject({ role: "owner", notifyIntervalHours: 2 });
    const invite = (await db.doc(`invites/${inviteCode}`).get()).data();
    expect(invite).toMatchObject({ journeyId, name: "우리의 포르투", memberCount: 1 });
    // 여정 종료일이 끝나는 때 만료(종료일 다음 날 12:00 UTC)
    expect(invite!.expiresAt.toMillis()).toBe(inviteExpiresAtMillis(END));
    expect(new Date(inviteExpiresAtMillis(END)).toISOString()).toBe(`${dateFromToday(3)}T12:00:00.000Z`);
  });

  it("로그인하지 않으면 거부", async () => {
    await expect(createJourney.run(req({ name: "x" }))).rejects.toMatchObject({ code: "unauthenticated" });
  });

  it("종료일이 시작일보다 앞이면 거부", async () => {
    await expect(createJourney.run(req({
      name: "x", city: "포르투", startDate: START, endDate: dateFromToday(-1),
    }, newUid()))).rejects.toMatchObject({ code: "invalid-argument", message: expect.stringContaining("종료일") });
  });

  it("시작일은 오늘만(폰 시간대 기준): 내일·어제 시작하는 여정은 만들 수 없다", async () => {
    const make = (startDate: string, endDate: string, timeZone?: string) => createJourney.run(req({
      name: "x", city: "포르투", startDate, endDate, notifyIntervalHours: 2, ...(timeZone ? { timeZone } : {}),
    }, newUid()));
    const seoulToday = localDateIn("Asia/Seoul")!;
    const seoulPlus = (days: number) =>
      new Date(Date.parse(`${seoulToday}T00:00:00Z`) + days * 86_400_000).toISOString().slice(0, 10);
    // 폰 시간대의 오늘이면 만든다
    await expect(make(seoulToday, seoulPlus(2), "Asia/Seoul")).resolves.toMatchObject({ journeyId: expect.any(String) });
    // 내일·어제 시작은 거부
    await expect(make(seoulPlus(1), seoulPlus(3), "Asia/Seoul"))
      .rejects.toMatchObject({ code: "invalid-argument", message: expect.stringContaining("오늘") });
    await expect(make(seoulPlus(-1), seoulPlus(1), "Asia/Seoul")).rejects.toMatchObject({ code: "invalid-argument" });
    // 시간대를 보내지 않는 예전 앱: UTC 어제~내일만 받고 그보다 먼 미래는 거부
    await expect(make(START, END)).resolves.toMatchObject({ journeyId: expect.any(String) });
    await expect(make(dateFromToday(3), dateFromToday(5))).rejects.toMatchObject({ code: "invalid-argument" });
  });
});

describe("joinJourney", () => {
  it("초대 코드로 참여하면 memberIds·memberCount·멤버 문서·초대 요약이 함께 바뀐다", async () => {
    const owner = newUid();
    const guest = newUid();
    const { journeyId, inviteCode } = await makeJourney(owner);

    const result = await joinJourney.run(req({ inviteCode }, guest));
    expect(result).toEqual({ journeyId, alreadyMember: false });

    const journey = (await db.doc(`journeys/${journeyId}`).get()).data();
    expect(journey?.memberIds).toEqual([owner, guest]);
    expect(journey?.memberCount).toBe(2);
    const member = (await db.doc(`journeys/${journeyId}/members/${guest}`).get()).data();
    expect(member).toMatchObject({ role: "member", notifyIntervalHours: 2 });
    expect((await db.doc(`invites/${inviteCode}`).get()).get("memberCount")).toBe(2);
  });

  it("두 번 참여해도 인원이 늘지 않는다", async () => {
    const owner = newUid();
    const guest = newUid();
    const { inviteCode, journeyId } = await makeJourney(owner);
    await joinJourney.run(req({ inviteCode }, guest));
    const again = await joinJourney.run(req({ inviteCode }, guest));
    expect(again.alreadyMember).toBe(true);
    expect((await db.doc(`journeys/${journeyId}`).get()).get("memberCount")).toBe(2);
  });

  it("없는 초대 코드는 거부", async () => {
    await expect(joinJourney.run(req({ inviteCode: "ZZZZZ2" }, newUid())))
      .rejects.toMatchObject({ code: "not-found" });
  });

  it("6자리 형식이 아니면 거부", async () => {
    await expect(joinJourney.run(req({ inviteCode: "inviteCode123456" }, newUid())))
      .rejects.toMatchObject({ code: "invalid-argument" });
    await expect(joinJourney.run(req({ inviteCode: "K0PQ1M" }, newUid()))) // 0·1 은 쓰지 않는 글자
      .rejects.toMatchObject({ code: "invalid-argument" });
  });

  it("소문자·공백이 섞여도 같은 코드로 참여한다", async () => {
    const owner = newUid();
    const guest = newUid();
    const { journeyId, inviteCode } = await makeJourney(owner);
    const result = await joinJourney.run(req({ inviteCode: ` ${inviteCode.toLowerCase()} ` }, guest));
    expect(result).toEqual({ journeyId, alreadyMember: false });
  });

  it("끝난 여정의 초대 코드는 거부", async () => {
    const owner = newUid();
    const { inviteCode, journeyId } = await makeJourney(owner);
    await endJourney(journeyId);
    await expect(joinJourney.run(req({ inviteCode }, newUid())))
      .rejects.toMatchObject({ code: "failed-precondition" });
  });

  it("여정이 끝나기 전이면 만든 지 7일이 지난 코드도 쓸 수 있다(같은 여정은 같은 코드)", async () => {
    const owner = newUid();
    const guest = newUid();
    const { inviteCode, journeyId } = await makeJourney(owner);
    // 예전 규칙(7일 만료)으로 만든 초대 요약처럼 expiresAt 이 지나 있어도, 여정 종료일 기준으로 확인한다
    await db.doc(`invites/${inviteCode}`).update({ expiresAt: new Date(Date.now() - 1000) });
    const result = await joinJourney.run(req({ inviteCode }, guest));
    expect(result).toEqual({ journeyId, alreadyMember: false });
    // 여정 문서의 코드는 그대로다
    expect((await db.doc(`journeys/${journeyId}`).get()).get("inviteCode")).toBe(inviteCode);
  });

  it("한 사람이 하루에 너무 많이 입력하면 거부", async () => {
    const guest = newUid();
    const today = new Date().toISOString().slice(0, 10);
    await db.doc(`inviteAttempts/${guest}`).set({ date: today, count: 20 });
    await expect(joinJourney.run(req({ inviteCode: "ZZZZZ2" }, guest)))
      .rejects.toMatchObject({ code: "resource-exhausted" });
  });
});

describe("초대 보상(코드 주인에게 5코인)과 구성원별 초대 코드", () => {
  it("만든 사람의 코드로 새 친구가 들어오면 만든 사람이 5코인을 한 번만 받는다", async () => {
    expect(INVITE_REWARD_COINS).toBe(5);
    const owner = newUid();
    const guest = newUid();
    await makeUser(owner, "성연");
    const { journeyId, inviteCode } = await makeJourney(owner);
    expect((await db.doc(`journeys/${journeyId}/members/${owner}`).get()).get("inviteCode")).toBe(inviteCode);
    expect((await db.doc(`invites/${inviteCode}`).get()).get("inviterId")).toBe(owner);

    await joinJourney.run(req({ inviteCode }, guest));
    expect(await coinsOf(owner)).toBe(5);
    const ledger = (await db.doc(`users/${owner}/coinLedger/invite_${journeyId}_${guest}`).get()).data();
    expect(ledger).toMatchObject({ reason: "inviteFriend", amount: 5, journeyId, invitedUid: guest });

    // 같은 친구가 코드를 다시 넣어도(이미 구성원) 더 주지 않는다
    await joinJourney.run(req({ inviteCode }, guest));
    expect(await coinsOf(owner)).toBe(5);
    // 다른 친구가 들어오면 또 5
    await joinJourney.run(req({ inviteCode }, newUid()));
    expect(await coinsOf(owner)).toBe(10);
  });

  it("구성원마다 자기 코드를 받고(바뀌지 않음), 그 코드로 들어오면 코드를 공유한 사람이 코인을 받는다", async () => {
    const owner = newUid();
    const bob = newUid();
    const carol = newUid();
    await makeUser(owner, "성연");
    await makeUser(bob, "민수");
    const { journeyId, inviteCode: ownerCode } = await makeJourney(owner);
    await joinJourney.run(req({ inviteCode: ownerCode }, bob));

    // 만든 사람은 여정을 만들 때 받은 코드 그대로
    expect((await getInviteCode.run(req({ journeyId }, owner))).inviteCode).toBe(ownerCode);
    // 다른 구성원은 처음 부를 때 새 코드가 생기고, 다시 불러도 같은 코드
    const bobCode = (await getInviteCode.run(req({ journeyId }, bob))).inviteCode;
    expect(bobCode).toMatch(/^[A-HJ-NP-Z2-9]{6}$/);
    expect(bobCode).not.toBe(ownerCode);
    expect((await getInviteCode.run(req({ journeyId }, bob))).inviteCode).toBe(bobCode);
    expect((await db.doc(`journeys/${journeyId}/members/${bob}`).get()).get("inviteCode")).toBe(bobCode);
    expect((await db.doc(`invites/${bobCode}`).get()).data()).toMatchObject({
      journeyId, inviterId: bob, inviterName: "민수", name: "우리의 포르투", endDate: END,
    });

    // I01 에는 코드를 공유한 사람이 초대한 사람으로 보인다
    expect((await previewInvite.run(req({ inviteCode: bobCode }, carol))).inviterName).toBe("민수");

    const ownerCoinsBefore = await coinsOf(owner);
    await joinJourney.run(req({ inviteCode: bobCode }, carol));
    expect(await coinsOf(bob)).toBe(5);
    expect(await coinsOf(owner)).toBe(ownerCoinsBefore); // 만든 사람은 이번에는 받지 않는다
    expect((await db.doc(`journeys/${journeyId}`).get()).get("memberIds")).toEqual([owner, bob, carol]);
  });

  it("구성원이 아니거나 로그인하지 않으면 코드를 받지 못하고, 끝난 여정에는 새 코드를 만들지 않는다", async () => {
    const owner = newUid();
    const bob = newUid();
    const { journeyId, inviteCode } = await makeJourney(owner);
    await expect(getInviteCode.run(req({ journeyId }, newUid())))
      .rejects.toMatchObject({ code: "permission-denied" });
    await expect(getInviteCode.run(req({ journeyId })))
      .rejects.toMatchObject({ code: "unauthenticated" });
    await joinJourney.run(req({ inviteCode }, bob));
    await endJourney(journeyId);
    await expect(getInviteCode.run(req({ journeyId }, bob)))
      .rejects.toMatchObject({ code: "failed-precondition" });
  });

  it("코드 주인의 프로필이 없으면 코인 없이 참여만 된다", async () => {
    const owner = newUid(); // makeUser 를 부르지 않음
    const guest = newUid();
    const { journeyId, inviteCode } = await makeJourney(owner);
    const result = await joinJourney.run(req({ inviteCode }, guest));
    expect(result).toEqual({ journeyId, alreadyMember: false });
    expect((await db.doc(`users/${owner}`).get()).exists).toBe(false);
  });
});

describe("saveLogLocation (코인 중복 지급 거부)", () => {
  it("위치를 처음 저장하면 5코인을 한 번만 준다", async () => {
    expect(LOCATION_REWARD_COINS).toBe(5); // 앱 Coins.LOCATION_REWARD·화면 문구 "+5코인"과 같아야 한다(2026-11-11 팀 결정 10 → 5)
    const owner = newUid();
    const { journeyId } = await makeJourney(owner);
    const logId = await makeLog(journeyId, owner);
    const data = { journeyId, logId, location: porto, placeName: "Rua A, Porto", isPublic: true };

    const first = await saveLogLocation.run(req(data, owner));
    expect(first.coinsGranted).toBe(LOCATION_REWARD_COINS);

    // 같은 기록으로 다시 저장해도(위치를 바꿔도) 코인은 더 주지 않는다.
    const second = await saveLogLocation.run(req({ ...data, location: { lat: 41.15, lng: -8.62 } }, owner));
    expect(second.coinsGranted).toBe(0);

    const user = (await db.doc(`users/${owner}`).get()).data();
    expect(user?.coins).toBe(LOCATION_REWARD_COINS);
    const ledger = await db.collection(`users/${owner}/coinLedger`).get();
    expect(ledger.size).toBe(1);
    expect(ledger.docs[0].id).toBe(logId);

    const log = (await db.doc(`journeys/${journeyId}/logs/${logId}`).get()).data();
    expect(log).toMatchObject({ location: { lat: 41.15, lng: -8.62 }, placeName: "Rua A, Porto", isPublic: true });
  });

  it("동시에 두 번 불러도 코인은 한 번만 준다", async () => {
    const owner = newUid();
    const { journeyId } = await makeJourney(owner);
    const logId = await makeLog(journeyId, owner);
    const data = { journeyId, logId, location: porto, placeName: null, isPublic: false };

    const results = await Promise.all([
      saveLogLocation.run(req(data, owner)),
      saveLogLocation.run(req(data, owner)),
    ]);
    expect(results.map((r) => r.coinsGranted).sort()).toEqual([0, LOCATION_REWARD_COINS]);
    expect((await db.doc(`users/${owner}`).get()).get("coins")).toBe(LOCATION_REWARD_COINS);
  });

  it("위치 없이 저장하거나 (0,0)이면 코인을 주지 않고, 나중에 위치를 넣으면 준다", async () => {
    const owner = newUid();
    const { journeyId } = await makeJourney(owner);
    const logId = await makeLog(journeyId, owner);

    expect((await saveLogLocation.run(req({ journeyId, logId, location: null }, owner))).coinsGranted).toBe(0);
    expect((await saveLogLocation.run(req({ journeyId, logId, location: { lat: 0, lng: 0 } }, owner))).coinsGranted).toBe(0);
    const log = (await db.doc(`journeys/${journeyId}/logs/${logId}`).get()).data();
    expect(log?.location).toBeNull();

    expect((await saveLogLocation.run(req({ journeyId, logId, location: porto }, owner))).coinsGranted)
      .toBe(LOCATION_REWARD_COINS);
  });

  it("구성원이 아니거나 작성자가 아니면 거부", async () => {
    const owner = newUid();
    const guest = newUid();
    const stranger = newUid();
    const { journeyId, inviteCode } = await makeJourney(owner);
    await joinJourney.run(req({ inviteCode }, guest));
    const logId = await makeLog(journeyId, owner);
    const data = { journeyId, logId, location: porto };

    await expect(saveLogLocation.run(req(data, stranger))).rejects.toMatchObject({ code: "permission-denied" });
    await expect(saveLogLocation.run(req(data, guest))).rejects.toMatchObject({ code: "permission-denied" });
    await expect(saveLogLocation.run(req(data))).rejects.toMatchObject({ code: "unauthenticated" });
    expect((await db.doc(`users/${guest}`).get()).exists).toBe(false);
  });

  it("잘못된 좌표는 거부", async () => {
    const owner = newUid();
    const { journeyId } = await makeJourney(owner);
    const logId = await makeLog(journeyId, owner);
    await expect(saveLogLocation.run(req({ journeyId, logId, location: { lat: 200, lng: 0 } }, owner)))
      .rejects.toMatchObject({ code: "invalid-argument" });
  });
});

describe("previewInvite", () => {
  it("코드로 여정 요약을 확인한다(참여는 하지 않는다)", async () => {
    const owner = newUid();
    const guest = newUid();
    const { journeyId, inviteCode } = await makeJourney(owner);
    const result = await previewInvite.run(req({ inviteCode: inviteCode.toLowerCase() }, guest));
    expect(result).toMatchObject({
      name: "우리의 포르투", city: "포르투", startDate: START, endDate: END,
      memberCount: 1, notifyIntervalHours: 2, alreadyMember: false,
    });
    // 확인만 하므로 구성원이 늘지 않는다
    expect((await db.doc(`journeys/${journeyId}`).get()).get("memberIds")).toEqual([owner]);
  });

  it("이미 구성원이면 alreadyMember", async () => {
    const owner = newUid();
    const { inviteCode } = await makeJourney(owner);
    const result = await previewInvite.run(req({ inviteCode }, owner));
    expect(result.alreadyMember).toBe(true);
  });

  it("없는 코드·끝난 여정의 코드·로그인 안 함은 거부", async () => {
    await expect(previewInvite.run(req({ inviteCode: "ZZZZZ2" }, newUid())))
      .rejects.toMatchObject({ code: "not-found" });
    const { inviteCode, journeyId } = await makeJourney(newUid());
    await endJourney(journeyId);
    await expect(previewInvite.run(req({ inviteCode }, newUid())))
      .rejects.toMatchObject({ code: "failed-precondition" });
    await expect(previewInvite.run(req({ inviteCode })))
      .rejects.toMatchObject({ code: "unauthenticated" });
  });

  it("코드 확인도 하루 입력 횟수에 포함된다", async () => {
    const guest = newUid();
    const today = new Date().toISOString().slice(0, 10);
    await db.doc(`inviteAttempts/${guest}`).set({ date: today, count: 19 });
    await expect(previewInvite.run(req({ inviteCode: "ZZZZZ2" }, guest))).rejects.toMatchObject({ code: "not-found" });
    await expect(previewInvite.run(req({ inviteCode: "ZZZZZ2" }, guest)))
      .rejects.toMatchObject({ code: "resource-exhausted" });
  });
});


describe("하루 코인 제한(UTC 하루 50코인)", () => {
  it("위치 코인은 하루 50코인까지만 쌓이고, 넘으면 0코인과 dailyLimitReached 를 돌려준다", async () => {
    expect(DAILY_COIN_LIMIT).toBe(50);
    const owner = newUid();
    const { journeyId } = await makeJourney(owner);
    // 위치 5코인 × 10번 = 50코인
    for (let i = 0; i < DAILY_COIN_LIMIT / LOCATION_REWARD_COINS; i++) {
      const logId = await makeLog(journeyId, owner);
      const result = await saveLogLocation.run(req({ journeyId, logId, location: porto, placeName: "히베이라" }, owner));
      expect(result).toEqual({ coinsGranted: LOCATION_REWARD_COINS, dailyLimitReached: false });
    }
    expect(await coinsOf(owner)).toBe(50);
    expect((await db.doc(`coinDaily/${owner}`).get()).data()).toEqual({ date: utcToday(), earned: 50 });

    const sixth = await makeLog(journeyId, owner);
    const over = await saveLogLocation.run(req({ journeyId, logId: sixth, location: porto }, owner));
    expect(over).toEqual({ coinsGranted: 0, dailyLimitReached: true });
    expect(await coinsOf(owner)).toBe(50);
    // 위치는 저장되고, 코인 내역은 0코인으로 남아 다음 날 다시 불러도 이 기록으로는 주지 않는다
    expect((await db.doc(`journeys/${journeyId}/logs/${sixth}`).get()).get("location")).toEqual(porto);
    expect((await db.doc(`users/${owner}/coinLedger/${sixth}`).get()).data())
      .toMatchObject({ reason: "saveLogLocation", amount: 0, dailyLimited: true });
    await setDailyCoins(owner, 0, "2000-01-01");
    expect((await saveLogLocation.run(req({ journeyId, logId: sixth, location: porto }, owner))).coinsGranted).toBe(0);
  });

  it("50에 가까우면 50까지만 채운다(오늘 48코인 → 위치 저장 2코인)", async () => {
    const owner = newUid();
    await makeUser(owner, "성연");
    const { journeyId } = await makeJourney(owner);
    await setDailyCoins(owner, 48);
    const logId = await makeLog(journeyId, owner);
    const result = await saveLogLocation.run(req({ journeyId, logId, location: porto }, owner));
    expect(result).toEqual({ coinsGranted: 2, dailyLimitReached: true });
    expect(await coinsOf(owner)).toBe(2);
    expect((await db.doc(`coinDaily/${owner}`).get()).get("earned")).toBe(50);
    expect((await db.doc(`users/${owner}/coinLedger/${logId}`).get()).data()).toMatchObject({ amount: 2, dailyLimited: true });
  });

  it("어제(UTC) 받은 코인은 오늘 제한에 세지 않는다", async () => {
    const owner = newUid();
    const { journeyId } = await makeJourney(owner);
    await setDailyCoins(owner, 50, "2000-01-01");
    const logId = await makeLog(journeyId, owner);
    expect(await saveLogLocation.run(req({ journeyId, logId, location: porto }, owner)))
      .toEqual({ coinsGranted: LOCATION_REWARD_COINS, dailyLimitReached: false });
    expect((await db.doc(`coinDaily/${owner}`).get()).data()).toEqual({ date: utcToday(), earned: LOCATION_REWARD_COINS });
  });

  it("초대 코인도 같은 하루 50코인 안에서 준다(오늘 48코인 → 2코인, 그다음 친구는 0코인)", async () => {
    const owner = newUid();
    await makeUser(owner, "성연");
    const { journeyId, inviteCode } = await makeJourney(owner);
    await setDailyCoins(owner, 48);

    const first = newUid();
    await joinJourney.run(req({ inviteCode }, first));
    expect(await coinsOf(owner)).toBe(2);
    expect((await db.doc(`users/${owner}/coinLedger/invite_${journeyId}_${first}`).get()).data())
      .toMatchObject({ reason: "inviteFriend", amount: 2, dailyLimited: true });

    const second = newUid();
    await joinJourney.run(req({ inviteCode }, second));
    expect(await coinsOf(owner)).toBe(2);
    expect((await db.doc(`users/${owner}/coinLedger/invite_${journeyId}_${second}`).get()).get("amount")).toBe(0);
    // 코인이 0이어도 참여는 그대로 된다
    expect((await db.doc(`journeys/${journeyId}`).get()).get("memberIds")).toEqual([owner, first, second]);
  });
});

describe("deleteJourney (만든 사람의 여정 삭제)", () => {
  it("여정·구성원·기록·초대 코드·파일이 모두 지워지고, 받은 코인은 남는다", async () => {
    const owner = newUid();
    const guest = newUid();
    await makeUser(owner, "성연");
    await makeUser(guest, "민지");
    const { journeyId, inviteCode } = await makeJourney(owner);
    await joinJourney.run(req({ inviteCode }, guest));
    const guestCode = (await getInviteCode.run(req({ journeyId }, guest))).inviteCode;
    const logId = await makeLog(journeyId, guest);
    await putFile(`journeys/${journeyId}/${guest}/${logId}.mp4`);
    await putFile(`journeys/${journeyId}/${owner}/cover_1.jpg`);
    const { journeyId: otherJourney } = await makeJourney(owner);
    await putFile(`journeys/${otherJourney}/${owner}/keep.mp4`);

    expect(await deleteJourney.run(req({ journeyId }, owner))).toEqual({ deleted: true });

    expect((await db.doc(`journeys/${journeyId}`).get()).exists).toBe(false);
    expect((await db.collection(`journeys/${journeyId}/members`).get()).empty).toBe(true);
    expect((await db.collection(`journeys/${journeyId}/logs`).get()).empty).toBe(true);
    expect((await db.doc(`invites/${inviteCode}`).get()).exists).toBe(false);
    expect((await db.doc(`invites/${guestCode}`).get()).exists).toBe(false);
    expect(await fileExists(`journeys/${journeyId}/${guest}/${logId}.mp4`)).toBe(false);
    expect(await fileExists(`journeys/${journeyId}/${owner}/cover_1.jpg`)).toBe(false);
    // 다른 여정은 그대로, 받은 초대 코인과 코인 내역도 그대로
    expect(await fileExists(`journeys/${otherJourney}/${owner}/keep.mp4`)).toBe(true);
    expect((await db.doc(`journeys/${otherJourney}`).get()).exists).toBe(true);
    expect(await coinsOf(owner)).toBe(INVITE_REWARD_COINS);
    expect((await db.doc(`users/${owner}/coinLedger/invite_${journeyId}_${guest}`).get()).exists).toBe(true);

    // 두 번 불러도 안전하다
    expect(await deleteJourney.run(req({ journeyId }, owner))).toEqual({ deleted: false });
  });

  it("만든 사람이 아니면(참여자·남·로그인 안 함) 지울 수 없다", async () => {
    const owner = newUid();
    const guest = newUid();
    const { journeyId, inviteCode } = await makeJourney(owner);
    await joinJourney.run(req({ inviteCode }, guest));
    await expect(deleteJourney.run(req({ journeyId }, guest))).rejects.toMatchObject({ code: "permission-denied" });
    await expect(deleteJourney.run(req({ journeyId }, newUid()))).rejects.toMatchObject({ code: "permission-denied" });
    await expect(deleteJourney.run(req({ journeyId }))).rejects.toMatchObject({ code: "unauthenticated" });
    expect((await db.doc(`journeys/${journeyId}`).get()).get("memberIds")).toEqual([owner, guest]);
  });
});

describe("leaveJourney (참여자의 여정 나가기)", () => {
  it("구성원에서 빠지고 내 초대 코드·대표 사진은 지워지며, 내가 올린 기록은 남는다", async () => {
    const owner = newUid();
    const guest = newUid();
    await makeUser(owner, "성연");
    await makeUser(guest, "민지");
    const { journeyId, inviteCode } = await makeJourney(owner);
    await joinJourney.run(req({ inviteCode }, guest));
    const guestCode = (await getInviteCode.run(req({ journeyId }, guest))).inviteCode;
    const logId = await makeLog(journeyId, guest);
    const cover = `journeys/${journeyId}/${guest}/cover_1.jpg`;
    await putFile(cover);
    await putFile(`journeys/${journeyId}/${guest}/${logId}.mp4`);
    await db.doc(`journeys/${journeyId}/members/${guest}`).update({ coverPath: cover });

    expect(await leaveJourney.run(req({ journeyId }, guest))).toEqual({ left: true });

    const journey = (await db.doc(`journeys/${journeyId}`).get()).data();
    expect(journey).toMatchObject({ memberIds: [owner], memberCount: 1 });
    expect((await db.doc(`journeys/${journeyId}/members/${guest}`).get()).exists).toBe(false);
    expect((await db.doc(`invites/${guestCode}`).get()).exists).toBe(false);
    expect((await db.doc(`invites/${inviteCode}`).get()).exists).toBe(true); // 만든 사람의 코드는 그대로
    expect((await db.doc(`journeys/${journeyId}/logs/${logId}`).get()).exists).toBe(true);
    expect(await fileExists(`journeys/${journeyId}/${guest}/${logId}.mp4`)).toBe(true);
    expect(await fileExists(cover)).toBe(false);

    // 두 번 불러도 안전하다
    expect(await leaveJourney.run(req({ journeyId }, guest))).toEqual({ left: false });
    // 다시 들어오면 구성원이 되지만, 같은 친구로 초대 코인을 또 주지는 않는다
    await joinJourney.run(req({ inviteCode }, guest));
    expect((await db.doc(`journeys/${journeyId}`).get()).get("memberIds")).toEqual([owner, guest]);
    expect(await coinsOf(owner)).toBe(INVITE_REWARD_COINS);
  });

  it("만든 사람은 나갈 수 없고, 구성원이 아니면 아무것도 바뀌지 않는다", async () => {
    const owner = newUid();
    const { journeyId } = await makeJourney(owner);
    await expect(leaveJourney.run(req({ journeyId }, owner))).rejects.toMatchObject({ code: "failed-precondition" });
    expect(await leaveJourney.run(req({ journeyId }, newUid()))).toEqual({ left: false });
    await expect(leaveJourney.run(req({ journeyId }))).rejects.toMatchObject({ code: "unauthenticated" });
    expect((await db.doc(`journeys/${journeyId}`).get()).get("memberIds")).toEqual([owner]);
  });
});

describe("앱 안 알림함(users/{uid}/notifications)", () => {
  const inbox = (uid: string, id: string) => db.doc(`users/${uid}/notifications/${id}`).get();

  it("위치 코인을 받으면 '+5코인을 받았어요', 덜 받으면 안내를 붙이고, 못 받으면 남기지 않는다", async () => {
    const owner = newUid();
    const { journeyId } = await makeJourney(owner);
    const first = await makeLog(journeyId, owner);
    await saveLogLocation.run(req({ journeyId, logId: first, location: porto }, owner));
    expect((await inbox(owner, `coin_${first}`)).data()).toMatchObject({
      type: "coins", title: "+5코인을 받았어요", body: "위치와 함께 저장한 기록 1개", journeyId, read: false,
    });

    await setDailyCoins(owner, 48);
    const partial = await makeLog(journeyId, owner);
    await saveLogLocation.run(req({ journeyId, logId: partial, location: porto }, owner));
    const p = (await inbox(owner, `coin_${partial}`)).data();
    expect(p?.title).toBe("+2코인을 받았어요");
    expect(p?.body).toContain("오늘 받을 수 있는 코인");

    const none = await makeLog(journeyId, owner);
    await saveLogLocation.run(req({ journeyId, logId: none, location: porto }, owner));
    expect((await inbox(owner, `coin_${none}`)).exists).toBe(false);
  });

  it("새 구성원이 들어오면 원래 구성원에게 '○○ 님이 여정에 참여했어요', 코드 주인에게 '+5코인'", async () => {
    const owner = newUid();
    const guest = newUid();
    await makeUser(owner, "성연");
    await makeUser(guest, "수현");
    const { journeyId, inviteCode } = await makeJourney(owner);
    await joinJourney.run(req({ inviteCode }, guest));
    expect((await inbox(owner, `joined_${journeyId}_${guest}`)).data()).toMatchObject({
      type: "memberJoined", title: "수현 님이 여정에 참여했어요", body: "우리의 포르투 · 구성원 2명", journeyId,
    });
    expect((await inbox(owner, `coin_invite_${journeyId}_${guest}`)).data()).toMatchObject({
      type: "coins", title: `+${INVITE_REWARD_COINS}코인을 받았어요`, body: "수현 님이 내 초대 코드로 참여했어요",
    });
    // 들어온 사람 자신에게는 남기지 않는다
    expect((await inbox(guest, `joined_${journeyId}_${guest}`)).exists).toBe(false);
  });

  it("기록을 올리면 다른 구성원에게 '○○ 님이 기록을 올렸어요'(올린 사람·여정을 마친 사람은 빼고)", async () => {
    const owner = newUid();
    const guest = newUid();
    const done = newUid();
    await makeUser(owner, "성연");
    await makeUser(guest, "민지");
    await makeUser(done, "현우");
    const { journeyId, inviteCode } = await makeJourney(owner);
    await joinJourney.run(req({ inviteCode }, guest));
    await joinJourney.run(req({ inviteCode }, done));
    await db.doc(`journeys/${journeyId}/members/${done}`).update({ finishedAt: new Date() });
    const logId = await makeLog(journeyId, guest);
    const log = (await db.doc(`journeys/${journeyId}/logs/${logId}`).get()).data()!;

    expect(await notifyNewLog(journeyId, logId, log)).toBe(1);
    const n = (await inbox(owner, `log_${logId}`)).data();
    expect(n).toMatchObject({ type: "newLog", title: "민지 님이 기록을 올렸어요", journeyId, read: false });
    expect(n?.body).toMatch(/^우리의 포르투 · \d{2}:\d{2} 시간대$/);
    expect((await inbox(guest, `log_${logId}`)).exists).toBe(false);
    expect((await inbox(done, `log_${logId}`)).exists).toBe(false);
  });

  it("알림의 시간대는 N01 과 같이 여정을 만든 시각에서 간격마다 나눈다", () => {
    const anchor = Date.UTC(2031, 4, 9, 19 - 9, 23); // 한국 19:23 에 만든 여정 → 기준 19:20
    const seoul = (day: number, hour: number, minute: number) => Date.UTC(2031, 4, day, hour - 9, minute);
    expect(windowStartLabel(seoul(9, 19, 25), anchor, 2, "Asia/Seoul")).toBe("19:20");
    expect(windowStartLabel(seoul(10, 9, 30), anchor, 2, "Asia/Seoul")).toBe("09:20");
    expect(windowStartLabel(seoul(10, 0, 30), anchor, 2, "Asia/Seoul")).toBe("23:20");
    expect(windowStartLabel(seoul(10, 10, 0), anchor, 3, "Asia/Seoul")).toBe("07:20");
    expect(windowStartLabel(seoul(10, 10, 0), null, 2, "Asia/Seoul")).toBeNull();
  });
});

describe("claimAdReward (광고 보고 코인 받기: 하루 5번 · 1번에 2코인)", () => {
  const viewId = () => randomUUID();

  it("광고를 끝까지 보면 2코인을 주고, 코인 내역·오늘 횟수를 남긴다", async () => {
    expect(AD_REWARD_COINS).toBe(2); // 앱 ads/AdReward.kt 의 COINS_PER_VIEW 와 같아야 한다
    expect(AD_REWARD_DAILY_LIMIT).toBe(5); // 앱 ads/AdReward.kt 의 DAILY_LIMIT 과 같아야 한다
    const uid = newUid();
    await makeUser(uid, "성연");
    const id = viewId();
    const res = await claimAdReward.run(req({ viewId: id }, uid));
    expect(res).toEqual({ granted: 2, count: 1, limit: 5, coins: 2, alreadyClaimed: false, limitReached: false });
    expect(await coinsOf(uid)).toBe(2);
    expect((await db.doc(`users/${uid}/coinLedger/ad_${id}`).get()).data())
      .toMatchObject({ reason: "adReward", amount: 2, viewId: id });
    expect((await db.doc(`adDaily/${uid}`).get()).data()).toMatchObject({ date: utcToday(), count: 1 });
  });

  it("같은 광고(viewId)로 다시 불러도 코인을 두 번 주지 않는다", async () => {
    const uid = newUid();
    await makeUser(uid, "성연");
    const id = viewId();
    await claimAdReward.run(req({ viewId: id }, uid));
    const again = await claimAdReward.run(req({ viewId: id }, uid));
    expect(again).toMatchObject({ granted: 0, count: 1, coins: 2, alreadyClaimed: true });
    expect(await coinsOf(uid)).toBe(2);
  });

  it("하루 5번(10코인)까지만 주고, 6번째는 0코인과 limitReached 를 돌려준다", async () => {
    const uid = newUid();
    await makeUser(uid, "성연");
    for (let i = 1; i <= AD_REWARD_DAILY_LIMIT; i++) {
      const res = await claimAdReward.run(req({ viewId: viewId() }, uid));
      expect(res).toMatchObject({ granted: 2, count: i, limitReached: i === AD_REWARD_DAILY_LIMIT });
    }
    expect(await coinsOf(uid)).toBe(10);
    const sixth = await claimAdReward.run(req({ viewId: viewId() }, uid));
    expect(sixth).toEqual({ granted: 0, count: 5, limit: 5, coins: 10, alreadyClaimed: false, limitReached: true });
    expect(await coinsOf(uid)).toBe(10);
  });

  it("어제(UTC) 본 횟수는 오늘 횟수에 세지 않는다", async () => {
    const uid = newUid();
    await makeUser(uid, "성연");
    await db.doc(`adDaily/${uid}`).set({ date: "2000-01-01", count: 5 });
    expect(await claimAdReward.run(req({ viewId: viewId() }, uid))).toMatchObject({ granted: 2, count: 1 });
  });

  it("위치·초대 코인의 하루 50코인 제한과는 따로 센다", async () => {
    const uid = newUid();
    await makeUser(uid, "성연");
    await setDailyCoins(uid, 50);
    expect(await claimAdReward.run(req({ viewId: viewId() }, uid))).toMatchObject({ granted: 2 });
    expect((await db.doc(`coinDaily/${uid}`).get()).get("earned")).toBe(50);
  });

  it("로그인하지 않았거나, 광고 ID 가 이상하거나, 프로필이 없으면 거부", async () => {
    await expect(claimAdReward.run(req({ viewId: viewId() }))).rejects.toMatchObject({ code: "unauthenticated" });
    const uid = newUid();
    await makeUser(uid, "성연");
    await expect(claimAdReward.run(req({ viewId: "짧음" }, uid))).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(claimAdReward.run(req({ viewId: "a/b/c/d/e/f/g" }, uid))).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(claimAdReward.run(req({ viewId: viewId() }, newUid()))).rejects.toMatchObject({ code: "failed-precondition" });
  });
});
