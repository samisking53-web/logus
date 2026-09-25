// 프로필 동그라미. 닉네임 첫 글자를 보여준다 (사진 올리기는 나중에 만든다)
export function InitialAvatar({ nickname }: { nickname: string }) {
  const initial = Array.from(nickname.trim())[0] ?? "";
  return (
    <div
      aria-hidden="true"
      className="flex size-24 items-center justify-center rounded-full bg-brand-soft text-4xl font-bold text-brand-strong"
    >
      {initial}
    </div>
  );
}
