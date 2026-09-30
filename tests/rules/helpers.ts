// 테스트 공용: 가짜 프로젝트 ID와 기본 데이터
import { Timestamp } from "firebase/firestore";

export const PROJECT_ID = "demo-logus";

export const ALICE = "alice"; // 여정을 만든 사람
export const BOB = "bob"; // 초대받아 참여한 사람
export const CAROL = "carol"; // 이 여정과 관계없는 사람
export const JOURNEY_ID = "journey1";
export const LOG_ID = "log1";
export const INVITE_CODE = "inviteCode123456";

export const journeyDoc = {
  name: "우리의 포르투",
  city: "포르투",
  country: "포르투갈",
  startDate: "2026-09-24",
  endDate: "2026-09-26",
  ownerId: ALICE,
  memberIds: [ALICE, BOB],
  memberCount: 2,
  inviteCode: INVITE_CODE,
  createdAt: Timestamp.fromDate(new Date("2026-09-01T00:00:00Z")),
};

/** 규칙을 통과하는 새 기록 데이터 (createdAt은 serverTimestamp로 넣는다) */
export function validLog(authorId: string) {
  return {
    authorId,
    mediaType: "photo",
    mediaPath: `journeys/${JOURNEY_ID}/${authorId}/photo1.jpg`,
    body: "함께 쉬어가는 오후",
    theme: "카페",
    taggedUids: [ALICE],
    capturedAt: Timestamp.fromDate(new Date("2026-09-24T05:20:00Z")),
    capturedTz: "Europe/Lisbon",
    location: null,
    placeName: null,
    isPublic: false,
  };
}
