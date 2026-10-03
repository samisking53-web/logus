// Admin SDK 초기화 (서버 함수 전체에서 한 번만)
// Admin SDK는 보안 규칙을 거치지 않으므로, 함수 안에서 권한을 직접 확인해야 한다.
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

if (getApps().length === 0) {
  initializeApp();
}

export const db = getFirestore();
