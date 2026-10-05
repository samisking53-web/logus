// joinJourney: 6자리 초대 코드로 여정에 참여한다 (I01 초대 확인의 "초대 수락하기"에서 호출)
// memberIds·memberCount, 멤버 문서, invites 요약의 인원수를 한 트랜잭션으로 함께 고친다.
// 코드가 짧은 대신(6자리) 아래로 보완한다.
//   - 로그인한 사람만 쓸 수 있다(invites 는 앱이 직접 읽지 못하고 이 함수만 읽는다)
//   - 한 사람당 하루 INVITE_ATTEMPTS_PER_DAY 번까지만 입력할 수 있다(여러 코드를 넣어 보는 것 방지)
//   - 만든 지 7일이 지난 코드는 쓸 수 없다
import { FieldValue, type Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import { countInviteAttempt, requireAuth, requireInviteCode, requireObject } from "./common";

type JoinJourneyResult = { journeyId: string; alreadyMember: boolean };

export const joinJourney = onCall(async (request): Promise<JoinJourneyResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const inviteCode = requireInviteCode(data.inviteCode);

  await countInviteAttempt(uid);

  return db.runTransaction(async (tx) => {
    const inviteRef = db.collection("invites").doc(inviteCode);
    const inviteSnap = await tx.get(inviteRef);
    if (!inviteSnap.exists) {
      throw new HttpsError("not-found", "초대 코드를 찾을 수 없어요. 6자리를 다시 확인해 주세요.");
    }

    const journeyId = inviteSnap.get("journeyId") as string;
    const journeyRef = db.collection("journeys").doc(journeyId);
    const journeySnap = await tx.get(journeyRef);
    if (!journeySnap.exists) {
      throw new HttpsError("not-found", "여정을 찾을 수 없어요.");
    }

    const memberIds = (journeySnap.get("memberIds") as string[]) ?? [];
    // 이미 참여했으면 아무것도 바꾸지 않는다(코드를 두 번 입력해도 안전).
    if (memberIds.includes(uid)) {
      return { journeyId, alreadyMember: true };
    }

    const expiresAt = inviteSnap.get("expiresAt") as Timestamp | undefined;
    if (expiresAt && expiresAt.toMillis() < Date.now()) {
      throw new HttpsError("failed-precondition", "초대 코드가 만료됐어요. 친구에게 새 코드를 받아 주세요.");
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
