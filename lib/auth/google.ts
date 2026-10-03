// 구글 로그인과 회원가입(프로필 만들기)
// - 폰: signInWithRedirect (구글 페이지로 이동했다가 돌아온다). authDomain을 앱 주소로 두고
//   next.config.ts에서 /__/auth/ 를 Firebase로 연결해 두었다(CLAUDE.md "인증").
// - PC: signInWithPopup (작은 창)
import { FirebaseError } from "firebase/app";

function isMobile(): boolean {
  return /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
}

export async function signInWithGoogle(): Promise<void> {
  const [{ auth }, { GoogleAuthProvider, signInWithPopup, signInWithRedirect }] = await Promise.all([
    import("@/lib/firebase/client"),
    import("firebase/auth"),
  ]);
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" }); // 매번 계정을 고를 수 있게
  if (isMobile()) {
    await signInWithRedirect(auth, provider);
  } else {
    await signInWithPopup(auth, provider);
  }
}

/** 구글 페이지에서 돌아왔을 때 로그인 오류가 있었는지 확인한다. 성공하면 상태는 AuthProvider가 알아서 바뀐다. */
export async function checkRedirectResult(): Promise<void> {
  const [{ auth }, { getRedirectResult }] = await Promise.all([
    import("@/lib/firebase/client"),
    import("firebase/auth"),
  ]);
  await getRedirectResult(auth);
}

/** 회원가입 완료: users/{uid} 문서를 보안 규칙에 맞는 필드(nickname·photoURL·createdAt)로 만든다. */
export async function createProfile(uid: string, nickname: string, photoURL: string | null): Promise<void> {
  const [{ db }, { doc, serverTimestamp, setDoc }] = await Promise.all([
    import("@/lib/firebase/client"),
    import("firebase/firestore"),
  ]);
  await setDoc(doc(db, "users", uid), {
    nickname,
    photoURL,
    createdAt: serverTimestamp(),
  });
}

/** 구글 프로필 사진 주소가 보안 규칙(https, 500자 이하)에 맞을 때만 쓴다. */
export function safePhotoURL(url: string | null | undefined): string | null {
  return url && url.startsWith("https://") && url.length <= 500 ? url : null;
}

/** 닉네임: 앞뒤 공백을 빼고 1~20자(이모지도 한 글자로 센다) */
export function validateNickname(raw: string): { ok: true; value: string } | { ok: false; message: string } {
  const value = raw.trim();
  const length = Array.from(value).length;
  if (length === 0) return { ok: false, message: "닉네임을 입력해 주세요." };
  if (length > 20) return { ok: false, message: "닉네임은 20자 이하로 입력해 주세요." };
  return { ok: true, value };
}

/** 사용자에게 보여 줄 한국어 오류 문구. 창을 닫은 경우는 null(오류로 보지 않음) */
export function loginErrorMessage(error: unknown): string | null {
  const code = error instanceof FirebaseError ? error.code : "";
  switch (code) {
    case "auth/popup-closed-by-user":
    case "auth/cancelled-popup-request":
    case "auth/user-cancelled":
      return null;
    case "auth/popup-blocked":
      return "팝업이 막혔어요. 브라우저에서 팝업을 허용한 뒤 다시 눌러 주세요.";
    case "auth/unauthorized-domain":
      return "이 주소에서는 로그인을 쓸 수 없어요. 실서비스 주소에서 다시 시도해 주세요.";
    case "auth/network-request-failed":
      return "인터넷 연결을 확인해 주세요.";
    case "auth/operation-not-allowed":
      return "구글 로그인이 아직 켜져 있지 않아요. (Firebase 콘솔 설정 필요)";
    default:
      return "로그인하지 못했어요. 잠시 후 다시 시도해 주세요.";
  }
}
