"use client";

// S02 새 여정 만들기: 여정 이름·도시·여행 기간·촬영 알림을 정하고 저장한다.
// - 여행 기간을 누르면 달력 화면(/journeys/new/dates)으로 갔다가 돌아온다. 그동안 입력한 내용은 임시 저장해 둔다
// - 저장 버튼: 오늘이 여행 기간 안이면 "기록 시작하기"(저장 후 카메라), 아니면 "여정 저장하기"(저장 후 홈)
// - 친구 초대하기: 초대 화면을 만들 때 연결한다. 지금은 눌러도 아무 일도 일어나지 않는다
import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";
import { ChevronRight, UserPlus } from "lucide-react";
import { FormError } from "@/components/form-error";
import { CityField } from "@/components/journey/city-field";
import { FormRow, VALUE_BOX } from "@/components/journey/form-row";
import { JourneyHeader } from "@/components/journey/journey-header";
import { NextButton } from "@/components/signup/next-button";
import { Checkbox } from "@/components/ui/checkbox";
import { formatKorean, formatNights, formatRangeDot, todayKey } from "@/lib/dates";
import { getCurrentAccount } from "@/lib/local-auth";
import {
  EMPTY_JOURNEY_DRAFT,
  JOURNEY_NAME_MAX_LENGTH,
  NOTIFY_INTERVALS,
  clearJourneyDraft,
  createJourney,
  journeyStatus,
  markCameraAutoOpened,
  readJourneyDraft,
  saveJourneyDraft,
  setFlashMessage,
  type JourneyDraft,
  type NotifyInterval,
} from "@/lib/local-journeys";
import { cn } from "@/lib/utils";

export function NewJourneyForm() {
  const router = useRouter();
  const [draft, setDraft] = useState<JourneyDraft>(EMPTY_JOURNEY_DRAFT);
  const [today, setToday] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [moving, startMoving] = useTransition();

  // 임시 저장한 내용을 불러온다. 달력 화면에서 돌아올 때도 다시 불러와서 고른 기간이 바로 보인다
  useEffect(() => {
    setDraft(readJourneyDraft());
    setToday(todayKey());
  }, []);

  // 바꿀 때마다 임시 저장한다
  function update(patch: Partial<JourneyDraft>) {
    setDraft((prev) => {
      const next = { ...prev, ...patch };
      saveJourneyDraft(next);
      return next;
    });
  }

  const name = draft.name.trim();
  const hasDates = Boolean(draft.startDate && draft.endDate);
  const canSave = name.length > 0 && draft.cityText.trim().length > 0 && hasDates && !moving;
  const startsRecording =
    hasDates && today !== null && journeyStatus({ startDate: draft.startDate!, endDate: draft.endDate! }, today) === "ongoing";

  function save() {
    if (!canSave) return;
    setError(null);
    const account = getCurrentAccount();
    if (!account) {
      router.replace("/login");
      return;
    }
    const result = createJourney(account.id, {
      name,
      city: draft.city?.name ?? draft.cityText,
      country: draft.city?.country ?? "",
      startDate: draft.startDate!,
      endDate: draft.endDate!,
      notifyIntervalHours: draft.noNotify ? null : draft.notifyInterval,
    });
    if (!result.ok) {
      setError(result.message);
      return;
    }
    clearJourneyDraft();
    const journey = result.journey;
    const status = journeyStatus(journey, todayKey());
    if (status === "ongoing") {
      // 지금 바로 카메라를 여니, 오늘 앱을 다시 열었을 때 또 자동으로 열리지 않게 표시해 둔다
      markCameraAutoOpened(journey.id);
      startMoving(() => router.replace(`/journeys/${journey.id}/camera`));
      return;
    }
    setFlashMessage(
      status === "upcoming"
        ? `‘${journey.name}’ 여정을 저장했어요. ${formatKorean(journey.startDate)}에 앱을 열면 카메라가 바로 열려요.`
        : `‘${journey.name}’ 여정을 저장했어요.`,
    );
    startMoving(() => router.replace("/"));
  }

  return (
    <>
      <JourneyHeader title="새 여정 만들기" />

      <div className="mt-6 flex flex-col gap-3">
        <FormRow label="여정 이름" labelId="journey-name-label" htmlFor="journey-name">
          <input
            id="journey-name"
            value={draft.name}
            onChange={(e) => update({ name: e.target.value })}
            maxLength={JOURNEY_NAME_MAX_LENGTH}
            placeholder="예: 우리의 포르투"
            autoComplete="off"
            className={cn(
              VALUE_BOX,
              "placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
            )}
          />
        </FormRow>

        <CityField
          text={draft.cityText}
          selected={draft.city}
          onChange={(cityText, city) => update({ cityText, city })}
        />

        <FormRow label="여행 기간" labelId="journey-period-label">
          <button
            type="button"
            onClick={() => router.push("/journeys/new/dates")}
            aria-labelledby="journey-period-label journey-period-value"
            className={cn(
              VALUE_BOX,
              "flex items-center justify-between gap-2 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
            )}
          >
            <span id="journey-period-value" className="min-w-0 truncate">
              {hasDates ? (
                <>
                  {formatRangeDot(draft.startDate!, draft.endDate!)}
                  <span className="ml-2 text-sm text-muted-foreground">
                    {formatNights(draft.startDate!, draft.endDate!)}
                  </span>
                </>
              ) : (
                <span className="text-muted-foreground">기간 선택</span>
              )}
            </span>
            <ChevronRight className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
          </button>
        </FormRow>

        <FormRow label="알림" labelId="journey-notify-label">
          <NotifyIntervalPicker
            value={draft.notifyInterval}
            disabled={draft.noNotify}
            onChange={(notifyInterval) => update({ notifyInterval })}
          />
        </FormRow>

        <div className="flex items-center gap-3 px-2 py-2">
          <Checkbox
            id="journey-no-notify"
            checked={draft.noNotify}
            onCheckedChange={(value) => update({ noNotify: value === true })}
            className="size-6 rounded-full border-2 border-muted-foreground data-[state=checked]:border-brand data-[state=checked]:bg-brand data-[state=checked]:text-brand-foreground"
          />
          <label htmlFor="journey-no-notify" className="flex-1 py-1 text-base text-muted-foreground">
            촬영 알림 받지 않기
          </label>
        </div>
      </div>

      <div className="min-h-8 flex-1" />
      {!canSave && !moving && (
        <p className="mb-3 text-center text-sm text-muted-foreground">여정 이름·도시·여행 기간을 정하면 저장할 수 있어요.</p>
      )}
      {error && (
        <div className="mb-3">
          <FormError alert>{error}</FormError>
        </div>
      )}
      <div className="flex flex-col gap-3">
        {/* 초대 화면(링크 복사·카카오톡 보내기)을 만들 때 연결한다 */}
        <button
          type="button"
          className="flex h-14 w-full items-center justify-center gap-2 rounded-2xl border border-brand-line bg-brand-soft text-base font-semibold text-brand-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <UserPlus className="size-5" aria-hidden="true" />
          친구 초대하기
        </button>
        <NextButton type="button" disabled={!canSave} onClick={save}>
          {moving ? "저장하는 중…" : startsRecording ? "기록 시작하기" : "여정 저장하기"}
        </NextButton>
      </div>
    </>
  );
}

// 촬영 알림 간격: 1시간 · 2시간 · 3시간 중 하나. '촬영 알림 받지 않기'를 체크하면 고를 수 없다.
// 화면 읽기 프로그램에는 라디오 버튼 묶음으로 알려 주고, ←→ 키로도 바꿀 수 있다
function NotifyIntervalPicker({
  value,
  disabled,
  onChange,
}: {
  value: NotifyInterval;
  disabled: boolean;
  onChange: (value: NotifyInterval) => void;
}) {
  function onKeyDown(event: React.KeyboardEvent) {
    if (disabled || (event.key !== "ArrowLeft" && event.key !== "ArrowRight")) return;
    event.preventDefault();
    const i = NOTIFY_INTERVALS.indexOf(value);
    const next = NOTIFY_INTERVALS[(i + (event.key === "ArrowRight" ? 1 : -1) + NOTIFY_INTERVALS.length) % NOTIFY_INTERVALS.length];
    onChange(next);
    document.getElementById(`journey-notify-${next}`)?.focus();
  }

  return (
    <div
      role="radiogroup"
      aria-labelledby="journey-notify-label"
      aria-disabled={disabled}
      onKeyDown={onKeyDown}
      className="flex h-14 items-center gap-1 rounded-2xl border bg-card p-1.5"
    >
      {NOTIFY_INTERVALS.map((hours) => {
        const checked = value === hours;
        return (
          <button
            key={hours}
            id={`journey-notify-${hours}`}
            type="button"
            role="radio"
            aria-checked={!disabled && checked}
            disabled={disabled}
            tabIndex={checked ? 0 : -1}
            onClick={() => onChange(hours)}
            className={cn(
              "h-full flex-1 rounded-xl text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
              disabled
                ? "text-muted-foreground line-through"
                : checked
                  ? "bg-brand text-brand-foreground"
                  : "text-foreground active:bg-brand-soft",
            )}
          >
            {hours}시간
          </button>
        );
      })}
    </div>
  );
}
