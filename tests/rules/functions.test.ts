// 서버 함수 테스트: 함수를 에뮬레이터 Firestore에 직접 실행해 결과를 확인한다.
// (서버 함수는 보안 규칙을 거치지 않으므로 권한 확인과 코인 중복 방지를 함수 안에서 검사한다)
import { randomUUID } from "node:crypto";
import type { CallableRequest } from "firebase-functions/v2/https";
import { describe, expect, it } from "vitest";
import { db } from "../../functions/src/admin";
import { INVITE_REWARD_COINS, inviteExpiresAtMillis } from "../../functions/src/common";
import { createJourney } from "../../functions/src/createJourney";
import { getInviteCode } from "../../functions/src/getInviteCode";
import { joinJourney } from "../../functions/src/joinJourney";
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
      name: "x", city: "포르투", startDate: "2026-09-26", endDate: "2026-09-24",
    }, newUid()))).rejects.toMatchObject({ code: "invalid-argument" });
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

describe("초대 보상(코드 주인에게 30코인)과 구성원별 초대 코드", () => {
  it("만든 사람의 코드로 새 친구가 들어오면 만든 사람이 30코인을 한 번만 받는다", async () => {
    expect(INVITE_REWARD_COINS).toBe(30);
    const owner = newUid();
    const guest = newUid();
    await makeUser(owner, "성연");
    const { journeyId, inviteCode } = await makeJourney(owner);
    expect((await db.doc(`journeys/${journeyId}/members/${owner}`).get()).get("inviteCode")).toBe(inviteCode);
    expect((await db.doc(`invites/${inviteCode}`).get()).get("inviterId")).toBe(owner);

    await joinJourney.run(req({ inviteCode }, guest));
    expect(await coinsOf(owner)).toBe(30);
    const ledger = (await db.doc(`users/${owner}/coinLedger/invite_${journeyId}_${guest}`).get()).data();
    expect(ledger).toMatchObject({ reason: "inviteFriend", amount: 30, journeyId, invitedUid: guest });

    // 같은 친구가 코드를 다시 넣어도(이미 구성원) 더 주지 않는다
    await joinJourney.run(req({ inviteCode }, guest));
    expect(await coinsOf(owner)).toBe(30);
    // 다른 친구가 들어오면 또 30
    await joinJourney.run(req({ inviteCode }, newUid()));
    expect(await coinsOf(owner)).toBe(60);
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
    expect(await coinsOf(bob)).toBe(30);
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
  it("위치를 처음 저장하면 10코인을 한 번만 준다", async () => {
    expect(LOCATION_REWARD_COINS).toBe(10); // 화면 문구 "+10P"와 같아야 한다
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

