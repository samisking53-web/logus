"use client";

// 동그란 프로필 사진. 사진이 없거나 불러오지 못하면 기본 프로필 그림을 보여 준다.
// 기본 그림은 다크 모드에서도 같은 색이어야 해서 팔레트 값을 직접 쓴다(앰버 배경·본문·프라이머리).
import { useState } from "react";

type Props = {
  photoURL: string | null;
  size: number; // px
};

export default function Avatar({ photoURL, size }: Props) {
  // 불러오기에 실패한 주소를 기억해 둔다(주소가 바뀌면 다시 시도한다).
  const [failedURL, setFailedURL] = useState<string | null>(null);
  const showPhoto = photoURL !== null && photoURL !== failedURL;

  return (
    <div
      className="shrink-0 overflow-hidden rounded-full border-4 border-background bg-surface"
      style={{ width: size, height: size }}
    >
      {showPhoto ? (
        // 사용자가 올린 사진은 크기가 제각각이라 next/image 대신 일반 img를 쓴다.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={photoURL}
          alt=""
          referrerPolicy="no-referrer" // 구글 프로필 사진은 다른 사이트에서 부를 때 referrer가 있으면 막힐 수 있다
          onError={() => setFailedURL(photoURL)}
          className="size-full object-cover"
        />
      ) : (
        <svg viewBox="0 0 64 64" className="size-full" aria-hidden="true">
          <circle cx="32" cy="25" r="12" fill="#fdf0dc" />
          <path d="M20 26c0-10 6-15 12-15s12 5 12 15c-2-6-7-8-12-8s-10 2-12 8z" fill="#1a1725" />
          <path d="M10 62c2-12 11-18 22-18s20 6 22 18z" fill="#5b3dd6" />
        </svg>
      )}
    </div>
  );
}
