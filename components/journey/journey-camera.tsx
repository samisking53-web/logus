"use client";

// S04 앱 안 카메라: 여정 이름, "여정 진행 중 · 14:30", 카메라 화면, ● 촬영
// - 화면에 들어오면 폰의 뒤쪽 카메라가 영상 촬영 준비 상태로 바로 켜진다
//   (처음 한 번은 브라우저가 카메라·마이크 권한을 묻는다. 마이크를 거절하면 소리 없이 찍는다)
// - ● 촬영을 누르면 최대 10초까지 영상을 찍는다. 10초가 되면 저절로 멈춘다
// - 찍는 동안 1초마다 장면을 한 장씩 남겨 두고, 찍은 뒤 그중 하나를 여정의 '대표 화면'으로 고를 수 있다
// - "기록 올리기"(S05)는 다음 작업에서 연결한다. 지금은 눌러도 아무 일도 없다
// - 카메라는 https 주소(또는 내 컴퓨터의 localhost)에서만 켤 수 있다
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Check, Circle, RotateCcw, Square } from "lucide-react";
import { JourneyHeader } from "@/components/journey/journey-header";
import { NextButton } from "@/components/signup/next-button";
import { formatKorean, todayKey } from "@/lib/dates";
import { getCurrentAccount } from "@/lib/local-auth";
import { getMyJourney, journeyStatus, setJourneyCover, type Journey } from "@/lib/local-journeys";
import { cn } from "@/lib/utils";

export const MAX_VIDEO_SECONDS = 10;
const FRAME_WIDTH = 720; // 대표 화면 후보 이미지의 가로 크기(px)

type CameraState = "starting" | "live" | "denied" | "unavailable" | "insecure" | "unsupported" | "error";
type Phase = "ready" | "recording" | "review";

const CAMERA_MESSAGES: Record<Exclude<CameraState, "starting" | "live">, string> = {
  denied: "카메라 권한이 꺼져 있어요. 브라우저 설정에서 이 사이트의 카메라를 허용한 뒤 다시 시도해 주세요.",
  unavailable: "이 기기에서 카메라를 찾지 못했어요.",
  insecure: "카메라는 https 주소에서만 켤 수 있어요.",
  unsupported: "이 브라우저에서는 영상을 찍을 수 없어요. 최신 Safari나 Chrome에서 열어 주세요.",
  error: "카메라를 켜지 못했어요. 다른 앱이 카메라를 쓰고 있다면 닫고 다시 시도해 주세요.",
};

// 브라우저가 지원하는 영상 형식 (아이폰 Safari는 mp4, Chrome은 webm이 많다)
function pickVideoType() {
  const types = ["video/mp4;codecs=avc1,mp4a", "video/mp4", "video/webm;codecs=vp9,opus", "video/webm;codecs=vp8,opus", "video/webm"];
  return types.find((t) => MediaRecorder.isTypeSupported(t)) ?? "";
}

function formatClock(date: Date) {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function formatSeconds(ms: number) {
  return `0:${String(Math.min(MAX_VIDEO_SECONDS, Math.floor(ms / 1000))).padStart(2, "0")}`;
}

// 카메라 화면의 지금 장면을 JPEG 이미지 글자로 만든다
function grabFrame(video: HTMLVideoElement) {
  if (!video.videoWidth) return null;
  const scale = Math.min(1, FRAME_WIDTH / video.videoWidth);
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(video.videoWidth * scale);
  canvas.height = Math.round(video.videoHeight * scale);
  canvas.getContext("2d")?.drawImage(video, 0, 0, canvas.width, canvas.height);
  return canvas.toDataURL("image/jpeg", 0.8);
}

async function openCamera() {
  const video = { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } };
  try {
    return await navigator.mediaDevices.getUserMedia({ video, audio: true });
  } catch (err) {
    // 마이크만 거절했거나 마이크가 없으면 소리 없이 다시 시도한다
    const name = err instanceof DOMException ? err.name : "";
    if (name === "NotAllowedError" || name === "NotFoundError") {
      return await navigator.mediaDevices.getUserMedia({ video, audio: false });
    }
    throw err;
  }
}

export function JourneyCamera() {
  const { id } = useParams<{ id: string }>();
  const [journey, setJourney] = useState<Journey | null | undefined>(undefined); // undefined: 불러오는 중
  const [camera, setCamera] = useState<CameraState>("starting");
  const [phase, setPhase] = useState<Phase>("ready");
  const [elapsed, setElapsed] = useState(0);
  const [video, setVideo] = useState<{ url: string } | null>(null);
  const [frames, setFrames] = useState<string[]>([]);
  const [cover, setCover] = useState<{ index: number | null; message: string | null }>({ index: null, message: null });
  const [retry, setRetry] = useState(0);
  const [now, setNow] = useState<Date | null>(null);

  const liveRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const account = getCurrentAccount();
    setJourney(account ? getMyJourney(account.id, id) : null);
  }, [id]);

  // "여정 진행 중 · 14:30"의 시각을 30초마다 새로 그린다
  useEffect(() => {
    setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(timer);
  }, []);

  // 카메라 켜기. 찍은 영상을 보는 동안이나 화면을 떠나면 카메라를 끈다(배터리·개인정보 보호)
  const reviewing = phase === "review";
  useEffect(() => {
    if (!journey || reviewing) return;
    let cancelled = false;

    async function start() {
      if (!window.isSecureContext) return setCamera("insecure");
      if (!navigator.mediaDevices?.getUserMedia) return setCamera("unavailable");
      if (typeof MediaRecorder === "undefined") return setCamera("unsupported");
      setCamera("starting");
      try {
        const stream = await openCamera();
        if (cancelled) return stream.getTracks().forEach((t) => t.stop());
        streamRef.current = stream;
        const el = liveRef.current;
        if (el) {
          el.srcObject = stream;
          await el.play().catch(() => {});
        }
        setCamera("live");
      } catch (err) {
        if (cancelled) return;
        const name = err instanceof DOMException ? err.name : "";
        if (name === "NotAllowedError" || name === "SecurityError") setCamera("denied");
        else if (name === "NotFoundError" || name === "OverconstrainedError") setCamera("unavailable");
        else setCamera("error");
      }
    }

    start();
    return () => {
      cancelled = true;
      if (recorderRef.current?.state === "recording") recorderRef.current.stop();
      if (timerRef.current) clearInterval(timerRef.current);
      streamRef.current?.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    };
  }, [journey, reviewing, retry]);

  // 찍은 영상을 다시 찍거나 화면을 떠나면 영상이 차지하던 메모리를 돌려준다
  useEffect(() => {
    return () => {
      if (video) URL.revokeObjectURL(video.url);
    };
  }, [video]);

  function stopRecording() {
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = null;
    if (recorderRef.current?.state === "recording") recorderRef.current.stop();
  }

  function startRecording() {
    const stream = streamRef.current;
    const live = liveRef.current;
    if (!stream || !live || camera !== "live" || phase !== "ready") return;

    const type = pickVideoType();
    const recorder = new MediaRecorder(stream, type ? { mimeType: type } : undefined);
    const chunks: Blob[] = [];
    const captured: string[] = [];
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data);
    };
    recorder.onstop = () => {
      const blob = new Blob(chunks, { type: recorder.mimeType || type || "video/webm" });
      setVideo({ url: URL.createObjectURL(blob) });
      setFrames([...captured]);
      setCover({ index: null, message: null });
      setPhase("review");
    };
    recorderRef.current = recorder;
    recorder.start(1000);

    // 0초·1초·2초…마다 장면을 한 장씩 남긴다 (대표 화면 후보)
    const startedAt = performance.now();
    const first = grabFrame(live);
    if (first) captured.push(first);
    setElapsed(0);
    setPhase("recording");
    timerRef.current = setInterval(() => {
      const ms = performance.now() - startedAt;
      setElapsed(ms);
      if (ms >= captured.length * 1000 && captured.length < MAX_VIDEO_SECONDS) {
        const frame = grabFrame(live);
        if (frame) captured.push(frame);
      }
      if (ms >= MAX_VIDEO_SECONDS * 1000) stopRecording();
    }, 100);
  }

  function retake() {
    setVideo(null);
    setFrames([]);
    setElapsed(0);
    setPhase("ready");
  }

  function chooseCover(index: number) {
    const account = getCurrentAccount();
    if (!account || !journey) return;
    const result = setJourneyCover(account.id, journey.id, frames[index]);
    setCover(result.ok ? { index, message: "대표 화면으로 정했어요. 홈에서 보여요." } : { index: null, message: result.message });
  }

  if (journey === null) {
    return (
      <>
        <JourneyHeader title="카메라" />
        <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
          <p className="text-base">여정을 찾을 수 없어요.</p>
          <Link href="/" className="text-base font-semibold text-brand-strong underline underline-offset-2">
            홈으로
          </Link>
        </div>
      </>
    );
  }

  const status = journey && now ? journeyStatus(journey, todayKey()) : null;
  const errorMessage = camera === "starting" || camera === "live" ? null : CAMERA_MESSAGES[camera];
  const recording = phase === "recording";

  return (
    <>
      <JourneyHeader title={journey?.name ?? "카메라"} />

      <p className="mt-5 flex h-11 items-center justify-center rounded-2xl bg-brand-soft px-4 text-base font-semibold text-brand-strong">
        {journey && now && status === "ongoing" && `여정 진행 중 · ${formatClock(now)}`}
        {journey && status === "upcoming" && `${formatKorean(journey.startDate)}에 시작하는 여정이에요`}
        {journey && status === "past" && "지난 여정이에요"}
      </p>

      <div className="relative mt-4 aspect-[3/4] max-h-[46dvh] w-full overflow-hidden rounded-3xl bg-foreground">
        {reviewing && video ? (
          <video
            key={video.url}
            src={video.url}
            // 찍은 영상을 소리 없이 반복해서 보여준다. 아래 조절 막대로 소리를 켤 수 있다
            autoPlay
            muted
            loop
            playsInline
            controls
            aria-label="방금 찍은 영상"
            className="size-full object-cover"
          />
        ) : (
          <video
            ref={liveRef}
            // playsInline: 아이폰에서 전체 화면으로 바뀌지 않고 이 칸 안에서 보이게 한다
            playsInline
            muted
            aria-label="카메라 화면"
            className="size-full object-cover"
          />
        )}

        {/* 찍는 중: 빨간 점과 시간, 아래쪽 진행 막대 */}
        {recording && (
          <>
            <p
              role="timer"
              aria-live="off"
              className="absolute left-3 top-3 flex items-center gap-2 rounded-full bg-foreground/70 px-3 py-1 text-sm font-semibold text-background"
            >
              <span className="size-2.5 rounded-full bg-destructive" aria-hidden="true" />
              {formatSeconds(elapsed)} / 0:{MAX_VIDEO_SECONDS}
            </p>
            <div className="absolute inset-x-0 bottom-0 h-1.5 bg-background/30" aria-hidden="true">
              <div
                className="h-full bg-destructive"
                style={{ width: `${Math.min(100, (elapsed / (MAX_VIDEO_SECONDS * 1000)) * 100)}%` }}
              />
            </div>
          </>
        )}

        {!reviewing && camera === "starting" && (
          <p className="absolute inset-0 flex items-center justify-center text-sm text-background">카메라를 켜는 중…</p>
        )}
        {!reviewing && errorMessage && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 p-6 text-center text-background">
            <p role="alert" className="text-base leading-relaxed">
              {errorMessage}
            </p>
            {(camera === "denied" || camera === "error") && (
              <button
                type="button"
                onClick={() => setRetry((n) => n + 1)}
                className="h-11 rounded-xl border border-background px-5 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                다시 시도
              </button>
            )}
          </div>
        )}
      </div>

      {/* 찍은 뒤: 1초마다 남긴 장면 중 하나를 대표 화면으로 고른다 */}
      {reviewing && frames.length > 0 && (
        <section className="mt-4" aria-labelledby="cover-picker-title">
          <h2 id="cover-picker-title" className="text-sm font-semibold">
            대표 화면 고르기
          </h2>
          <p className="mt-0.5 text-xs text-muted-foreground">여행 중 홈에 크게 보일 장면을 눌러 주세요.</p>
          <ul className="-mx-4 mt-2 flex gap-2 overflow-x-auto px-4 pb-1">
            {frames.map((frame, i) => {
              const chosen = cover.index === i;
              return (
                <li key={i} className="shrink-0">
                  <button
                    type="button"
                    onClick={() => chooseCover(i)}
                    aria-pressed={chosen}
                    aria-label={`${i}초 장면`}
                    className={cn(
                      "relative block h-20 w-14 overflow-hidden rounded-xl border-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                      chosen ? "border-brand" : "border-transparent",
                    )}
                  >
                    {/* eslint-disable-next-line @next/next/no-img-element -- 방금 찍은 장면(이미지 글자)이라 next/image를 쓸 수 없다 */}
                    <img src={frame} alt="" className="size-full object-cover" />
                    {chosen && (
                      <span className="absolute right-1 top-1 flex size-5 items-center justify-center rounded-full bg-brand text-brand-foreground">
                        <Check className="size-3.5" aria-hidden="true" />
                      </span>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
          {cover.message && (
            <p role="status" className="mt-1 text-sm text-brand-strong">
              {cover.message}
            </p>
          )}
        </section>
      )}

      <div className="min-h-6 flex-1" />
      {reviewing ? (
        <div className="flex gap-3">
          <button
            type="button"
            onClick={retake}
            className="flex h-14 flex-1 items-center justify-center gap-2 rounded-2xl border border-brand-line bg-brand-soft text-base font-semibold text-brand-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <RotateCcw className="size-5" aria-hidden="true" />
            다시 찍기
          </button>
          {/* 기록 올리기(S05) 화면을 만들 때 연결한다 */}
          <NextButton type="button" className="flex-1">
            기록 올리기
          </NextButton>
        </div>
      ) : recording ? (
        <NextButton
          type="button"
          onClick={stopRecording}
          className="bg-destructive text-destructive-foreground hover:bg-destructive active:bg-destructive"
        >
          <Square className="size-4 fill-current" aria-hidden="true" />
          멈추기
        </NextButton>
      ) : (
        <>
          <p className="mb-2 text-center text-sm text-muted-foreground">최대 {MAX_VIDEO_SECONDS}초까지 찍혀요</p>
          <NextButton type="button" disabled={camera !== "live"} onClick={startRecording}>
            <Circle className="size-4 fill-current" aria-hidden="true" />
            촬영
          </NextButton>
        </>
      )}
    </>
  );
}
