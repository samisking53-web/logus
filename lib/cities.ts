// 도시 추천: 입력한 글자로 도시를 찾는다 (새 여정 만들기 > 도시).
// 도시 목록은 public/data/cities.json (Natural Earth, public domain). 만드는 방법은 scripts/build-cities.mjs
// 목록은 처음 필요할 때 한 번만 내려받고, 그 뒤로는 다시 받지 않는다.

export type City = {
  name: string; // 한국어 이름 (예: 포르투)
  english: string; // 영어 이름 (예: Porto). "porto"로 찾을 때 쓴다
  country: string; // 한국어 국가 이름 (예: 포르투갈)
  region: string; // 같은 나라에 이름이 같은 도시가 있을 때만 (예: Oregon)
};

type SearchableCity = City & { keys: string[]; countryKey: string };

type CitiesFile = {
  countries: string[];
  cities: [string, string, number, string?, string?][];
};

// 비교하기 쉽게 바꾼다: 영어 대문자→소문자, 악센트 제거(São→sao), 띄어쓰기·점·하이픈 제거
export function normalizeForSearch(text: string) {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .normalize("NFC")
    .toLowerCase()
    .replace(/[\s.·'\-]/g, "");
}

let loading: Promise<SearchableCity[]> | null = null;

export function loadCities(): Promise<SearchableCity[]> {
  loading ??= fetch("/data/cities.json")
    .then((res) => {
      if (!res.ok) throw new Error(`도시 목록을 불러오지 못했어요 (${res.status})`);
      return res.json() as Promise<CitiesFile>;
    })
    .then((file) =>
      file.cities.map(([name, english, countryIndex, alias = "", region = ""]) => {
        const country = file.countries[countryIndex];
        return {
          name,
          english,
          country,
          region,
          keys: [name, english, alias].filter(Boolean).map(normalizeForSearch),
          countryKey: normalizeForSearch(country),
        };
      }),
    )
    .catch((err) => {
      loading = null; // 실패하면 다음에 다시 시도한다
      throw err;
    });
  return loading;
}

// 찾는 순서: ① 이름이 입력한 글자와 똑같은 도시 ② 이름이 입력한 글자로 시작하는 도시
// ③ 이름에 입력한 글자가 들어 있는 도시 ④ 나라 이름이 맞으면 그 나라의 도시.
// 같은 순서 안에서는 큰 도시가 먼저 나온다 (목록이 중요도순)
export function searchCities(cities: SearchableCity[], query: string, limit = 8): City[] {
  const q = normalizeForSearch(query);
  if (!q) return [];
  const tiers: SearchableCity[][] = [[], [], [], []];
  for (const city of cities) {
    if (city.keys.includes(q)) tiers[0].push(city);
    else if (city.keys.some((k) => k.startsWith(q))) tiers[1].push(city);
    else if (q.length >= 2 && city.keys.some((k) => k.includes(q))) tiers[2].push(city);
    else if (city.countryKey.startsWith(q)) tiers[3].push(city);
  }
  return tiers
    .flat()
    .slice(0, limit)
    .map(({ name, english, country, region }) => ({ name, english, country, region }));
}

// 입력한 글자가 어떤 도시 이름과 똑같으면 그 도시 (가장 큰 도시). 목록에서 고르지 않고 칸을 벗어났을 때 쓴다
export function findExactCity(cities: SearchableCity[], query: string): City | null {
  const q = normalizeForSearch(query);
  if (!q) return null;
  const found = cities.find((c) => c.keys.includes(q));
  return found ? { name: found.name, english: found.english, country: found.country, region: found.region } : null;
}
