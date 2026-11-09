// DAY RECAP 탐색 공개(2026-11-08): 구성원 한 사람이 그날 DAY RECAP 을 탐색에 올리자고 요청하면
// 다른 구성원 모두에게 동의 요청 알림(FCM)을 보내고, 모두 동의하면 공개 사본(publicRecaps)을 만든다.
//
// - requestRecapPublish: 공개 요청을 만든다(요청한 사람은 동의한 것으로 본다). 혼자인 여정이면 바로 공개한다.
// - answerRecapPublish: 동의·거절. 한 명이라도 거절하면 요청이 끝나고(rejected), 모두 동의하면 공개한다.
// - publishRecap: 영상·대표 화면을 Storage public/recaps/{recapId}/ 로 복사하고 publicRecaps/{recapId} 를 만든다.
//   좌표·장소 이름은 publicRecaps/{recapId}/places/{logId} 에 따로 둬서, 20코인을 쓴 사람(unlockRecapPlaces)과
//   그 여정의 구성원만 읽는다(보안 규칙).
//
// recapPublishRequests/{journeyId}_{date}: 한 여정의 같은 날은 요청이 하나뿐이다(거절된 요청만 다시 할 수 있다).
//   journeyId, journeyName, city, country, date("YYYY-MM-DD"), requesterId, requesterName,
//   memberIds(요청할 때의 구성원 = 동의해야 하는 사람), approvals({uid: true}), rejectedBy(uid 또는 null),
//   status(pending|publishing|published|rejected|unpublished), scenes([{label, logIds}]), publicRecapId, createdAt, updatedAt
//   앱은 memberIds 에 들어 있는 사람만 읽고, 쓰기는 서버만 한다.
import { FieldValue, type DocumentSnapshot } from "firebase-admin/firestore";
import type { Message, SendResponse } from "firebase-admin/messaging";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db, storage } from "./admin";
import { requireAuth, requireDate, requireObject, requireString } from "./common";
import { addToInboxes, type NotificationType } from "./notifications";

/** 동의 요청 알림 채널·누르면 여는 동작(앱 notify/RecapConsent.kt 와 같아야 한다) */
export const CONSENT_CHANNEL_ID = "recap_consent";
export const CONSENT_CLICK_ACTION = "com.logus.app.RECAP_CONSENT";

/** 한 리캡에 넣을 수 있는 장면·칸·영상 수(앱 DAY RECAP 은 칸 최대 4개) */
export const MAX_SCENES = 24;
export const MAX_CLIPS_PER_SCENE = 4;
export const MAX_CLIPS = 60;

/** 알림 보내기(테스트에서는 가짜로 바꾼다) */
export const recapNotifier: { send: (messages: Message[]) => Promise<SendResponse[]> } = {
  send: async (messages) => (await getMessaging().sendEach(messages)).responses,
};

type SceneInput = { label: string; logIds: string[] };

/** 요청 받은 장면 목록 확인: [{label: "11:00", logIds: [...]}] */
function parseScenes(value: unknown): SceneInput[] {
  if (!Array.isArray(value) || value.length < 1 || value.length > MAX_SCENES) {
    throw new HttpsError("invalid-argument", "DAY RECAP 장면이 올바르지 않아요.");
  }
  const seen = new Set<string>();
  const scenes = value.map((raw) => {
    const scene = requireObject(raw);
    const label = requireString(scene.label, "장면 시각", 1, 20);
    if (!Array.isArray(scene.logIds) || scene.logIds.length < 1 || scene.logIds.length > MAX_CLIPS_PER_SCENE) {
      throw new HttpsError("invalid-argument", "장면마다 영상은 1~4개여야 해요.");
    }
    const logIds = scene.logIds.map((id) => requireString(id, "logId", 1, 128));
    logIds.forEach((id) => seen.add(id));
    return { label, logIds };
  });
  if (seen.size > MAX_CLIPS) {
    throw new HttpsError("invalid-argument", `영상은 ${MAX_CLIPS}개까지 올릴 수 있어요.`);
  }
  return scenes;
}

/** "2026-09-24" → "9.24" */
function shortDate(date: string): string {
  const [, m, d] = date.split("-");
  return `${Number(m)}.${Number(d)}`;
}

/** 대표 화면(썸네일) 경로: 영상과 같은 폴더의 {logId}.jpg */
function thumbnailPath(mediaPath: string): string {
  return mediaPath.replace(/\.[^/.]+$/, ".jpg");
}

/** 검색 낱말: 소문자, 공백으로 나눈 낱말(2글자 이상은 그대로, 너무 긴 것은 자름) */
export function searchKeywords(parts: Array<string | null | undefined>): string[] {
  const words = new Set<string>();
  for (const part of parts) {
    if (!part) continue;
    const whole = part.trim().toLowerCase();
    if (whole) words.add(whole.slice(0, 40));
    for (const word of whole.split(/[\s,·#]+/)) {
      if (word) words.add(word.slice(0, 40));
    }
  }
  return [...words].slice(0, 50);
}

/**
 * uid 들의 기기(FCM 토큰)로 알림을 보낸다. 실패해도 요청·공개는 그대로 진행한다.
 * 앱 안 알림 목록(users/{uid}/notifications, 2026-11-09)에도 같은 제목·내용을 남긴다(type = recapConsent·recapPublished·recapRejected).
 */
async function notify(uids: string[], title: string, body: string, data: Record<string, string>): Promise<void> {
  const type = data.type as NotificationType;
  await addToInboxes(uids, { type, title, body, requestId: data.requestId ?? null }, `${type}_${data.requestId ?? Date.now()}`);
  try {
    const messages: Message[] = [];
    const owners: Array<{ uid: string; token: string }> = [];
    for (const uid of uids) {
      const devices = await db.collection("users").doc(uid).collection("devices").get();
      for (const device of devices.docs) {
        messages.push({
          token: device.id,
          notification: { title, body },
          data,
          android: {
            priority: "high",
            notification: { channelId: CONSENT_CHANNEL_ID, clickAction: CONSENT_CLICK_ACTION },
          },
        });
        owners.push({ uid, token: device.id });
      }
    }
    if (messages.length === 0) return;
    const responses = await recapNotifier.send(messages);
    // 몇 대에 보냈고 몇 대가 실패했는지 남긴다(폰에 알림이 안 뜰 때 원인 찾기용)
    const failed = responses.map((r, i) => (r.success ? null : `${owners[i].uid}:${r.error?.code}`)).filter(Boolean);
    logger.info("DAY RECAP 알림 보냄", { type: data.type, sent: responses.length - failed.length, failed });
    // 더는 쓸 수 없는 토큰은 기기 문서를 지운다(sendCaptureReminders 와 같은 방식)
    await Promise.all(responses.map(async (res, i) => {
      const code = res.error?.code;
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
        await db.collection("users").doc(owners[i].uid).collection("devices").doc(owners[i].token).delete();
      }
    }));
  } catch (e) {
    logger.warn("DAY RECAP 공개 알림을 보내지 못했어요", e);
  }
}

/** 지금도 이 여정의 구성원인 사람만 동의해야 한다(요청 뒤에 나간 사람은 빼고 센다) */
function requiredApprovers(request: DocumentSnapshot, journey: DocumentSnapshot): string[] {
  const asked = (request.get("memberIds") as string[] | undefined) ?? [];
  const current = new Set((journey.get("memberIds") as string[] | undefined) ?? []);
  return asked.filter((uid) => current.has(uid));
}

function allApproved(request: DocumentSnapshot, journey: DocumentSnapshot, extra?: string): boolean {
  const approvals = { ...((request.get("approvals") as Record<string, boolean> | undefined) ?? {}) };
  if (extra) approvals[extra] = true;
  return requiredApprovers(request, journey).every((uid) => approvals[uid] === true);
}

/**
 * 모두 동의한 요청을 탐색에 공개한다. 영상·대표 화면을 public/ 로 복사하고 공개 사본을 만든다.
 * 중간에 실패하면 요청을 다시 pending 으로 돌려서, 다시 동의(answerRecapPublish)를 누르면 이어서 공개한다.
 */
export async function publishRecap(requestId: string): Promise<string> {
  const requestRef = db.collection("recapPublishRequests").doc(requestId);
  const request = await requestRef.get();
  const journeyId = request.get("journeyId") as string;
  const recapId = requestId;
  try {
    const journey = await db.collection("journeys").doc(journeyId).get();
    if (!journey.exists) throw new HttpsError("not-found", "여정을 찾을 수 없어요.");
    const scenesIn = (request.get("scenes") as SceneInput[] | undefined) ?? [];
    const logsRef = journey.ref.collection("logs");
    const logIds = [...new Set(scenesIn.flatMap((s) => s.logIds))];
    const logSnaps = logIds.length ? await db.getAll(...logIds.map((id) => logsRef.doc(id))) : [];
    const logs = new Map(logSnaps.filter((s) => s.exists).map((s) => [s.id, s]));

    const bucket = storage.bucket();
    const copied = new Map<string, { videoPath: string; thumbPath: string | null }>();
    await Promise.all([...logs.values()].map(async (log) => {
      const mediaPath = log.get("mediaPath") as string | null;
      if (log.get("mediaType") !== "video" || !mediaPath) return;
      const videoPath = `public/recaps/${recapId}/${log.id}.mp4`;
      await bucket.file(mediaPath).copy(bucket.file(videoPath));
      // 대표 화면은 없을 수도 있다(그때는 앱이 보라 그림을 그린다)
      const thumbPath = `public/recaps/${recapId}/${log.id}.jpg`;
      const thumbCopied = await bucket.file(thumbnailPath(mediaPath)).copy(bucket.file(thumbPath))
        .then(() => true, () => false);
      copied.set(log.id, { videoPath, thumbPath: thumbCopied ? thumbPath : null });
    }));

    const scenes = scenesIn
      .map((scene) => ({
        label: scene.label,
        clips: scene.logIds.filter((id) => copied.has(id)).map((id) => {
          const log = logs.get(id)!;
          return {
            logId: id,
            authorId: log.get("authorId") as string,
            videoPath: copied.get(id)!.videoPath,
            thumbPath: copied.get(id)!.thumbPath,
            capturedAt: log.get("capturedAt") ?? null,
          };
        }),
      }))
      .filter((scene) => scene.clips.length > 0);
    if (scenes.length === 0) {
      throw new HttpsError("failed-precondition", "올릴 영상이 남아 있지 않아요(기록이 지워졌어요).");
    }

    // 영상에 나오는 사람의 프로필(구성원 순서). 나간 사람은 "구성원"·기본 프로필로
    const memberIds = (journey.get("memberIds") as string[] | undefined) ?? [];
    const authorIds = [...new Set(scenes.flatMap((s) => s.clips.map((c) => c.authorId)))];
    const ordered = [...memberIds.filter((u) => authorIds.includes(u)), ...authorIds.filter((u) => !memberIds.includes(u))];
    const users = await db.getAll(...ordered.map((uid) => db.collection("users").doc(uid)));
    const members = users.map((u) => ({
      uid: u.id,
      nickname: memberIds.includes(u.id) ? ((u.get("nickname") as string | undefined) ?? "구성원") : "구성원",
      photoURL: memberIds.includes(u.id) ? ((u.get("photoURL") as string | null | undefined) ?? null) : null,
    }));

    const themes = [...new Set([...logs.values()].flatMap((l) => (l.get("themes") as string[] | undefined) ?? []))]
      .slice(0, 10);
    const name = journey.get("name") as string;
    const city = journey.get("city") as string;
    const country = (journey.get("country") as string | null | undefined) ?? null;
    const date = request.get("date") as string;

    const recapRef = db.collection("publicRecaps").doc(recapId);
    const batch = db.batch();
    batch.set(recapRef, {
      journeyId,
      journeyName: name,
      city,
      country,
      date,
      startDate: journey.get("startDate") ?? null,
      endDate: journey.get("endDate") ?? null,
      memberIds,
      members,
      scenes,
      themes,
      keywords: searchKeywords([city, country, name, ...themes]),
      clipCount: copied.size,
      likeCount: 0,
      saveCount: 0,
      requesterId: request.get("requesterId"),
      createdAt: FieldValue.serverTimestamp(),
    });
    // 좌표·장소 이름은 따로(20코인을 쓴 사람과 구성원만 읽는다)
    for (const [logId] of copied) {
      const log = logs.get(logId)!;
      const location = log.get("location") as { lat: number; lng: number } | null;
      batch.set(recapRef.collection("places").doc(logId), {
        authorId: log.get("authorId"),
        location: location ?? null,
        placeName: location ? ((log.get("placeName") as string | null) ?? null) : null,
        capturedAt: log.get("capturedAt") ?? null,
      });
    }
    batch.update(requestRef, {
      status: "published",
      publicRecapId: recapId,
      updatedAt: FieldValue.serverTimestamp(),
    });
    await batch.commit();

    await notify(
      requiredApprovers(request, journey),
      "DAY RECAP을 탐색에 올렸어요",
      `${name} · ${shortDate(date)} · 모두 동의해서 전체 공개로 올라갔어요`,
      { type: "recapPublished", requestId, recapId },
    );
    return recapId;
  } catch (e) {
    logger.error("DAY RECAP 공개 실패", e);
    await requestRef.update({ status: "pending", updatedAt: FieldValue.serverTimestamp() });
    if (e instanceof HttpsError) throw e;
    throw new HttpsError("internal", "탐색에 올리지 못했어요. 잠시 뒤 다시 동의를 눌러 주세요.");
  }
}

type RequestResult = { requestId: string; status: string; alreadyRequested: boolean };

export const requestRecapPublish = onCall({ timeoutSeconds: 300 }, async (request): Promise<RequestResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const journeyId = requireString(data.journeyId, "journeyId", 1, 128);
  const date = requireDate(data.date, "날짜");
  const scenes = parseScenes(data.scenes);

  const journeyRef = db.collection("journeys").doc(journeyId);
  const requestId = `${journeyId}_${date}`;
  const requestRef = db.collection("recapPublishRequests").doc(requestId);
  const logIds = [...new Set(scenes.flatMap((s) => s.logIds))];

  const result = await db.runTransaction(async (tx) => {
    const journey = await tx.get(journeyRef);
    const memberIds = (journey.get("memberIds") as string[] | undefined) ?? [];
    if (!journey.exists || !memberIds.includes(uid)) {
      throw new HttpsError("permission-denied", "이 여정의 구성원만 할 수 있어요.");
    }
    const [existing, me, ...logs] = await Promise.all([
      tx.get(requestRef),
      tx.get(db.collection("users").doc(uid)),
      ...logIds.map((id) => tx.get(journeyRef.collection("logs").doc(id))),
    ]);
    for (const log of logs) {
      if (!log.exists || log.get("mediaType") !== "video" || !log.get("mediaPath")) {
        throw new HttpsError("failed-precondition", "올릴 수 없는 기록이 있어요. 화면을 새로 고쳐 주세요.");
      }
    }
    if (existing.exists) {
      const status = existing.get("status") as string;
      if (status === "published") {
        throw new HttpsError("already-exists", "이날 DAY RECAP은 이미 탐색에 올렸어요.");
      }
      if (status === "pending" || status === "publishing") {
        return { requestId, status, alreadyRequested: true, publishNow: false, journey };
      }
      // 거절된 요청·탐색에서 내린 요청(unpublished)은 새로 요청할 수 있다
    }
    const publishNow = memberIds.length === 1;
    tx.set(requestRef, {
      journeyId,
      journeyName: journey.get("name"),
      city: journey.get("city"),
      country: journey.get("country") ?? null,
      date,
      requesterId: uid,
      requesterName: (me.get("nickname") as string | undefined) ?? "구성원",
      memberIds,
      approvals: { [uid]: true },
      rejectedBy: null,
      status: publishNow ? "publishing" : "pending",
      scenes,
      publicRecapId: null,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { requestId, status: publishNow ? "publishing" : "pending", alreadyRequested: false, publishNow, journey };
  });

  if (result.publishNow) {
    await publishRecap(requestId);
    return { requestId, status: "published", alreadyRequested: false };
  }
  if (!result.alreadyRequested) {
    const journey = result.journey;
    const memberIds = (journey.get("memberIds") as string[]).filter((m) => m !== uid);
    const me = await db.collection("users").doc(uid).get();
    const nickname = (me.get("nickname") as string | undefined) ?? "구성원";
    await notify(
      memberIds,
      "DAY RECAP 공개 동의 요청",
      `${nickname} 님이 '${journey.get("name")}' ${shortDate(date)} DAY RECAP을 탐색에 올리려고 해요. 눌러서 동의해 주세요`,
      { type: "recapConsent", requestId },
    );
  }
  return { requestId: result.requestId, status: result.status, alreadyRequested: result.alreadyRequested };
});

type AnswerResult = { status: string; publicRecapId: string | null };

export const answerRecapPublish = onCall({ timeoutSeconds: 300 }, async (request): Promise<AnswerResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const requestId = requireString(data.requestId, "requestId", 1, 200);
  if (typeof data.approve !== "boolean") {
    throw new HttpsError("invalid-argument", "동의 여부가 올바르지 않아요.");
  }
  const approve = data.approve;
  const requestRef = db.collection("recapPublishRequests").doc(requestId);

  const outcome = await db.runTransaction(async (tx) => {
    const req = await tx.get(requestRef);
    if (!req.exists) throw new HttpsError("not-found", "공개 요청을 찾을 수 없어요.");
    const journey = await tx.get(db.collection("journeys").doc(req.get("journeyId") as string));
    if (!journey.exists) throw new HttpsError("not-found", "여정을 찾을 수 없어요.");
    if (!requiredApprovers(req, journey).includes(uid)) {
      throw new HttpsError("permission-denied", "이 여정의 구성원만 답할 수 있어요.");
    }
    const status = req.get("status") as string;
    if (status === "published") return { status, publish: false, publicRecapId: req.get("publicRecapId") as string };
    if (status === "rejected") return { status, publish: false, publicRecapId: null };
    if (status === "publishing") {
      throw new HttpsError("aborted", "지금 탐색에 올리는 중이에요. 잠시 뒤 다시 확인해 주세요.");
    }
    if (!approve) {
      tx.update(requestRef, {
        [`approvals.${uid}`]: false,
        rejectedBy: uid,
        status: "rejected",
        updatedAt: FieldValue.serverTimestamp(),
      });
      return { status: "rejected", publish: false, publicRecapId: null, notifyReject: true, req, journey };
    }
    const everyone = allApproved(req, journey, uid);
    tx.update(requestRef, {
      [`approvals.${uid}`]: true,
      status: everyone ? "publishing" : "pending",
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { status: everyone ? "publishing" : "pending", publish: everyone, publicRecapId: null };
  });

  if (outcome.publish) {
    const recapId = await publishRecap(requestId);
    return { status: "published", publicRecapId: recapId };
  }
  if ("notifyReject" in outcome && outcome.notifyReject) {
    const others = requiredApprovers(outcome.req, outcome.journey).filter((m) => m !== uid);
    await notify(
      others,
      "DAY RECAP 공개가 취소됐어요",
      `${outcome.journey.get("name")} · ${shortDate(outcome.req.get("date") as string)} · 구성원 중 한 명이 동의하지 않았어요`,
      { type: "recapRejected", requestId },
    );
  }
  return { status: outcome.status, publicRecapId: outcome.publicRecapId };
});
