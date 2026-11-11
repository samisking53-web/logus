// claimAdReward: 홈(S01·S01-A) 보유 코인 상자 → "광고 보고 코인 받기" 팝업에서 보상형 광고를 끝까지 보면 앱이 부른다(2026-11-11 요청).
// - 광고 1편 = 2코인(AD_REWARD_COINS), 하루(세계 표준시 UTC 0시, 한국 오전 9시에 바뀜) 5번까지(AD_REWARD_DAILY_LIMIT) = 하루 최대 10코인.
// - 위치·초대 코인의 하루 50코인 제한(coinDaily)과는 따로 센다(광고를 봤는데 0코인이 되는 일이 없게).
// - viewId(광고 한 편을 볼 때 앱이 만드는 무작위 ID)로 같은 광고는 한 번만 준다: coinLedger 문서 ID ad_{viewId}.
// - adDaily/{uid}: date("YYYY-MM-DD", UTC), count(오늘 받은 횟수), updatedAt. 본인만 읽고(팝업 "오늘 N / 5회 사용"), 쓰기는 서버만.
// 주의: 지금은 앱이 "광고를 끝까지 봤다"고 알려 주는 것을 믿는다(구글 테스트 광고는 서버로 확인 신호를 보내지 않음).
//       앱을 뜯어고쳐 광고 없이 불러도 하루 5번(10코인)을 넘을 수는 없다. 출시 전에는 AdMob 서버 측 보상 확인(SSV)으로 바꾼다.
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import { requireAuth, requireObject, utcToday } from "./common";

/** 광고 1편을 보면 받는 코인(앱 ads/AdReward.kt 의 COINS_PER_VIEW 와 같아야 한다) */
export const AD_REWARD_COINS = 2;
/** 하루에 광고로 코인을 받을 수 있는 횟수(앱 ads/AdReward.kt 의 DAILY_LIMIT 과 같아야 한다) */
export const AD_REWARD_DAILY_LIMIT = 5;

/** 광고 한 편 ID: 영문·숫자·_- 8~64자(앱은 UUID 를 보낸다) */
const VIEW_ID = /^[A-Za-z0-9_-]{8,64}$/;

/**
 * granted: 이번에 받은 코인(0이면 못 받음), count: 오늘 받은 횟수(이번 것 포함), limit: 하루 횟수,
 * coins: 받은 뒤 보유 코인, alreadyClaimed: 같은 광고로 이미 받았음, limitReached: 오늘 횟수를 다 씀
 */
export type AdRewardResult = {
  granted: number;
  count: number;
  limit: number;
  coins: number;
  alreadyClaimed: boolean;
  limitReached: boolean;
};

export const claimAdReward = onCall(async (request): Promise<AdRewardResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const viewId = typeof data.viewId === "string" ? data.viewId.trim() : "";
  if (!VIEW_ID.test(viewId)) throw new HttpsError("invalid-argument", "광고 정보가 올바르지 않아요.");

  const userRef = db.collection("users").doc(uid);
  const dailyRef = db.collection("adDaily").doc(uid);
  const ledgerRef = userRef.collection("coinLedger").doc(`ad_${viewId}`);
  const limit = AD_REWARD_DAILY_LIMIT;

  return db.runTransaction(async (tx) => {
    const [user, daily, ledger] = await Promise.all([tx.get(userRef), tx.get(dailyRef), tx.get(ledgerRef)]);
    if (!user.exists) throw new HttpsError("failed-precondition", "프로필을 먼저 만들어 주세요.");
    const today = utcToday();
    // 날짜가 오늘이 아니면(어제 문서) 오늘은 아직 0번 받은 것으로 본다
    const count = daily.get("date") === today ? ((daily.get("count") as number | undefined) ?? 0) : 0;
    const coins = (user.get("coins") as number | undefined) ?? 0;
    if (ledger.exists) {
      return { granted: 0, count, limit, coins, alreadyClaimed: true, limitReached: count >= limit };
    }
    if (count >= limit) {
      return { granted: 0, count, limit, coins, alreadyClaimed: false, limitReached: true };
    }
    tx.update(userRef, { coins: FieldValue.increment(AD_REWARD_COINS) });
    tx.set(dailyRef, { date: today, count: count + 1, updatedAt: FieldValue.serverTimestamp() });
    tx.create(ledgerRef, { reason: "adReward", amount: AD_REWARD_COINS, viewId, createdAt: FieldValue.serverTimestamp() });
    return {
      granted: AD_REWARD_COINS,
      count: count + 1,
      limit,
      coins: coins + AD_REWARD_COINS,
      alreadyClaimed: false,
      limitReached: count + 1 >= limit,
    };
  });
});
