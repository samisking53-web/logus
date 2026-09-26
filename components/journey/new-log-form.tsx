"use client";

// S05 기록 올리기 (새 기록 남기기). 카메라에서 영상을 찍고 멈추면 바로 이 화면이 열린다.
// - 맨 위: 방금 찍은 영상 + 대표 화면 고르기
// - 머문 장소: GPS + 장소 추천 (place-field.tsx)
// - 방문 일시: 날짜·시간 직접 입력 (처음엔 영상을 찍은 시각)
// - 별점(0.5점 단위, 끌어서 매기기)과 한줄평(100byte 이하)
// - 게시: 기록을 이 기기에 저장하고(lib/local-logs.ts) 여정 상세(S11)의 기록 탭으로 간다
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ArrowRight, CalendarDays, Globe, RotateCcw, Star, Video } from "lucide-react";
import { CoverPicker } from "@/components/journey/cover-picker";
import { FormError } from "@/components/form-error";
import { PlaceField, type SelectedPlace } from "@/components/journey/place-field";
import { StarRating } from "@/components/journey/star-rating";
import { countBytes, cutToBytes } from "@/lib/bytes";
import { toDateKey } from "@/lib/dates";
import { getCurrentAccount } from "@/lib/local-auth";
import { addLog } from "@/lib/local-logs";
import { clearPendingRecording, getPendingRecording, type PendingRecording } from "@/lib/pending-recording";

export const REVIEW_MAX_BYTES = 100;

const FIELD = "h-12 w-full rounded-2xl border bg-background px-4 text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

// 방문 일시의 날짜·시간 칸. 아이폰 Safari는 날짜 글자 길이보다 칸을 줄이지 않아 옆 칸·박스 밖으로 밀려나므로
// appearance-none·min-w-0으로 자기 자리 폭에 맞춘다.
// 글자는 칸의 가로·세로 가운데에 둔다. 칸을 flex로 두어야 아이폰에서도 글자가 위로 붙지 않는다
// (아이폰 날짜 칸의 기본값이 flex + 세로 가운데라서, block으로 바꾸면 글자가 왼쪽 위로 간다).
// 안드로이드 Chrome의 달력·시계 아이콘 주변 여백도 줄여 한국어 날짜("2026. 10. 14.")가 잘리지 않게 한다.
// 글자 크기는 16px(text-base) 그대로 둔다. 더 작으면 아이폰이 칸을 누를 때 화면을 확대한다
const DATE_TIME_FIELD =
  "flex h-11 w-full min-w-0 appearance-none items-center justify-center rounded-xl border bg-background px-3 text-center text-base text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-date-and-time-value]:m-0 [&::-webkit-date-and-time-value]:text-center [&::-webkit-calendar-picker-indicator]:m-0 [&::-webkit-calendar-picker-indicator]:ml-0.5 [&::-webkit-calendar-picker-indicator]:p-0";

function timeOf(date: Date) {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export function NewLogForm() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [recording, setRecording] = useState<PendingRecording | null>(null);

  // 카메라가 넘겨준 영상을 받는다. 없으면(새로고침 등) 카메라로 돌아간다
  useEffect(() => {
    const pending = getPendingRecording(id);
    if (!pending) {
      router.replace(`/journeys/${id}/camera`);
      return;
    }
    setRecording(pending);
  }, [id, router]);

  if (!recording) return null;
  // key: 새로 찍은 영상이면 입력 칸(장소·별점·한줄평 등)을 모두 처음 상태로 새로 만든다.
  // 같은 영상으로 돌아온 경우(다른 화면에 잠깐 다녀옴)에는 입력한 내용이 그대로 남는다
  return <LogFormBody key={recording.id} journeyId={id} recording={recording} />;
}

function LogFormBody({ journeyId: id, recording }: { journeyId: string; recording: PendingRecording }) {
  const router = useRouter();
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [place, setPlace] = useState<SelectedPlace | null>(null);
  // 방문 일시의 처음 값은 영상을 찍은 시각
  const [visitDate, setVisitDate] = useState(() => toDateKey(new Date(recording.recordedAt)));
  const [visitTime, setVisitTime] = useState(() => timeOf(new Date(recording.recordedAt)));
  const [rating, setRating] = useState(0);
  const [review, setReview] = useState("");
  const [posting, setPosting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const composing = useRef(false); // 한글을 조합하는 중에는 자르지 않는다 (글자가 깨지지 않게)

  // 영상 파일을 <video>가 재생할 수 있는 임시 주소로 만든다. 화면을 떠나면 지운다
  useEffect(() => {
    const url = URL.createObjectURL(recording.video);
    setVideoUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [recording]);

  function retake() {
    clearPendingRecording();
    if (window.history.length > 1) router.back();
    else router.replace(`/journeys/${id}/camera`);
  }

  function cancel() {
    if (!window.confirm("찍은 영상과 입력한 내용이 사라져요. 기록을 그만둘까요?")) return;
    clearPendingRecording();
    router.replace("/");
  }

  // 게시: 이 기기에 기록을 저장하고 여정 상세의 기록 탭으로 간다
  async function post() {
    if (posting) return;
    // 날짜·시간 칸은 이 폰의 현지 시각이다. UTC로 바꿔 저장하고 시간대를 함께 남긴다(CLAUDE.md 데이터 원칙)
    const capturedAt = new Date(`${visitDate}T${visitTime}`);
    if (!visitDate || !visitTime || Number.isNaN(capturedAt.getTime())) {
      setError("방문 일시의 날짜와 시간을 입력해 주세요.");
      return;
    }
    const account = getCurrentAccount();
    if (!account) {
      router.replace("/login");
      return;
    }
    setPosting(true);
    setError(null);
    const result = await addLog({
      journeyId: id,
      authorId: account.id,
      video: recording.video,
      poster: recording.frames[0] ?? null,
      capturedAt: capturedAt.toISOString(),
      capturedTz: recording.timezone,
      placeName: place?.name || null,
      lat: place?.lat ?? null,
      lng: place?.lng ?? null,
      rating: rating > 0 ? rating : null,
      review: review.trim() || null,
      taggedUserIds: [], // 함께한 사람을 고르는 칸은 아직 없다
    });
    if (!result.ok) {
      setPosting(false);
      setError(result.message);
      return;
    }
    clearPendingRecording();
    router.replace(`/journeys/${id}`);
  }

  function changeReview(value: string) {
    setReview(composing.current ? value : cutToBytes(value, REVIEW_MAX_BYTES));
  }

  const reviewBytes = countBytes(review);
  const seconds = Math.max(1, Math.round(recording.durationMs / 1000));

  return (
    <>
      <header className="pt-[env(safe-area-inset-top)]">
        <p className="flex items-center justify-center gap-2 pt-3 text-sm font-bold tracking-widest text-brand-strong">
          <Globe className="size-4" aria-hidden="true" />
          LOG EARTH
        </p>
        <div className="mt-3 flex h-11 items-center justify-between gap-2">
          <button
            type="button"
            onClick={cancel}
            className="-ml-2 h-11 rounded-xl px-2 text-base font-semibold text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            취소
          </button>
          <h1 className="text-lg font-bold">새 기록 남기기</h1>
          <button
            type="button"
            onClick={post}
            disabled={posting}
            className="flex h-10 items-center gap-1 rounded-full bg-foreground px-4 text-sm font-bold text-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60"
          >
            {posting ? "올리는 중…" : "게시"}
            {!posting && <ArrowRight className="size-4" aria-hidden="true" />}
          </button>
        </div>
        {error && (
          <div className="mt-3">
            <FormError alert>{error}</FormError>
          </div>
        )}
      </header>

      <div className="mt-4 flex flex-col gap-3 pb-4">
        {/* 영상: 화면 맨 위 */}
        <section className="rounded-3xl border bg-card p-4" aria-labelledby="video-title">
          <h2 id="video-title" className="flex items-center gap-2 text-base font-semibold">
            <Video className="size-5 text-brand-strong" aria-hidden="true" />
            영상
          </h2>
          <div className="relative mt-3 aspect-[3/4] max-h-[45dvh] w-full overflow-hidden rounded-2xl bg-foreground">
            {videoUrl && (
              <video
                src={videoUrl}
                autoPlay
                muted
                loop
                playsInline
                controls
                aria-label="방금 찍은 영상"
                className="size-full object-cover"
              />
            )}
            <p className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-foreground/70 px-3 py-1 text-xs font-semibold text-background">
              <Video className="size-3.5" aria-hidden="true" />
              0:{String(seconds).padStart(2, "0")} 영상 (최대 10초)
            </p>
            <button
              type="button"
              onClick={retake}
              className="absolute right-3 top-3 flex h-9 items-center gap-1.5 rounded-full bg-background/90 px-3 text-xs font-semibold text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <RotateCcw className="size-3.5" aria-hidden="true" />
              다시 찍기
            </button>
          </div>
          <CoverPicker journeyId={id} frames={recording.frames} />
        </section>

        <PlaceField value={place} onChange={setPlace} />

        <section className="rounded-3xl border bg-card p-4" aria-labelledby="visit-title">
          <h2 id="visit-title" className="flex items-center gap-2 text-base font-semibold">
            <CalendarDays className="size-5 text-brand-strong" aria-hidden="true" />
            방문 일시
          </h2>
          {/* 두 칸을 나란히 두고 가운데를 띄운다. 날짜 글자가 더 길어서 날짜 칸을 조금 넓게 준다. 폭이 아주 좁은 폰(360px 미만)에서는 위아래로 쌓는다 */}
          <div className="mt-3 grid grid-cols-1 gap-3 min-[360px]:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] min-[360px]:gap-4">
            <label className="flex min-w-0 flex-col gap-1.5">
              <span className="text-xs font-semibold text-muted-foreground">날짜</span>
              <input
                type="date"
                value={visitDate}
                onChange={(e) => setVisitDate(e.target.value)}
                className={DATE_TIME_FIELD}
              />
            </label>
            <label className="flex min-w-0 flex-col gap-1.5">
              <span className="text-xs font-semibold text-muted-foreground">시간</span>
              <input
                type="time"
                value={visitTime}
                onChange={(e) => setVisitTime(e.target.value)}
                className={DATE_TIME_FIELD}
              />
            </label>
          </div>
        </section>

        <section className="rounded-3xl border bg-card p-4" aria-labelledby="rating-title">
          <div className="flex items-center justify-between gap-2">
            <h2 id="rating-title" className="flex items-center gap-2 text-base font-semibold">
              <Star className="size-5 text-brand-strong" aria-hidden="true" />
              별점 및 한줄평
            </h2>
            <p className="text-2xl font-bold tabular-nums" aria-hidden="true">
              {rating.toFixed(1)}
            </p>
          </div>
          <div className="mt-2">
            <StarRating value={rating} onChange={setRating} labelledBy="rating-title" />
          </div>
          <p className="mt-1 text-xs text-muted-foreground">별을 누르거나 좌우로 끌어서 0.5점 단위로 매겨요.</p>

          <label htmlFor="review" className="sr-only">
            한줄평
          </label>
          <input
            id="review"
            value={review}
            onChange={(e) => changeReview(e.target.value)}
            onCompositionStart={() => (composing.current = true)}
            onCompositionEnd={(e) => {
              composing.current = false;
              setReview(cutToBytes(e.currentTarget.value, REVIEW_MAX_BYTES));
            }}
            placeholder="한줄평을 남겨 주세요"
            aria-describedby="review-count"
            autoComplete="off"
            className={`${FIELD} mt-3 placeholder:text-muted-foreground`}
          />
          <p id="review-count" className="mt-1 text-right text-xs text-muted-foreground">
            {reviewBytes} / {REVIEW_MAX_BYTES}byte
          </p>
        </section>
      </div>
    </>
  );
}
