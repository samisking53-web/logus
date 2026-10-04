package com.logus.app.auth

/**
 * 약관 버전. 약관 문구(legal/LegalDocs.kt)를 바꾸면 이 날짜도 바꾼다.
 * Firestore 에 users/{uid}/agreements/{TERMS_VERSION} 으로 동의 기록이 남는다(보안 규칙: YYYY-MM-DD 형식).
 */
const val TERMS_VERSION = "2026-10-04" // 2026-10-04: 새 여정 도시 추천에 현재 위치를 쓰는 내용 추가, 초대 링크 → 6자리 초대 코드

/** 약관 동의 화면의 체크 상태 */
data class Agreements(
    val ageOver14: Boolean = false, // (필수) 만 14세 이상
    val terms: Boolean = false, // (필수) 서비스 이용약관
    val location: Boolean = false, // (필수) 위치기반서비스 이용약관
    val notifyNewLogs: Boolean = false, // (선택) 새 기록 알림 받기
) {
    /** 필수 항목을 모두 동의했는지 */
    val requiredDone: Boolean get() = ageOver14 && terms && location

    /** "약관 전체에 동의합니다" 체크 표시 여부 */
    val allChecked: Boolean get() = requiredDone && notifyNewLogs

    /** 전체 동의를 누르면: 모두 켜져 있으면 모두 끄고, 아니면 모두 켠다 */
    fun toggleAll(): Agreements {
        val next = !allChecked
        return Agreements(ageOver14 = next, terms = next, location = next, notifyNewLogs = next)
    }
}

/** 약관 화면의 체크 항목 */
enum class AgreementItem { AGE_OVER_14, TERMS, LOCATION, NOTIFY_NEW_LOGS }

fun Agreements.toggle(item: AgreementItem): Agreements = when (item) {
    AgreementItem.AGE_OVER_14 -> copy(ageOver14 = !ageOver14)
    AgreementItem.TERMS -> copy(terms = !terms)
    AgreementItem.LOCATION -> copy(location = !location)
    AgreementItem.NOTIFY_NEW_LOGS -> copy(notifyNewLogs = !notifyNewLogs)
}
