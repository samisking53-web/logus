// previewInvite: 6자리 초대 코드로 여정 요약을 확인한다(참여는 하지 않는다)
// 홈 "초대 코드로 참여" 팝업의 "여정 확인하기" → I01 초대 확인 화면에서 보여 줄 내용을 돌려준다.
// 앱은 invites 를 직접 읽지 못하므로(6자리 코드를 넣어 보는 것 방지) 이 함수로만 확인한다.
// joinJourney 와 같은 하루 입력 횟수 제한·만료(여정이 끝났는지) 확인을 거친다.
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./admin";
import { countInviteAttempt, requireAuth, requireInviteCode, requireInviteOpen, requireObject } from "./common";

type PreviewInviteResult = {
  name: string;
  city: string;
  startDate: string;
  endDate: string;
  memberCount: number;
  inviterName: string;
  /** 여정을 만든 사람의 촬영 알림 간격(1·2·3시간, 없으면 null). 참여하면 이 값이 기본값이 된다 */
  notifyIntervalHours: number | null;
  /** 이미 이 여정의 구성원인지 */
  alreadyMember: boolean;
};

export const previewInvite = onCall(async (request): Promise<PreviewInviteResult> => {
  const uid = requireAuth(request);
  const data = requireObject(request.data);
  const inviteCode = requireInviteCode(data.inviteCode);

  await countInviteAttempt(uid);

  const inviteSnap = await db.collection("invites").doc(inviteCode).get();
  if (!inviteSnap.exists) {
    throw new HttpsError("not-found", "초대 코드를 찾을 수 없어요. 6자리를 다시 확인해 주세요.");
  }
  const journeyId = inviteSnap.get("journeyId") as string;
  const journeySnap = await db.collection("journeys").doc(journeyId).get();
  if (!journeySnap.exists) {
    throw new HttpsError("not-found", "여정을 찾을 수 없어요.");
  }
  requireInviteOpen(journeySnap.get("endDate")); // 여정이 끝났으면 거부
  const ownerId = journeySnap.get("ownerId") as string;
  const ownerMemberSnap = await journeySnap.ref.collection("members").doc(ownerId).get();
  const memberIds = (journeySnap.get("memberIds") as string[]) ?? [];

  return {
    name: inviteSnap.get("name") as string,
    city: (inviteSnap.get("city") as string | undefined) ?? "",
    startDate: inviteSnap.get("startDate") as string,
    endDate: inviteSnap.get("endDate") as string,
    memberCount: memberIds.length,
    inviterName: (inviteSnap.get("inviterName") as string | undefined) ?? "친구",
    notifyIntervalHours: (ownerMemberSnap.get("notifyIntervalHours") as number | null | undefined) ?? null,
    alreadyMember: memberIds.includes(uid),
  };
});
