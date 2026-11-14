package app.still.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.still.R

@Immutable
data class UnloopColors(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val danger: Color,
    val dark: Boolean,
)

val DarkColors = UnloopColors(
    bg = Color(0xFF000000), surface = Color(0xFF111111), surfaceRaised = Color(0xFF1C1C1C),
    line = Color(0xFF262626), lineStrong = Color(0xFF666666),
    ink = Color(0xFFF3F3F3), ink2 = Color(0xFFA5A5A5), ink3 = Color(0xFF808080),
    primary = Color(0xFFEBEBEB), onPrimary = Color(0xFF151515),
    accent = Color(0xFF6DBE98), onAccent = Color(0xFF000000), accentSoft = Color(0xFF0F2A20),
    warn = Color(0xFFE8B44A), warnSoft = Color(0xFF2A2008), danger = Color(0xFFFF6B5E),
    dark = true,
)

val LightColors = UnloopColors(
    bg = Color(0xFFFFFFFF), surface = Color(0xFFF5F5F5), surfaceRaised = Color(0xFFEBEBEB),
    line = Color(0xFFE0E0E0), lineStrong = Color(0xFF858585),
    ink = Color(0xFF151515), ink2 = Color(0xFF5E5E5E), ink3 = Color(0xFF6B6B6B),
    primary = Color(0xFF171717), onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF285D4B), onAccent = Color(0xFFFFFFFF), accentSoft = Color(0xFFE6EDE5),
    warn = Color(0xFF8A5A00), warnSoft = Color(0xFFFAF1DC), danger = Color(0xFFC2362B),
    dark = false,
)

// One variable font file: each weight needs its own wght axis value, otherwise every weight renders at 400.
@OptIn(ExperimentalTextApi::class)
private fun onest(weight: Int) =
    Font(R.font.onest, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Onest = FontFamily(onest(400), onest(500), onest(600), onest(700))

object UnloopType {
    val display = TextStyle(fontFamily = Onest, fontSize = 48.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2.4).sp)
    val number = TextStyle(fontFamily = Onest, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Medium, letterSpacing = (-3).sp, fontFeatureSettings = "tnum")
    val title = TextStyle(fontFamily = Onest, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
    val wordmark = TextStyle(fontFamily = Onest, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
    val headline = TextStyle(fontFamily = Onest, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val subtitle = TextStyle(fontFamily = Onest, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val body = TextStyle(fontFamily = Onest, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = TextStyle(fontFamily = Onest, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val button = TextStyle(fontFamily = Onest, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val caption = TextStyle(fontFamily = Onest, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val label = TextStyle(fontFamily = Onest, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

private val LocalUnloopColors = staticCompositionLocalOf { DarkColors }

object Unloop {
    val colors: UnloopColors
        @Composable @ReadOnlyComposable get() = LocalUnloopColors.current
}

@Composable
fun UnloopTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, background = c.bg, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.ink2,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.surfaceRaised, outline = c.lineStrong, outlineVariant = c.line,
        inverseSurface = c.primary, inverseOnSurface = c.onPrimary, error = c.danger, scrim = Color(0xB8000000),
    ) else lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, background = c.bg, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.ink2,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.surfaceRaised, outline = c.lineStrong, outlineVariant = c.line,
        inverseSurface = c.primary, inverseOnSurface = c.onPrimary, error = c.danger, scrim = Color(0x66000000),
    )
    val typography = Typography(
        titleLarge = UnloopType.headline, bodyLarge = UnloopType.body, bodyMedium = UnloopType.body,
        bodySmall = UnloopType.caption, labelLarge = UnloopType.button, labelMedium = UnloopType.caption,
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalUnloopColors provides c, content = content)
    }
}
