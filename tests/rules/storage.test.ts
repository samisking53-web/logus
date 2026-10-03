// Storage 보안 규칙 테스트 (구성원 확인에 Firestore 에뮬레이터 데이터를 함께 쓴다)
import { readFileSync } from "node:fs";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { getBytes, ref, uploadBytes } from "firebase/storage";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { ALICE, BOB, CAROL, JOURNEY_ID, PROJECT_ID, journeyDoc } from "./helpers";

let testEnv: RulesTestEnvironment;

const MB = 1024 * 1024;
const bytes = (size: number) => new Uint8Array(size);
const image = { contentType: "image/jpeg" };
const video = { contentType: "video/mp4" };
const aliceFile = `journeys/${JOURNEY_ID}/${ALICE}/photo1.jpg`;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: readFileSync("firestore.rules", "utf8") },
    storage: { rules: readFileSync("storage.rules", "utf8") },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.clearStorage();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "journeys", JOURNEY_ID), journeyDoc);
    await uploadBytes(ref(ctx.storage(), aliceFile), bytes(100), image);
    await uploadBytes(ref(ctx.storage(), "public/p1.jpg"), bytes(100), image);
  });
});

const storageAs = (uid: string) => testEnv.authenticatedContext(uid).storage();

describe("여정 미디어", () => {
  it("구성원은 본인 폴더에 사진·영상을 올린다", async () => {
    const s = storageAs(BOB);
    await assertSucceeds(uploadBytes(ref(s, `journeys/${JOURNEY_ID}/${BOB}/a.jpg`), bytes(1000), image));
    await assertSucceeds(uploadBytes(ref(s, `journeys/${JOURNEY_ID}/${BOB}/b.mp4`), bytes(11 * MB), video));
  });

  it("구성원은 다른 구성원의 파일을 읽을 수 있다", async () => {
    await assertSucceeds(getBytes(ref(storageAs(BOB), aliceFile)));
  });

  it("다른 사람 폴더에는 올리지 못한다", async () => {
    await assertFails(uploadBytes(ref(storageAs(BOB), `journeys/${JOURNEY_ID}/${ALICE}/x.jpg`), bytes(100), image));
  });

  it("구성원이 아닌 사람은 읽기·쓰기 모두 거부", async () => {
    const s = storageAs(CAROL);
    await assertFails(getBytes(ref(s, aliceFile)));
    await assertFails(uploadBytes(ref(s, `journeys/${JOURNEY_ID}/${CAROL}/x.jpg`), bytes(100), image));
    await assertFails(getBytes(ref(testEnv.unauthenticatedContext().storage(), aliceFile)));
  });

  it("크기 제한: 이미지 10MB 미만, 영상 50MB 미만", async () => {
    const s = storageAs(BOB);
    await assertFails(uploadBytes(ref(s, `journeys/${JOURNEY_ID}/${BOB}/big.jpg`), bytes(10 * MB), image));
    await assertFails(uploadBytes(ref(s, `journeys/${JOURNEY_ID}/${BOB}/big.mp4`), bytes(50 * MB), video));
  });

  it("이미지·영상이 아닌 파일은 거부", async () => {
    await assertFails(uploadBytes(ref(storageAs(BOB), `journeys/${JOURNEY_ID}/${BOB}/a.txt`), bytes(10), { contentType: "text/plain" }));
  });
});

describe("프로필 사진", () => {
  const profile = (uid: string) => `users/${uid}/profile_1.jpg`;

  it("본인은 이미지를 올리고, 로그인한 사람은 볼 수 있다", async () => {
    await assertSucceeds(uploadBytes(ref(storageAs(BOB), profile(BOB)), bytes(1000), image));
    await assertSucceeds(getBytes(ref(storageAs(CAROL), profile(BOB))));
  });

  it("다른 사람 폴더·이미지가 아닌 파일·5MB 이상은 거부", async () => {
    await assertFails(uploadBytes(ref(storageAs(CAROL), profile(BOB)), bytes(1000), image));
    await assertFails(uploadBytes(ref(storageAs(BOB), `users/${BOB}/a.txt`), bytes(10), { contentType: "text/plain" }));
    await assertFails(uploadBytes(ref(storageAs(BOB), profile(BOB)), bytes(5 * MB), image));
  });

  it("로그인하지 않으면 볼 수 없다", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await uploadBytes(ref(ctx.storage(), profile(ALICE)), bytes(100), image);
    });
    await assertFails(getBytes(ref(testEnv.unauthenticatedContext().storage(), profile(ALICE))));
  });
});

describe("공개 사본", () => {
  it("누구나 읽고, 아무도 쓰지 못한다", async () => {
    await assertSucceeds(getBytes(ref(testEnv.unauthenticatedContext().storage(), "public/p1.jpg")));
    await assertFails(uploadBytes(ref(storageAs(ALICE), "public/p2.jpg"), bytes(10), image));
  });

  it("규칙에 없는 경로는 거부", async () => {
    await assertFails(uploadBytes(ref(storageAs(ALICE), "other/x.jpg"), bytes(10), image));
  });
});
