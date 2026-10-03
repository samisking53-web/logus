// joinJourney: 초대 코드로 여정에 참여한다 (/invite/[code] → I02 참여 완료에서 호출)
// memberIds·memberCount, 멤버 문서, invites 요약의 인원수를 한 트랜잭션으로 함께 고친다.
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import { requireAuth, requireInviteCode, requireObject } from "./common";

type JoinJourneyResult = { journeyId: string; alreadyMember: boolean };

export const joinJourney = onCall(async (request): Promise<JoinJourneyResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const inviteCode = requireInviteCode(data.inviteCode);

  return db.runTransaction(async (tx) => {
    const inviteRef = db.collection("invites").doc(inviteCode);
    const inviteSnap = await tx.get(inviteRef);
    if (!inviteSnap.exists) {
      throw new HttpsError("not-found", "초대 링크가 없거나 만료됐어요.");
    }

    const journeyId = inviteSnap.get("journeyId") as string;
    const journeyRef = db.collection("journeys").doc(journeyId);
    const journeySnap = await tx.get(journeyRef);
    if (!journeySnap.exists) {
      throw new HttpsError("not-found", "여정을 찾을 수 없어요.");
    }

    const memberIds = (journeySnap.get("memberIds") as string[]) ?? [];
    // 이미 참여했으면 아무것도 바꾸지 않는다(링크를 두 번 눌러도 안전).
    if (memberIds.includes(uid)) {
      return { journeyId, alreadyMember: true };
    }

    // 새 구성원의 알림 주기는 여정을 만든 사람의 설정을 기본값으로 쓴다(I02에서 본인이 바꿀 수 있다).
    const ownerId = journeySnap.get("ownerId") as string;
    const ownerMemberSnap = await tx.get(journeyRef.collection("members").doc(ownerId));
    const notifyIntervalHours = (ownerMemberSnap.get("notifyIntervalHours") as number | null) ?? null;

    const memberCount = memberIds.length + 1;
    tx.update(journeyRef, {
      memberIds: FieldValue.arrayUnion(uid),
      memberCount,
    });
    tx.create(journeyRef.collection("members").doc(uid), {
      role: "member",
      notifyIntervalHours,
      joinedAt: FieldValue.serverTimestamp(),
    });
    tx.update(inviteRef, { memberCount });

    return { journeyId, alreadyMember: false };
  });
});
