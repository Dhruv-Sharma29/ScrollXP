package com.scrollxp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

internal data class Palette(
    val ink: Color, val muted: Color, val green: Color, val lime: Color,
    val paper: Color, val surface: Color, val line: Color, val mist: Color,
    val sky: Color, val accent: Color,
)
private val Day = Palette(Color(0xFF253D35), Color(0xFF59665C), Color(0xFF426A53), Color(0xFFDCE8CA),
    Color(0xFFF6F4EE), Color(0xFFFEFDF9), Color(0xFFE3E5DC), Color(0xFFEEEEE5), Color(0xFFE8EDE0), Color(0xFFA96143))
private val Night = Palette(Color(0xFFE3E8DB), Color(0xFFAFBBAD), Color(0xFFADC9A1), Color(0xFF344C3B),
    Color(0xFF161F1B), Color(0xFF202B24), Color(0xFF364238), Color(0xFF2A352D), Color(0xFF243B35), Color(0xFFE5B99A))
internal fun islandPalette(dusk: Boolean) = if (dusk) Night else Day
private val LocalPalette = staticCompositionLocalOf { Day }
val LocalDusk = staticCompositionLocalOf { false }
val LocalReducedMotion = staticCompositionLocalOf { false }
val Ink: Color @Composable get() = LocalPalette.current.ink
val Muted: Color @Composable get() = LocalPalette.current.muted
val Green: Color @Composable get() = LocalPalette.current.green
val Lime: Color @Composable get() = LocalPalette.current.lime
val Paper: Color @Composable get() = LocalPalette.current.paper
val Card: Color @Composable get() = LocalPalette.current.surface
val Line: Color @Composable get() = LocalPalette.current.line
val Mist: Color @Composable get() = LocalPalette.current.mist
val Sky: Color @Composable get() = LocalPalette.current.sky
val Accent: Color @Composable get() = LocalPalette.current.accent

@Composable
fun ScrollXPTheme(appearance: String = "Daylight", reducedMotion: Boolean = false, proTheme: String? = null, content: @Composable () -> Unit) {
    val dusk = appearance == "Dusk" || (appearance == "System" && isSystemInDarkTheme())
    val base = if (dusk) Night else Day
    val p = when (proTheme) {
        "Ocean" -> if (dusk) base.copy(ink=Color(0xFFE3F0F2),muted=Color(0xFFABC1C9),green=Color(0xFF9FC8D8),lime=Color(0xFF304957),paper=Color(0xFF152027),surface=Color(0xFF1D2B33),line=Color(0xFF344953),mist=Color(0xFF283A44),sky=Color(0xFF223942))
            else base.copy(ink=Color(0xFF203F4C),muted=Color(0xFF536A74),green=Color(0xFF345B6E),lime=Color(0xFFD8EAF0),paper=Color(0xFFF0F5F6),surface=Color(0xFFF8FCFC),line=Color(0xFFD6E3E4),mist=Color(0xFFE5EFF0),sky=Color(0xFFDEEDF0))
        "Rose" -> if (dusk) base.copy(ink=Color(0xFFF2E4EB),muted=Color(0xFFC7B0BD),green=Color(0xFFDAB1C1),lime=Color(0xFF523643),paper=Color(0xFF251A21),surface=Color(0xFF31232C),line=Color(0xFF503C47),mist=Color(0xFF3C2B35),sky=Color(0xFF3E2D35))
            else base.copy(ink=Color(0xFF503B44),muted=Color(0xFF726069),green=Color(0xFF87516A),lime=Color(0xFFF0DAE1),paper=Color(0xFFF9F2F1),surface=Color(0xFFFFFAF8),line=Color(0xFFEDDCDD),mist=Color(0xFFF3E7E9),sky=Color(0xFFF3E4E4))
        "Lavender" -> if (dusk) base.copy(ink=Color(0xFFEAE5F4),muted=Color(0xFFBEB4D0),green=Color(0xFFC3B6DE),lime=Color(0xFF433956),paper=Color(0xFF211D2B),surface=Color(0xFF2C2639),line=Color(0xFF443B53),mist=Color(0xFF362F45),sky=Color(0xFF332C43))
            else base.copy(ink=Color(0xFF443B55),muted=Color(0xFF6C627B),green=Color(0xFF6F5A8C),lime=Color(0xFFE6DCF2),paper=Color(0xFFF5F2FA),surface=Color(0xFFFDFBFF),line=Color(0xFFE5DDED),mist=Color(0xFFEDE7F3),sky=Color(0xFFE8E1F0))
        else -> base
    }
    val scheme = if (dusk) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPalette provides p, LocalDusk provides dusk, LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = scheme.copy(
            primary = p.green, onPrimary = if (dusk) Color(0xFF203426) else Color.White,
            primaryContainer = p.lime, onPrimaryContainer = p.ink,
            secondary = p.accent, secondaryContainer = p.lime, onSecondaryContainer = p.ink,
            background = p.paper, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.mist, onSurfaceVariant = p.muted, outline = p.muted,
            outlineVariant = p.line, surfaceTint = p.green,
            surfaceContainerLowest = p.paper, surfaceContainerLow = p.surface,
            surfaceContainer = p.mist, surfaceContainerHigh = p.surface,
            surfaceContainerHighest = p.mist, surfaceBright = p.surface, surfaceDim = p.paper,
        ), content = content)
    }
}
