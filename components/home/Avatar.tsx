// 동그란 프로필 사진. 사진이 없으면 기본 프로필 그림을 보여 준다.
// 기본 그림은 다크 모드에서도 같은 색이어야 해서 팔레트 값을 직접 쓴다(앰버 배경·본문·프라이머리).
type Props = {
  photoURL: string | null;
  size: number; // px
};

export default function Avatar({ photoURL, size }: Props) {
  return (
    <div
      className="shrink-0 overflow-hidden rounded-full border-4 border-background bg-surface"
      style={{ width: size, height: size }}
    >
      {photoURL ? (
        // 사용자가 올린 사진은 크기가 제각각이라 next/image 대신 일반 img를 쓴다.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={photoURL} alt="" className="size-full object-cover" />
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
