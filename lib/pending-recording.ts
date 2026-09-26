// 방금 찍은 영상을 카메라 화면(S04)에서 기록 올리기 화면(S05)으로 넘겨주는 임시 보관함.
// 영상은 파일이 커서 브라우저 저장 공간에 넣지 않고, 앱이 켜져 있는 동안 메모리에만 둔다.
// 그래서 기록 올리기 화면에서 새로고침하면 영상이 사라지고 카메라로 돌아간다.

export type PendingRecording = {
  id: string; // 찍을 때마다 새로 만드는 번호. 기록 올리기 화면이 새 영상인지 알아보는 데 쓴다
  journeyId: string;
  video: Blob; // 찍은 영상 파일
  frames: string[]; // 1초마다 남긴 장면(JPEG 이미지 글자). 대표 화면 후보
  recordedAt: string; // 찍기 시작한 시각 (UTC)
  timezone: string; // 찍은 곳의 시간대 (예: Asia/Seoul)
  durationMs: number;
};

let pending: PendingRecording | null = null;

export function setPendingRecording(recording: PendingRecording) {
  pending = recording;
}

export function getPendingRecording(journeyId: string) {
  return pending?.journeyId === journeyId ? pending : null;
}

export function clearPendingRecording() {
  pending = null;
}
