"use client";

// 머문 장소: GPS로 지금 위치를 알아낸 뒤, 장소 이름을 입력하면 가까운 장소부터 추천하고 그중 하나를 고른다.
// - 장소 추천은 OpenStreetMap(Photon)에서 찾는다 (lib/places.ts)
// - 추천에 없는 장소는 입력한 글자 그대로 쓸 수 있다. 이때 좌표는 지금 폰 위치를 쓴다
// - 위치 권한을 거절해도 장소 검색은 된다 (가까운 순서로만 정렬되지 않을 뿐)
import { useEffect, useState } from "react";
import { Check, LocateFixed, MapPin, Search, X } from "lucide-react";
import { formatDistance, searchPlaces, type LatLng, type PlaceSuggestion } from "@/lib/places";
import { cn } from "@/lib/utils";

export type SelectedPlace = {
  name: string;
  address: string;
  lat: number | null;
  lng: number | null;
  source: "osm" | "typed"; // 추천에서 골랐는지, 입력한 글자 그대로인지
  osmId?: string;
};

type Gps = { status: "locating" } | { status: "found"; at: LatLng } | { status: "denied" | "unavailable" };

const SEARCH_DELAY_MS = 350; // 입력을 멈추고 이만큼 지나면 찾는다 (공용 서버라 너무 자주 부르지 않기)

export function PlaceField({
  value,
  onChange,
}: {
  value: SelectedPlace | null;
  onChange: (place: SelectedPlace | null) => void;
}) {
  const [gps, setGps] = useState<Gps>({ status: "locating" });
  const [text, setText] = useState("");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PlaceSuggestion[]>([]);
  const [searching, setSearching] = useState(false);
  const [failed, setFailed] = useState(false);

  // 화면에 들어오면 지금 위치를 한 번 알아낸다 (처음 한 번은 브라우저가 위치 권한을 묻는다)
  useEffect(() => {
    if (!window.isSecureContext || !("geolocation" in navigator)) {
      setGps({ status: "unavailable" });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        // 좌표 (0,0)은 위치를 모르는 것으로 본다 (CLAUDE.md 데이터 원칙)
        if (coords.latitude === 0 && coords.longitude === 0) setGps({ status: "unavailable" });
        else setGps({ status: "found", at: { lat: coords.latitude, lng: coords.longitude } });
      },
      (err) => setGps({ status: err.code === err.PERMISSION_DENIED ? "denied" : "unavailable" }),
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    );
  }, []);

  const near = gps.status === "found" ? gps.at : null;

  // 입력을 멈추면 장소를 찾는다. 새로 입력하면 이전 요청은 취소한다
  useEffect(() => {
    const q = query.trim();
    if (!q) {
      setResults([]);
      setFailed(false);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setSearching(true);
      setFailed(false);
      try {
        setResults(await searchPlaces(q, near, controller.signal));
      } catch {
        if (!controller.signal.aborted) {
          setResults([]);
          setFailed(true);
        }
      } finally {
        if (!controller.signal.aborted) setSearching(false);
      }
    }, SEARCH_DELAY_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
    // near는 좌표가 바뀔 때만 다시 찾도록 숫자로 비교한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, near?.lat, near?.lng]);

  function type(next: string) {
    setText(next);
    setQuery(next);
    if (value) onChange(null);
  }

  function choose(place: PlaceSuggestion) {
    setText(place.name);
    onChange({ name: place.name, address: place.address, lat: place.lat, lng: place.lng, source: "osm", osmId: place.id });
  }

  function chooseTyped() {
    const name = text.trim();
    setText(name);
    onChange({ name, address: "", lat: near?.lat ?? null, lng: near?.lng ?? null, source: "typed" });
  }

  const typed = text.trim();

  return (
    <section className="rounded-3xl border bg-card p-4" aria-labelledby="place-title">
      <div className="flex items-center justify-between gap-2">
        <h2 id="place-title" className="flex items-center gap-2 text-base font-semibold">
          <MapPin className="size-5 text-brand-strong" aria-hidden="true" />
          머문 장소
        </h2>
        <GpsBadge gps={gps} />
      </div>

      <div className="relative mt-3">
        <Search className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
        <input
          id="place-input"
          type="search"
          value={text}
          onChange={(e) => type(e.target.value)}
          placeholder="장소 이름을 입력하세요"
          aria-labelledby="place-title"
          aria-describedby="place-help"
          autoComplete="off"
          enterKeyHint="search"
          className="h-12 w-full rounded-2xl border bg-background pl-12 pr-12 text-base placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-search-cancel-button]:hidden"
        />
        {text && (
          <button
            type="button"
            onClick={() => type("")}
            aria-label="장소 지우기"
            className="absolute right-2 top-1/2 flex size-9 -translate-y-1/2 items-center justify-center rounded-full text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        )}
      </div>

      <p id="place-help" aria-live="polite" className="mt-2 text-xs text-muted-foreground empty:hidden">
        {searching
          ? "장소를 찾는 중…"
          : failed
            ? "장소를 불러오지 못했어요. 입력한 글자 그대로 쓸 수 있어요."
            : query.trim() && results.length === 0
              ? "추천할 장소를 찾지 못했어요. 입력한 글자 그대로 쓸 수 있어요."
              : !typed && (gps.status === "denied" || gps.status === "unavailable")
                ? "위치를 허용하면 가까운 장소부터 추천해요."
                : ""}
      </p>

      {(results.length > 0 || (typed && !searching)) && (
        <ul className="mt-2 flex flex-col gap-2">
          {results.map((place) => {
            const chosen = value?.source === "osm" && value.osmId === place.id;
            return (
              <li key={place.id}>
                <button
                  type="button"
                  onClick={() => choose(place)}
                  aria-pressed={chosen}
                  className={cn(
                    "flex w-full items-center gap-3 rounded-2xl border px-4 py-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                    chosen ? "border-brand bg-brand-soft" : "bg-card active:bg-brand-soft",
                  )}
                >
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-2">
                      <span className="truncate font-semibold">{place.name}</span>
                      {chosen && (
                        <span className="shrink-0 rounded-full bg-brand px-2 py-0.5 text-xs font-semibold text-brand-foreground">
                          선택됨
                        </span>
                      )}
                    </span>
                    <span className="mt-0.5 block truncate text-sm text-muted-foreground">
                      {place.address || "주소 정보 없음"}
                    </span>
                  </span>
                  {place.distanceM !== null && (
                    <span className="shrink-0 text-sm text-muted-foreground">{formatDistance(place.distanceM)}</span>
                  )}
                  {chosen && <Check className="size-5 shrink-0 text-brand-strong" aria-hidden="true" />}
                </button>
              </li>
            );
          })}
          {typed && !searching && (
            <li>
              <button
                type="button"
                onClick={chooseTyped}
                aria-pressed={value?.source === "typed"}
                className={cn(
                  "flex w-full items-center gap-3 rounded-2xl border border-dashed px-4 py-3 text-left text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                  value?.source === "typed" ? "border-brand bg-brand-soft" : "active:bg-brand-soft",
                )}
              >
                <span className="min-w-0 flex-1 truncate">
                  <span className="font-semibold">‘{typed}’</span> 그대로 쓰기
                </span>
                {value?.source === "typed" && <Check className="size-5 shrink-0 text-brand-strong" aria-hidden="true" />}
              </button>
            </li>
          )}
        </ul>
      )}

      {results.length > 0 && <p className="mt-2 text-right text-xs text-muted-foreground">장소 정보 © OpenStreetMap 기여자</p>}
    </section>
  );
}

function GpsBadge({ gps }: { gps: Gps }) {
  const label =
    gps.status === "locating"
      ? "위치 확인 중…"
      : gps.status === "found"
        ? "GPS 자동 감지됨"
        : gps.status === "denied"
          ? "위치 권한 꺼짐"
          : "위치를 알 수 없어요";
  return (
    <span
      role="status"
      className={cn(
        "flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold",
        gps.status === "found" ? "bg-brand-soft text-brand-strong" : "bg-brand-soft text-muted-foreground",
      )}
    >
      <LocateFixed className="size-3.5" aria-hidden="true" />
      {label}
    </span>
  );
}
