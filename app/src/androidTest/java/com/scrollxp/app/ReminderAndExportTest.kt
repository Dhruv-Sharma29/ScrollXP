package com.scrollxp.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollxp.app.data.ReminderStore
import com.scrollxp.app.domain.ReminderSettings
import com.scrollxp.app.sharing.IslandExport
import com.scrollxp.app.sharing.IslandSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class ReminderAndExportTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val utc = ZoneId.of("UTC")

    @Test fun optInSettingsAndReceiptSurviveReopening() {
        val name = "reminder-test-${UUID.randomUUID()}"
        try {
            val store = ReminderStore(context, name)
            assertFalse(store.settings().enabled)
            store.save(ReminderSettings(enabled = true, minute = 1140, quietStart = 1260, quietEnd = 420))
            val now = Instant.parse("2026-10-05T19:00:00Z")
            assertTrue(store.claim(now, utc))
            val reopened = ReminderStore(context, name)
            assertEquals(1140, reopened.settings().minute)
            assertEquals(1, reopened.settings().revision)
            assertFalse(reopened.claim(now.plusSeconds(60), utc))
            assertEquals(Instant.parse("2026-10-06T19:00:00Z"), reopened.next(now.plusSeconds(60), utc))
            reopened.save(reopened.settings().copy(enabled = false))
            assertFalse(reopened.claim(now.plusSeconds(86400), utc))
        } finally { context.deleteSharedPreferences(name) }
    }
    @Test fun concurrentWorkersCannotClaimTwoReceipts() {
        val name = "reminder-test-${UUID.randomUUID()}"
        val pool = Executors.newFixedThreadPool(4)
        try {
            ReminderStore(context, name).save(ReminderSettings(enabled = true))
            val futures = (1..8).map { pool.submit<Boolean> { ReminderStore(context, name).claim(Instant.parse("2026-10-05T20:00:00Z"), utc) } }
            assertEquals(1, futures.count { it.get() })
        } finally { pool.shutdownNow(); context.deleteSharedPreferences(name) }
    }
    @Test fun resetClearsOptInAndDeliveryHistory() {
        val name = "reminder-test-${UUID.randomUUID()}"
        try {
            val store = ReminderStore(context, name)
            store.save(ReminderSettings(enabled = true, quietEnabled = false))
            assertTrue(store.claim(Instant.parse("2026-10-05T20:00:00Z"), utc))
            store.clear()
            assertEquals(ReminderSettings(revision = 2), store.settings())
            store.save(store.settings().copy(enabled = true))
            assertTrue(store.claim(Instant.parse("2026-10-05T20:01:00Z"), utc))
        } finally { context.deleteSharedPreferences(name) }
    }
    @Test fun postcardsRenderInBothThemesAndRespectHiddenItems() {
        val base = IslandSnapshot("Littlehaven", 2800, 2, emptySet(), setOf("lantern"), false)
        val day = IslandExport.render(base)
        val night = IslandExport.render(base.copy(dusk = true))
        val hidden = IslandExport.render(base.copy(hidden = setOf("house", "trees", "dragon", "lantern")))
        try {
            assertEquals(1080, day.width); assertEquals(1350, day.height)
            assertFalse(day.sameAs(night)); assertFalse(day.sameAs(hidden))
        } finally { day.recycle(); night.recycle(); hidden.recycle() }
    }
    @Test fun pngProviderExposesOnlyReadAccessToTheExport() {
        val file = IslandExport.create(context, IslandSnapshot("A very long island name that should fit into the postcard", 2, 0, emptySet(), setOf("lantern"), true))
        try {
            val intent = IslandExport.shareIntent(context, file)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val uri = intent.clipData!!.getItemAt(0).uri
            assertEquals("content", uri.scheme)
            val bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            assertEquals(1080, bitmap.width); assertEquals(1350, bitmap.height); bitmap.recycle()
            val provider = context.packageManager.resolveContentProvider("${context.packageName}.island-images", 0)!!
            assertFalse(provider.exported)
            assertTrue(provider.grantUriPermissions)
        } finally { file.delete() }
    }
}
