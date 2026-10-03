package com.logus.app.auth

/** 닉네임 검사 결과 */
sealed interface NicknameCheck {
    data class Ok(val value: String) : NicknameCheck
    data class Invalid(val message: String) : NicknameCheck
}

const val NICKNAME_MAX = 20

/** 앞뒤 공백을 빼고 1~20자(이모지도 한 글자로 센다). 보안 규칙의 길이 제한과 같다. */
fun checkNickname(raw: String): NicknameCheck {
    val value = raw.trim()
    val length = value.codePointCount(0, value.length)
    return when {
        length == 0 -> NicknameCheck.Invalid("닉네임을 입력해 주세요.")
        length > NICKNAME_MAX -> NicknameCheck.Invalid("닉네임은 ${NICKNAME_MAX}자 이하로 입력해 주세요.")
        else -> NicknameCheck.Ok(value)
    }
}

/** 구글 이름을 닉네임 기본값으로 쓸 때 20자에 맞춰 자른다(이모지가 반으로 잘리지 않게). */
fun suggestNickname(googleName: String?): String {
    val name = googleName?.trim().orEmpty()
    if (name.codePointCount(0, name.length) <= NICKNAME_MAX) return name
    return name.substring(0, name.offsetByCodePoints(0, NICKNAME_MAX))
}
