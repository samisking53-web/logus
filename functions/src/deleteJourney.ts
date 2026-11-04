// deleteJourney: 여정을 만든 사람이 여정을 통째로 지운다 (S01-A 여정 카드를 길게 눌러 "여정 삭제", 2026-11-04 팀 그림)
// 구성원 모두의 홈·마이로그에서 사라지고, 모든 구성원의 기록·영상·첫 장면·대표 사진·초대 코드가 함께 지워진다. 되돌릴 수 없다.
// 이미 받은 코인과 coinLedger 는 그대로 둔다(2026-11-04 팀 결정, N01 기록 삭제와 같음).
// 앱이 직접 지우지 않는 까닭: 다른 구성원의 기록·파일은 보안 규칙상 본인만 지울 수 있어서, 서버(Admin SDK)가 확인한 뒤 지운다.
// 지우는 순서(중간에 실패해도 다시 부르면 이어서 지울 수 있게 여정 문서를 맨 마지막에 지운다):
//   ① 초대 코드(invites) → 새 사람이 못 들어오게
//   ② 탐색 공개 사본(publicLogs)
//   ③ Storage journeys/{journeyId}/ 아래 모든 파일(구성원마다 영상·첫 장면·대표 사진)
//   ④ 여정 문서와 그 아래 전부(members·logs·댓글·반응·recaps)
import type { Query } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db, storage } from "./admin";
import { requireAuth, requireObject, requireString } from "./common";

type DeleteJourneyResult = { deleted: boolean };

/** 여정이 크면(기록·파일이 많으면) 오래 걸릴 수 있어 5분까지 기다린다 */
export const deleteJourney = onCall({ timeoutSeconds: 300 }, async (request): Promise<DeleteJourneyResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const journeyId = requireString(data.journeyId, "journeyId", 1, 128);

  const journeyRef = db.collection("journeys").doc(journeyId);
  const snap = await journeyRef.get();
  if (!snap.exists) return { deleted: false }; // 이미 지워졌다(두 번 불러도 안전)

  const memberIds = (snap.get("memberIds") as string[] | undefined) ?? [];
  if (!memberIds.includes(uid)) {
    throw new HttpsError("permission-denied", "이 여정의 구성원만 할 수 있어요.");
  }
  if (snap.get("ownerId") !== uid) {
    throw new HttpsError("permission-denied", "여정을 만든 사람만 여정을 삭제할 수 있어요.");
  }

  await deleteAll(db.collection("invites").where("journeyId", "==", journeyId));
  await deleteAll(db.collection("publicLogs").where("journeyId", "==", journeyId));
  await storage.bucket().deleteFiles({ prefix: `journeys/${journeyId}/` });
  await db.recursiveDelete(journeyRef);
  return { deleted: true };
});

/** 찾은 문서를 500개씩 묶어 지운다(한 번에 쓸 수 있는 최대 개수) */
async function deleteAll(query: Query): Promise<void> {
  const snap = await query.get();
  for (let i = 0; i < snap.docs.length; i += 500) {
    const batch = db.batch();
    snap.docs.slice(i, i + 500).forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
  }
}
