"use client";

// S04 앱 안 카메라 (기본형): 여정 이름, "여정 진행 중 · 14:30", 카메라 화면, ● 촬영
// - 화면에 들어오면 폰의 뒤쪽 카메라가 바로 켜진다 (처음 한 번은 브라우저가 카메라 권한을 묻는다)
// - 촬영하면 찍은 사진을 보여준다. "기록 올리기"(S05)는 다음 작업에서 연결한다. 지금은 눌러도 아무 일도 없다
// - 카메라는 https 주소(또는 내 컴퓨터의 localhost)에서만 켤 수 있다
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Circle, RotateCcw } from "lucide-react";
import { JourneyHeader } from "@/components/journey/journey-header";
import { NextButton } from "@/components/signup/next-button";
import { formatKorean, todayKey } from "@/lib/dates";
import { getCurrentAccount } from "@/lib/local-auth";
import { getMyJourney, journeyStatus, type Journey } from "@/lib/local-journeys";

type CameraState = "starting" | "live" | "denied" | "unavailable" | "insecure" | "error";

const CAMERA_MESSAGES: Record<Exclude<CameraState, "starting" | "live">, string> = {
  denied: "카메라 권한이 꺼져 있어요. 브라우저 설정에서 이 사이트의 카메라를 허용한 뒤 다시 시도해 주세요.",
  unavailable: "이 기기에서 카메라를 찾지 못했어요.",
  insecure: "카메라는 https 주소에서만 켤 수 있어요.",
  error: "카메라를 켜지 못했어요. 다른 앱이 카메라를 쓰고 있다면 닫고 다시 시도해 주세요.",
};

function formatTime(date: Date) {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export function JourneyCamera() {
  const { id } = useParams<{ id: string }>();
  const [journey, setJourney] = useState<Journey | null | undefined>(undefined); // undefined: 불러오는 중
  const [camera, setCamera] = useState<CameraState>("starting");
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  const [now, setNow] = useState<Date | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);

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

  // 카메라 켜기. 사진을 찍었거나 화면을 떠나면 카메라를 끈다(배터리·개인정보 보호)
  useEffect(() => {
    if (!journey || photoUrl) return;
    let stream: MediaStream | null = null;
    let cancelled = false;

    async function start() {
      if (!window.isSecureContext) return setCamera("insecure");
      if (!navigator.mediaDevices?.getUserMedia) return setCamera("unavailable");
      setCamera("starting");
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: "environment" }, width: { ideal: 1920 }, height: { ideal: 1440 } },
          audio: false,
        });
        if (cancelled) return stream.getTracks().forEach((t) => t.stop());
        const video = videoRef.current;
        if (video) {
          video.srcObject = stream;
          await video.play().catch(() => {});
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
      stream?.getTracks().forEach((t) => t.stop());
    };
  }, [journey, photoUrl, retry]);

  // 찍은 사진을 다시 찍거나 화면을 떠나면 사진이 차지하던 메모리를 돌려준다
  useEffect(() => {
    return () => {
      if (photoUrl) URL.revokeObjectURL(photoUrl);
    };
  }, [photoUrl]);

  function takePhoto() {
    const video = videoRef.current;
    if (!video || camera !== "live" || !video.videoWidth) return;
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext("2d")?.drawImage(video, 0, 0);
    canvas.toBlob((blob) => blob && setPhotoUrl(URL.createObjectURL(blob)), "image/jpeg", 0.9);
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
        {journey && now && status === "ongoing" && `여정 진행 중 · ${formatTime(now)}`}
        {journey && status === "upcoming" && `${formatKorean(journey.startDate)}에 시작하는 여정이에요`}
        {journey && status === "past" && "지난 여정이에요"}
      </p>

      <div className="relative mt-4 aspect-[3/4] w-full overflow-hidden rounded-3xl bg-foreground">
        {photoUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- 방금 찍은 사진(blob 주소)이라 next/image를 쓸 수 없다
          <img src={photoUrl} alt="방금 찍은 사진" className="size-full object-cover" />
        ) : (
          <video
            ref={videoRef}
            // playsInline: 아이폰에서 전체 화면으로 바뀌지 않고 이 칸 안에서 보이게 한다
            playsInline
            muted
            aria-label="카메라 화면"
            className="size-full object-cover"
          />
        )}
        {!photoUrl && camera === "starting" && (
          <p className="absolute inset-0 flex items-center justify-center text-sm text-background">카메라를 켜는 중…</p>
        )}
        {!photoUrl && errorMessage && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 p-6 text-center text-background">
            <p role="alert" className="text-base leading-relaxed">
              {errorMessage}
            </p>
            {camera !== "unavailable" && camera !== "insecure" && (
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

      <div className="min-h-6 flex-1" />
      {photoUrl ? (
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => setPhotoUrl(null)}
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
      ) : (
        <NextButton type="button" disabled={camera !== "live"} onClick={takePhoto}>
          <Circle className="size-4 fill-current" aria-hidden="true" />
          촬영
        </NextButton>
      )}
    </>
  );
}
