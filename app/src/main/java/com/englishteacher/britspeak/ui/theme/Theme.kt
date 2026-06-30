package com.englishteacher.britspeak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — a friendly indigo/teal scheme distinct from generic AI purple-on-white.
val Indigo = Color(0xFF3F3D9E)
val IndigoLight = Color(0xFF5B57C9)
val Teal = Color(0xFF2BB7A8)
val Coral = Color(0xFFFF6F61)
val Cream = Color(0xFFFBFAFF)
val InkDark = Color(0xFF15151F)

private val LightColors =
    lightColorScheme(
        primary = Indigo,
        onPrimary = Color.White,
        primaryContainer = IndigoLight,
        onPrimaryContainer = Color.White,
        secondary = Teal,
        onSecondary = Color.White,
        tertiary = Coral,
        background = Cream,
        onBackground = InkDark,
        surface = Color.White,
        onSurface = InkDark,
    )

private val DarkColors =
    darkColorScheme(
        primary = IndigoLight,
        onPrimary = Color.White,
        secondary = Teal,
        tertiary = Coral,
        background = Color(0xFF101018),
        surface = Color(0xFF181824),
    )

@Composable
fun BritSpeakTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = BritSpeakTypography,
        content = content,
    )
}
