// unlockRecapPlaces: 탐색(S08·S09) "장소 보기" → 위치 정보 열람 팝업 "20코인 사용"(v5 21·22쪽)
// 코인 20개를 빼고 열람 기록(users/{uid}/placeUnlocks/{recapId})을 한 트랜잭션으로 만든다.
// - 같은 리캡을 다시 열면 코인을 다시 빼지 않는다(열람 기록이 있으면 그대로 돌려줌).
// - 그 여정의 구성원은 코인 없이 볼 수 있다(보안 규칙도 구성원은 places 를 읽게 둔다).
// - 코인이 모자라면 거부한다(failed-precondition).
// coinLedger 문서 ID는 viewPlace_{recapId}, reason "viewPlace", amount -20.
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import { requireAuth, requireObject, requireString } from "./common";

/** 장소 열람에 쓰는 코인(앱 explore/PlaceUnlock.kt 의 PLACE_VIEW_COINS 와 같아야 한다) */
export const PLACE_VIEW_COINS = 20;

/** coins: 남은 코인. charged: 이번에 뺀 코인(0이면 이미 열람했거나 구성원) */
type UnlockResult = { coins: number; charged: number };

export const unlockRecapPlaces = onCall(async (request): Promise<UnlockResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const recapId = requireString(data.recapId, "recapId", 1, 200);

  const recapRef = db.collection("publicRecaps").doc(recapId);
  const userRef = db.collection("users").doc(uid);
  const unlockRef = userRef.collection("placeUnlocks").doc(recapId);
  const ledgerRef = userRef.collection("coinLedger").doc(`viewPlace_${recapId}`);

  return db.runTransaction(async (tx) => {
    const [recap, user, unlock] = await Promise.all([tx.get(recapRef), tx.get(userRef), tx.get(unlockRef)]);
    if (!recap.exists) throw new HttpsError("not-found", "공개된 DAY RECAP을 찾을 수 없어요.");
    const coins = (user.get("coins") as number | undefined) ?? 0;
    const memberIds = (recap.get("memberIds") as string[] | undefined) ?? [];
    if (unlock.exists || memberIds.includes(uid)) return { coins, charged: 0 };
    if (!user.exists || coins < PLACE_VIEW_COINS) {
      throw new HttpsError("failed-precondition", `코인이 부족해요. 장소를 보려면 ${PLACE_VIEW_COINS}코인이 필요해요.`);
    }
    tx.update(userRef, { coins: FieldValue.increment(-PLACE_VIEW_COINS) });
    tx.create(unlockRef, { recapId, journeyId: recap.get("journeyId"), createdAt: FieldValue.serverTimestamp() });
    tx.create(ledgerRef, {
      reason: "viewPlace",
      amount: -PLACE_VIEW_COINS,
      journeyId: recap.get("journeyId"),
      recapId,
      createdAt: FieldValue.serverTimestamp(),
    });
    return { coins: coins - PLACE_VIEW_COINS, charged: PLACE_VIEW_COINS };
  });
});
