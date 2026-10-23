package com.seen.film.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val SeenBackground = Color(0xFFF7F7F5)
val SeenInk = Color(0xFF202124)
val SeenSecondary = Color(0xFF64676C)

@Composable
fun SeenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = SeenInk, onPrimary = Color.White,
            background = SeenBackground, onBackground = SeenInk,
            surface = SeenBackground, onSurface = SeenInk,
            surfaceVariant = Color(0xFFE5E6E3), onSurfaceVariant = SeenSecondary,
            outline = Color(0xFF7B7E83), error = Color(0xFFA63732),
        ),
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp)),
        content = content,
    )
}
