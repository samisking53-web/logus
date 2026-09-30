// 모든 함수에 공통으로 적용하는 옵션
// - region: 서울(Firestore와 같은 위치)
// - maxInstances: 갑작스러운 호출 폭주로 요금이 늘지 않도록 상한을 둔다
// - minInstances는 설정하지 않는다(상시 과금)
// index.ts에서 함수보다 먼저 import 해야 모든 함수에 적용된다.
import { setGlobalOptions } from "firebase-functions/v2";

setGlobalOptions({
  region: "asia-northeast3",
  maxInstances: 10,
});
