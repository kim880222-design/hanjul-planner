package com.hanjul.planner.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanjul.planner.R

/**
 * 색은 모두 xterm 256색 팔레트 안에서만 고른다. (괄호 안은 팔레트 번호)
 * 검은 화면 + 호박색 글자 + 청록 포인트 = 카세트 퓨처리즘 단말기 느낌.
 */
object Pal {
    val Black = Color(0xFF000000)      // 16
    val Gray0 = Color(0xFF121212)      // 233
    val Gray1 = Color(0xFF1C1C1C)      // 234
    val Gray2 = Color(0xFF262626)      // 235
    val Gray3 = Color(0xFF3A3A3A)      // 237
    val Gray4 = Color(0xFF585858)      // 240
    val Gray5 = Color(0xFF808080)      // 244
    val Gray6 = Color(0xFFBCBCBC)      // 250
    val Text = Color(0xFFD0D0D0)       // 252

    val Amber = Color(0xFFFFAF00)      // 214
    val AmberLight = Color(0xFFFFD787) // 222
    val AmberDeep = Color(0xFF875F00)  // 94
    val Orange = Color(0xFFFF8700)     // 208
    val Rust = Color(0xFFD75F00)       // 166
    val Red = Color(0xFFFF005F)        // 197
    val Cyan = Color(0xFF00D7FF)       // 45
    val CyanDeep = Color(0xFF005F5F)   // 23
    val Green = Color(0xFF87FF00)      // 118
    val Violet = Color(0xFFAF5FFF)     // 135
    val VioletDeep = Color(0xFF5F0087) // 54
}

val Overdue = Pal.Red
val Soon = Pal.Orange
val EventColor = Pal.Cyan
val TaskColor = Pal.Green
val ProjectColor = Pal.Violet

val Mono = FontFamily(
    // 굵은 글씨도 보통 굵기 파일을 써서 얇게 보이도록 (가짜 굵게 처리도 막는다)
    Font(R.font.galmuri_mono, FontWeight.Normal),
    Font(R.font.galmuri_mono, FontWeight.Medium),
    Font(R.font.galmuri_mono, FontWeight.SemiBold),
    Font(R.font.galmuri_mono, FontWeight.Bold),
)

private val Scheme = darkColorScheme(
    primary = Pal.Amber,
    onPrimary = Pal.Black,
    primaryContainer = Pal.AmberDeep,
    onPrimaryContainer = Pal.AmberLight,
    inversePrimary = Pal.AmberDeep,
    secondary = Pal.Cyan,
    onSecondary = Pal.Black,
    secondaryContainer = Pal.CyanDeep,
    onSecondaryContainer = Pal.Cyan,
    tertiary = Pal.Violet,
    onTertiary = Pal.Black,
    tertiaryContainer = Pal.VioletDeep,
    onTertiaryContainer = Pal.Violet,
    background = Pal.Black,
    onBackground = Pal.Text,
    surface = Pal.Black,
    onSurface = Pal.Text,
    surfaceVariant = Pal.Gray2,
    onSurfaceVariant = Pal.Gray5,
    surfaceTint = Pal.Black, // 그림자 색 섞임을 막아서 팔레트 밖의 색이 생기지 않게
    inverseSurface = Pal.Amber,
    inverseOnSurface = Pal.Black,
    error = Pal.Red,
    onError = Pal.Black,
    errorContainer = Pal.Red,
    onErrorContainer = Pal.Black,
    outline = Pal.Gray4,
    outlineVariant = Pal.Gray3,
    scrim = Pal.Black,
    surfaceBright = Pal.Gray2,
    surfaceDim = Pal.Black,
    surfaceContainerLowest = Pal.Black,
    surfaceContainerLow = Pal.Gray0,
    surfaceContainer = Pal.Gray0,
    surfaceContainerHigh = Pal.Gray1,
    surfaceContainerHighest = Pal.Gray2,
)

private fun s(size: Int, weight: FontWeight = FontWeight.Normal, height: Int = size + 6) =
    TextStyle(fontFamily = Mono, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp, letterSpacing = 0.sp)

private val Type = Typography(
    displayLarge = s(44, FontWeight.Normal),
    displayMedium = s(36, FontWeight.Normal),
    displaySmall = s(30, FontWeight.Normal),
    headlineLarge = s(28, FontWeight.Normal),
    headlineMedium = s(24, FontWeight.Normal),
    headlineSmall = s(22, FontWeight.Normal),
    titleLarge = s(20, FontWeight.Normal),
    titleMedium = s(17, FontWeight.Normal),
    titleSmall = s(15, FontWeight.Normal),
    bodyLarge = s(16),
    bodyMedium = s(14),
    bodySmall = s(12),
    labelLarge = s(14, FontWeight.Normal),
    labelMedium = s(12),
    labelSmall = s(11),
)

/** 모서리 없는 각진 상자 */
private val Square = RoundedCornerShape(0.dp)
private val Boxy = Shapes(
    extraSmall = Square, small = Square, medium = Square, large = Square, extraLarge = Square,
)

@Composable
fun PlannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, shapes = Boxy, content = content)
}
