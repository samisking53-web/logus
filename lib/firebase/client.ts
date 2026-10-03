// Firebase 초기화는 이 파일 한 곳에서만 한다.
// 다른 파일에서는 `import { auth, db } from "@/lib/firebase/client"`처럼 가져다 쓴다.
import { getApp, getApps, initializeApp, type FirebaseOptions } from "firebase/app";
import { connectAuthEmulator, getAuth } from "firebase/auth";
import { connectFirestoreEmulator, getFirestore } from "firebase/firestore";
import { connectFunctionsEmulator, getFunctions } from "firebase/functions";
import { connectStorageEmulator, getStorage } from "firebase/storage";

// NEXT_PUBLIC_ 값은 공개돼도 괜찮다. 실제 권한은 보안 규칙이 지킨다.
// 값은 .env.local(로컬)과 Vercel 환경변수에 넣는다. .env.example 참고.
const firebaseConfig: FirebaseOptions = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

// 개발 서버가 코드를 다시 불러와도 앱이 두 번 만들어지지 않게 한다.
export const app = getApps().length ? getApp() : initializeApp(firebaseConfig);

export const auth = getAuth(app);
export const db = getFirestore(app);
export const storage = getStorage(app);
// 서버 함수와 같은 서울 리전을 지정해야 callable 호출이 맞는 주소로 간다.
export const functions = getFunctions(app, "asia-northeast3");

// 로컬 개발: NEXT_PUBLIC_USE_FIREBASE_EMULATORS=true 이면 진짜 Firebase 대신 에뮬레이터(firebase emulators:start)에 붙는다.
// 가짜 구글 로그인 창이 뜨고, 데이터도 내 컴퓨터에만 저장된다. 배포 주소(Vercel)에서는 이 값을 넣지 않는다.
const globalForEmulators = globalThis as { __logusEmulatorsConnected?: boolean };
if (
  process.env.NEXT_PUBLIC_USE_FIREBASE_EMULATORS === "true" &&
  typeof window !== "undefined" &&
  !globalForEmulators.__logusEmulatorsConnected
) {
  globalForEmulators.__logusEmulatorsConnected = true;
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  connectStorageEmulator(storage, "127.0.0.1", 9199);
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
}
