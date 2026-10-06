package com.scrollxp.app.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.scrollxp.app.domain.Progression
import com.scrollxp.app.domain.Worlds
import com.scrollxp.app.ui.drawIsland
import com.scrollxp.app.ui.theme.islandPalette
import java.io.File
import java.util.UUID

data class IslandSnapshot(val name: String, val xp: Int, val roof: Int, val hidden: Set<String>,
    val treasures: Set<String>, val dusk: Boolean, val includeProgress: Boolean = false, val region: String = Worlds.STARTER)

/** Exports artwork rather than a screen capture; usage records never enter the poster. */
object IslandExport {
    const val WIDTH = 1080
    const val HEIGHT = 1350
    fun render(snapshot: IslandSnapshot): Bitmap {
        val p = islandPalette(snapshot.dusk)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(p.paper.toArgb())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = p.surface.toArgb()
        canvas.drawRoundRect(48f, 48f, 1032f, 1302f, 60f, 60f, paint)
        fun text(value: String, y: Float, size: Float, color: Int, font: Typeface) {
            paint.color = color; paint.textSize = size; paint.typeface = font; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(value, 540f, y, paint)
        }
        val world = Worlds.active(snapshot.region, snapshot.xp)
        text(world.name.uppercase(), 148f, 26f, p.muted.toArgb(), Typeface.create("sans-serif-medium", Typeface.NORMAL))
        paint.typeface = Typeface.create("serif", Typeface.NORMAL); paint.textSize = 66f
        val name = android.text.TextUtils.ellipsize(snapshot.name, android.text.TextPaint(paint), 864f, android.text.TextUtils.TruncateAt.END).toString()
        text(name, 252f, 66f, p.ink.toArgb(), paint.typeface)
        canvas.save()
        canvas.translate(72f, 338f)
        canvas.clipPath(android.graphics.Path().apply {
            addRoundRect(0f, 0f, 936f, 608.4f, 32f, 32f, android.graphics.Path.Direction.CW)
        })
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(canvas), Size(936f, 608.4f)) {
            drawIsland(snapshot.xp, snapshot.roof.coerceIn(0, 2), snapshot.hidden, snapshot.treasures, snapshot.dusk, p.sky, world.id)
        }
        canvas.restore()
        text(if (snapshot.includeProgress) "LEVEL ${Progression.level(snapshot.xp).level}  ·  ${snapshot.xp} XP" else "Small moments. A world that grows.",
            1035f, 32f, p.green.toArgb(), Typeface.create("sans-serif-medium", Typeface.NORMAL))
        text("ScrollXP", 1178f, 42f, p.ink.toArgb(), Typeface.create("sans-serif", Typeface.BOLD))
        text("Turn your screen time into progress.", 1227f, 25f, p.muted.toArgb(), Typeface.create("sans-serif", Typeface.NORMAL))
        return bitmap
    }
    fun create(context: Context, snapshot: IslandSnapshot): File {
        val directory = File(context.cacheDir, "shared_islands").apply { check(mkdirs() || isDirectory) }
        // Keep recent files alive for share targets; bound cache growth across repeated exports.
        directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(19)?.forEach { it.delete() }
        val file = File(directory, "ScrollXP-island-${UUID.randomUUID()}.png")
        val bitmap = render(snapshot)
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        catch (failure: Exception) { file.delete(); throw failure }
        finally { bitmap.recycle() }
        return file
    }
    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.island-images", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "My ScrollXP island", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    fun clear(context: Context) { File(context.cacheDir, "shared_islands").deleteRecursively() }
}
