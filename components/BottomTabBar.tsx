"use client";

// 하단 탭: 홈 / 탐색 / 마이로그
// 글자 대신 그림(아이콘)으로 보여 주고, 화면 읽기 프로그램을 위해 이름은 aria-label로 붙인다.
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

function HomeIcon() {
  return (
    <path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z" />
  );
}

function SearchIcon() {
  return (
    <>
      <circle cx="10.5" cy="10.5" r="6" />
      <path d="m15 15 5 5" />
    </>
  );
}

function FolderIcon() {
  return <path d="M3.5 6.5a1 1 0 0 1 1-1h5l2 2h8a1 1 0 0 1 1 1V18a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1z" />;
}

const TABS: { href: string; label: string; icon: ReactNode }[] = [
  { href: "/", label: "홈", icon: <HomeIcon /> },
  { href: "/explore", label: "탐색하기", icon: <SearchIcon /> },
  { href: "/mylog", label: "마이로그", icon: <FolderIcon /> },
];

export default function BottomTabBar() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="주요 메뉴"
      className="fixed inset-x-0 bottom-0 z-10 mx-auto w-full max-w-[390px] px-5 pb-[calc(env(safe-area-inset-bottom)+0.75rem)]"
    >
      <ul className="flex h-16 items-center rounded-full bg-surface">
        {TABS.map((tab) => {
          // 홈은 정확히 "/"일 때만, 나머지는 하위 주소까지 같은 탭으로 본다.
          const active =
            tab.href === "/" ? pathname === "/" : pathname.startsWith(tab.href);
          return (
            <li key={tab.href} className="flex flex-1 justify-center">
              <Link
                href={tab.href}
                aria-label={tab.label}
                aria-current={active ? "page" : undefined}
                className={`flex size-12 items-center justify-center rounded-full ${
                  active ? "bg-primary text-on-primary" : "text-text"
                }`}
              >
                <svg
                  viewBox="0 0 24 24"
                  className="size-7"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={1.8}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  {tab.icon}
                </svg>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
