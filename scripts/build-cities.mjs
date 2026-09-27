// 도시 추천(새 여정 만들기 > 도시)에 쓰는 도시 목록 public/data/cities.json을 만든다.
//
// 출처: Natural Earth 10m populated places + admin 0 countries (public domain, https://www.naturalearthdata.com)
//   - 세계 지도에 표시되는 도시 7천여 곳이고, 모든 도시에 한국어 이름(NAME_KO)이 있다.
//   - 카카오·구글 장소 검색 결과가 아니라서 앱에 넣고 저장해도 된다 (CLAUDE.md 데이터 원칙).
//
// 실행: node scripts/build-cities.mjs
// 데이터 버전을 바꾸려면 아래 NATURAL_EARTH_COMMIT만 바꾼다.
import { writeFileSync } from "node:fs";

const NATURAL_EARTH_COMMIT = "ca96624a56bd078437bca8184e78163e5039ad19";
const BASE = `https://raw.githubusercontent.com/nvkelso/natural-earth-vector/${NATURAL_EARTH_COMMIT}/geojson`;
const OUTPUT = new URL("../public/data/cities.json", import.meta.url);

// 한국에서 더 흔히 쓰는 국가 이름으로 바꾼다
const COUNTRY_NAME_OVERRIDES = {
  중화민국: "대만",
  중화인민공화국: "중국",
  조선민주주의인민공화국: "북한",
  오스트레일리아: "호주",
  터키: "튀르키예",
};
// 나라 표(admin 0)에 없는 코드: 도시 표의 코드 → 한국어 국가 이름
const COUNTRY_FALLBACKS = { SSD: "남수단", SJM: "노르웨이", TKL: "토켈라우" };
// 이름 뒤에 '시'가 붙어 있는 나라(예: 후쿠오카시, 제주시). 화면에는 '시'를 뺀 이름을 보여준다
const CITY_SUFFIX_COUNTRIES = new Set(["KOR", "PRK", "JPN", "CHN", "TWN"]);
const CITY_NAME_OVERRIDES = { 도쿄도: "도쿄" };

async function download(file) {
  const res = await fetch(`${BASE}/${file}`);
  if (!res.ok) throw new Error(`${file} 내려받기 실패: ${res.status}`);
  return res.json();
}

function displayName(nameKo, countryA3) {
  const name = nameKo.trim();
  if (CITY_NAME_OVERRIDES[name]) return CITY_NAME_OVERRIDES[name];
  if (!CITY_SUFFIX_COUNTRIES.has(countryA3)) return name;
  const stripped = name.replace(/(특별자치시|특별시|광역시|시)$/, "");
  return stripped.length >= 2 ? stripped : name;
}

const [places, countries] = await Promise.all([
  download("ne_10m_populated_places.geojson"),
  download("ne_10m_admin_0_countries.geojson"),
]);

const countryByA3 = new Map();
const countryByIso2 = new Map();
for (const { properties: c } of countries.features) {
  const name = COUNTRY_NAME_OVERRIDES[c.NAME_KO] ?? c.NAME_KO;
  countryByA3.set(c.ADM0_A3, name);
  if (c.ISO_A2 && c.ISO_A2 !== "-99") countryByIso2.set(c.ISO_A2, name);
}

function countryName(p) {
  return (
    countryByA3.get(p.ADM0_A3) ??
    countryByIso2.get(p.ISO_A2) ??
    countryByA3.get(p.SOV_A3) ??
    COUNTRY_FALLBACKS[p.ADM0_A3] ??
    p.ADM0NAME
  );
}

// 중요한 도시부터: 지도 축척 등급(SCALERANK)이 낮을수록, 인구가 많을수록 앞에 둔다
const sorted = places.features
  .map((f) => f.properties)
  .sort((a, b) => a.SCALERANK - b.SCALERANK || b.POP_MAX - a.POP_MAX);

const rows = sorted.map((p) => {
  const name = displayName(p.NAME_KO, p.ADM0_A3);
  return {
    name,
    english: p.NAME,
    country: countryName(p),
    alias: name === p.NAME_KO.trim() ? "" : p.NAME_KO.trim(),
    region: p.ADM1NAME ?? "",
  };
});

// 같은 나라에 이름이 같은 도시가 있으면(예: 미국의 스프링필드) 주·도 이름을 붙여 구분한다
const sameName = new Map();
for (const r of rows) {
  const key = `${r.name}|${r.country}`;
  sameName.set(key, (sameName.get(key) ?? 0) + 1);
}
const seen = new Set();
const unique = [];
for (const r of rows) {
  const needsRegion = sameName.get(`${r.name}|${r.country}`) > 1;
  const key = `${r.name}|${r.country}|${needsRegion ? r.region : ""}`;
  if (seen.has(key)) continue; // 이름·나라·지역까지 같으면 덜 중요한 쪽을 뺀다
  seen.add(key);
  unique.push({ ...r, region: needsRegion ? r.region : "" });
}

const countryList = [...new Set(unique.map((r) => r.country))];
const countryIndex = new Map(countryList.map((c, i) => [c, i]));

// 한 줄에 도시 하나: [한국어 이름, 영어 이름, 국가 번호, 원래 한국어 이름(다를 때만), 지역(구분이 필요할 때만)]
const lines = unique.map((r) => {
  const row = [r.name, r.english, countryIndex.get(r.country)];
  if (r.alias || r.region) row.push(r.alias);
  if (r.region) row.push(r.region);
  return JSON.stringify(row);
});

const json =
  `{"source":"Natural Earth 10m populated places (public domain) @ ${NATURAL_EARTH_COMMIT.slice(0, 7)}",\n` +
  `"countries":${JSON.stringify(countryList)},\n` +
  `"cities":[\n${lines.join(",\n")}\n]}\n`;
writeFileSync(OUTPUT, json);
console.log(`도시 ${unique.length}곳, 국가 ${countryList.length}곳 → public/data/cities.json`);
