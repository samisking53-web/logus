package com.logus.app.auth

/**
 * 코인 규칙(서버 functions/src/common.ts 와 같아야 한다).
 * 코인은 서버만 준다(위치 저장 saveLogLocation 10코인, 초대 보상 joinJourney 5코인). 앱은 안내 문구에만 이 값을 쓴다.
 */
object Coins {
    /** 한 사람이 하루(세계 표준시 UTC 0시 기준)에 받을 수 있는 코인. 서버 DAILY_COIN_LIMIT 과 같다(2026-11-04 팀 결정) */
    const val DAILY_LIMIT = 50
}
