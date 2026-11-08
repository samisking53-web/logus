// unpublishRecap(2026-11-08): 탐색 [내 영상]에서 꾹 눌러 "탐색에서 내리기".
// 올린 사람(publicRecaps.requesterId)만 내릴 수 있다. 공개 사본 파일(Storage public/recaps/{recapId}/)과
// publicRecaps/{recapId}(그 아래 places 포함)를 지우고, 공개 요청은 status "unpublished" 로 바꿔 다시 요청할 수 있게 한다.
// 여정의 원본 기록·영상은 그대로 남는다. 다른 사람의 좋아요·저장 문서는 남지만 모아보기에서 "볼 수 없어요"로 보인다.
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db, storage } from "./admin";
import { requireAuth, requireObject, requireString } from "./common";

export const unpublishRecap = onCall({ timeoutSeconds: 120 }, async (request): Promise<{ removed: boolean }> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const recapId = requireString(data.recapId, "recapId", 1, 200);

  const recapRef = db.collection("publicRecaps").doc(recapId);
  const snap = await recapRef.get();
  if (!snap.exists) return { removed: false }; // 이미 내려갔다(두 번 불러도 안전)
  if (snap.get("requesterId") !== uid) {
    throw new HttpsError("permission-denied", "탐색에 올린 사람만 내릴 수 있어요.");
  }

  await storage.bucket().deleteFiles({ prefix: `public/recaps/${recapId}/` });
  await db.recursiveDelete(recapRef);
  const requestRef = db.collection("recapPublishRequests").doc(recapId);
  if ((await requestRef.get()).exists) {
    await requestRef.update({ status: "unpublished", publicRecapId: null, updatedAt: FieldValue.serverTimestamp() });
  }
  return { removed: true };
});
