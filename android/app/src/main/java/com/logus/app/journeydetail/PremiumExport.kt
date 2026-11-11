package com.logus.app.journeydetail

/**
 * DAY RECAP 프리미엄 추출(2026-11-11 팀 요청).
 * - 일반 다운로드: 지금처럼 무료, 오른쪽 아래 "LOG EARTH" 워터마크가 들어간다(만드는 동안 진행 팝업에 광고 배너, 2026-11-11).
 * - 프리미엄 다운로드: 50코인을 내면 워터마크 없이 만든다. 한 번 내면 같은 여정의 같은 날 DAY RECAP 은
 *   다시 받을 때도 무료다(낸 사람만, 다른 구성원은 따로 낸다).
 * 코인은 서버 함수 unlockPremiumExport 가 뺀다(앱은 코인을 직접 못 바꿈).
 */
object PremiumExport {
    /** 프리미엄 추출에 쓰는 코인(서버 functions/src/premiumExport.ts 의 PREMIUM_EXPORT_COINS 와 같아야 한다) */
    const val COINS = 50
}
