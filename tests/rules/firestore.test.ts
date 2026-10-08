// Firestore 보안 규칙 테스트
import { readFileSync } from "node:fs";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import {
  ALICE,
  BOB,
  CAROL,
  INVITE_CODE,
  JOURNEY_ID,
  LOG_ID,
  PROJECT_ID,
  journeyDoc,
  validLog,
} from "./helpers";

let testEnv: RulesTestEnvironment;

const past = Timestamp.fromDate(new Date("2026-09-24T06:00:00Z"));

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: readFileSync("firestore.rules", "utf8") },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

// 매 테스트마다 데이터를 지우고, 규칙을 끈 상태로 기본 데이터를 넣는다.
beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "journeys", JOURNEY_ID), journeyDoc);
    await setDoc(doc(db, "journeys", JOURNEY_ID, "members", ALICE), {
      role: "owner", notifyIntervalHours: 2, joinedAt: past,
    });
    await setDoc(doc(db, "journeys", JOURNEY_ID, "members", BOB), {
      role: "member", notifyIntervalHours: 2, joinedAt: past,
    });
    await setDoc(doc(db, "journeys", JOURNEY_ID, "logs", LOG_ID), {
      ...validLog(ALICE), createdAt: past,
    });
    await setDoc(doc(db, "journeys", JOURNEY_ID, "logs", LOG_ID, "comments", "c1"), {
      authorId: BOB, body: "좋다!", createdAt: past,
    });
    await setDoc(doc(db, "journeys", JOURNEY_ID, "logs", LOG_ID, "reactions", BOB), {
      emoji: "❤️", createdAt: past,
    });
    await setDoc(doc(db, "journeys", JOURNEY_ID, "recaps", "r1"), {
      rangeType: "day", startDate: "2026-09-24", endDate: "2026-09-24",
      title: "첫날", captions: [], createdAt: past,
    });
    await setDoc(doc(db, "users", ALICE), { nickname: "성연", photoURL: null, coins: 0, createdAt: past });
    await setDoc(doc(db, "users", ALICE, "coinLedger", "oldLog"), {
      reason: "saveLogLocation", amount: 30, createdAt: past,
    });
    await setDoc(doc(db, "invites", INVITE_CODE), {
      journeyId: JOURNEY_ID, name: "우리의 포르투", city: "포르투",
      startDate: "2026-09-24", endDate: "2026-09-26", memberCount: 2, inviterName: "성연",
    });
    await setDoc(doc(db, "publicLogs", "p1"), { journeyId: JOURNEY_ID, city: "포르투", createdAt: past });
    await setDoc(doc(db, "sharedRecaps", "sharedSlug123456"), { title: "첫날" });
  });
});

const dbAs = (uid: string) => testEnv.authenticatedContext(uid).firestore();
const dbAnon = () => testEnv.unauthenticatedContext().firestore();
const logPath = ["journeys", JOURNEY_ID, "logs"] as const;

describe("구성원이 아닌 사람은 여정 데이터를 읽거나 쓸 수 없다", () => {
  it("여정·멤버·기록·댓글·반응·리캡 읽기 거부", async () => {
    const db = dbAs(CAROL);
    await assertFails(getDoc(doc(db, "journeys", JOURNEY_ID)));
    await assertFails(getDoc(doc(db, "journeys", JOURNEY_ID, "members", ALICE)));
    await assertFails(getDoc(doc(db, ...logPath, LOG_ID)));
    await assertFails(getDocs(collection(db, ...logPath)));
    await assertFails(getDoc(doc(db, ...logPath, LOG_ID, "comments", "c1")));
    await assertFails(getDoc(doc(db, ...logPath, LOG_ID, "reactions", BOB)));
    await assertFails(getDoc(doc(db, "journeys", JOURNEY_ID, "recaps", "r1")));
  });

  it("조건 없이 전체 여정 목록을 조회할 수 없다", async () => {
    await assertFails(getDocs(collection(dbAs(CAROL), "journeys")));
  });

  it("기록·댓글·반응 쓰기 거부", async () => {
    const db = dbAs(CAROL);
    await assertFails(setDoc(doc(db, ...logPath, "new"), { ...validLog(CAROL), taggedUids: [], createdAt: serverTimestamp() }));
    await assertFails(setDoc(doc(db, ...logPath, LOG_ID, "comments", "c2"), {
      authorId: CAROL, body: "안녕", createdAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(db, ...logPath, LOG_ID, "reactions", CAROL), {
      emoji: "👍", createdAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(db, ...logPath, LOG_ID), { body: "해킹" }));
    await assertFails(deleteDoc(doc(db, ...logPath, LOG_ID)));
  });

  it("스스로 memberIds에 들어가거나 멤버 문서를 만들 수 없다", async () => {
    const db = dbAs(CAROL);
    await assertFails(updateDoc(doc(db, "journeys", JOURNEY_ID), { memberIds: [ALICE, BOB, CAROL] }));
    await assertFails(setDoc(doc(db, "journeys", JOURNEY_ID, "members", CAROL), {
      role: "member", notifyIntervalHours: null, joinedAt: serverTimestamp(),
    }));
  });

  it("로그인하지 않은 사람도 읽을 수 없다", async () => {
    await assertFails(getDoc(doc(dbAnon(), "journeys", JOURNEY_ID)));
    await assertFails(getDoc(doc(dbAnon(), ...logPath, LOG_ID)));
  });
});

describe("여정", () => {
  it("구성원은 여정과 기록을 읽는다", async () => {
    const db = dbAs(BOB);
    await assertSucceeds(getDoc(doc(db, "journeys", JOURNEY_ID)));
    await assertSucceeds(getDocs(collection(db, ...logPath)));
    await assertSucceeds(getDoc(doc(db, "journeys", JOURNEY_ID, "recaps", "r1")));
  });

  it("내 여정 목록은 memberIds array-contains 조건으로 조회한다", async () => {
    await assertSucceeds(getDocs(query(collection(dbAs(BOB), "journeys"), where("memberIds", "array-contains", BOB))));
  });

  it("클라이언트는 여정을 직접 만들거나 고치지 못한다", async () => {
    const db = dbAs(ALICE);
    await assertFails(setDoc(doc(db, "journeys", "j2"), { ...journeyDoc, memberIds: [ALICE] }));
    await assertFails(updateDoc(doc(db, "journeys", JOURNEY_ID), { name: "새 이름" }));
    await assertFails(updateDoc(doc(db, "journeys", JOURNEY_ID), { memberIds: [ALICE] }));
    await assertFails(deleteDoc(doc(db, "journeys", JOURNEY_ID)));
  });

  it("리캡은 클라이언트가 쓰지 못한다", async () => {
    await assertFails(setDoc(doc(dbAs(ALICE), "journeys", JOURNEY_ID, "recaps", "r2"), { title: "x" }));
  });
});

describe("멤버 문서", () => {
  it("본인 알림 주기만 바꾼다", async () => {
    const ref = doc(dbAs(BOB), "journeys", JOURNEY_ID, "members", BOB);
    await assertSucceeds(updateDoc(ref, { notifyIntervalHours: 3 }));
    await assertSucceeds(updateDoc(ref, { notifyIntervalHours: null }));
    await assertFails(updateDoc(ref, { notifyIntervalHours: 5 }));
    await assertFails(updateDoc(ref, { role: "owner" }));
  });

  it("다른 사람의 알림 주기는 바꾸지 못한다", async () => {
    await assertFails(updateDoc(doc(dbAs(BOB), "journeys", JOURNEY_ID, "members", ALICE), { notifyIntervalHours: 1 }));
  });
});

describe("기록", () => {
  it("구성원은 올바른 기록을 만든다", async () => {
    await assertSucceeds(setDoc(doc(dbAs(BOB), ...logPath, "new"), { ...validLog(BOB), createdAt: serverTimestamp() }));
  });

  it("글 기록은 mediaPath가 null이다", async () => {
    await assertSucceeds(setDoc(doc(dbAs(BOB), ...logPath, "text1"), {
      ...validLog(BOB), mediaType: "text", mediaPath: null, createdAt: serverTimestamp(),
    }));
  });

  it("위치·장소·공개 여부를 넣어서 만들 수 없다 (saveLogLocation 전용)", async () => {
    const db = dbAs(BOB);
    const base = { ...validLog(BOB), createdAt: serverTimestamp() };
    await assertFails(setDoc(doc(db, ...logPath, "n1"), { ...base, location: { lat: 41.1, lng: -8.6 } }));
    await assertFails(setDoc(doc(db, ...logPath, "n2"), { ...base, placeName: "Rua A" }));
    await assertFails(setDoc(doc(db, ...logPath, "n3"), { ...base, isPublic: true }));
  });

  it("다른 사람 이름으로, 다른 사람 폴더 파일로, 잘못된 형식으로 만들 수 없다", async () => {
    const db = dbAs(BOB);
    const base = { ...validLog(BOB), createdAt: serverTimestamp() };
    await assertFails(setDoc(doc(db, ...logPath, "n1"), { ...base, authorId: ALICE }));
    await assertFails(setDoc(doc(db, ...logPath, "n2"), { ...base, mediaPath: `journeys/${JOURNEY_ID}/${ALICE}/x.jpg` }));
    await assertFails(setDoc(doc(db, ...logPath, "n3"), { ...base, mediaPath: "journeys/other/bob/x.jpg" }));
    await assertFails(setDoc(doc(db, ...logPath, "n4"), { ...base, extra: "필드 추가" }));
    await assertFails(setDoc(doc(db, ...logPath, "n5"), { ...base, body: "가".repeat(501) }));
    await assertFails(setDoc(doc(db, ...logPath, "n6"), { ...base, taggedUids: [CAROL] }));
    await assertFails(setDoc(doc(db, ...logPath, "n7"), { ...base, mediaType: "audio" }));
    await assertFails(setDoc(doc(db, ...logPath, "n8"), { ...base, createdAt: past }));
  });

  it("테마 태그는 0~3개, 각각 1~20자 글자", async () => {
    const db = dbAs(BOB);
    const base = { ...validLog(BOB), createdAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(db, ...logPath, "t0"), { ...base, themes: [] }));
    await assertSucceeds(setDoc(doc(db, ...logPath, "t3"), { ...base, themes: ["카페", "에그타르트", "야경"] }));
    await assertFails(setDoc(doc(db, ...logPath, "t4"), { ...base, themes: ["a", "b", "c", "d"] }));
    await assertFails(setDoc(doc(db, ...logPath, "t5"), { ...base, themes: ["가".repeat(21)] }));
    await assertFails(setDoc(doc(db, ...logPath, "t6"), { ...base, themes: [""] }));
    await assertFails(setDoc(doc(db, ...logPath, "t7"), { ...base, themes: [1] }));
    await assertFails(setDoc(doc(db, ...logPath, "t8"), { ...base, themes: "카페" }));
    // 예전 필드 이름(theme)은 받지 않는다
    const { themes: _unused, ...withoutThemes } = base;
    await assertFails(setDoc(doc(db, ...logPath, "t9"), { ...withoutThemes, theme: "카페" }));
  });

  it("작성자는 글·테마·태그만 고친다", async () => {
    const ref = doc(dbAs(ALICE), ...logPath, LOG_ID);
    await assertSucceeds(updateDoc(ref, { body: "수정한 글", themes: ["야경"], taggedUids: [ALICE, BOB] }));
    await assertFails(updateDoc(ref, { themes: ["a", "b", "c", "d"] }));
    await assertFails(updateDoc(ref, { location: { lat: 41.1, lng: -8.6 } }));
    await assertFails(updateDoc(ref, { placeName: "Rua A" }));
    await assertFails(updateDoc(ref, { isPublic: true }));
    await assertFails(updateDoc(ref, { authorId: BOB }));
    await assertFails(updateDoc(ref, { body: "가".repeat(501) }));
  });

  it("작성자가 아닌 구성원은 고치거나 지우지 못한다", async () => {
    const ref = doc(dbAs(BOB), ...logPath, LOG_ID);
    await assertFails(updateDoc(ref, { body: "남의 글 수정" }));
    await assertFails(deleteDoc(ref));
  });

  it("작성자는 지울 수 있다", async () => {
    await assertSucceeds(deleteDoc(doc(dbAs(ALICE), ...logPath, LOG_ID)));
  });
});

describe("댓글·반응", () => {
  it("구성원은 댓글을 쓰고, 본인 댓글만 고친다", async () => {
    await assertSucceeds(setDoc(doc(dbAs(ALICE), ...logPath, LOG_ID, "comments", "c2"), {
      authorId: ALICE, body: "고마워", createdAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(dbAs(ALICE), ...logPath, LOG_ID, "comments", "c3"), {
      authorId: BOB, body: "사칭", createdAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(dbAs(ALICE), ...logPath, LOG_ID, "comments", "c1"), { body: "남의 댓글" }));
    await assertSucceeds(updateDoc(doc(dbAs(BOB), ...logPath, LOG_ID, "comments", "c1"), { body: "고친 댓글" }));
  });

  it("반응은 본인 uid 문서에만 쓴다 (1인 1반응)", async () => {
    await assertSucceeds(setDoc(doc(dbAs(ALICE), ...logPath, LOG_ID, "reactions", ALICE), {
      emoji: "👍", createdAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(dbAs(ALICE), ...logPath, LOG_ID, "reactions", BOB), {
      emoji: "👎", createdAt: serverTimestamp(),
    }));
  });
});

describe("사용자·코인 (코인 중복 지급 거부)", () => {
  it("로그인한 사용자는 프로필을 읽고, 본인은 닉네임·사진만 고친다", async () => {
    await assertSucceeds(getDoc(doc(dbAs(CAROL), "users", ALICE)));
    await assertFails(getDoc(doc(dbAnon(), "users", ALICE)));
    await assertSucceeds(updateDoc(doc(dbAs(ALICE), "users", ALICE), { nickname: "새닉네임" }));
    await assertFails(updateDoc(doc(dbAs(ALICE), "users", ALICE), { nickname: "" }));
    await assertFails(updateDoc(doc(dbAs(BOB), "users", ALICE), { nickname: "남의이름" }));
  });

  it("본인 프로필은 만들 수 있지만 코인을 넣을 수 없다", async () => {
    await assertSucceeds(setDoc(doc(dbAs(CAROL), "users", CAROL), {
      nickname: "지원", photoURL: null, createdAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(dbAs(BOB), "users", BOB), {
      nickname: "승안", photoURL: null, coins: 1000, createdAt: serverTimestamp(),
    }));
  });

  it("클라이언트는 코인을 직접 늘릴 수 없다", async () => {
    await assertFails(updateDoc(doc(dbAs(ALICE), "users", ALICE), { coins: 30 }));
    await assertFails(updateDoc(doc(dbAs(ALICE), "users", ALICE), { coins: 60, nickname: "같이바꾸기" }));
  });

  it("클라이언트는 코인 내역을 만들거나 고치거나 지울 수 없다", async () => {
    const db = dbAs(ALICE);
    await assertFails(setDoc(doc(db, "users", ALICE, "coinLedger", LOG_ID), {
      reason: "saveLogLocation", amount: 30, createdAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(db, "users", ALICE, "coinLedger", "oldLog"), { amount: 3000 }));
    // 내역을 지우고 다시 받는 것도 막는다.
    await assertFails(deleteDoc(doc(db, "users", ALICE, "coinLedger", "oldLog")));
  });

  it("약관 동의 기록: 본인만 필수 항목을 모두 동의한 기록을 한 번 만든다", async () => {
    const ok = { ageOver14: true, terms: true, location: true, notifyNewLogs: false, agreedAt: serverTimestamp() };
    const ref = (uid: string, version = "2026-10-03") => doc(dbAs(uid), "users", uid, "agreements", version);
    await assertSucceeds(setDoc(ref(CAROL), ok));
    // 다시 쓰거나(수정) 지울 수 없다
    await assertFails(setDoc(ref(CAROL), { ...ok, notifyNewLogs: true }));
    await assertFails(deleteDoc(ref(CAROL)));
    // 본인만 읽는다
    await assertSucceeds(getDoc(ref(CAROL)));
    await assertFails(getDoc(doc(dbAs(BOB), "users", CAROL, "agreements", "2026-10-03")));
    // 다른 사람 이름으로 만들 수 없다
    await assertFails(setDoc(doc(dbAs(BOB), "users", CAROL, "agreements", "2026-10-04"), ok));
  });

  it("약관 동의 기록: 필수 항목이 빠지거나 형식이 틀리면 거부", async () => {
    const ok = { ageOver14: true, terms: true, location: true, notifyNewLogs: true, agreedAt: serverTimestamp() };
    const ref = (version: string) => doc(dbAs(BOB), "users", BOB, "agreements", version);
    await assertFails(setDoc(ref("2026-10-01"), { ...ok, location: false }));
    await assertFails(setDoc(ref("2026-10-02"), { ...ok, ageOver14: false }));
    await assertFails(setDoc(ref("2026-10-03"), { ...ok, terms: "yes" }));
    await assertFails(setDoc(ref("2026-10-04"), { ...ok, extra: 1 }));
    await assertFails(setDoc(ref("2026-10-05"), { ...ok, agreedAt: Timestamp.fromDate(new Date("2020-01-01")) }));
    await assertFails(setDoc(ref("v1"), ok)); // 문서 ID는 YYYY-MM-DD 버전
    await assertSucceeds(setDoc(ref("2026-10-06"), { ...ok, notifyNewLogs: false }));
  });

  it("코인 내역은 본인만 읽는다", async () => {
    await assertSucceeds(getDoc(doc(dbAs(ALICE), "users", ALICE, "coinLedger", "oldLog")));
    await assertFails(getDoc(doc(dbAs(BOB), "users", ALICE, "coinLedger", "oldLog")));
  });
});

describe("공개 문서 (서버만 쓴다)", () => {
  it("초대 요약은 앱이 읽지도 쓰지도 못한다(6자리 코드를 넣어 보는 것 방지, 서버 함수만 사용)", async () => {
    await assertFails(getDoc(doc(dbAnon(), "invites", INVITE_CODE)));
    await assertFails(getDoc(doc(dbAs(ALICE), "invites", INVITE_CODE)));
    await assertFails(getDocs(collection(dbAnon(), "invites")));
    await assertFails(setDoc(doc(dbAs(ALICE), "invites", "ABCDEF"), { journeyId: JOURNEY_ID }));
    await assertFails(updateDoc(doc(dbAs(ALICE), "invites", INVITE_CODE), { memberCount: 99 }));
  });

  it("초대 코드 입력 횟수는 본인도 읽거나 고치지 못한다", async () => {
    await assertFails(getDoc(doc(dbAs(ALICE), "inviteAttempts", ALICE)));
    await assertFails(setDoc(doc(dbAs(ALICE), "inviteAttempts", ALICE), { date: "2026-10-04", count: 0 }));
  });

  it("공개 기록 목록은 누구나 읽고, 아무도 쓰지 못한다", async () => {
    await assertSucceeds(getDocs(collection(dbAnon(), "publicLogs")));
    await assertFails(setDoc(doc(dbAs(ALICE), "publicLogs", "p2"), { city: "포르투" }));
  });

  it("공유 리캡은 slug로 한 건만 읽고, 아무도 쓰지 못한다", async () => {
    await assertSucceeds(getDoc(doc(dbAnon(), "sharedRecaps", "sharedSlug123456")));
    await assertFails(getDocs(collection(dbAnon(), "sharedRecaps")));
    await assertFails(setDoc(doc(dbAs(ALICE), "sharedRecaps", "s2"), { title: "x" }));
  });

  it("규칙에 없는 컬렉션은 거부된다", async () => {
    await assertFails(getDoc(doc(dbAs(ALICE), "anything", "x")));
    await assertFails(setDoc(doc(dbAs(ALICE), "anything", "x"), { a: 1 }));
  });
});
