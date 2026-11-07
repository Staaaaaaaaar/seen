package com.seen.film.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object SeenIcons {
    private fun icon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()
    val Film by lazy { icon("Film") {
        moveTo(5f,3f);lineTo(19f,3f);lineTo(19f,21f);lineTo(5f,21f);close()
        moveTo(9f,3f);lineTo(9f,21f);moveTo(15f,3f);lineTo(15f,21f)
        moveTo(5f,8f);lineTo(9f,8f);moveTo(15f,8f);lineTo(19f,8f)
        moveTo(5f,16f);lineTo(9f,16f);moveTo(15f,16f);lineTo(19f,16f)
        moveTo(9f,12f);lineTo(15f,12f)
    } }
    val Chart by lazy { icon("Chart") { moveTo(5f,20f);lineTo(5f,11f);moveTo(12f,20f);lineTo(12f,4f);moveTo(19f,20f);lineTo(19f,8f) } }
    val Journal by lazy { icon("Journal") { moveTo(5f,4f);lineTo(19f,4f);lineTo(19f,21f);lineTo(5f,21f);close();moveTo(8f,4f);lineTo(8f,21f);moveTo(11f,9f);lineTo(16f,9f);moveTo(11f,13f);lineTo(15f,13f) } }
}
