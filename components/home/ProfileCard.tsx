// 홈의 프로필 상자: 동그란 프로필 사진, 이름, 보유 코인
// - large: S01 기본 홈 (아래에 '사진 수정 ›')
// - compact: S01-A 여행 기간 중 홈 (사진 옆 연필 표시)
import type { HomeProfile } from "@/lib/home/useHomeData";
import Avatar from "./Avatar";

type Props = {
  profile: HomeProfile;
  variant: "large" | "compact";
};

// 사진 수정 화면(P03)은 아직 없어서 눌러도 동작하지 않는다. 화면을 만들면 Link로 바꾼다.
const EDIT_PHOTO_HINT = "사진 수정은 준비 중이에요";

export default function ProfileCard({ profile, variant }: Props) {
  const coins = profile.coins.toLocaleString("ko-KR");

  if (variant === "compact") {
    return (
      <section
        aria-label="내 프로필"
        className="flex items-center gap-4 rounded-3xl border border-border bg-surface px-4 py-3"
      >
        <div className="relative">
          <Avatar photoURL={profile.photoURL} size={56} />
          <button
            type="button"
            disabled
            title={EDIT_PHOTO_HINT}
            aria-label="사진 수정 (준비 중)"
            className="absolute -bottom-1 -right-1 flex size-6 items-center justify-center rounded-full bg-background text-primary-strong"
          >
            <svg viewBox="0 0 24 24" className="size-4" fill="currentColor" aria-hidden="true">
              <path d="M4 20h4L19 9l-4-4L4 16zM16.5 3.5l4 4 1-1a1.4 1.4 0 0 0 0-2l-2-2a1.4 1.4 0 0 0-2 0z" />
            </svg>
          </button>
        </div>
        <div>
          <p className="text-xl font-bold text-text">{profile.nickname}</p>
          <p className="font-semibold text-amber-text">보유 {coins} 코인</p>
        </div>
      </section>
    );
  }

  return (
    <section aria-label="내 프로필" className="rounded-3xl border border-border bg-surface p-5">
      <div className="flex items-center gap-5">
        <Avatar photoURL={profile.photoURL} size={88} />
        <div>
          <p className="text-2xl font-bold text-text">{profile.nickname}</p>
          <p className="mt-1 text-lg font-semibold text-amber-text">보유 {coins} 코인</p>
        </div>
      </div>
      <button
        type="button"
        disabled
        title={EDIT_PHOTO_HINT}
        className="mt-3 text-sm font-semibold text-primary-strong"
      >
        사진 수정 ›
      </button>
    </section>
  );
}
