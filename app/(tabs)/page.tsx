// S01 홈 / S01-A 여행 기간 중 홈
// 오늘 진행 중인 여정이 있으면 S01-A(components/home/ongoing-home.tsx), 없으면 아래 S01 메뉴 카드를 보여준다
import Link from "next/link";
import { FlashBanner } from "@/components/home/flash-banner";
import { HomeSwitch } from "@/components/home/home-switch";
import { PageHeader } from "@/components/page-header";

// S01 홈 메뉴 카드. 배열 순서대로 위에서부터 놓인다.
const MENUS = [
  {
    number: "①",
    title: "기록 시작하기",
    description: "새로운 여정 기록하기",
    href: "/journeys/new",
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
      {/* 여정을 저장한 뒤 돌아오면 안내를 한 번 보여준다 */}
      <div className="px-4 pt-2 empty:hidden">
        <FlashBanner />
      </div>
      <HomeSwitch>
        <section className="flex flex-col gap-5 px-4 pb-6 pt-2">
          <h2 className="text-2xl font-bold">어떤 순간을 남길까요?</h2>
          <ul className="flex flex-col gap-4">
            {MENUS.map((menu) => (
              <li key={menu.title}>
                <Link
                  href={menu.href}
                  className="block rounded-3xl border-2 border-brand-line bg-brand-soft px-5 py-5 active:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
                >
                  <p className="text-xl font-bold">
                    {/* ①②③은 꾸밈 글자라 화면 읽기 프로그램은 건너뛰게 한다 */}
                    <span aria-hidden="true">{menu.number} </span>
                    {menu.title}
                  </p>
                  <p className="mt-1 text-base text-muted-foreground">{menu.description}</p>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      </HomeSwitch>
    </>
  );
}
