// 탐색 좋아요·저장 숫자(2026-11-08): 앱은 users/{uid}/recapLikes|recapSaves/{recapId} 를 만들고 지우기만 하고,
// 이 트리거가 publicRecaps/{recapId} 의 likeCount·saveCount 를 1씩 늘리고 줄인다(공개 사본은 서버만 쓴다).
// 리캡이 이미 내려갔으면(없으면) 아무것도 하지 않는다. 0 밑으로는 내려가지 않는다.
import { FieldValue } from "firebase-admin/firestore";
import { onDocumentCreated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { db } from "./admin";

export type MarkField = "likeCount" | "saveCount";

/** publicRecaps/{recapId} 의 숫자를 delta 만큼 바꾼다(트리거 본체, 테스트에서 바로 부른다) */
export async function applyMarkCount(recapId: string, field: MarkField, delta: 1 | -1): Promise<void> {
  const ref = db.collection("publicRecaps").doc(recapId);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return;
    const current = (snap.get(field) as number | undefined) ?? 0;
    tx.update(ref, { [field]: delta < 0 ? Math.max(0, current - 1) : FieldValue.increment(1) });
  });
}

export const onRecapLikeCreated = onDocumentCreated("users/{uid}/recapLikes/{recapId}", (event) =>
  applyMarkCount(event.params.recapId, "likeCount", 1));
export const onRecapLikeDeleted = onDocumentDeleted("users/{uid}/recapLikes/{recapId}", (event) =>
  applyMarkCount(event.params.recapId, "likeCount", -1));
export const onRecapSaveCreated = onDocumentCreated("users/{uid}/recapSaves/{recapId}", (event) =>
  applyMarkCount(event.params.recapId, "saveCount", 1));
export const onRecapSaveDeleted = onDocumentDeleted("users/{uid}/recapSaves/{recapId}", (event) =>
  applyMarkCount(event.params.recapId, "saveCount", -1));
