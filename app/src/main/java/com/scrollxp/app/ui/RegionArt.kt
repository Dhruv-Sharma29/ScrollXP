package com.scrollxp.app.ui

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*

internal fun regionSky(region: String, dusk: Boolean, fallback: Color): Color = when (region) {
    "cove" -> if (dusk) Color(0xFF1B333B) else Color(0xFFE2EDF0)
    "clouds" -> if (dusk) Color(0xFF282E41) else Color(0xFFECE9F3)
    else -> fallback
}

/** Original scenery for two destinations, on the same 400 × 260 coordinate system. */
internal fun DrawScope.drawRegionScenery(region: String, dusk: Boolean) {
    val mist = if (dusk) Color(0xFF64768A) else Color(0xFFFEFDF8)
    if (region == "cove") {
        drawOval((if (dusk) Color(0xFF264A53) else Color(0xFFAED0D1)).copy(alpha = .55f), Offset(-20f, 155f), Size(440f, 105f))
        val water = if (dusk) Color(0xFF78A4AB) else Color(0xFFE7F3ED)
        listOf(Offset(20f, 219f), Offset(337f, 183f), Offset(153f, 240f)).forEach { spot ->
            drawArc(water.copy(alpha = .65f), 10f, 135f, false, spot, Size(41f, 7f), style = Stroke(1.5f))
        }
        drawOval((if (dusk) Color(0xFF456477) else Color(0xFFBCD1D5)).copy(alpha = .45f), Offset(41f, 101f), Size(93f, 17f))
        drawOval((if (dusk) Color(0xFF456477) else Color(0xFFC9DCDA)).copy(alpha = .5f), Offset(254f, 111f), Size(129f, 12f))
    } else if (region == "clouds") {
        listOf(Offset(9f, 208f), Offset(287f, 215f), Offset(153f, 83f)).forEachIndexed { index, spot ->
            val alpha = if (dusk) .25f else .7f
            drawOval(mist.copy(alpha = alpha), spot, Size(if (index == 2) 110f else 100f, 24f))
            drawCircle(mist.copy(alpha = alpha), 17f, spot + Offset(35f, 2f))
            drawCircle(mist.copy(alpha = alpha), 12f, spot + Offset(58f, 4f))
        }
        drawOval(mist.copy(alpha = .22f), Offset(71f, 245f), Size(245f, 11f))
    }
}

internal fun DrawScope.drawExpansionGround(region: String, dusk: Boolean) {
    fun tint(color: Long) = Color(color).let { if (dusk) lerp(it, regionSky(region, true, Color.Black), .23f) else it }
    fun poly(color: Color, vararg points: Pair<Float, Float>) = drawPath(Path().apply {
        moveTo(points[0].first, points[0].second); points.drop(1).forEach { lineTo(it.first, it.second) }; close()
    }, color)
    val sand = tint(if (region == "cove") 0xFFD9CBA6 else 0xFFC3BCD4)
    val stone = tint(if (region == "cove") 0xFFAFA98D else 0xFF888AAB)
    poly(stone, 55f to 157f, 200f to 101f, 345f to 157f, 339f to 177f, 203f to 230f, 62f to 177f)
    poly(stone.copy(alpha = .9f), 62f to 177f, 203f to 230f, 339f to 177f, 221f to 242f, 188f to 250f)
    poly(sand, 55f to 157f, 200f to 101f, 345f to 157f, 200f to 213f)
    poly(tint(if (region == "cove") 0xFFC0CEAA else 0xFFC8D1B7), 73f to 157f, 200f to 112f, 327f to 157f, 200f to 202f)
    if (region == "cove") {
        val cream = tint(0xFFF7ECCD); val clay = tint(0xFFBB8069); val wood = tint(0xFF9C805B)
        // Lighthouse on a rocky outcrop, beyond the collection's placement slots.
        drawOval(stone, Offset(25f, 166f), Size(48f, 16f))
        poly(cream, 41f to 167f, 46f to 120f, 58f to 120f, 63f to 168f)
        drawRect(clay, Offset(44f, 139f), Size(17f, 8f))
        drawRect(cream, Offset(43f, 112f), Size(18f, 10f))
        drawRect(tint(0xFFE4C77F), Offset(47f, 113f), Size(10f, 8f))
        poly(clay, 40f to 112f, 52f to 103f, 64f to 112f)
        if (dusk) drawCircle(Color(0xFFF2D09A).copy(alpha = .12f), 23f, Offset(52f, 117f))
        drawLine(wood, Offset(58f, 169f), Offset(78f, 164f), 4f)
        // A small pier and a moored sailing boat.
        poly(wood, 227f to 202f, 271f to 221f, 260f to 227f, 216f to 207f)
        for (i in 0..4) drawLine(cream.copy(alpha = .65f), Offset(223f + i * 9, 205f + i * 4), Offset(232f + i * 9, 201f + i * 4), 1.2f)
        listOf(Offset(221f, 207f), Offset(260f, 226f)).forEach { drawLine(wood, it, it + Offset(0f, 8f), 3f) }
        poly(clay, 302f to 225f, 331f to 225f, 325f to 233f, 309f to 233f)
        drawLine(wood, Offset(316f, 226f), Offset(316f, 195f), 2f)
        poly(cream, 314f to 198f, 314f to 222f, 298f to 222f)
        poly(tint(0xFF91B5B6), 319f to 201f, 332f to 221f, 319f to 221f)
    } else {
        val cream = tint(0xFFE9E4D5); val leaf = tint(0xFF7D9B83)
        // A moon gate and vines suspended beneath the floating rock.
        drawOval(stone, Offset(23f, 164f), Size(49f, 15f))
        drawCircle(cream, 17f, Offset(48f, 146f), style = Stroke(6f))
        drawLine(cream, Offset(25f, 169f), Offset(72f, 169f), 4f)
        drawCircle(tint(0xFFDAB87C), 3f, Offset(48f, 126f))
        listOf(Offset(81f, 185f), Offset(122f, 203f), Offset(295f, 196f)).forEachIndexed { index, spot ->
            val length = 29f - index * 4
            val path = Path().apply { moveTo(spot.x, spot.y); cubicTo(spot.x - 8, spot.y + 12, spot.x + 6, spot.y + 17, spot.x - 4, spot.y + length) }
            drawPath(path, leaf, style = Stroke(2f))
            for (j in 1..3) drawOval(leaf, spot + Offset(if (j % 2 == 0) -8f else -1f, j * 6f), Size(7f, 4f))
        }
        for (i in 0..4) drawCircle(tint(0xFFE5D3A2), 1.5f, Offset(62f + i * 67, 85f + (i % 2) * 12))
    }
}
