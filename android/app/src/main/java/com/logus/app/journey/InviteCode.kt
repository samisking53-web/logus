package com.logus.app.journey

/**
 * 6자리 초대 코드 규칙 (서버 functions/src/common.ts 와 같다)
 * 헷갈리는 글자(0·O·1·I)를 뺀 영문 대문자·숫자. 소문자로 쳐도 대문자로 바꿔 받는다.
 */
object InviteCode {
    const val LENGTH = 6
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /**
     * 입력한 글자를 코드 형식으로 정리한다: 대문자로 바꾸고, 코드에 없는 글자(공백·0·O·1·I·기호)는 빼고, 6자리까지만.
     * 돌려주는 값: (정리한 코드, 빠진 글자가 있었는지)
     */
    fun clean(input: String): Pair<String, Boolean> {
        val upper = input.uppercase()
        val kept = upper.filter { it in ALPHABET }
        val droppedSomething = kept.length != upper.count { !it.isWhitespace() }
        return kept.take(LENGTH) to droppedSomething
    }

    fun isComplete(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
