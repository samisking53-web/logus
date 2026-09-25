"use client";

// 프로필 사진 동그라미. 닉네임 첫 글자를 깔아 두고 그 위에 계정 사진을 올린다.
// 사진을 불러오는 중이거나, 없거나, 불러오지 못하면 첫 글자가 보인다.
import { useState } from "react";

export function AvatarPreview({ nickname, avatarUrl }: { nickname: string; avatarUrl: string | null }) {
  const [broken, setBroken] = useState(false);
  const initial = Array.from(nickname.trim())[0] ?? "";

  return (
    <div className="relative flex size-24 items-center justify-center overflow-hidden rounded-full bg-brand-soft text-4xl font-bold text-brand-strong">
      <span aria-hidden="true">{initial}</span>
      {avatarUrl && !broken && (
        // 구글·카카오 서버에 있는 사진이라 next/image 대신 img로 바로 보여준다
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={avatarUrl}
          alt=""
          referrerPolicy="no-referrer"
          onError={() => setBroken(true)}
          className="absolute inset-0 size-full object-cover"
        />
      )}
    </div>
  );
}
