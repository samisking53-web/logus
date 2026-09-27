"use client";

// 마이로그 아래쪽: 로그인한 계정과 로그아웃 버튼
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { getCurrentAccount, signOut } from "@/lib/local-auth";

export function AccountBox() {
  const router = useRouter();
  const [account, setAccount] = useState<{ nickname: string; username: string } | null>(null);

  useEffect(() => {
    const current = getCurrentAccount();
    if (current) setAccount({ nickname: current.nickname, username: current.username });
  }, []);

  function logout() {
    signOut();
    router.replace("/login");
  }

  return (
    <div className="mx-4 mb-6 flex items-center gap-3 rounded-2xl border bg-card p-4">
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold">{account ? `${account.nickname}님` : " "}</p>
        <p className="truncate text-sm text-muted-foreground">
          {account ? `@${account.username} · 이 기기에 저장된 계정` : " "}
        </p>
      </div>
      <button
        type="button"
        onClick={logout}
        className="h-10 shrink-0 rounded-xl border px-4 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:bg-muted"
      >
        로그아웃
      </button>
    </div>
  );
}
