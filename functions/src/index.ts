// LOG US 서버 함수 진입점
// 기능별 함수는 functions/src/ 아래에 파일을 나눠 만들고, 여기서 export 한다.
// 예) export { createJourney } from "./createJourney";
import { setGlobalOptions } from "firebase-functions/v2";

// 모든 함수에 공통으로 적용하는 옵션
// - region: 서울(Firestore와 같은 위치)
// - maxInstances: 갑작스러운 호출 폭주로 요금이 늘지 않도록 상한을 둔다
// - minInstances는 설정하지 않는다(상시 과금)
setGlobalOptions({
  region: "asia-northeast3",
  maxInstances: 10,
});
