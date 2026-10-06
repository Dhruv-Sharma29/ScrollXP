package com.scrollxp.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrollxp.app.data.*
import com.scrollxp.app.domain.Treasures
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class TreasurePersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = { Instant.parse("2026-10-19T12:00:00Z").toEpochMilli() }

    @Test fun concurrentSettlementReservesEachMilestoneExactlyOnceWithoutAddingXp() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, ScrollDatabase::class.java).build()
        try {
            val dao = db.dao()
            val engine = TreasureRepository(db, Random(3), clock)
            engine.settle()
            assertTrue(dao.chests().isEmpty())
            dao.saveProfile(Profile(onboarded = false, zone = "UTC"))
            engine.settle()
            assertTrue(dao.chests().isEmpty())
            dao.saveProfile(Profile(onboarded = true, zone = "UTC"))
            dao.saveReward(Reward("usage:test", "2026-10-18", "USAGE", 10_000))
            coroutineScope { repeat(12) { launch(Dispatchers.Default) { engine.settle() } } }
            assertEquals(Treasures.chestMilestones.size, dao.chests().size)
            assertEquals(dao.chests().size, dao.chests().map { it.itemId }.distinct().size)
            assertTrue(dao.chests().all { it.openedAt == null && it.economyVersion == Treasures.ECONOMY_VERSION })
            assertEquals(10_000, dao.totalXp())
        } finally { db.close() }
    }

    @Test fun repeatedOpeningAndRestartRevealTheSameReservedItemAndKeepPlacement() = runBlocking<Unit> {
        val name = "treasure-restart-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, ScrollDatabase::class.java, name).build()
        val first = open()
        var itemId = ""
        var openedAt: Long? = null
        try {
            first.dao().saveProfile(Profile(onboarded = true, islandName = "My haven", roof = 2, appearance = "Dusk", hiddenItems = "house", zone = "UTC"))
            val engine = TreasureRepository(first, Random(12), clock)
            engine.settle()
            val reserved = requireNotNull(first.dao().chest("milestone:0"))
            itemId = reserved.itemId
            val outcomes = coroutineScope { List(10) { async(Dispatchers.Default) { engine.open(reserved.key) } }.awaitAll() }
            assertTrue(outcomes.all { it?.itemId == itemId && it.openedAt == clock() })
            openedAt = outcomes.first()?.openedAt
            assertTrue(itemId in requireNotNull(first.dao().profile()).hiddenItems.packages())
            assertTrue("house" in requireNotNull(first.dao().profile()).hiddenItems.packages())
            // Place the item, then check that later reveal retries never hide it again.
            val profile = requireNotNull(first.dao().profile())
            first.dao().saveProfile(profile.copy(hiddenItems = (profile.hiddenItems.packages() - itemId).encoded()))
            engine.open(reserved.key)
            assertFalse(itemId in requireNotNull(first.dao().profile()).hiddenItems.packages())
        } finally { first.close() }
        val reopened = open()
        try {
            val chest = requireNotNull(TreasureRepository(reopened, Random(999), clock).open("milestone:0"))
            assertEquals(itemId, chest.itemId)
            assertEquals(openedAt, chest.openedAt)
            assertEquals(1, reopened.dao().chests().size)
            assertEquals("My haven", reopened.dao().profile()?.islandName)
            assertEquals("Dusk", reopened.dao().profile()?.appearance)
            assertFalse(itemId in requireNotNull(reopened.dao().profile()).hiddenItems.packages())
            assertEquals(0, reopened.dao().totalXp())
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun weeklyChestNeedsFiveFinalizedGoalDatesAndDoesNotCountUsageOrFutureDays() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, ScrollDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.saveProfile(Profile(onboarded = true, zone = "UTC"))
            val engine = TreasureRepository(db, Random(4), clock)
            val dates = listOf("2026-09-28", "2026-09-29", "2026-10-01", "2026-10-03", "2026-10-04")
            dates.take(4).forEach { dao.saveReward(Reward("goal:$it", it, "GOAL", 100)) }
            dao.saveReward(Reward("usage:${dates.last()}", dates.last(), "USAGE", 120))
            dao.saveDay(DayRecord(dates.last(), 0, 100, "app", 45, 0, goalStatus = "PENDING"))
            engine.settle()
            assertTrue(dao.chests().none { it.key.startsWith("week:") })
            dao.saveReward(Reward("goal:${dates.last()}", dates.last(), "GOAL", 100))
            repeat(3) { engine.settle() }
            assertEquals(listOf("week:2026-09-28"), dao.chests().filter { it.key.startsWith("week:") }.map { it.key })
            // A later calendar week earns its own reward; it never changes the earlier chest.
            listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09").forEach {
                dao.saveReward(Reward("goal:$it", it, "GOAL", 100))
            }
            listOf("2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06").forEach {
                dao.saveReward(Reward("goal:$it", it, "GOAL", 100))
            }
            engine.settle()
            assertEquals(setOf("week:2026-09-28", "week:2026-10-05"), dao.chests().filter { it.key.startsWith("week:") }.map { it.key }.toSet())
        } finally { db.close() }
    }

    @Test fun fullCollectionProducesKnownKeepsakeWithoutChangingXpOrInventory() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, ScrollDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.saveProfile(Profile(onboarded = true, zone = "UTC"))
            Treasures.all.forEach { dao.insertChest(Chest("seed:${it.id}", "Fixture", clock(), it.id, it.rarity.label)) }
            val engine = TreasureRepository(db, Random(7), clock)
            engine.settle()
            val chest = requireNotNull(engine.open("milestone:0"))
            assertEquals(Treasures.KEEPSAKE, chest.itemId)
            assertEquals("Keepsake", chest.rarity)
            assertEquals("", dao.profile()?.hiddenItems)
            assertEquals(0, dao.totalXp())
        } finally { db.close() }
    }

    @Test fun versionTwoUpgradePreservesComfortAndAddsAnEmptyRewardStore() = runBlocking<Unit> {
        val name = "treasure-upgrade-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.scrollxp.app.data.ScrollDatabase/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO profile VALUES (1, 'Old haven', 'app', 45, NULL, NULL, 0, 1, 'Gentle', 'trees', 1, 0, 100, 200, 'UTC', 'Dusk', 1)")
            old.execSQL("INSERT INTO rewards VALUES ('usage:day', 'day', 'USAGE', 2)")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, ScrollDatabase::class.java, name).addMigrations(ScrollDatabase.MIGRATION_2_3, ScrollDatabase.MIGRATION_3_4).build()
        try {
            assertEquals("Old haven", db.dao().profile()?.islandName)
            assertEquals("Dusk", db.dao().profile()?.appearance)
            assertEquals(true, db.dao().profile()?.reducedMotion)
            assertEquals(true, db.dao().profile()?.haptics)
            assertEquals(2, db.dao().totalXp())
            assertTrue(db.dao().chests().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
