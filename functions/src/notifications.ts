// 앱 안 알림함(2026-11-09 팀 그림: S01·S01-A 홈 오른쪽 위 종 버튼 → 알림 목록 화면).
// users/{uid}/notifications/{알림 ID}:
//   type(아래 NotificationType), title·body(화면에 보이는 글), journeyId(관련 여정, 없으면 null),
//   requestId(DAY RECAP 공개 요청, 없으면 null), read(처음엔 false), createdAt(서버 시각)
// 서버만 만든다. 앱은 내 알림만 읽고 read 를 true 로만 바꾼다(firestore.rules).
// 폰 알림(푸시)은 지금처럼 촬영 알림·DAY RECAP 공개 알림만 보내고, 나머지(새 기록·참여·코인)는 이 목록에만 남긴다(2026-11-09 팀 결정).
import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { db } from "./admin";

export type NotificationType =
  | "captureReminder" // 지금 기록할 시간이에요(촬영 알림)
  | "newLog" // ○○ 님이 기록을 올렸어요
  | "memberJoined" // ○○ 님이 여정에 참여했어요
  | "coins" // +10코인을 받았어요
  | "recapConsent" // DAY RECAP 공개 동의 요청
  | "recapPublished" // DAY RECAP을 탐색에 올렸어요
  | "recapRejected"; // DAY RECAP 공개가 취소됐어요

export type InboxItem = {
  type: NotificationType;
  title: string;
  body: string;
  journeyId?: string | null;
  requestId?: string | null;
};

/** 내 알림함 문서 자리. id 를 주면 그 이름으로(같은 알림이 두 번 생기지 않게), 없으면 새 ID */
export function inboxRef(uid: string, id?: string) {
  const collection = db.collection("users").doc(uid).collection("notifications");
  return id ? collection.doc(id) : collection.doc();
}

/** 알림함 문서 내용 */
export function inboxData(item: InboxItem) {
  return {
    type: item.type,
    title: item.title,
    body: item.body,
    journeyId: item.journeyId ?? null,
    requestId: item.requestId ?? null,
    read: false,
    createdAt: FieldValue.serverTimestamp(),
  };
}

/**
 * 여러 사람의 알림함에 같은 알림을 넣는다(한 번에 500개씩). id 를 주면 사람마다 같은 이름의 문서로 넣는다.
 * 알림함에 못 넣어도 본래 일(기록·참여·공개)은 그대로 끝난 것으로 본다(로그만 남김).
 */
export async function addToInboxes(uids: string[], item: InboxItem, id?: string): Promise<void> {
  const targets = [...new Set(uids)];
  try {
    for (let i = 0; i < targets.length; i += 500) {
      const batch = db.batch();
      targets.slice(i, i + 500).forEach((uid) => batch.set(inboxRef(uid, id), inboxData(item)));
      await batch.commit();
    }
  } catch (e) {
    logger.warn("알림함에 넣지 못했어요", { type: item.type, error: String(e) });
  }
}
