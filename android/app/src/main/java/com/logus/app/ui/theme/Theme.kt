package com.logus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = PrimaryStrong,
    background = Background,
    onBackground = Text,
    surface = Surface,
    onSurface = Text,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    error = Error,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimary,
    secondary = PrimaryStrongDark,
    background = BackgroundDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    error = Error,
)

/** 폰이 다크 모드면 다크 팔레트를 쓴다. */
@Composable
fun LogUsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** Material 색 체계에 없는 팔레트 색(코인·카드 테두리 등). 다크 모드에 맞춰 골라 준다. */
object LogUsColors {
    val amberText: androidx.compose.ui.graphics.Color
        @Composable get() = if (isSystemInDarkTheme()) AmberTextDark else AmberText
    val border: androidx.compose.ui.graphics.Color
        @Composable get() = if (isSystemInDarkTheme()) BorderDark else Border
    val primaryStrong: androidx.compose.ui.graphics.Color
        @Composable get() = if (isSystemInDarkTheme()) PrimaryStrongDark else PrimaryStrong
    /** 흰 카드(라이트) / 카드 배경(다크) */
    val card: androidx.compose.ui.graphics.Color
        @Composable get() = if (isSystemInDarkTheme()) SurfaceDark else Card
    /** 홈 프로필 상자 배경: 라이트는 연보라(border 색), 다크는 카드 배경 */
    val profileBox: androidx.compose.ui.graphics.Color
        @Composable get() = if (isSystemInDarkTheme()) SurfaceDark else Border
    /** 구분선 */
    val line: androidx.compose.ui.graphics.Color
        @Composable get() = if (isSystemInDarkTheme()) BorderDark else Line
    /** 조금 밝은 보라 버튼 채움(S01-A "새 여정 시작하기"). 다크 모드는 primary 와 같은 #6B4FE0(흰 글자 대비를 지키는 가장 밝은 보라) */
    val primaryLight: androidx.compose.ui.graphics.Color
        @Composable get() = if (isSystemInDarkTheme()) PrimaryDark else PrimaryLight
    /** 팝업 위쪽 그림 동그라미: 라이트는 연보라(surface), 다크는 바탕색(카드와 같은 색이면 동그라미가 안 보여서) */
    val iconCircle: androidx.compose.ui.graphics.Color
        @Composable get() = if (isSystemInDarkTheme()) BackgroundDark else Surface
    /** 프로필 상자 안 코인 상자: 라이트는 흰색, 다크는 바탕색(상자보다 어둡게) */
    val coinChip: androidx.compose.ui.graphics.Color
        @Composable get() = if (isSystemInDarkTheme()) BackgroundDark else Card
}
