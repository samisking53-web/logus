// unlockPremiumExport: S17 DAY RECAP "영상 추출" → "프리미엄 다운로드"(워터마크 없이, 2026-11-11 팀 요청)
// 코인 50개를 빼고 열어 둔 기록(users/{uid}/premiumExports/{journeyId}_{date})을 한 트랜잭션으로 만든다.
// - 한 번 50코인을 내면 같은 여정의 같은 날 DAY RECAP 은 다시 받을 때도 무료다(열어 둔 기록이 있으면 코인을 빼지 않음).
//   영상을 만들다 실패하거나 취소해도 다시 무료로 만들 수 있어서 코인을 돌려주지 않아도 된다.
// - 열어 두기는 낸 사람만이다. 같은 여정의 다른 구성원은 따로 50코인을 낸다.
// - 일반 다운로드(워터마크 있음)는 이 함수를 부르지 않고 지금처럼 무료로 추출한다(광고 없음).
// - 이 여정의 구성원만, 여정 기간 안의 날짜만 열 수 있다. 코인이 모자라면 거부한다(failed-precondition).
// coinLedger 문서 ID는 premiumExport_{journeyId}_{date}, reason "premiumExport", amount -50.
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import { requireAuth, requireDate, requireObject, requireString } from "./common";

/** 프리미엄 추출(워터마크 없음)에 쓰는 코인(앱 journeydetail/PremiumExport.kt 의 COINS 와 같아야 한다) */
export const PREMIUM_EXPORT_COINS = 50;

/** coins: 남은 코인. charged: 이번에 뺀 코인(0이면 이미 이날을 열어 둠) */
type PremiumExportResult = { coins: number; charged: number };

export const unlockPremiumExport = onCall(async (request): Promise<PremiumExportResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const journeyId = requireString(data.journeyId, "journeyId", 1, 128);
  const date = requireDate(data.date, "날짜");

  const journeyRef = db.collection("journeys").doc(journeyId);
  const userRef = db.collection("users").doc(uid);
  const unlockId = `${journeyId}_${date}`;
  const unlockRef = userRef.collection("premiumExports").doc(unlockId);
  const ledgerRef = userRef.collection("coinLedger").doc(`premiumExport_${unlockId}`);

  return db.runTransaction(async (tx) => {
    const [journey, user, unlock] = await Promise.all([tx.get(journeyRef), tx.get(userRef), tx.get(unlockRef)]);
    const memberIds = (journey.get("memberIds") as string[] | undefined) ?? [];
    if (!journey.exists || !memberIds.includes(uid)) {
      throw new HttpsError("permission-denied", "이 여정의 구성원만 할 수 있어요.");
    }
    const startDate = journey.get("startDate") as string | undefined;
    const endDate = journey.get("endDate") as string | undefined;
    if ((startDate && date < startDate) || (endDate && date > endDate)) {
      throw new HttpsError("invalid-argument", "여정 기간 안의 날짜만 추출할 수 있어요.");
    }
    if (!user.exists) throw new HttpsError("failed-precondition", "프로필을 먼저 만들어 주세요.");
    const coins = (user.get("coins") as number | undefined) ?? 0;
    if (unlock.exists) return { coins, charged: 0 };
    if (coins < PREMIUM_EXPORT_COINS) {
      throw new HttpsError(
        "failed-precondition",
        `코인이 부족해요. 프리미엄 추출에는 ${PREMIUM_EXPORT_COINS}코인이 필요해요.`,
      );
    }
    tx.update(userRef, { coins: FieldValue.increment(-PREMIUM_EXPORT_COINS) });
    tx.create(unlockRef, { journeyId, date, createdAt: FieldValue.serverTimestamp() });
    tx.create(ledgerRef, {
      reason: "premiumExport",
      amount: -PREMIUM_EXPORT_COINS,
      journeyId,
      date,
      createdAt: FieldValue.serverTimestamp(),
    });
    return { coins: coins - PREMIUM_EXPORT_COINS, charged: PREMIUM_EXPORT_COINS };
  });
});
