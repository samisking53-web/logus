// 탐색 서버 함수 테스트: DAY RECAP 공개 동의(requestRecapPublish·answerRecapPublish)와 장소 열람(unlockRecapPlaces)
import { randomUUID } from "node:crypto";
import type { CallableRequest } from "firebase-functions/v2/https";
import { beforeAll, describe, expect, it } from "vitest";
import { db, storage } from "../../functions/src/admin";
import { createJourney } from "../../functions/src/createJourney";
import { joinJourney } from "../../functions/src/joinJourney";
import { answerRecapPublish, recapNotifier, requestRecapPublish, searchKeywords } from "../../functions/src/recapPublish";
import { PLACE_VIEW_COINS, unlockRecapPlaces } from "../../functions/src/unlockRecapPlaces";

function req<T>(data: T, uid?: string): CallableRequest<T> {
  return {
    data,
    auth: uid ? { uid, token: { name: uid } as never, rawToken: "" } : undefined,
    rawRequest: {} as never,
    acceptsStreaming: false,
  } as CallableRequest<T>;
}

const newUid = () => `u_${randomUUID().slice(0, 8)}`;
const today = new Date().toISOString().slice(0, 10);
const later = new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 10);

// 알림은 실제로 보내지 않고 보낸 내용만 모은다
const sent: Array<{ token: string; type: string }> = [];
beforeAll(() => {
  recapNotifier.send = async (messages) => {
    for (const m of messages) sent.push({ token: (m as { token: string }).token, type: m.data?.type ?? "" });
    return messages.map(() => ({ success: true }) as never);
  };
});

async function makeUser(uid: string, coins = 0) {
  await db.doc(`users/${uid}`).set({ nickname: uid, photoURL: null, coins, createdAt: new Date() });
}

/** 구성원 uids 로 여정을 만든다(첫 사람이 만든 사람) */
async function makeJourney(uids: string[]): Promise<string> {
  for (const uid of uids) await makeUser(uid);
  const { journeyId, inviteCode } = await createJourney.run(req({
    name: "우리의 포르투", city: "포르투", country: "포르투갈",
    startDate: today, endDate: later, notifyIntervalHours: 2,
  }, uids[0]));
  for (const uid of uids.slice(1)) await joinJourney.run(req({ inviteCode }, uid));
  return journeyId;
}

/** 영상 기록과 Storage 파일(영상·대표 화면)을 만든다 */
async function makeVideo(journeyId: string, authorId: string, location: { lat: number; lng: number } | null = null) {
  const ref = db.collection("journeys").doc(journeyId).collection("logs").doc();
  const mediaPath = `journeys/${journeyId}/${authorId}/${ref.id}.mp4`;
  await ref.set({
    authorId, mediaType: "video", mediaPath, body: "", themes: ["카페"], taggedUids: [],
    capturedAt: new Date(), capturedTz: "Europe/Lisbon", location,
    placeName: location ? "상벤투역" : null, isPublic: false, createdAt: new Date(),
  });
  const bucket = storage.bucket();
  await bucket.file(mediaPath).save(Buffer.from("v"), { contentType: "video/mp4", resumable: false });
  await bucket.file(mediaPath.replace(".mp4", ".jpg")).save(Buffer.from("j"), { contentType: "image/jpeg", resumable: false });
  return ref.id;
}

async function exists(path: string) {
  const [ok] = await storage.bucket().file(path).exists();
  return ok;
}

describe("requestRecapPublish·answerRecapPublish (구성원 모두 동의하면 탐색에 공개)", () => {
  it("요청 → 다른 구성원에게 알림 → 모두 동의하면 공개 사본·파일 복사·장소 분리", async () => {
    const [a, b, c] = [newUid(), newUid(), newUid()];
    const journeyId = await makeJourney([a, b, c]);
    await db.doc(`users/${b}/devices/tokenB`).set({ token: "tokenB", timeZone: "Asia/Seoul", platform: "android" });
    const logA = await makeVideo(journeyId, a, { lat: 41.14, lng: -8.61 });
    const logB = await makeVideo(journeyId, b);
    const logC = await makeVideo(journeyId, c);
    const scenes = [{ label: "11:00", logIds: [logA, logB, logC] }];

    const res = await requestRecapPublish.run(req({ journeyId, date: today, scenes }, a));
    expect(res).toMatchObject({ status: "pending", alreadyRequested: false });
    expect(sent.some((s) => s.token === "tokenB" && s.type === "recapConsent")).toBe(true);

    // 같은 날 다시 요청하면 새로 만들지 않는다
    expect(await requestRecapPublish.run(req({ journeyId, date: today, scenes }, b)))
      .toMatchObject({ alreadyRequested: true });

    expect(await answerRecapPublish.run(req({ requestId: res.requestId, approve: true }, b)))
      .toEqual({ status: "pending", publicRecapId: null });
    expect((await db.doc(`publicRecaps/${res.requestId}`).get()).exists).toBe(false);

    const done = await answerRecapPublish.run(req({ requestId: res.requestId, approve: true }, c));
    expect(done.status).toBe("published");
    const recap = await db.doc(`publicRecaps/${done.publicRecapId}`).get();
    expect(recap.get("memberIds")).toEqual([a, b, c]);
    expect(recap.get("keywords")).toEqual(expect.arrayContaining(["포르투", "카페"]));
    expect(recap.get("scenes")[0].clips).toHaveLength(3);
    // 공개 사본에는 좌표가 없고, places 에만 있다
    expect(JSON.stringify(recap.data())).not.toContain("41.14");
    expect((await db.doc(`publicRecaps/${done.publicRecapId}/places/${logA}`).get()).get("placeName")).toBe("상벤투역");
    expect(await exists(`public/recaps/${done.publicRecapId}/${logA}.mp4`)).toBe(true);
    expect(await exists(`public/recaps/${done.publicRecapId}/${logA}.jpg`)).toBe(true);

    await expect(requestRecapPublish.run(req({ journeyId, date: today, scenes }, a)))
      .rejects.toMatchObject({ code: "already-exists" });
  });

  it("한 명이라도 거절하면 공개하지 않고, 그 뒤 다시 요청할 수 있다", async () => {
    const [a, b] = [newUid(), newUid()];
    const journeyId = await makeJourney([a, b]);
    const logA = await makeVideo(journeyId, a);
    const scenes = [{ label: "09:00", logIds: [logA] }];
    const { requestId } = await requestRecapPublish.run(req({ journeyId, date: today, scenes }, a));
    expect(await answerRecapPublish.run(req({ requestId, approve: false }, b))).toEqual({ status: "rejected", publicRecapId: null });
    expect((await db.doc(`publicRecaps/${requestId}`).get()).exists).toBe(false);
    expect(await requestRecapPublish.run(req({ journeyId, date: today, scenes }, a))).toMatchObject({ status: "pending" });
  });

  it("혼자인 여정은 바로 공개된다", async () => {
    const a = newUid();
    const journeyId = await makeJourney([a]);
    const logA = await makeVideo(journeyId, a);
    const res = await requestRecapPublish.run(req({ journeyId, date: today, scenes: [{ label: "09:00", logIds: [logA] }] }, a));
    expect(res.status).toBe("published");
    expect((await db.doc(`publicRecaps/${res.requestId}`).get()).exists).toBe(true);
  });

  it("구성원이 아니거나 로그인 안 함·없는 기록·영상이 아닌 기록은 거부", async () => {
    const [a, b] = [newUid(), newUid()];
    const journeyId = await makeJourney([a, b]);
    const logA = await makeVideo(journeyId, a);
    const scenes = [{ label: "09:00", logIds: [logA] }];
    await expect(requestRecapPublish.run(req({ journeyId, date: today, scenes }, newUid())))
      .rejects.toMatchObject({ code: "permission-denied" });
    await expect(requestRecapPublish.run(req({ journeyId, date: today, scenes })))
      .rejects.toMatchObject({ code: "unauthenticated" });
    await expect(requestRecapPublish.run(req({ journeyId, date: today, scenes: [{ label: "09:00", logIds: ["nope"] }] }, a)))
      .rejects.toMatchObject({ code: "failed-precondition" });
    await expect(requestRecapPublish.run(req({ journeyId, date: today, scenes: [] }, a)))
      .rejects.toMatchObject({ code: "invalid-argument" });
    const { requestId } = await requestRecapPublish.run(req({ journeyId, date: today, scenes }, a));
    await expect(answerRecapPublish.run(req({ requestId, approve: true }, newUid())))
      .rejects.toMatchObject({ code: "permission-denied" });
  });

  it("검색 낱말은 소문자로 나눈다", () => {
    expect(searchKeywords(["Porto", "우리의 포르투", null])).toEqual(["porto", "우리의 포르투", "우리의", "포르투"]);
  });
});

describe("unlockRecapPlaces (장소 보기 20코인)", () => {
  async function publishedRecap() {
    const a = newUid();
    const journeyId = await makeJourney([a]);
    const logA = await makeVideo(journeyId, a, { lat: 41.14, lng: -8.61 });
    const { requestId } = await requestRecapPublish.run(req({ journeyId, date: today, scenes: [{ label: "09:00", logIds: [logA] }] }, a));
    return { recapId: requestId, member: a };
  }

  it("코인 20개를 한 번만 빼고 열람 기록·코인 내역을 남긴다", async () => {
    const { recapId } = await publishedRecap();
    const viewer = newUid();
    await makeUser(viewer, 50);
    expect(await unlockRecapPlaces.run(req({ recapId }, viewer))).toEqual({ coins: 30, charged: PLACE_VIEW_COINS });
    expect(await unlockRecapPlaces.run(req({ recapId }, viewer))).toEqual({ coins: 30, charged: 0 });
    expect((await db.doc(`users/${viewer}/placeUnlocks/${recapId}`).get()).exists).toBe(true);
    expect((await db.doc(`users/${viewer}/coinLedger/viewPlace_${recapId}`).get()).get("amount")).toBe(-PLACE_VIEW_COINS);
  });

  it("코인이 모자라면 거부하고, 구성원은 코인 없이 본다", async () => {
    const { recapId, member } = await publishedRecap();
    const poor = newUid();
    await makeUser(poor, 19);
    await expect(unlockRecapPlaces.run(req({ recapId }, poor))).rejects.toMatchObject({ code: "failed-precondition" });
    expect(((await db.doc(`users/${poor}`).get()).get("coins"))).toBe(19);
    expect(await unlockRecapPlaces.run(req({ recapId }, member))).toMatchObject({ charged: 0 });
    await expect(unlockRecapPlaces.run(req({ recapId: "nope" }, poor))).rejects.toMatchObject({ code: "not-found" });
    await expect(unlockRecapPlaces.run(req({ recapId }))).rejects.toMatchObject({ code: "unauthenticated" });
  });
});
