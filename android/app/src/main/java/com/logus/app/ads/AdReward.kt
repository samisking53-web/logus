package com.logus.app.ads

/**
 * 광고 보고 코인 받기 규칙(2026-11-11 요청). 서버 functions/src/adReward.ts 와 같아야 한다.
 * 코인은 서버(claimAdReward)만 준다. 앱은 안내 문구와 "오늘 N / 5회"에만 이 값을 쓴다.
 * 위치·초대 코인의 하루 50코인 제한(Coins.DAILY_LIMIT)과는 따로 센다.
 */
object AdReward {
    /** 광고 1편을 끝까지 보면 받는 코인(서버 AD_REWARD_COINS) */
    const val COINS_PER_VIEW = 2

    /** 하루(세계 표준시 UTC 0시 = 한국 오전 9시에 바뀜)에 광고로 코인을 받을 수 있는 횟수(서버 AD_REWARD_DAILY_LIMIT) */
    const val DAILY_LIMIT = 5

    /** 하루에 광고로 받을 수 있는 코인 합 */
    const val DAILY_MAX_COINS = COINS_PER_VIEW * DAILY_LIMIT
}
