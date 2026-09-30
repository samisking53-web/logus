import { defineConfig } from "vitest/config";

// 에뮬레이터 규칙·함수 테스트 설정. `npm run test:rules`로 에뮬레이터와 함께 실행한다.
export default defineConfig({
  test: {
    include: ["tests/**/*.test.ts"],
    // 테스트 파일들이 같은 에뮬레이터 데이터를 쓰므로 한 파일씩 차례로 실행한다.
    fileParallelism: false,
    testTimeout: 20_000,
    hookTimeout: 30_000,
    env: {
      // 테스트 전용 가짜 프로젝트. "demo-"로 시작하면 실제 Firebase에 절대 연결되지 않는다.
      GCLOUD_PROJECT: "demo-logus",
    },
  },
});
