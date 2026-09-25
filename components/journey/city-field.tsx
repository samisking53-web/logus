"use client";

// 새 여정 만들기 > 도시. 글자를 입력하면 지도에 있는 도시를 추천하고, 목록에서 고를 수 있다.
// 목록에 없는 도시는 입력한 글자 그대로 쓸 수도 있다.
// 키보드: ↑↓로 고르고 Enter로 선택, Esc로 목록 닫기 (화면 읽기 프로그램용 combobox 규칙을 따른다)
import { useEffect, useMemo, useState } from "react";
import { FormRow, VALUE_BOX } from "@/components/journey/form-row";
import { findExactCity, loadCities, searchCities, type City } from "@/lib/cities";
import { cn } from "@/lib/utils";

type SelectedCity = { name: string; country: string };
type CityList = Awaited<ReturnType<typeof loadCities>>;
type Option = { kind: "city"; city: City } | { kind: "free"; text: string };

const INPUT_ID = "journey-city";
const LIST_ID = "journey-city-options";

export function CityField({
  text,
  selected,
  onChange,
}: {
  text: string;
  selected: SelectedCity | null;
  onChange: (text: string, selected: SelectedCity | null) => void;
}) {
  const [cities, setCities] = useState<CityList | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

  // 이 화면에 들어오면 도시 목록을 미리 받아 둔다 (처음 한 번만 받는다)
  useEffect(() => {
    loadCities().then(setCities, () => setLoadFailed(true));
  }, []);

  const typed = text.trim();
  const options = useMemo<Option[]>(() => {
    if (!typed || selected) return [];
    const found: Option[] = cities ? searchCities(cities, typed).map((city) => ({ kind: "city", city })) : [];
    return [...found, { kind: "free", text: typed }];
  }, [cities, typed, selected]);
  const showList = open && options.length > 0;

  function choose(option: Option) {
    if (option.kind === "city") onChange(option.city.name, { name: option.city.name, country: option.city.country });
    else onChange(option.text, null);
    setOpen(false);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      setOpen(true);
      const step = event.key === "ArrowDown" ? 1 : -1;
      setActive((i) => (options.length ? (i + step + options.length) % options.length : 0));
    } else if (event.key === "Enter" && showList) {
      event.preventDefault();
      choose(options[active] ?? options[0]);
    } else if (event.key === "Escape") {
      setOpen(false);
    }
  }

  // 목록에서 고르지 않고 칸을 벗어났을 때, 입력한 글자가 도시 이름과 똑같으면 그 도시로 정한다
  function onBlur() {
    setOpen(false);
    if (selected || !cities) return;
    const exact = findExactCity(cities, typed);
    if (exact) onChange(exact.name, { name: exact.name, country: exact.country });
  }

  return (
    <FormRow label="도시" labelId="journey-city-label" htmlFor={INPUT_ID}>
      <div className="relative">
        <input
          id={INPUT_ID}
          role="combobox"
          aria-expanded={showList}
          aria-controls={LIST_ID}
          aria-autocomplete="list"
          aria-activedescendant={showList ? `${LIST_ID}-${active}` : undefined}
          aria-describedby={selected ? "journey-city-country" : undefined}
          autoComplete="off"
          placeholder="예: 포르투"
          value={text}
          onChange={(e) => {
            onChange(e.target.value, null);
            setOpen(true);
            setActive(0);
          }}
          onFocus={() => setOpen(true)}
          onBlur={onBlur}
          onKeyDown={onKeyDown}
          className={cn(
            VALUE_BOX,
            "placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
            selected && "pr-28",
          )}
        />
        {/* 목록에서 고른 도시는 나라 이름을 오른쪽에 함께 보여준다 */}
        {selected?.country && (
          <span
            id="journey-city-country"
            className="pointer-events-none absolute inset-y-0 right-4 flex max-w-24 items-center truncate text-sm text-muted-foreground"
          >
            {selected.country}
          </span>
        )}
      </div>

      {showList && (
        <ul
          id={LIST_ID}
          role="listbox"
          aria-label="추천 도시"
          // 목록을 누를 때 입력칸에서 포커스가 빠져 목록이 먼저 닫히지 않게 한다
          onPointerDown={(e) => e.preventDefault()}
          className="absolute left-0 right-0 top-full z-20 mt-2 max-h-80 overflow-y-auto rounded-2xl border bg-card py-2 shadow-lg"
        >
          {options.map((option, i) => (
            <li
              key={option.kind === "city" ? `${option.city.name}-${option.city.country}-${option.city.region}` : "free"}
              id={`${LIST_ID}-${i}`}
              role="option"
              aria-selected={i === active}
              onClick={() => choose(option)}
              onPointerEnter={() => setActive(i)}
              className={cn(
                "flex cursor-pointer items-baseline gap-2 px-4 py-3",
                i === active && "bg-brand-soft",
                option.kind === "free" && "border-t",
              )}
            >
              {option.kind === "city" ? (
                <>
                  <span className="font-semibold">{option.city.name}</span>
                  <span className="min-w-0 truncate text-sm text-muted-foreground">
                    {option.city.country}
                    {option.city.region && ` · ${option.city.region}`}
                  </span>
                </>
              ) : (
                <span className="min-w-0 truncate text-sm">
                  <span className="font-semibold">‘{option.text}’</span> 그대로 쓰기
                </span>
              )}
            </li>
          ))}
          {!cities && (
            <li role="presentation" className="px-4 py-2 text-sm text-muted-foreground">
              {loadFailed ? "도시 목록을 불러오지 못했어요. 직접 입력해도 돼요." : "도시 목록을 불러오는 중…"}
            </li>
          )}
        </ul>
      )}
    </FormRow>
  );
}
