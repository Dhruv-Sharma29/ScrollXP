package com.scrollxp.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.scrollxp.app.ui.theme.*
import com.scrollxp.app.domain.Progression
import com.scrollxp.app.domain.Treasures
import com.scrollxp.app.domain.Worlds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** Original vector artwork, scaled from a 400 × 260 logical canvas. */
@Composable
fun Island(xp: Int, roof: Int, modifier: Modifier = Modifier, hidden: Set<String> = emptySet(), treasures: Set<String> = emptySet(), region: String = Worlds.STARTER) {
    val dusk = LocalDusk.current
    val sky = Sky
    val placed = (Progression.milestones.filter { xp >= it.xp && it.id !in hidden }.map { it.name } + Treasures.all.filter { it.id in treasures && it.id !in hidden }.map { it.name }).joinToString()
    Canvas(modifier.fillMaxWidth().aspectRatio(400f / 260)
        .semantics { contentDescription = "${Worlds.find(region)?.name ?: "Your island"} at ${if (dusk) "dusk" else "daylight"}. ${if (placed.isBlank()) "No items placed." else "Placed: $placed."}" }) {
        drawIsland(xp, roof, hidden, treasures, dusk, sky, region)
    }
}

/** Shared renderer for the live island and exported postcards. */
internal fun DrawScope.drawIsland(xp: Int, roof: Int, hidden: Set<String>, treasures: Set<String>, dusk: Boolean, baseSky: Color, region: String = Worlds.STARTER) {
    val destination = Worlds.find(region)?.id ?: Worlds.STARTER
    val sky = regionSky(destination, dusk, baseSky)
    drawRect(Brush.verticalGradient(listOf(sky, sky.copy(alpha = .7f))))
    scale(size.width / 400f, size.height / 260f, pivot = Offset.Zero) {
        fun poly(points: List<Offset>, color: Color) {
            val p = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(p, if (dusk) lerp(color, sky, .28f) else color)
        }
        fun cloud(x: Float, y: Float) {
            drawOval(Color.White.copy(alpha = .75f), Offset(x, y), Size(43f, 10f))
            drawCircle(Color.White.copy(alpha = .75f), 9f, Offset(x + 16, y))
            drawCircle(Color.White.copy(alpha = .75f), 6f, Offset(x + 28, y + 2))
        }
        if (!dusk) { cloud(35f, 49f); cloud(296f, 34f) }
        else {
            listOf(Offset(46f, 40f), Offset(91f, 65f), Offset(198f, 29f), Offset(282f, 47f), Offset(365f, 94f)).forEach {
                drawCircle(Color(0xFFE5DEBA).copy(alpha = .5f), 1.4f, it)
            }
        }
        drawCircle(if (dusk) Color(0xFFD5D4B7) else Color(0xFFE6D2A0), 18f, Offset(330f, 66f))
        if (dusk) drawCircle(sky, 16f, Offset(338f, 61f))
        if (!dusk) drawCircle(Color(0xFFE6D2A0).copy(alpha = .22f), 30f, Offset(330f, 66f))
        drawRegionScenery(destination, dusk)
        if (destination == Worlds.STARTER) {
        poly(listOf(Offset(74f, 118f), Offset(137f, 53f), Offset(199f, 118f)), if (dusk) Color(0xFF304B41) else Color(0xFFD1DCC6))
        poly(listOf(Offset(139f, 55f), Offset(174f, 92f), Offset(155f, 83f), Offset(138f, 93f), Offset(120f, 79f)), if (dusk) Color(0xFF476155) else Color(0xFFF1F0E6))
        poly(listOf(Offset(160f, 119f), Offset(225f, 71f), Offset(275f, 120f)), if (dusk) Color(0xFF395447) else Color(0xFFDCE3D1))
        drawOval((if (dusk) Color(0xFF142A21) else Color(0xFFCBDDC1)).copy(alpha = .45f), Offset(66f, 212f), Size(273f, 22f))
        val island = listOf(Offset(200f, 101f), Offset(345f, 157f), Offset(339f, 177f), Offset(203f, 230f), Offset(62f, 177f), Offset(55f, 157f))
        poly(island, Color(0xFF719B60))
        poly(listOf(Offset(55f, 157f), Offset(200f, 213f), Offset(345f, 157f), Offset(339f, 177f), Offset(203f, 230f), Offset(62f, 177f)), Color(0xFF678B56))
        poly(listOf(Offset(55f, 157f), Offset(200f, 101f), Offset(345f, 157f), Offset(200f, 213f)), Color(0xFF9EB988))
        poly(listOf(Offset(69f, 157f), Offset(200f, 109f), Offset(331f, 157f), Offset(200f, 205f)), Color(0xFFBCD0A2))
        } else drawExpansionGround(destination, dusk)
        val path = Path().apply { moveTo(200f, 157f); cubicTo(177f, 171f, 182f, 179f, 223f, 194f) }
        drawPath(path, Color(0xFFEEE1B5), style = Stroke(9f))
        drawPath(path, Color(0xFFF9EDC8), style = Stroke(5f))
        listOf(Offset(107f, 155f), Offset(138f, 181f), Offset(274f, 164f), Offset(231f, 120f), Offset(158f, 125f)).forEach {
            drawOval(Color(0xFF87B570), it, Size(10f, 4f))
        }
        drawOval(Color(0xFF91B4AE), Offset(249f, 171f), Size(37f, 13f))
        drawArc(Color(0xFFD9E2D0), 10f, 125f, false, Offset(253f, 174f), Size(24f, 5f), style = Stroke(1.5f))
        for (i in 0..2) drawOval(Color(0xFFE4DABD), Offset(213f + i * 8, 187f - i * 3), Size(6f, 3f))
        if ("trees" !in hidden) {
            tree(97f, 147f, .88f); tree(120f, 139f, .75f); tree(281f, 146f, .9f)
            tree(300f, 160f, .67f); tree(152f, 113f, .8f); tree(253f, 113f, .8f)
        }
        if ("house" !in hidden) house(190f, 150f, roof, 1f)
        if (xp >= 100 && "campfire" !in hidden) {
            drawOval(Color(0xFF88B16A), Offset(229f, 162f), Size(25f, 9f))
            drawLine(Color(0xFF8C6547), Offset(234f, 166f), Offset(249f, 169f), 4f)
            drawLine(Color(0xFF8C6547), Offset(234f, 169f), Offset(249f, 166f), 4f)
            poly(listOf(Offset(237f, 166f), Offset(240f, 151f), Offset(246f, 157f), Offset(250f, 151f), Offset(249f, 166f)), Color(0xFFEA9A44))
            poly(listOf(Offset(240f, 166f), Offset(244f, 157f), Offset(247f, 166f)), Color(0xFFFFD97B))
        }
        if (xp >= 300 && "garden" !in hidden) {
            poly(listOf(Offset(111f, 163f), Offset(137f, 173f), Offset(119f, 182f), Offset(94f, 172f)), Color(0xFF8B7151))
            for (i in 0..3) for (j in 0..1) {
                val x = 106f + i * 6 - j * 5; val y = 165f + i * 2 + j * 5
                drawLine(Color(0xFF47845B), Offset(x, y), Offset(x, y - 5), 2f)
                drawCircle(Color(0xFFF4B3A2), 2f, Offset(x, y - 6))
            }
        }
        if (xp >= 600 && "workshop" !in hidden) house(250f, 148f, 1, .65f)
        if (xp >= 1200 && "castle" !in hidden) castle(215f, 125f)
        if (xp >= 2500 && "dragon" !in hidden) {
            drawOval(Color(0xFF7795BB), Offset(144f, 172f), Size(24f, 12f))
            drawCircle(Color(0xFF7795BB), 7f, Offset(165f, 172f))
            poly(listOf(Offset(147f, 174f), Offset(138f, 154f), Offset(160f, 168f)), Color(0xFFA7BBDD))
            drawCircle(Color(0xFF18352D), 1.5f, Offset(168f, 170f))
        }
        for (i in 0..16) {
            val x = 100f + i * 12; val y = 188f + sin(i.toFloat()) * 8
            if (x < 170 || x > 242) drawCircle(Color(0xFFF7F2CD), 1.3f, Offset(x, y))
        }
        drawLine(Color(0xFF5E8762), Offset(73f, 158f), Offset(73f, 135f), 2f)
        poly(listOf(Offset(74f, 135f), Offset(89f, 140f), Offset(74f, 145f)), Color(0xFFF5D87B))
        drawTreasures(treasures - hidden, dusk)
        if (dusk) {
            if ("house" !in hidden) {
                drawCircle(Color(0xFFF0CC85).copy(alpha = .1f), 17f, Offset(209f, 130f))
                poly(listOf(Offset(205f, 128f), Offset(213f, 124f), Offset(213f, 133f), Offset(205f, 137f)), Color(0xFFD7B47D))
            }
            if (xp >= 100 && "campfire" !in hidden) drawCircle(Color(0xFFF2BF73).copy(alpha = .13f), 18f, Offset(243f, 162f))
        } else {
            val bird = Path().apply { moveTo(60f, 87f); quadraticTo(64f, 83f, 68f, 87f); quadraticTo(72f, 83f, 76f, 87f) }
            drawPath(bird, Color(0xFF859784), style = Stroke(1.5f))
        }
    }
}
private fun DrawScope.tree(x: Float, y: Float, scale: Float) {
    drawOval(Color(0xFF79A864), Offset(x - 10 * scale, y - 3 * scale), Size(23 * scale, 8 * scale))
    drawLine(Color(0xFF8A7250), Offset(x, y), Offset(x, y - 19 * scale), 4 * scale)
    drawCircle(Color(0xFF4D8558), 12 * scale, Offset(x, y - 24 * scale))
    drawCircle(Color(0xFF659C65), 10 * scale, Offset(x - 7 * scale, y - 20 * scale))
    drawCircle(Color(0xFF7BAD71), 10 * scale, Offset(x + 5 * scale, y - 28 * scale))
    drawCircle(Color(0xFF88BA7B), 5 * scale, Offset(x + 2 * scale, y - 33 * scale))
}
private fun DrawScope.house(x: Float, y: Float, roof: Int, s: Float) {
    fun poly(vararg pts: Pair<Float, Float>, color: Color) {
        val p = Path().apply { moveTo(x + pts[0].first * s, y + pts[0].second * s)
            pts.drop(1).forEach { lineTo(x + it.first * s, y + it.second * s) }; close() }
        drawPath(p, color)
    }
    drawOval(Color(0xFF82AD68), Offset(x - 27 * s, y - 5 * s), Size(67 * s, 17 * s))
    poly(-22f to -32f, 6f to -24f, 6f to 8f, -22f to -1f, color = Color(0xFFF9ECCB))
    poly(6f to -24f, 32f to -36f, 32f to -4f, 6f to 8f, color = Color(0xFFE2CEA0))
    val colors = listOf(Color(0xFF648F72), Color(0xFFD88768), Color(0xFF8792B3))
    poly(-28f to -33f, -8f to -57f, 16f to -47f, 8f to -22f, color = colors[roof])
    poly(-8f to -57f, 20f to -67f, 38f to -39f, 8f to -22f, color = colors[roof].copy(red = (colors[roof].red * .8f)))
    poly(-10f to -16f, -1f to -13f, -1f to 5f, -10f to 2f, color = Color(0xFF9C7851))
    drawCircle(Color(0xFFF8D989), 1.5f * s, Offset(x - 3 * s, y - 4 * s))
    poly(15f to -22f, 23f to -26f, 23f to -17f, 15f to -13f, color = Color(0xFF94BDB4))
    drawLine(Color(0xFFF5E6C0), Offset(x + 19 * s, y - 24 * s), Offset(x + 19 * s, y - 15 * s), 1.5f * s)
}
private fun DrawScope.castle(x: Float, y: Float) {
    drawRect(Color(0xFFC0CBBE), Offset(x - 22, y - 38), Size(38f, 35f))
    drawRect(Color(0xFFD6DED0), Offset(x - 25, y - 48), Size(12f, 46f))
    drawRect(Color(0xFFD6DED0), Offset(x + 10, y - 48), Size(12f, 46f))
    for (i in 0..3) drawRect(Color(0xFFD6DED0), Offset(x - 25 + i * 12, y - 50), Size(7f, 8f))
    drawRoundRect(Color(0xFF59736B), Offset(x - 7, y - 17), Size(10f, 16f), androidx.compose.ui.geometry.CornerRadius(5f, 5f))
    drawLine(Color(0xFF627E62), Offset(x + 16, y - 49), Offset(x + 16, y - 65), 2f)
    val p = Path().apply { moveTo(x + 17, y - 65); lineTo(x + 30, y - 60); lineTo(x + 17, y - 55); close() }
    drawPath(p, Color(0xFFF1CD73))
}
