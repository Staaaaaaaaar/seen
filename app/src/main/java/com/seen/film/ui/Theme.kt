package com.seen.film.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Semantic palette: quiet surfaces let the film posters carry the colour.
private val Ink = Color(0xFF202124)
private val Paper = Color(0xFFFAFAF9)
val SeenSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private val SeenTypography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 25.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
)

@Composable
fun SeenTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) darkColorScheme(
        primary = Color(0xFFF0F0EE), onPrimary = Ink,
        background = Color(0xFF141516), onBackground = Color(0xFFF0F0EE),
        surface = Color(0xFF141516), onSurface = Color(0xFFF0F0EE),
        surfaceVariant = Color(0xFF282A2C), onSurfaceVariant = Color(0xFFB0B2B6),
        surfaceContainer = Color(0xFF202224), surfaceContainerHigh = Color(0xFF282A2C),
        outline = Color(0xFF92959A), outlineVariant = Color(0xFF393C40),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    ) else lightColorScheme(
        primary = Ink, onPrimary = Color.White,
        background = Paper, onBackground = Ink,
        surface = Paper, onSurface = Ink,
        surfaceVariant = Color(0xFFEEEFEF), onSurfaceVariant = Color(0xFF64676C),
        surfaceContainer = Color(0xFFF1F2F1), surfaceContainerHigh = Color(0xFFE9EAE9),
        outline = Color(0xFF797D83), outlineVariant = Color(0xFFDDDFE1),
        error = Color(0xFFA63732), onError = Color.White,
    )
    MaterialTheme(
        colorScheme = colors, typography = SeenTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = content,
    )
}
