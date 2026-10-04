// createJourney: 새 여정을 만든다 (S02 새 여정 만들기에서 호출)
// 여정 문서, 만든 사람의 멤버 문서, 초대 요약(invites, 6자리 코드·7일 뒤 만료)을 한 트랜잭션으로 함께 만든다.
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import {
  createInviteCode,
  INVITE_VALID_DAYS,
  requireAuth,
  requireDate,
  requireNotifyInterval,
  requireObject,
  requireString,
} from "./common";

type CreateJourneyResult = {
  journeyId: string;
  inviteCode: string;
  /** 초대 코드 만료 시각(밀리초) */
  inviteExpiresAt: number;
};

/** 같은 코드가 이미 있으면 새 코드로 다시 시도하는 횟수 */
const MAX_CODE_TRIES = 5;

export const createJourney = onCall(async (request): Promise<CreateJourneyResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);

  const name = requireString(data.name, "여정 이름", 1, 40);
  const city = requireString(data.city, "도시", 1, 60);
  const country = requireString(data.country ?? "", "국가", 0, 60);
  const startDate = requireDate(data.startDate, "시작일");
  const endDate = requireDate(data.endDate, "종료일");
  if (endDate < startDate) {
    throw new HttpsError("invalid-argument", "종료일은 시작일과 같거나 뒤여야 해요.");
  }
  const notifyIntervalHours = requireNotifyInterval(data.notifyIntervalHours);

  const userRef = db.collection("users").doc(uid);
  const inviteExpiresAt = Timestamp.fromMillis(Date.now() + INVITE_VALID_DAYS * 24 * 60 * 60 * 1000);

  // 6자리 코드는 드물게 겹칠 수 있어서, 이미 쓰는 코드면 새 코드로 다시 만든다.
  for (let attempt = 0; attempt < MAX_CODE_TRIES; attempt++) {
    const journeyRef = db.collection("journeys").doc();
    const inviteCode = createInviteCode();
    const inviteRef = db.collection("invites").doc(inviteCode);

    const created = await db.runTransaction(async (tx) => {
      // 트랜잭션에서는 읽기를 모두 끝낸 뒤 쓴다.
      const inviteSnap = await tx.get(inviteRef);
      if (inviteSnap.exists) return false; // 이미 있는 코드 → 다시 시도
      const userSnap = await tx.get(userRef);
      const inviterName =
        (userSnap.get("nickname") as string | undefined) ??
        (request.auth?.token.name as string | undefined) ??
        "친구";

      tx.create(journeyRef, {
        name,
        city,
        country,
        startDate,
        endDate,
        ownerId: uid,
        memberIds: [uid],
        memberCount: 1,
        inviteCode,
        createdAt: FieldValue.serverTimestamp(),
      });
      tx.create(journeyRef.collection("members").doc(uid), {
        role: "owner",
        notifyIntervalHours,
        joinedAt: FieldValue.serverTimestamp(),
      });
      tx.create(inviteRef, {
        journeyId: journeyRef.id,
        name,
        city,
        startDate,
        endDate,
        memberCount: 1,
        inviterName: inviterName.slice(0, 20),
        expiresAt: inviteExpiresAt,
      });
      return true;
    });

    if (created) {
      return { journeyId: journeyRef.id, inviteCode, inviteExpiresAt: inviteExpiresAt.toMillis() };
    }
  }
  throw new HttpsError("unavailable", "초대 코드를 만들지 못했어요. 잠시 후 다시 시도해 주세요.");
});
