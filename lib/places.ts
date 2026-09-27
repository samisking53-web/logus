// 장소 검색: OpenStreetMap 데이터를 쓰는 무료 검색 서비스 Photon(photon.komoot.io)으로 찾는다.
// - 키·가입 없이 쓸 수 있고, 결과(장소 이름·좌표)를 저장해도 된다 (ODbL. 화면에 "© OpenStreetMap" 출처 표기 필요)
// - 공용 서버라 너무 자주 부르지 않는다: 입력을 멈추고 잠시 뒤에 한 번만 찾는다(place-field.tsx)

export type LatLng = { lat: number; lng: number };

export type PlaceSuggestion = {
  id: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
  distanceM: number | null; // 현재 위치에서의 거리(미터). 위치를 모르면 null
};

type PhotonFeature = {
  geometry: { coordinates: [number, number] };
  properties: {
    osm_type?: string;
    osm_id?: number;
    name?: string;
    housenumber?: string;
    street?: string;
    locality?: string;
    district?: string;
    city?: string;
    county?: string;
    state?: string;
    country?: string;
    countrycode?: string;
  };
};

const PHOTON_URL = "https://photon.komoot.io/api/";

// 두 좌표 사이의 거리(미터)
export function distanceMeters(a: LatLng, b: LatLng) {
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
}

// "120m", "1.4km"
export function formatDistance(meters: number) {
  return meters < 1000 ? `${Math.round(meters / 10) * 10}m` : `${(meters / 1000).toFixed(1)}km`;
}

// 주소 한 줄. 한국은 "제주시 한림읍 협재1길 19-8" 순서, 다른 나라는 "Rua X 12, Porto"
function formatAddress(p: PhotonFeature["properties"]) {
  const road = [p.street, p.housenumber].filter(Boolean).join(" ");
  if (p.countrycode === "KR") {
    return [p.city ?? p.county, p.district, p.locality, road].filter(Boolean).join(" ");
  }
  return [road, p.city ?? p.county, p.country].filter(Boolean).join(", ");
}

// 입력한 글자로 장소를 찾는다. 현재 위치를 알면 가까운 곳부터 보여준다
export async function searchPlaces(query: string, near: LatLng | null, signal?: AbortSignal, limit = 5) {
  const params = new URLSearchParams({ q: query, limit: "10" });
  if (near) {
    params.set("lat", String(near.lat));
    params.set("lon", String(near.lng));
    params.set("zoom", "15"); // 이 정도 범위(동네) 안의 결과를 먼저
    params.set("location_bias_scale", "0.1"); // 유명한 곳보다 가까운 곳을 더 앞에
  }
  const res = await fetch(`${PHOTON_URL}?${params}`, { signal });
  if (!res.ok) throw new Error(`장소 검색 실패 (${res.status})`);
  const data: { features?: PhotonFeature[] } = await res.json();

  const places: PlaceSuggestion[] = (data.features ?? [])
    .filter((f) => f.properties.name)
    .map((f) => {
      const [lng, lat] = f.geometry.coordinates;
      return {
        id: `${f.properties.osm_type ?? ""}${f.properties.osm_id ?? `${lat},${lng}`}`,
        name: f.properties.name!,
        address: formatAddress(f.properties),
        lat,
        lng,
        distanceM: near ? distanceMeters(near, { lat, lng }) : null,
      };
    });

  // 같은 장소가 두 번 나오면 하나만
  const unique = places.filter((p, i) => places.findIndex((q) => q.id === p.id) === i);
  if (near) unique.sort((a, b) => (a.distanceM ?? 0) - (b.distanceM ?? 0));
  return unique.slice(0, limit);
}
