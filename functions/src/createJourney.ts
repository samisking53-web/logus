// createJourney: 새 여정을 만든다 (S02 새 여정 만들기에서 호출)
// 여정 문서, 만든 사람의 멤버 문서, 초대 화면용 요약(invites)을 한 트랜잭션으로 함께 만든다.
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import {
  createInviteCode,
  requireAuth,
  requireDate,
  requireNotifyInterval,
  requireObject,
  requireString,
} from "./common";

type CreateJourneyResult = { journeyId: string; inviteCode: string };

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

  const journeyRef = db.collection("journeys").doc();
  const inviteCode = createInviteCode();
  const inviteRef = db.collection("invites").doc(inviteCode);
  const userRef = db.collection("users").doc(uid);

  await db.runTransaction(async (tx) => {
    // 트랜잭션에서는 읽기를 모두 끝낸 뒤 쓴다.
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
    // create는 같은 코드가 이미 있으면 실패한다(겹칠 확률은 사실상 0).
    tx.create(inviteRef, {
      journeyId: journeyRef.id,
      name,
      city,
      startDate,
      endDate,
      memberCount: 1,
      inviterName: inviterName.slice(0, 20),
    });
  });

  return { journeyId: journeyRef.id, inviteCode };
});
