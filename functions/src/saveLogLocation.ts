// saveLogLocation: 기록에 위치·장소 이름·탐색 공개 여부를 저장하고, 위치가 있으면 30코인을 준다 (S07)
// 코인은 users/{uid}/coinLedger/{logId} 문서가 없을 때만 준다 → 같은 기록으로 두 번 받을 수 없다.
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import { requireAuth, requireObject, requireString } from "./common";

export const LOCATION_REWARD_COINS = 30;

type Location = { lat: number; lng: number };
type SaveLogLocationResult = { coinsGranted: number };

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
  // 장소 이름은 사용자가 입력한 글자만 저장한다. 위치가 없으면 장소 이름도 지운다.
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
    const [journeySnap, logSnap, ledgerSnap, userSnap] = await Promise.all([
      tx.get(journeyRef),
      tx.get(logRef),
      tx.get(ledgerRef),
      tx.get(userRef),
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

    // 위치가 있고, 이 기록으로 받은 코인 내역이 없을 때만 지급한다.
    const coinsGranted = location && !ledgerSnap.exists ? LOCATION_REWARD_COINS : 0;
    if (coinsGranted > 0) {
      tx.create(ledgerRef, {
        reason: "saveLogLocation",
        amount: coinsGranted,
        journeyId,
        createdAt: FieldValue.serverTimestamp(),
      });
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

    return { coinsGranted };
  });
});
