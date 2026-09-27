"use client";

// S11 여정 상세: 여정 제목, 기간·구성원 이름, 기록·추억 지도·추억 영상 버튼, 기록(공동 피드)
// - 처음 열면 '기록' 버튼이 골라져 있고, 여정에 올린 영상이 방문 일시 순서(여행 순서)대로 보인다
// - 추억 지도·추억 영상 버튼은 아직 눌러도 아무 일도 없다(S12·S14를 만들 때 연결)
// - 여행 기간 중이면 맨 아래 '촬영 이어가기'로 이 여정의 카메라를 연다
// - 여정·기록은 이 기기(브라우저)에 저장되어 있어서, 화면을 연 뒤에 읽는다
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Clock, Users, Video } from "lucide-react";
import { JourneyHeader } from "@/components/journey/journey-header";
import { formatRangeDot, todayKey } from "@/lib/dates";
import { findNickname, getCurrentAccount } from "@/lib/local-auth";
import { getMyJourney, journeyStatus, type Journey } from "@/lib/local-journeys";
import { listJourneyLogs, type LocalLog } from "@/lib/local-logs";
import { cn } from "@/lib/utils";

// 기록·추억 지도·추억 영상. 지금은 기록만 동작한다
const MENUS = [
  { key: "logs", label: "기록" },
  { key: "map", label: "추억 지도" },
  { key: "recap", label: "추억 영상" },
] as const;

const UNKNOWN_NAME = "친구"; // 이 기기에 계정이 없는 구성원 (서버를 붙이면 profiles.nickname을 쓴다)

// 이름 목록을 한 줄로: 3명까지는 "테스터·민지·준", 4명 이상이면 "테스터 외 3명"
function joinNames(names: string[]) {
  return names.length <= 3 ? names.join("·") : `${names[0]} 외 ${names.length - 1}명`;
}

// 여정 구성원 이름. 나를 맨 앞에 두고, 나머지는 참여한 순서대로
function memberNames(journey: Journey, meId: string) {
  const members = [...journey.members].sort(
    (a, b) => Number(b.userId === meId) - Number(a.userId === meId) || a.joinedAt.localeCompare(b.joinedAt),
  );
  return members.map((m) => findNickname(m.userId) ?? UNKNOWN_NAME);
}

// 방문 일시를 찍은 곳의 시간으로: "9월 26일 (토) 14:30", 올해가 아니면 앞에 "2025년 "
function formatCapturedAt(iso: string, timeZone: string) {
  const date = new Date(iso);
  let parts: Intl.DateTimeFormatPart[];
  const options: Intl.DateTimeFormatOptions = { year: "numeric", month: "numeric", day: "numeric", weekday: "short", hour: "2-digit", minute: "2-digit", hourCycle: "h23" };
  try {
    parts = new Intl.DateTimeFormat("ko-KR", { ...options, timeZone }).formatToParts(date);
  } catch {
    parts = new Intl.DateTimeFormat("ko-KR", options).formatToParts(date); // 시간대 이름을 모르면 이 폰의 시간으로
  }
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? "";
  const year = Number(get("year")) === new Date().getFullYear() ? "" : `${get("year")}년 `;
  return `${year}${get("month")}월 ${get("day")}일 (${get("weekday")}) ${get("hour")}:${get("minute")}`;
}

type Loaded = { journey: Journey; meId: string; logs: LocalLog[] | null }; // logs null: 기록을 읽는 중

export function JourneyDetail() {
  const { id } = useParams<{ id: string }>();
  const [state, setState] = useState<Loaded | null | undefined>(undefined); // undefined: 읽는 중, null: 여정 없음
  const [logsFailed, setLogsFailed] = useState(false);

  // 다른 화면(카메라·기록 올리기)에서 돌아올 때도 다시 읽어서, 방금 올린 기록이 바로 보이게 한다
  useEffect(() => {
    let cancelled = false;
    const account = getCurrentAccount();
    const journey = account ? getMyJourney(account.id, id) : null;
    if (!account || !journey) {
      setState(null);
      return;
    }
    setState({ journey, meId: account.id, logs: null });
    setLogsFailed(false);
    listJourneyLogs(id)
      .then((logs) => !cancelled && setState({ journey, meId: account.id, logs }))
      .catch(() => {
        if (cancelled) return;
        setLogsFailed(true);
        setState({ journey, meId: account.id, logs: [] });
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (state === undefined) return null;
  if (state === null) {
    return (
      <>
        <JourneyHeader title="여정" fallbackHref="/mylog" />
        <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
          <p className="text-base">여정을 찾을 수 없어요.</p>
          <Link href="/mylog" className="text-base font-semibold text-brand-strong underline underline-offset-2">
            마이로그로
          </Link>
        </div>
      </>
    );
  }

  const { journey, meId, logs } = state;
  const ongoing = journeyStatus(journey, todayKey()) === "ongoing";

  return (
    <>
      <JourneyHeader title={journey.name} fallbackHref="/mylog" />

      <p className="mt-5 flex min-h-12 items-center rounded-2xl bg-brand-soft px-5 py-2 text-base font-semibold text-brand-strong">
        {formatRangeDot(journey.startDate, journey.endDate)} · {joinNames(memberNames(journey, meId))}
      </p>

      {/* 세 버튼은 따로 동작한다. 지금은 기록만 골라져 있고, 추억 지도·추억 영상은 눌러도 아무 일도 없다 */}
      <div role="group" aria-label="여정 메뉴" className="mt-3 grid grid-cols-3 gap-2">
        {MENUS.map(({ key, label }) => {
          const selected = key === "logs";
          return (
            <button
              key={key}
              type="button"
              aria-pressed={selected}
              className={cn(
                "h-11 rounded-full text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                selected ? "bg-brand text-brand-foreground" : "border bg-card text-foreground",
              )}
            >
              {label}
            </button>
          );
        })}
      </div>

      <section aria-label="기록" className="mt-4 flex flex-col gap-4 pb-4">
        {logs && logs.length === 0 && (
          <div className="flex flex-col items-center gap-2 rounded-3xl border border-dashed px-6 py-12 text-center">
            <p className="text-base font-semibold">{logsFailed ? "기록을 불러오지 못했어요." : "아직 올린 기록이 없어요."}</p>
            {ongoing && !logsFailed && (
              <p className="text-sm text-muted-foreground">아래 촬영 이어가기로 첫 영상을 남겨 보세요.</p>
            )}
          </div>
        )}
        {logs?.map((log) => (
          <LogCard key={log.id} log={log} />
        ))}
      </section>

      {ongoing && (
        // 여행 기간 중: 화면을 내려도 맨 아래에 붙어 있는 촬영 버튼
        <div className="sticky bottom-0 -mx-4 mt-auto bg-background px-4 pb-[calc(env(safe-area-inset-bottom)+0.75rem)] pt-3">
          <Link
            href={`/journeys/${journey.id}/camera`}
            className="flex h-14 items-center justify-center gap-2 rounded-2xl bg-brand text-lg font-bold text-brand-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:bg-brand-pressed"
          >
            <Video className="size-5" aria-hidden="true" />
            진행 중 · 촬영 이어가기
          </Link>
        </div>
      )}
    </>
  );
}

// 기록 하나: 영상, 그 밑에 방문 일시와 함께한 사람
function LogCard({ log }: { log: LocalLog }) {
  const [videoUrl, setVideoUrl] = useState<string | null>(null);

  // 영상 파일을 <video>가 재생할 수 있는 임시 주소로 만든다. 화면을 떠나면 지운다
  useEffect(() => {
    const url = URL.createObjectURL(log.video);
    setVideoUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [log.video]);

  const when = formatCapturedAt(log.capturedAt, log.capturedTz);
  // 함께한 사람: 기록한 사람 + (나중에) 태그한 사람
  const people = [log.authorId, ...log.taggedUserIds.filter((userId) => userId !== log.authorId)].map(
    (userId) => findNickname(userId) ?? UNKNOWN_NAME,
  );

  return (
    <article className="overflow-hidden rounded-3xl border bg-card">
      <div className="aspect-[3/4] max-h-[70dvh] w-full bg-foreground">
        {videoUrl && (
          <video
            src={videoUrl}
            poster={log.poster ?? undefined}
            controls
            playsInline
            preload="metadata"
            aria-label={`${when} 기록 영상`}
            className="size-full object-cover"
          />
        )}
      </div>
      <div className="flex flex-col gap-1 px-4 py-3">
        <p className="flex items-center gap-2 text-base font-bold">
          <Clock className="size-4 shrink-0 text-brand-strong" aria-hidden="true" />
          {when}
        </p>
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Users className="size-4 shrink-0" aria-hidden="true" />
          함께한 사람 · {joinNames(people)}
        </p>
      </div>
    </article>
  );
}
