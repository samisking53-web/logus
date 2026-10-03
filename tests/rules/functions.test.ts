// 서버 함수 테스트: 함수를 에뮬레이터 Firestore에 직접 실행해 결과를 확인한다.
// (서버 함수는 보안 규칙을 거치지 않으므로 권한 확인과 코인 중복 방지를 함수 안에서 검사한다)
import { randomUUID } from "node:crypto";
import type { CallableRequest } from "firebase-functions/v2/https";
import { describe, expect, it } from "vitest";
import { db } from "../../functions/src/admin";
import { createJourney } from "../../functions/src/createJourney";
import { joinJourney } from "../../functions/src/joinJourney";
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

async function makeJourney(ownerId: string) {
  return createJourney.run(req({
    name: "우리의 포르투", city: "포르투", country: "포르투갈",
    startDate: "2026-09-24", endDate: "2026-09-26", notifyIntervalHours: 2,
  }, ownerId));
}

async function makeLog(journeyId: string, authorId: string) {
  const ref = db.collection("journeys").doc(journeyId).collection("logs").doc();
  await ref.set({
    authorId, mediaType: "photo", mediaPath: `journeys/${journeyId}/${authorId}/a.jpg`,
    body: "", theme: null, taggedUids: [], capturedAt: new Date(), capturedTz: "Europe/Lisbon",
    location: null, placeName: null, isPublic: false, createdAt: new Date(),
  });
  return ref.id;
}

const porto = { lat: 41.1413, lng: -8.6110 };

describe("createJourney", () => {
  it("여정·owner 멤버·초대 요약을 함께 만든다", async () => {
    const owner = newUid();
    const { journeyId, inviteCode } = await makeJourney(owner);

    expect(inviteCode.length).toBeGreaterThanOrEqual(12);
    const journey = (await db.doc(`journeys/${journeyId}`).get()).data();
    expect(journey).toMatchObject({ ownerId: owner, memberIds: [owner], memberCount: 1, inviteCode });
    const member = (await db.doc(`journeys/${journeyId}/members/${owner}`).get()).data();
    expect(member).toMatchObject({ role: "owner", notifyIntervalHours: 2 });
    const invite = (await db.doc(`invites/${inviteCode}`).get()).data();
    expect(invite).toMatchObject({ journeyId, name: "우리의 포르투", memberCount: 1 });
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
    await expect(joinJourney.run(req({ inviteCode: "noSuchCode123456" }, newUid())))
      .rejects.toMatchObject({ code: "not-found" });
  });
});

describe("saveLogLocation (코인 중복 지급 거부)", () => {
  it("위치를 처음 저장하면 30코인을 한 번만 준다", async () => {
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
