// getInviteCode: 이 여정에서 쓰는 "내" 초대 코드를 돌려준다 (S01-A 여정에 초대하기 팝업에서 호출)
// 구성원마다 자기 코드가 하나씩 있다. 이 코드로 새 친구가 들어오면 코드 주인(나)이 30코인을 받는다(joinJourney).
//   - 이미 있으면 그 코드를 그대로 돌려준다(한 번 만든 코드는 바뀌지 않는다)
//   - 없으면 새 6자리 코드를 만들어 내 멤버 문서(members/{uid}.inviteCode)와 초대 요약(invites/{코드})에 함께 저장한다
//   - 여정을 만든 사람의 코드는 여정 문서의 inviteCode 다(createJourney 가 만든다)
// 구성원만 부를 수 있고, 여정이 끝났으면 새 코드를 만들지 않는다.
import { Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import {
  createInviteCode,
  inviteExpiresAtMillis,
  MAX_INVITE_CODE_TRIES,
  requireAuth,
  requireInviteOpen,
  requireObject,
  requireString,
} from "./common";

type GetInviteCodeResult = { inviteCode: string };

export const getInviteCode = onCall(async (request): Promise<GetInviteCodeResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const journeyId = requireString(data.journeyId, "journeyId", 1, 128);

  const journeyRef = db.collection("journeys").doc(journeyId);
  const memberRef = journeyRef.collection("members").doc(uid);
  const userRef = db.collection("users").doc(uid);

  // 6자리 코드는 드물게 겹칠 수 있어서, 이미 쓰는 코드면 새 코드로 다시 만든다.
  for (let attempt = 0; attempt < MAX_INVITE_CODE_TRIES; attempt++) {
    const newCode = createInviteCode();
    const inviteRef = db.collection("invites").doc(newCode);

    const code = await db.runTransaction(async (tx): Promise<string | null> => {
      // 트랜잭션에서는 읽기를 모두 끝낸 뒤 쓴다.
      const [journeySnap, memberSnap, userSnap, inviteSnap] = await Promise.all([
        tx.get(journeyRef),
        tx.get(memberRef),
        tx.get(userRef),
        tx.get(inviteRef),
      ]);
      const memberIds = (journeySnap.get("memberIds") as string[] | undefined) ?? [];
      if (!journeySnap.exists || !memberIds.includes(uid) || !memberSnap.exists) {
        throw new HttpsError("permission-denied", "이 여정의 구성원만 할 수 있어요.");
      }

      // 이미 내 코드가 있으면 그대로(만든 사람은 여정 문서의 코드)
      const existing =
        (memberSnap.get("inviteCode") as string | undefined) ??
        (journeySnap.get("ownerId") === uid ? (journeySnap.get("inviteCode") as string | undefined) : undefined);
      if (existing) return existing;

      requireInviteOpen(journeySnap.get("endDate")); // 끝난 여정에는 새 코드를 만들지 않는다
      if (inviteSnap.exists) return null; // 이미 있는 코드 → 다시 시도

      const endDate = journeySnap.get("endDate") as string;
      const inviterName = (userSnap.get("nickname") as string | undefined) ??
        (request.auth?.token.name as string | undefined) ??
        "친구";
      tx.create(inviteRef, {
        journeyId,
        name: journeySnap.get("name") as string,
        city: (journeySnap.get("city") as string | undefined) ?? "",
        startDate: journeySnap.get("startDate") as string,
        endDate,
        memberCount: memberIds.length,
        inviterId: uid,
        inviterName: inviterName.slice(0, 20),
        expiresAt: Timestamp.fromMillis(inviteExpiresAtMillis(endDate)),
      });
      tx.update(memberRef, { inviteCode: newCode });
      return newCode;
    });

    if (code) return { inviteCode: code };
  }
  throw new HttpsError("unavailable", "초대 코드를 만들지 못했어요. 잠시 후 다시 시도해 주세요.");
});
