// S01-A 여행 기간 중 홈: 진행 중인 여정의 대표 화면·기간·인원과 "지금 기록하기"
// 대표 화면은 카메라에서 영상을 찍은 뒤 고른 장면이다. 아직 없으면 안내를 보여준다
import Link from "next/link";
import { Video } from "lucide-react";
import { formatRangeDot } from "@/lib/dates";
import type { Journey } from "@/lib/local-journeys";

export function OngoingHome({ journey }: { journey: Journey }) {
  return (
    <section className="flex flex-col gap-4 break-keep px-4 pb-6 pt-2">
      <p className="flex min-h-12 items-center rounded-2xl bg-brand-soft px-5 py-2 text-base font-semibold text-brand-strong">
        진행 중 · {journey.name}
      </p>

      <div className="aspect-[16/10] w-full overflow-hidden rounded-3xl bg-brand-soft">
        {journey.coverImage ? (
          // eslint-disable-next-line @next/next/no-img-element -- 이 기기에 저장한 이미지 글자라 next/image를 쓸 수 없다
          <img src={journey.coverImage} alt={`${journey.name} 대표 화면`} className="size-full object-cover" />
        ) : (
          <div className="flex size-full flex-col items-center justify-center gap-2 px-6 text-center text-sm text-muted-foreground">
            <Video className="size-8 text-brand-strong" aria-hidden="true" />
            영상을 찍은 뒤 한 장면을 골라 대표 화면으로 정할 수 있어요.
          </div>
        )}
      </div>

      <p className="px-1 text-xl font-bold">
        {formatRangeDot(journey.startDate, journey.endDate)} · {journey.members.length}명
      </p>

      <Link
        href={`/journeys/${journey.id}/camera`}
        className="flex h-16 items-center rounded-2xl bg-brand px-6 text-lg font-bold text-brand-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:bg-brand-pressed"
      >
        {/* ①은 꾸밈 글자라 화면 읽기 프로그램은 건너뛰게 한다 */}
        <span aria-hidden="true">①&nbsp;</span>지금 기록하기
      </Link>
      <p className="px-1 text-sm text-brand-strong">누르면 {journey.city} 여정 카메라가 바로 열려요</p>
    </section>
  );
}
