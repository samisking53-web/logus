"use client";

// S04 앱 안 카메라: 여정 이름, "여정 진행 중 · 14:30", 카메라 화면, ● 촬영
// - 화면에 들어오면 폰의 뒤쪽 카메라가 영상 촬영 준비 상태로 바로 켜진다
//   (처음 한 번은 브라우저가 카메라·마이크 권한을 묻는다. 마이크를 거절하면 소리 없이 찍는다)
// - 카메라 화면 오른쪽 아래 전환 버튼으로 후면·전면 카메라를 바꾼다 (찍는 중에는 바꿀 수 없다)
// - ● 촬영을 누르면 최대 10초까지 영상을 찍는다. 10초가 되면 저절로 멈춘다
// - 찍는 동안 1초마다 장면을 한 장씩 남겨 둔다 (대표 화면 후보)
// - 멈추면 찍은 영상을 들고 바로 S05 기록 올리기 화면(/journeys/여정번호/record)으로 간다
// - 카메라는 https 주소(또는 내 컴퓨터의 localhost)에서만 켤 수 있다
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Circle, Square, SwitchCamera } from "lucide-react";
import { JourneyHeader } from "@/components/journey/journey-header";
import { NextButton } from "@/components/signup/next-button";
import { formatKorean, todayKey } from "@/lib/dates";
import { getCurrentAccount } from "@/lib/local-auth";
import { getMyJourney, journeyStatus, type Journey } from "@/lib/local-journeys";
import { setPendingRecording } from "@/lib/pending-recording";
import { cn } from "@/lib/utils";

export const MAX_VIDEO_SECONDS = 10;
const FRAME_WIDTH = 720; // 대표 화면 후보 이미지의 가로 크기(px)

type CameraState = "starting" | "live" | "denied" | "unavailable" | "insecure" | "unsupported" | "error";
type Facing = "environment" | "user"; // environment: 후면(뒤쪽) 카메라, user: 전면(셀카) 카메라

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

const VIDEO_SIZE = { width: { ideal: 1280 }, height: { ideal: 720 } };

function deviceIdOf(stream: MediaStream) {
  return stream.getVideoTracks()[0]?.getSettings().deviceId ?? null;
}

async function openCamera(video: MediaTrackConstraints) {
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
  const router = useRouter();
  const [journey, setJourney] = useState<Journey | null | undefined>(undefined); // undefined: 불러오는 중
  const [camera, setCamera] = useState<CameraState>("starting");
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [retry, setRetry] = useState(0);
  const [facing, setFacing] = useState<Facing>("environment");
  const [mirrored, setMirrored] = useState(false); // 전면 카메라면 화면을 거울처럼 좌우로 뒤집어 보여준다
  const [canSwitch, setCanSwitch] = useState(false); // 카메라가 2개 이상인 기기에서만 전환 버튼을 보여준다
  const [now, setNow] = useState<Date | null>(null);

  const liveRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const discardRef = useRef(false); // 찍는 도중 화면을 떠나면 찍던 영상은 버린다
  const deviceRef = useRef<string | null>(null); // 지금(마지막으로) 쓴 카메라. 다시 찍기로 돌아와도 같은 카메라를 켠다
  const switchFromRef = useRef<string | null>(null); // 전환 버튼을 누르기 직전의 카메라

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

  // 카메라 켜기. 화면을 떠나면(기록 올리기로 넘어갈 때 포함) 카메라를 끈다(배터리·개인정보 보호)
  // 전환 버튼을 누르면 facing이 바뀌어 지금 카메라를 끄고 다른 쪽 카메라를 다시 켠다
  useEffect(() => {
    if (!journey) return;
    let cancelled = false;
    discardRef.current = false;

    async function start() {
      if (!window.isSecureContext) return setCamera("insecure");
      if (!navigator.mediaDevices?.getUserMedia) return setCamera("unavailable");
      if (typeof MediaRecorder === "undefined") return setCamera("unsupported");
      setCamera("starting");
      try {
        const from = switchFromRef.current;
        switchFromRef.current = null;
        // 전환할 때는 앞·뒤(facing)로 고르고, 그 밖에는 마지막으로 쓴 카메라를 다시 켠다(없어졌으면 앞·뒤로 고른다)
        const byFacing = { facingMode: { ideal: facing }, ...VIDEO_SIZE };
        const last = from ? null : deviceRef.current;
        let stream = last
          ? await openCamera({ deviceId: { exact: last }, ...VIDEO_SIZE }).catch(() => openCamera(byFacing))
          : await openCamera(byFacing);
        if (from && deviceIdOf(stream) === from) {
          // 앞·뒤를 알려 주지 않는 카메라라서 같은 카메라가 다시 켜졌다. 다른 카메라를 직접 고른다
          const devices = await navigator.mediaDevices.enumerateDevices().catch(() => []);
          const other = devices.find((d) => d.kind === "videoinput" && d.deviceId && d.deviceId !== from);
          if (other) {
            stream.getTracks().forEach((t) => t.stop());
            stream = await openCamera({ deviceId: { exact: other.deviceId }, ...VIDEO_SIZE });
          }
        }
        if (cancelled) return stream.getTracks().forEach((t) => t.stop());
        streamRef.current = stream;
        deviceRef.current = deviceIdOf(stream);
        // 실제로 켜진 카메라가 앞쪽인지 본다. 컴퓨터 카메라처럼 알려 주지 않으면 요청한 쪽으로 본다
        setMirrored((stream.getVideoTracks()[0]?.getSettings().facingMode || facing) === "user");
        const el = liveRef.current;
        if (el) {
          el.srcObject = stream;
          await el.play().catch(() => {});
        }
        setCamera("live");
        // 권한을 받은 뒤라야 카메라 개수를 정확히 알 수 있다
        const devices = await navigator.mediaDevices.enumerateDevices().catch(() => []);
        if (!cancelled) setCanSwitch(devices.filter((d) => d.kind === "videoinput").length > 1);
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
      if (recorderRef.current?.state === "recording") {
        discardRef.current = true;
        recorderRef.current.stop();
      }
      if (timerRef.current) clearInterval(timerRef.current);
      setRecording(false);
      streamRef.current?.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    };
  }, [journey, retry, facing]);

  function switchCamera() {
    if (recording) return; // 찍는 중에 카메라를 바꾸면 영상이 끊기므로 막는다
    switchFromRef.current = deviceRef.current;
    deviceRef.current = null;
    setFacing((f) => (f === "environment" ? "user" : "environment"));
  }

  function stopRecording() {
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = null;
    if (recorderRef.current?.state === "recording") recorderRef.current.stop();
  }

  function startRecording() {
    const stream = streamRef.current;
    const live = liveRef.current;
    if (!stream || !live || camera !== "live" || recording || !journey) return;

    const type = pickVideoType();
    const recorder = new MediaRecorder(stream, type ? { mimeType: type } : undefined);
    const chunks: Blob[] = [];
    const frames: string[] = [];
    const recordedAt = new Date().toISOString();
    const startedAt = performance.now();
    let durationMs = 0;

    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data);
    };
    recorder.onstop = () => {
      setRecording(false);
      if (discardRef.current) return;
      // 찍은 영상을 기록 올리기 화면에 넘기고 이동한다
      setPendingRecording({
        journeyId: journey.id,
        video: new Blob(chunks, { type: recorder.mimeType || type || "video/webm" }),
        frames,
        recordedAt,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        durationMs,
      });
      router.push(`/journeys/${journey.id}/record`);
    };
    recorderRef.current = recorder;
    recorder.start(1000);

    // 0초·1초·2초…마다 장면을 한 장씩 남긴다 (대표 화면 후보)
    const first = grabFrame(live);
    if (first) frames.push(first);
    setElapsed(0);
    setRecording(true);
    timerRef.current = setInterval(() => {
      const ms = performance.now() - startedAt;
      durationMs = Math.min(ms, MAX_VIDEO_SECONDS * 1000);
      setElapsed(ms);
      if (ms >= frames.length * 1000 && frames.length < MAX_VIDEO_SECONDS) {
        const frame = grabFrame(live);
        if (frame) frames.push(frame);
      }
      if (ms >= MAX_VIDEO_SECONDS * 1000) stopRecording();
    }, 100);
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

  return (
    <>
      <JourneyHeader title={journey?.name ?? "카메라"} />

      <p className="mt-5 flex h-11 items-center justify-center rounded-2xl bg-brand-soft px-4 text-base font-semibold text-brand-strong">
        {journey && now && status === "ongoing" && `여정 진행 중 · ${formatClock(now)}`}
        {journey && status === "upcoming" && `${formatKorean(journey.startDate)}에 시작하는 여정이에요`}
        {journey && status === "past" && "지난 여정이에요"}
      </p>

      <div className="relative mt-4 aspect-[3/4] max-h-[60dvh] w-full overflow-hidden rounded-3xl bg-foreground">
        <video
          ref={liveRef}
          // playsInline: 아이폰에서 전체 화면으로 바뀌지 않고 이 칸 안에서 보이게 한다
          playsInline
          muted
          aria-label="카메라 화면"
          className={cn("size-full object-cover", mirrored && "-scale-x-100")}
        />

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

        {camera === "starting" && (
          <p className="absolute inset-0 flex items-center justify-center text-sm text-background">카메라를 켜는 중…</p>
        )}
        {errorMessage && (
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

        {/* 카메라 전환: 후면 ↔ 전면. 찍는 중에는 숨긴다 */}
        {canSwitch && !recording && (
          <button
            type="button"
            onClick={switchCamera}
            disabled={camera === "starting"}
            aria-label={facing === "user" ? "후면 카메라로 전환" : "전면 카메라로 전환"}
            className="absolute bottom-3 right-3 flex size-12 items-center justify-center rounded-full bg-background/90 text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
          >
            <SwitchCamera className="size-6" aria-hidden="true" />
          </button>
        )}
      </div>

      <div className="min-h-6 flex-1" />
      {recording ? (
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
