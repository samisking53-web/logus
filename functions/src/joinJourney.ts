// joinJourney: 6자리 초대 코드로 여정에 참여한다 (I01 초대 확인의 "초대 수락하기"에서 호출)
// memberIds·memberCount, 멤버 문서, invites 요약의 인원수를 한 트랜잭션으로 함께 고친다.
// 새 친구가 들어오면 그 코드의 주인(초대한 사람, invites.inviterId)에게 INVITE_REWARD_COINS(30)코인을 준다.
//   - 코인 내역 users/{초대한 사람}/coinLedger/invite_{여정 ID}_{새 구성원 uid} 가 이미 있으면 주지 않는다(같은 친구로 두 번 받지 않음)
//   - 이미 구성원인 사람이 코드를 다시 넣거나, 초대한 사람이 구성원이 아니거나 프로필이 없으면 주지 않는다
// 코드가 짧은 대신(6자리) 아래로 보완한다.
//   - 로그인한 사람만 쓸 수 있다(invites 는 앱이 직접 읽지 못하고 이 함수만 읽는다)
//   - 한 사람당 하루 INVITE_ATTEMPTS_PER_DAY 번까지만 입력할 수 있다(여러 코드를 넣어 보는 것 방지)
//   - 여정이 끝나면(종료일이 지나면) 코드를 쓸 수 없다. 구성원마다 자기 코드가 있고 바뀌지 않는다
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import {
  countInviteAttempt,
  INVITE_REWARD_COINS,
  requireAuth,
  requireInviteCode,
  requireInviteOpen,
  requireObject,
} from "./common";

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

    requireInviteOpen(journeySnap.get("endDate")); // 여정이 끝났으면 거부

    // 새 구성원의 알림 주기는 여정을 만든 사람의 설정을 기본값으로 쓴다(I02에서 본인이 바꿀 수 있다).
    const ownerId = journeySnap.get("ownerId") as string;
    // 초대한 사람 = 이 코드의 주인. 예전에 만든 코드(inviterId 없음)는 여정을 만든 사람의 코드다.
    const inviterId = (inviteSnap.get("inviterId") as string | undefined) ?? ownerId;
    const inviterRef = db.collection("users").doc(inviterId);
    const ledgerRef = inviterRef.collection("coinLedger").doc(`invite_${journeyId}_${uid}`);
    // 트랜잭션에서는 읽기를 모두 끝낸 뒤 쓴다.
    const [ownerMemberSnap, inviterSnap, ledgerSnap] = await Promise.all([
      tx.get(journeyRef.collection("members").doc(ownerId)),
      tx.get(inviterRef),
      tx.get(ledgerRef),
    ]);
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

    // 초대 보상: 초대한 사람이 이 여정의 구성원이고 프로필이 있을 때, 이 친구로 처음 받는 코인만 준다.
    if (memberIds.includes(inviterId) && inviterSnap.exists && !ledgerSnap.exists) {
      tx.create(ledgerRef, {
        reason: "inviteFriend",
        amount: INVITE_REWARD_COINS,
        journeyId,
        invitedUid: uid,
        createdAt: FieldValue.serverTimestamp(),
      });
      tx.update(inviterRef, { coins: FieldValue.increment(INVITE_REWARD_COINS) });
    }

    return { journeyId, alreadyMember: false };
  });
});
