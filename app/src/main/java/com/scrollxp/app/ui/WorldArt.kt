package com.scrollxp.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.scrollxp.app.ui.theme.*

/** Small original vector illustrations, independent of fonts and emoji rendering. */
@Composable
fun WorldGlyph(id: String, modifier: Modifier = Modifier, color: Color = Green) {
    val warm = Accent
    val dusk = LocalDusk.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 48f, size.height / 48f, pivot = Offset.Zero) {
            val stroke = Stroke(2.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, Offset(x, y), Offset(xx, yy), 2.7f, StrokeCap.Round)
            fun shape(vararg pts: Pair<Float, Float>, fill: Boolean = false, tint: Color = color) {
                val path = Path().apply { moveTo(pts[0].first, pts[0].second); pts.drop(1).forEach { lineTo(it.first, it.second) }; close() }
                if (fill) drawPath(path, tint) else drawPath(path, tint, style = stroke)
            }
            when (id) {
                "Friends" -> {
                    drawCircle(color, 6f, Offset(17f, 15f), style = stroke)
                    drawCircle(color, 5f, Offset(33f, 18f), style = stroke)
                    drawArc(color, 180f, 180f, false, Offset(5f, 24f), Size(25f, 25f), style = stroke)
                    drawArc(color, 190f, 165f, false, Offset(26f, 27f), Size(18f, 20f), style = stroke)
                }
                "mushrooms", "bench", "mailbox", "flowers", "lantern", "stones", "picnic", "windmill", "beehive", "fountain", "telescope", "crystal", "airship" -> drawTreasure(id, 24f, 40f, 1f, dusk)
                "chest" -> {
                    drawRoundRect(warm.copy(alpha = .25f), Offset(6f, 17f), Size(36f, 25f), CornerRadius(5f))
                    drawRoundRect(warm, Offset(6f, 17f), Size(36f, 25f), CornerRadius(5f), style = stroke)
                    line(6f, 27f, 42f, 27f)
                    drawRoundRect(color, Offset(21f, 23f), Size(6f, 10f), CornerRadius(2f))
                    drawArc(warm, 180f, 180f, false, Offset(6f, 8f), Size(36f, 22f), style = stroke)
                }
                "keepsake" -> {
                    val points = (0..9).map { i -> val a = -Math.PI / 2 + i * Math.PI / 5; val r = if (i % 2 == 0) 20f else 9f; (24f + kotlin.math.cos(a).toFloat() * r) to (24f + kotlin.math.sin(a).toFloat() * r) }
                    shape(*points.toTypedArray(), fill = true, tint = warm.copy(alpha = .3f))
                    shape(*points.toTypedArray(), tint = warm)
                }
                "Home", "house", "workshop" -> {
                    shape(7f to 22f, 24f to 8f, 41f to 22f)
                    drawRoundRect(color, Offset(12f, 21f), Size(24f, 21f), CornerRadius(3f), style = stroke)
                    drawRoundRect(color.copy(alpha = .22f), Offset(21f, 29f), Size(7f, 13f), CornerRadius(2f))
                    line(28f, 42f, 28f, 30f); line(21f, 42f, 21f, 30f)
                }
                "World", "trees" -> {
                    line(24f, 42f, 24f, 27f)
                    drawCircle(color.copy(alpha = .16f), 13f, Offset(24f, 19f))
                    drawCircle(color, 13f, Offset(24f, 19f), style = stroke)
                    line(24f, 32f, 16f, 24f); line(24f, 28f, 30f, 22f); line(17f, 42f, 31f, 42f)
                }
                "Goals" -> {
                    drawCircle(color, 17f, Offset(24f, 24f), style = stroke)
                    drawCircle(color.copy(alpha = .25f), 10f, Offset(24f, 24f))
                    line(17f, 24f, 22f, 29f); line(22f, 29f, 32f, 18f)
                }
                "Stats" -> {
                    line(8f, 41f, 40f, 41f)
                    listOf(12f to 23f, 23f to 11f, 34f to 17f).forEach { (x, y) ->
                        drawRoundRect(color.copy(alpha = .22f), Offset(x, y), Size(6f, 24f - y + 15f), CornerRadius(2f))
                        line(x + 3, y, x + 3, 36f)
                    }
                }
                "campfire" -> {
                    shape(14f to 33f, 13f to 25f, 23f to 8f, 25f to 21f, 34f to 16f, 37f to 29f, 32f to 35f, fill = true, tint = warm.copy(alpha = .24f))
                    shape(14f to 33f, 13f to 25f, 23f to 8f, 25f to 21f, 34f to 16f, 37f to 29f, 32f to 35f, tint = warm)
                    line(10f, 39f, 38f, 44f); line(10f, 44f, 38f, 39f)
                }
                "garden" -> {
                    line(24f, 41f, 24f, 22f)
                    drawOval(color.copy(alpha = .25f), Offset(9f, 27f), Size(14f, 8f))
                    drawOval(color.copy(alpha = .25f), Offset(25f, 30f), Size(14f, 8f))
                    for (i in 0..3) { val angle = i * Math.PI / 2; drawCircle(warm.copy(alpha = .65f), 5f, Offset(24f + kotlin.math.cos(angle).toFloat() * 7, 16f + kotlin.math.sin(angle).toFloat() * 7)) }
                    drawCircle(color, 4f, Offset(24f, 16f))
                }
                "castle" -> {
                    shape(8f to 40f, 8f to 12f, 13f to 12f, 13f to 18f, 18f to 18f, 18f to 12f, 23f to 12f, 23f to 25f, 30f to 25f, 30f to 12f, 35f to 12f, 35f to 18f, 40f to 18f, 40f to 40f)
                    drawRoundRect(color.copy(alpha = .25f), Offset(21f, 31f), Size(7f, 10f), CornerRadius(4f))
                }
                "dragon" -> {
                    drawOval(color.copy(alpha = .18f), Offset(10f, 24f), Size(27f, 17f))
                    shape(15f to 28f, 8f to 8f, 30f to 22f, 32f to 14f, 40f to 21f, 40f to 30f, 33f to 37f, 14f to 37f)
                    drawCircle(color, 1.8f, Offset(35f, 23f)); line(14f, 37f, 7f, 40f)
                }
                "Settings" -> {
                    drawCircle(color, 16f, Offset(24f, 24f), style = stroke)
                    drawCircle(color, 5f, Offset(24f, 24f), style = stroke)
                    for (i in 0..7) { val a = i * Math.PI / 4; line(24f + kotlin.math.cos(a).toFloat() * 17, 24f + kotlin.math.sin(a).toFloat() * 17, 24f + kotlin.math.cos(a).toFloat() * 21, 24f + kotlin.math.sin(a).toFloat() * 21) }
                }
                "chevron" -> { line(18f, 12f, 30f, 24f); line(30f, 24f, 18f, 36f) }
                else -> { line(12f, 24f, 21f, 33f); line(21f, 33f, 37f, 14f) }
            }
        }
    }
}

@Composable
fun GentleProgress(fraction: Float, modifier: Modifier = Modifier) {
    val target = fraction.coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, animationSpec = if (LocalReducedMotion.current) snap() else tween(650), label = "Saved progress")
    LinearProgressIndicator(progress = { progress }, modifier = modifier.fillMaxWidth().height(6.dp), color = Green, trackColor = Line)
}
