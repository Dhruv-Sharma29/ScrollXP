package com.scrollxp.app.ui

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawTreasures(owned: Set<String>, dusk: Boolean) {
    data class Spot(val id: String, val x: Float, val y: Float, val scale: Float = .7f)
    listOf(Spot("mushrooms", 105f, 154f), Spot("bench", 145f, 155f), Spot("mailbox", 218f, 168f, .55f),
        Spot("flowers", 175f, 189f), Spot("lantern", 235f, 195f, .65f), Spot("stones", 196f, 203f),
        Spot("picnic", 127f, 185f), Spot("windmill", 323f, 157f, .8f), Spot("beehive", 88f, 171f, .55f),
        Spot("fountain", 292f, 181f), Spot("telescope", 278f, 132f, .6f), Spot("crystal", 162f, 193f),
        Spot("airship", 62f, 94f, 1.25f)).forEach { if (it.id in owned) drawTreasure(it.id, it.x, it.y, it.scale, dusk) }
}

/** Original miniature illustrations shared by the collection and the island. */
internal fun DrawScope.drawTreasure(id: String, x: Float, y: Float, s: Float = 1f, dusk: Boolean = false) {
    translate(x, y) { scale(s, pivot = Offset.Zero) {
        fun tint(hex: Long) = Color(hex).let { if (dusk) lerp(it, Color(0xFF254138), .16f) else it }
        val wood = tint(0xFF9D7654); val green = tint(0xFF668E63); val gold = tint(0xFFD9B671)
        val cream = tint(0xFFE7DAB5); val clay = tint(0xFFBD7E63); val blue = tint(0xFF81AAA7)
        fun line(a: Offset, b: Offset, color: Color = wood, width: Float = 2f) = drawLine(color, a, b, width, StrokeCap.Round)
        fun poly(color: Color, vararg p: Pair<Float, Float>) {
            drawPath(Path().apply { moveTo(p[0].first, p[0].second); p.drop(1).forEach { lineTo(it.first, it.second) }; close() }, color)
        }
        when (id) {
            "mushrooms" -> {
                line(Offset(-5f, 0f), Offset(-5f, -12f), cream, 4f)
                line(Offset(7f, 1f), Offset(7f, -7f), cream, 3f)
                drawArc(clay, 180f, 180f, true, Offset(-14f, -23f), Size(18f, 22f))
                drawArc(gold, 180f, 180f, true, Offset(1f, -14f), Size(13f, 14f))
                drawCircle(cream, 1.5f, Offset(-6f, -17f)); drawCircle(cream, 1f, Offset(-11f, -14f))
            }
            "bench" -> {
                line(Offset(-9f, 0f), Offset(-9f, -13f)); line(Offset(9f, 0f), Offset(9f, -13f))
                drawRoundRect(wood, Offset(-14f, -15f), Size(28f, 6f), CornerRadius(2f))
                drawRoundRect(gold, Offset(-15f, -7f), Size(30f, 5f), CornerRadius(2f))
            }
            "mailbox" -> {
                line(Offset(0f, 0f), Offset(0f, -16f), wood, 4f)
                drawRoundRect(blue, Offset(-10f, -25f), Size(20f, 13f), CornerRadius(5f, 5f))
                drawRoundRect(cream, Offset(-7f, -18f), Size(10f, 2f), CornerRadius(1f))
                line(Offset(10f, -20f), Offset(10f, -30f), clay)
                drawRect(clay, Offset(10f, -30f), Size(6f, 5f))
            }
            "flowers" -> {
                listOf(-10f to -13f, 0f to -21f, 10f to -12f).forEach { (px, py) ->
                    line(Offset(px, 0f), Offset(px, py), green)
                    for (i in 0..4) { val a = i * Math.PI * 2 / 5; drawCircle(clay, 3.2f, Offset(px + cos(a).toFloat() * 3, py + sin(a).toFloat() * 3)) }
                    drawCircle(gold, 2.3f, Offset(px, py))
                }
            }
            "lantern" -> {
                line(Offset(-5f, 0f), Offset(-5f, -31f), wood, 3f); line(Offset(-5f, -31f), Offset(9f, -31f))
                if (dusk) drawCircle(gold.copy(alpha = .17f), 14f, Offset(8f, -21f))
                drawRoundRect(gold, Offset(3f, -28f), Size(10f, 15f), CornerRadius(2f))
                poly(wood, 0f to -28f, 8f to -33f, 16f to -28f)
                line(Offset(3f, -13f), Offset(13f, -13f)); line(Offset(8f, -27f), Offset(8f, -16f), cream)
            }
            "stones" -> {
                for (i in 0..10) { val a = i * .7; val r = 2f + i; drawOval(if (i % 2 == 0) cream else blue, Offset(cos(a).toFloat() * r - 2, sin(a).toFloat() * r * .5f - 8), Size(5f, 3f)) }
            }
            "picnic" -> {
                poly(clay, -17f to -5f, 0f to -15f, 18f to -5f, 0f to 5f)
                line(Offset(-11f, -8f), Offset(7f, 2f), cream); line(Offset(-5f, -12f), Offset(13f, -2f), cream)
                drawRoundRect(gold, Offset(-4f, -15f), Size(10f, 8f), CornerRadius(2f))
                drawArc(wood, 180f, 180f, false, Offset(-2f, -21f), Size(6f, 10f), style = Stroke(1.5f))
            }
            "windmill" -> {
                poly(cream, -7f to 0f, -5f to -26f, 5f to -26f, 8f to 0f)
                poly(clay, -9f to -25f, 0f to -33f, 10f to -25f)
                line(Offset(-14f, -33f), Offset(14f, -13f), wood, 3f)
                line(Offset(-12f, -12f), Offset(13f, -35f), wood, 3f)
                drawCircle(gold, 3f, Offset(0f, -23f)); drawRect(wood, Offset(-2f, -7f), Size(4f, 7f))
            }
            "beehive" -> {
                drawOval(gold, Offset(-11f, -25f), Size(22f, 23f))
                for (i in 0..3) drawArc(wood, 0f, 180f, false, Offset(-10f, -24f + i * 5), Size(20f, 7f), style = Stroke(1f))
                drawCircle(wood, 3f, Offset(0f, -6f)); line(Offset(-13f, 0f), Offset(13f, 0f), green, 3f)
                drawCircle(gold, 2f, Offset(15f, -20f)); drawCircle(cream, 1.5f, Offset(15f, -23f))
            }
            "fountain" -> {
                drawOval(blue, Offset(-14f, -8f), Size(28f, 12f)); drawOval(cream, Offset(-12f, -10f), Size(24f, 10f))
                drawOval(blue, Offset(-9f, -8f), Size(18f, 6f)); line(Offset(0f, -6f), Offset(0f, -22f), cream, 4f)
                drawOval(cream, Offset(-8f, -24f), Size(16f, 6f)); drawCircle(blue, 2f, Offset(0f, -27f))
                drawArc(blue, 190f, 150f, false, Offset(-9f, -21f), Size(18f, 26f), style = Stroke(1.5f))
            }
            "telescope" -> {
                line(Offset(0f, -13f), Offset(-9f, 1f)); line(Offset(0f, -13f), Offset(9f, 1f)); line(Offset(0f, -13f), Offset(0f, 2f))
                line(Offset(-8f, -17f), Offset(10f, -29f), blue, 8f)
                line(Offset(8f, -31f), Offset(13f, -24f), gold, 3f)
            }
            "crystal" -> {
                poly(blue, -12f to -3f, -13f to -15f, -7f to -24f, 0f to -12f, -2f to 0f)
                poly(tint(0xFFAFA8C9), -2f to 0f, -2f to -24f, 5f to -35f, 12f to -22f, 9f to -2f)
                poly(cream.copy(alpha = .65f), 5f to -35f, 5f to -3f, 9f to -2f, 12f to -22f)
                if (dusk) drawCircle(blue.copy(alpha = .12f), 19f, Offset(0f, -15f))
            }
            "airship" -> {
                drawOval(cream, Offset(-18f, -29f), Size(36f, 19f)); drawOval(gold.copy(alpha = .6f), Offset(-6f, -29f), Size(12f, 19f))
                line(Offset(-8f, -11f), Offset(-5f, -4f)); line(Offset(8f, -11f), Offset(5f, -4f))
                poly(wood, -9f to -5f, 9f to -5f, 5f to 1f, -5f to 1f)
                poly(clay, -14f to -13f, -22f to -7f, -20f to -19f)
            }
        }
    } }
}
