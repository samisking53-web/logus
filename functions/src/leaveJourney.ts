// leaveJourney: 초대받아 들어온 구성원이 여정에서 나간다 (S01-A 여정 카드를 길게 눌러 "여정 나가기", 2026-11-04 팀 결정)
// - memberIds 에서 나를 빼고 memberCount 를 줄이고, 내 멤버 문서(알림 간격·대표 사진 경로·내 초대 코드)를 지운다.
// - 내 초대 코드(invites/{내 코드})도 지워서 그 코드로는 더 들어올 수 없다.
// - 내가 올린 기록·영상은 다른 구성원의 여정에 그대로 남는다(나간 뒤에는 나도 볼 수 없고 지울 수 없다).
// - 내 대표 사진 파일(내 홈에서만 보이던 cover_*.jpg)은 지운다.
// - 받은 코인은 그대로. 다시 들어오려면 초대 코드가 필요하고, 같은 사람으로는 초대 코인을 다시 주지 않는다(coinLedger 가 남음).
// - 여정을 만든 사람은 나갈 수 없다(대신 deleteJourney 로 여정을 지운다).
import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db, storage } from "./admin";
import { requireAuth, requireObject, requireString } from "./common";

type LeaveJourneyResult = { left: boolean };

export const leaveJourney = onCall(async (request): Promise<LeaveJourneyResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const journeyId = requireString(data.journeyId, "journeyId", 1, 128);

  const journeyRef = db.collection("journeys").doc(journeyId);
  const memberRef = journeyRef.collection("members").doc(uid);

  const result = await db.runTransaction(async (tx) => {
    const [journeySnap, memberSnap] = await Promise.all([tx.get(journeyRef), tx.get(memberRef)]);
    const memberIds = (journeySnap.get("memberIds") as string[] | undefined) ?? [];
    // 여정이 없거나 이미 나갔으면 아무것도 바꾸지 않는다(두 번 불러도 안전)
    if (!journeySnap.exists || !memberIds.includes(uid)) return { left: false, coverPath: null };
    if (journeySnap.get("ownerId") === uid) {
      throw new HttpsError("failed-precondition", "여정을 만든 사람은 나갈 수 없어요. 여정을 삭제해 주세요.");
    }

    // 내 초대 코드: 내가 주인인 코드만 지운다(트랜잭션에서는 읽기를 모두 끝낸 뒤 쓴다)
    const myCode = memberSnap.get("inviteCode");
    const inviteRef = typeof myCode === "string" ? db.collection("invites").doc(myCode) : null;
    const inviteSnap = inviteRef ? await tx.get(inviteRef) : null;

    tx.update(journeyRef, {
      memberIds: FieldValue.arrayRemove(uid),
      memberCount: memberIds.length - 1,
    });
    tx.delete(memberRef);
    if (inviteRef && inviteSnap?.exists && inviteSnap.get("inviterId") === uid) {
      tx.delete(inviteRef);
    }
    const coverPath = memberSnap.get("coverPath");
    return {
      left: true,
      coverPath: typeof coverPath === "string" && coverPath.startsWith(`journeys/${journeyId}/${uid}/`) ? coverPath : null,
    };
  });

  // 내 홈에서만 보이던 대표 사진 파일은 더 쓸 곳이 없어서 지운다(실패해도 나가기는 끝난 것으로 본다)
  if (result.coverPath) {
    await storage.bucket().file(result.coverPath).delete({ ignoreNotFound: true })
      .catch((e: unknown) => logger.warn("나간 사람의 대표 사진 지우기 실패", e));
  }
  return { left: result.left };
});
