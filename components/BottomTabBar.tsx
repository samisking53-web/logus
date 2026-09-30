"use client";

// 하단 탭: 홈 / 탐색 / 마이로그
import Link from "next/link";
import { usePathname } from "next/navigation";

const TABS = [
  { href: "/", label: "홈" },
  { href: "/explore", label: "탐색" },
  { href: "/mylog", label: "마이로그" },
] as const;

export default function BottomTabBar() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="주요 메뉴"
      className="fixed inset-x-0 bottom-0 z-10 mx-auto w-full max-w-[390px] border-t border-line bg-background pb-[env(safe-area-inset-bottom)]"
    >
      <ul className="flex">
        {TABS.map((tab) => {
          // 홈은 정확히 "/"일 때만, 나머지는 하위 주소까지 같은 탭으로 본다.
          const active =
            tab.href === "/" ? pathname === "/" : pathname.startsWith(tab.href);
          return (
            <li key={tab.href} className="flex-1">
              <Link
                href={tab.href}
                aria-current={active ? "page" : undefined}
                className={`flex h-16 items-center justify-center text-base ${
                  active ? "font-bold text-brand" : "text-muted"
                }`}
              >
                {tab.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
