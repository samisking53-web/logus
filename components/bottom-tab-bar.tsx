"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BookHeart, Compass, House } from "lucide-react";
import { cn } from "@/lib/utils";

// 하단 탭 메뉴. 배열 순서대로 왼쪽부터 놓인다.
const TABS = [
  { href: "/", label: "홈", icon: House },
  { href: "/explore", label: "탐색", icon: Compass },
  { href: "/mylog", label: "마이로그", icon: BookHeart },
];

// 지금 주소가 이 탭에 속하는지 확인한다.
// 홈("/")은 주소가 정확히 "/"일 때만, 나머지 탭은 하위 주소(예: /mylog/여정ID)까지 포함한다.
function isActiveTab(pathname: string, href: string) {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function BottomTabBar() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="하단 메뉴"
      // sticky bottom-0: 내용이 길어 스크롤해도 화면 맨 아래에 붙어 있는다
      // safe-area-inset-bottom: 아이폰 아래쪽 홈 바에 탭이 가리지 않도록 여백을 둔다
      className="sticky bottom-0 z-40 border-t bg-background pb-[env(safe-area-inset-bottom)]"
    >
      <ul className="flex">
        {TABS.map(({ href, label, icon: Icon }) => {
          const active = isActiveTab(pathname, href);
          return (
            <li key={href} className="flex-1">
              <Link
                href={href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex h-16 flex-col items-center justify-center gap-1 text-xs font-medium",
                  active ? "text-brand" : "text-muted-foreground",
                )}
              >
                <Icon className="size-6" aria-hidden="true" />
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
