// S01 홈
import Link from "next/link";
import { PageHeader } from "@/components/page-header";
import { cn } from "@/lib/utils";

// 홈 메뉴 카드. 배열 순서대로 위에서부터 놓인다.
// href가 없는 카드는 아직 연결할 화면이 정해지지 않아서 눌러도 이동하지 않는다.
const MENUS = [
  {
    number: "①",
    title: "기록 시작하기",
    description: "새로운 여정 기록하기",
    href: undefined, // 연결할 화면이 정해지면 주소를 넣는다
    borderClass: "border-fuchsia-200 dark:border-fuchsia-900",
  },
  {
    number: "②",
    title: "탐색",
    description: "다른 여행자의 순간 발견",
    href: "/explore",
    borderClass: "border-fuchsia-200 dark:border-fuchsia-900",
  },
  {
    number: "③",
    title: "마이로그",
    description: "여정별 기록과 추억 다시 보기",
    href: "/mylog",
    borderClass: "border-yellow-200 dark:border-yellow-900",
  },
];

export default function HomePage() {
  return (
    <>
      <PageHeader title="홈" />
      <section className="flex flex-col gap-5 px-4 pb-6 pt-2">
        <h2 className="text-2xl font-bold">어떤 순간을 남길까요?</h2>
        <ul className="flex flex-col gap-4">
          {MENUS.map((menu) => {
            const cardClass = cn(
              "block rounded-3xl border-2 bg-brand-soft px-5 py-5",
              menu.borderClass,
            );
            const content = (
              <>
                <p className="text-xl font-bold">
                  {/* ①②③은 꾸밈 글자라 화면 읽기 프로그램은 건너뛰게 한다 */}
                  <span aria-hidden="true">{menu.number} </span>
                  {menu.title}
                </p>
                {/* 연보라 배경 위에서도 명도 대비 4.5:1이 넘도록 글자색을 조금 흐리게만 한다 */}
                <p className="mt-1 text-base text-foreground/70">
                  {menu.description}
                </p>
              </>
            );

            return (
              <li key={menu.title}>
                {menu.href ? (
                  <Link
                    href={menu.href}
                    className={cn(
                      cardClass,
                      "active:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand",
                    )}
                  >
                    {content}
                  </Link>
                ) : (
                  <div className={cardClass}>{content}</div>
                )}
              </li>
            );
          })}
        </ul>
      </section>
    </>
  );
}
