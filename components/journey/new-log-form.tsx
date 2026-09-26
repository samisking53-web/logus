"use client";

// S05 기록 올리기 (새 기록 남기기). 카메라에서 영상을 찍고 멈추면 바로 이 화면이 열린다.
// - 맨 위: 방금 찍은 영상 + 대표 화면 고르기
// - 머문 장소: GPS + 장소 추천 (place-field.tsx)
// - 방문 일시: 날짜·시간 직접 입력 (처음엔 영상을 찍은 시각)
// - 별점(0.5점 단위, 끌어서 매기기)과 한줄평(100byte 이하)
// - 게시: 지금은 버튼만 있다. 서버에 영상을 올릴 수 있게 되면 연결한다
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ArrowRight, CalendarDays, Globe, RotateCcw, Star, Video } from "lucide-react";
import { CoverPicker } from "@/components/journey/cover-picker";
import { PlaceField, type SelectedPlace } from "@/components/journey/place-field";
import { StarRating } from "@/components/journey/star-rating";
import { countBytes, cutToBytes } from "@/lib/bytes";
import { toDateKey } from "@/lib/dates";
import { clearPendingRecording, getPendingRecording, type PendingRecording } from "@/lib/pending-recording";

export const REVIEW_MAX_BYTES = 100;

const FIELD = "h-12 w-full rounded-2xl border bg-background px-4 text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

function timeOf(date: Date) {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export function NewLogForm() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [recording, setRecording] = useState<PendingRecording | null>(null);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [place, setPlace] = useState<SelectedPlace | null>(null);
  const [visitDate, setVisitDate] = useState("");
  const [visitTime, setVisitTime] = useState("");
  const [rating, setRating] = useState(0);
  const [review, setReview] = useState("");
  const composing = useRef(false); // 한글을 조합하는 중에는 자르지 않는다 (글자가 깨지지 않게)

  // 카메라가 넘겨준 영상을 받는다. 없으면(새로고침 등) 카메라로 돌아간다
  useEffect(() => {
    const pending = getPendingRecording(id);
    if (!pending) {
      router.replace(`/journeys/${id}/camera`);
      return;
    }
    setRecording(pending);
    const url = URL.createObjectURL(pending.video);
    setVideoUrl(url);
    // 방문 일시의 처음 값은 영상을 찍은 시각. 이미 고쳤으면 그대로 둔다
    const at = new Date(pending.recordedAt);
    setVisitDate((d) => d || toDateKey(at));
    setVisitTime((t) => t || timeOf(at));
    return () => URL.revokeObjectURL(url);
  }, [id, router]);

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

  function changeReview(value: string) {
    setReview(composing.current ? value : cutToBytes(value, REVIEW_MAX_BYTES));
  }

  const reviewBytes = countBytes(review);
  const seconds = recording ? Math.max(1, Math.round(recording.durationMs / 1000)) : 0;

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
          {/* 서버에 올릴 수 있게 되면 연결한다. 지금은 눌러도 아무 일도 없다 */}
          <button
            type="button"
            className="flex h-10 items-center gap-1 rounded-full bg-foreground px-4 text-sm font-bold text-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            게시
            <ArrowRight className="size-4" aria-hidden="true" />
          </button>
        </div>
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
            {recording && (
              <p className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-foreground/70 px-3 py-1 text-xs font-semibold text-background">
                <Video className="size-3.5" aria-hidden="true" />
                0:{String(seconds).padStart(2, "0")} 영상 (최대 10초)
              </p>
            )}
            <button
              type="button"
              onClick={retake}
              className="absolute right-3 top-3 flex h-9 items-center gap-1.5 rounded-full bg-background/90 px-3 text-xs font-semibold text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <RotateCcw className="size-3.5" aria-hidden="true" />
              다시 찍기
            </button>
          </div>
          {recording && <CoverPicker journeyId={id} frames={recording.frames} />}
        </section>

        <PlaceField value={place} onChange={setPlace} />

        <section className="rounded-3xl border bg-card p-4" aria-labelledby="visit-title">
          <h2 id="visit-title" className="flex items-center gap-2 text-base font-semibold">
            <CalendarDays className="size-5 text-brand-strong" aria-hidden="true" />
            방문 일시
          </h2>
          <div className="mt-3 grid grid-cols-2 gap-2">
            <label className="flex flex-col gap-1 text-xs text-muted-foreground">
              날짜
              <input
                type="date"
                value={visitDate}
                onChange={(e) => setVisitDate(e.target.value)}
                className={`${FIELD} text-foreground`}
              />
            </label>
            <label className="flex flex-col gap-1 text-xs text-muted-foreground">
              시간
              <input
                type="time"
                value={visitTime}
                onChange={(e) => setVisitTime(e.target.value)}
                className={`${FIELD} text-foreground`}
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
