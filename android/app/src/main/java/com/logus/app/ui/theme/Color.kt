package com.logus.app.ui.theme

import androidx.compose.ui.graphics.Color

// 팔레트: CLAUDE.md "UI 규칙"과 같은 값이다. 새 색은 팔레트에서만 고른다.

// 라이트
val Primary = Color(0xFF5B3DD6) // 채움 전용(버튼·활성 탭). 위 글자는 흰색
val PrimaryStrong = Color(0xFF4A2FB8) // 강조 글자
val Surface = Color(0xFFEEEAFB) // 카드
val Border = Color(0xFFD8CFF5)
val Text = Color(0xFF1A1725)
val TextSecondary = Color(0xFF5A5570)
val Background = Color(0xFFF7F5FB)
val Error = Color(0xFFC0392B)
val AmberText = Color(0xFF8A5606)

// 다크
val PrimaryDark = Color(0xFF6B4FE0)
val PrimaryStrongDark = Color(0xFFB3A0FF)
val SurfaceDark = Color(0xFF1E1B2E)
val BorderDark = Color(0xFF4A2FB8)
val TextDark = Color(0xFFF7F5FB)
val TextSecondaryDark = Color(0xFFE3E0EC)
val BackgroundDark = Color(0xFF141220)
val AmberTextDark = Color(0xFFE0930F)

val OnPrimary = Color(0xFFFFFFFF)
val PhotoFrame = Color(0xFFFFFFFF) // 첫 화면 소개 사진의 흰 테두리(라이트·다크 같음)

// 구글 로그인 버튼은 구글 디자인 가이드에 정해진 색을 그대로 써야 해서 팔레트의 예외로 둔다.
val GoogleButton = Color(0xFFFFFFFF)
val GoogleLabel = Color(0xFF1F1F1F)
val GoogleOutline = Color(0xFF747775)
