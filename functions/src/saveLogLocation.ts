// saveLogLocation: 기록에 위치·장소 이름·탐색 공개 여부를 저장하고, 위치가 있으면 5코인을 준다 (S05·S07, 화면 문구 "+5코인", 2026-11-11 팀 결정으로 10 → 5)
// 코인은 users/{uid}/coinLedger/{logId} 문서가 없을 때만 준다 → 같은 기록으로 두 번 받을 수 없다.
// 하루(UTC)에 받을 수 있는 코인은 DAILY_COIN_LIMIT(50)까지다. 넘으면 50까지만 채워 주고(0코인일 수도 있다),
// 그래도 coinLedger 는 만들어서 이 기록의 위치 보상은 끝난 것으로 한다(다음 날 다시 불러도 주지 않음).
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import {
  grantDailyCoins,
  limitDailyCoins,
  readDailyCoins,
  requireAuth,
  requireObject,
  requireString,
} from "./common";
import { inboxData, inboxRef } from "./notifications";

export const LOCATION_REWARD_COINS = 5;

type Location = { lat: number; lng: number };
/** coinsGranted: 이번에 받은 코인. dailyLimitReached: 하루 50코인 제한 때문에 5코인보다 적게 받았다 */
type SaveLogLocationResult = { coinsGranted: number; dailyLimitReached: boolean };

/** 좌표를 확인한다. 없거나 (0,0)이면 결측(null)으로 본다. */
function parseLocation(value: unknown): Location | null {
  if (value === undefined || value === null) return null;
  const loc = requireObject(value);
  const { lat, lng } = loc;
  if (
    typeof lat !== "number" || typeof lng !== "number" ||
    !Number.isFinite(lat) || !Number.isFinite(lng) ||
    lat < -90 || lat > 90 || lng < -180 || lng > 180
  ) {
    throw new HttpsError("invalid-argument", "위치 좌표가 올바르지 않아요.");
  }
  if (lat === 0 && lng === 0) return null;
  return { lat, lng };
}

export const saveLogLocation = onCall(async (request): Promise<SaveLogLocationResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);

  const journeyId = requireString(data.journeyId, "journeyId", 1, 128);
  const logId = requireString(data.logId, "logId", 1, 128);
  const location = parseLocation(data.location);
  // 장소 이름은 사용자가 입력한 글자나 오픈스트리트맵(L01 위치 확인)이 알려준 이름만 저장한다. 위치가 없으면 장소 이름도 지운다.
  const placeName =
    location && data.placeName != null ? requireString(data.placeName, "장소 이름", 1, 100) : null;
  if (data.isPublic !== undefined && typeof data.isPublic !== "boolean") {
    throw new HttpsError("invalid-argument", "공개 여부가 올바르지 않아요.");
  }
  const isPublic = data.isPublic === true;

  const journeyRef = db.collection("journeys").doc(journeyId);
  const logRef = journeyRef.collection("logs").doc(logId);
  const userRef = db.collection("users").doc(uid);
  const ledgerRef = userRef.collection("coinLedger").doc(logId);

  return db.runTransaction(async (tx) => {
    const [journeySnap, logSnap, ledgerSnap, userSnap, daily] = await Promise.all([
      tx.get(journeyRef),
      tx.get(logRef),
      tx.get(ledgerRef),
      tx.get(userRef),
      readDailyCoins(tx, uid), // 오늘(UTC) 받은 코인
    ]);

    // 구성원 확인 → 기록 존재 확인 → 작성자 확인 순서로 막는다.
    const memberIds = (journeySnap.get("memberIds") as string[] | undefined) ?? [];
    if (!journeySnap.exists || !memberIds.includes(uid)) {
      throw new HttpsError("permission-denied", "이 여정의 구성원만 할 수 있어요.");
    }
    if (!logSnap.exists) {
      throw new HttpsError("not-found", "기록을 찾을 수 없어요.");
    }
    if (logSnap.get("authorId") !== uid) {
      throw new HttpsError("permission-denied", "내가 올린 기록만 위치를 저장할 수 있어요.");
    }

    tx.update(logRef, { location, placeName, isPublic });

    // 위치가 있고, 이 기록으로 받은 코인 내역이 없을 때만 지급한다(하루 50코인까지만).
    const rewarded = location !== null && !ledgerSnap.exists;
    const coinsGranted = rewarded ? limitDailyCoins(daily, LOCATION_REWARD_COINS) : 0;
    const dailyLimitReached = rewarded && coinsGranted < LOCATION_REWARD_COINS;
    if (rewarded) {
      tx.create(ledgerRef, {
        reason: "saveLogLocation",
        amount: coinsGranted,
        journeyId,
        ...(dailyLimitReached ? { dailyLimited: true } : {}),
        createdAt: FieldValue.serverTimestamp(),
      });
    }
    if (coinsGranted > 0) {
      grantDailyCoins(tx, daily, coinsGranted);
      // 앱 안 알림 목록(2026-11-09): "+5코인을 받았어요 · 위치와 함께 저장한 기록 1개"(폰 알림은 보내지 않음)
      tx.set(inboxRef(uid, `coin_${logId}`), inboxData({
        type: "coins",
        title: `+${coinsGranted}코인을 받았어요`,
        body: dailyLimitReached
          ? "위치와 함께 저장한 기록 1개 · 오늘 받을 수 있는 코인을 다 받았어요"
          : "위치와 함께 저장한 기록 1개",
        journeyId,
      }));
      if (userSnap.exists) {
        tx.update(userRef, { coins: FieldValue.increment(coinsGranted) });
      } else {
        // 프로필 문서가 아직 없으면 보안 규칙에 맞는 기본 프로필과 함께 만든다.
        const token = request.auth?.token;
        tx.create(userRef, {
          nickname: String(token?.name ?? "여행자").slice(0, 20) || "여행자",
          photoURL: typeof token?.picture === "string" && token.picture.startsWith("https://")
            ? token.picture.slice(0, 500)
            : null,
          coins: coinsGranted,
          createdAt: FieldValue.serverTimestamp(),
        });
      }
    }

    return { coinsGranted, dailyLimitReached };
  });
});
