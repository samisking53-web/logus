// S01 홈
import Link from "next/link";
import { FlashBanner } from "@/components/home/flash-banner";
import { StartRecordLink } from "@/components/home/start-record-link";
import { PageHeader } from "@/components/page-header";
import { cn } from "@/lib/utils";

// 홈 메뉴 카드. 배열 순서대로 위에서부터 놓인다.
// ① 기록 시작하기는 href 대신 StartRecordLink가 갈 곳을 정한다 (진행 중인 여정이 있으면 카메라, 없으면 새 여정 만들기)
const MENUS = [
  {
    number: "①",
    title: "기록 시작하기",
    description: "새로운 여정 기록하기",
    href: null,
  },
  {
    number: "②",
    title: "탐색",
    description: "다른 여행자의 순간 발견",
    href: "/explore",
  },
  {
    number: "③",
    title: "마이로그",
    description: "여정별 기록과 추억 다시 보기",
    href: "/mylog",
  },
];

export default function HomePage() {
  return (
    <>
      <PageHeader title="홈" />
      <section className="flex flex-col gap-5 px-4 pb-6 pt-2">
        {/* 여정을 저장한 뒤 돌아오면 안내를 한 번 보여준다 */}
        <FlashBanner />
        <h2 className="text-2xl font-bold">어떤 순간을 남길까요?</h2>
        <ul className="flex flex-col gap-4">
          {MENUS.map((menu) => {
            const cardClass = cn(
              "block rounded-3xl border-2 border-brand-line bg-brand-soft px-5 py-5",
              "active:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand",
            );
            const content = (
              <>
                <p className="text-xl font-bold">
                  {/* ①②③은 꾸밈 글자라 화면 읽기 프로그램은 건너뛰게 한다 */}
                  <span aria-hidden="true">{menu.number} </span>
                  {menu.title}
                </p>
                <p className="mt-1 text-base text-muted-foreground">
                  {menu.description}
                </p>
              </>
            );

            return (
              <li key={menu.title}>
                {menu.href ? (
                  <Link href={menu.href} className={cardClass}>
                    {content}
                  </Link>
                ) : (
                  <StartRecordLink className={cardClass}>{content}</StartRecordLink>
                )}
              </li>
            );
          })}
        </ul>
      </section>
    </>
  );
}
