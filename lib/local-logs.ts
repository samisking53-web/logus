// 실습용 기록(logs) 저장소 (서버 없음). 계정·여정처럼 이 기기(브라우저)에만 있다.
// - 영상 파일은 커서 localStorage(약 5MB)에 넣을 수 없어, 브라우저의 큰 저장소인 IndexedDB에 넣는다
// - 저장 모양은 CLAUDE.md 데이터 모델의 logs를 따른다. 영상은 media_path 대신 파일 내용을 그대로 둔다
// - 서버를 붙이면 영상·첫 장면은 Supabase Storage(journeys/{journey_id}/...)에, 나머지는 logs 표로 옮긴다
import { JOURNEY_STORAGE_ERROR } from "@/lib/local-journeys";

const DB_NAME = "logus";
const DB_VERSION = 1;
const STORE = "logs";

export type LocalLog = {
  id: string;
  journeyId: string;
  authorId: string;
  mediaType: "video";
  video: Blob;
  poster: string | null; // 영상 첫 장면(JPEG 이미지 글자). 영상을 틀기 전에 보여준다
  capturedAt: string; // 방문 일시 (UTC)
  capturedTz: string; // 찍은 곳의 시간대 (예: Asia/Seoul)
  lat: number | null;
  lng: number | null;
  placeName: string | null;
  rating: number | null; // 0.5~5, 매기지 않았으면 null
  review: string | null; // 한줄평
  isPublic: boolean;
  taggedUserIds: string[]; // 함께한 사람(log_tags). 지금은 고르는 칸이 없어 빈 목록
  createdAt: string;
};

// IndexedDB에는 영상을 Blob 대신 ArrayBuffer로 넣는다.
// 일부 아이폰 Safari에서 IndexedDB에 넣은 Blob을 다시 읽으면 오류가 나는 문제를 피하기 위해서다
type StoredLog = Omit<LocalLog, "video"> & { videoData: ArrayBuffer; videoType: string };

export type NewLogInput = Omit<LocalLog, "id" | "mediaType" | "isPublic" | "createdAt">;
export type AddLogResult = { ok: true; log: LocalLog } | { ok: false; message: string };

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === "undefined") return reject(new Error("IndexedDB를 쓸 수 없어요"));
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: "id" }).createIndex("journeyId", "journeyId");
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function addLog(input: NewLogInput): Promise<AddLogResult> {
  const log: LocalLog = {
    ...input,
    id: crypto.randomUUID(),
    mediaType: "video",
    isPublic: false, // 탐색 공개는 위치 저장(S07)에서 고른다
    createdAt: new Date().toISOString(),
  };
  try {
    const { video, ...rest } = log;
    const stored: StoredLog = { ...rest, videoData: await video.arrayBuffer(), videoType: video.type };
    const db = await openDb();
    try {
      await new Promise<void>((resolve, reject) => {
        const tx = db.transaction(STORE, "readwrite");
        tx.objectStore(STORE).add(stored);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
        tx.onabort = () => reject(tx.error);
      });
    } finally {
      db.close();
    }
    return { ok: true, log };
  } catch (err) {
    const name = err instanceof DOMException ? err.name : "";
    return {
      ok: false,
      message: name === "QuotaExceededError" ? "폰의 저장 공간이 부족해서 영상을 저장하지 못했어요." : JOURNEY_STORAGE_ERROR,
    };
  }
}

// 한 여정의 기록. 방문 일시가 이른 것부터(여행 순서)
export async function listJourneyLogs(journeyId: string): Promise<LocalLog[]> {
  const db = await openDb();
  try {
    const stored = await new Promise<StoredLog[]>((resolve, reject) => {
      const request = db.transaction(STORE, "readonly").objectStore(STORE).index("journeyId").getAll(journeyId);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    return stored
      .map(({ videoData, videoType, ...rest }) => ({ ...rest, video: new Blob([videoData], { type: videoType }) }))
      .sort((a, b) => a.capturedAt.localeCompare(b.capturedAt) || a.createdAt.localeCompare(b.createdAt));
  } finally {
    db.close();
  }
}
