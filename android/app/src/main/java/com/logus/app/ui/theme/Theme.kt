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

/** 폰이 다크 모드면 다크 팔레트를 쓴다(웹과 같은 규칙). */
@Composable
fun LogUsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
