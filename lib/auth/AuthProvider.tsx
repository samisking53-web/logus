"use client";

// 로그인 상태를 앱 전체에 알려 준다.
// - Firebase 로그인 여부 + Firestore users/{uid} 프로필이 있는지까지 함께 본다.
//   로그인했지만 프로필이 없으면 "회원가입이 덜 끝난 상태(needsProfile)"다.
// - Firebase 코드는 필요할 때만 불러온다(첫 화면을 가볍게).
import type { User } from "firebase/auth";
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";

export type Profile = {
  nickname: string;
  photoURL: string | null;
  coins: number;
};

export type AuthState =
  | { status: "unconfigured" } // Firebase 환경변수가 없음
  | { status: "loading" }
  | { status: "signedOut" }
  | { status: "needsProfile"; user: User } // 구글 로그인은 했지만 회원가입(프로필)이 아직
  | { status: "ready"; user: User; profile: Profile }
  | { status: "error"; message: string };

type AuthContextValue = {
  state: AuthState;
  /** 회원가입 직후처럼 프로필이 바뀌었을 때 다시 읽는다. */
  refreshProfile: () => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export const firebaseConfigured = Boolean(process.env.NEXT_PUBLIC_FIREBASE_API_KEY);

async function loadProfile(uid: string): Promise<Profile | null> {
  const [{ db }, { doc, getDoc }] = await Promise.all([
    import("@/lib/firebase/client"),
    import("firebase/firestore"),
  ]);
  const snap = await getDoc(doc(db, "users", uid));
  if (!snap.exists()) return null;
  const data = snap.data();
  return {
    nickname: (data.nickname as string | undefined) ?? "여행자",
    photoURL: (data.photoURL as string | null | undefined) ?? null,
    coins: (data.coins as number | undefined) ?? 0,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(
    firebaseConfigured ? { status: "loading" } : { status: "unconfigured" },
  );

  const applyUser = useCallback(async (user: User | null) => {
    if (!user) {
      setState({ status: "signedOut" });
      return;
    }
    try {
      const profile = await loadProfile(user.uid);
      setState(profile ? { status: "ready", user, profile } : { status: "needsProfile", user });
    } catch (error) {
      console.error("프로필을 불러오지 못했어요", error);
      setState({
        status: "error",
        message: "회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
      });
    }
  }, []);

  useEffect(() => {
    if (!firebaseConfigured) return;
    let unsubscribe: (() => void) | undefined;
    let cancelled = false;
    (async () => {
      const [{ auth }, { onAuthStateChanged }] = await Promise.all([
        import("@/lib/firebase/client"),
        import("firebase/auth"),
      ]);
      if (cancelled) return;
      unsubscribe = onAuthStateChanged(auth, (user) => void applyUser(user));
    })();
    return () => {
      cancelled = true;
      unsubscribe?.();
    };
  }, [applyUser]);

  const refreshProfile = useCallback(async () => {
    const { auth } = await import("@/lib/firebase/client");
    await applyUser(auth.currentUser);
  }, [applyUser]);

  const signOut = useCallback(async () => {
    const [{ auth }, { signOut: firebaseSignOut }] = await Promise.all([
      import("@/lib/firebase/client"),
      import("firebase/auth"),
    ]);
    await firebaseSignOut(auth);
  }, []);

  return (
    <AuthContext.Provider value={{ state, refreshProfile, signOut }}>{children}</AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth는 AuthProvider 안에서만 쓸 수 있어요.");
  return value;
}
