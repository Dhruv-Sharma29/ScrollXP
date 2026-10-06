package com.scrollxp.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrollxp.app.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun firstPlacementPersistsAfterHidingAndUnownedItemsCannotCompleteIt() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, ScrollDatabase::class.java).build()
        try {
            val dao = db.dao(); val repository = ScrollRepository(context, db)
            dao.saveProfile(Profile(onboarded = true, hiddenItems = "lantern"))
            dao.insertChest(Chest("welcome", "Welcome", 1, "lantern", "COMMON"))
            repository.decorate(item = "lantern", visible = true)
            assertEquals(false, dao.profile()?.hasPlacedTreasure)
            repository.decorate(item = "house", visible = true)
            assertEquals(false, dao.profile()?.hasPlacedTreasure)
            dao.updateChest(dao.chests().single().copy(openedAt = 2))
            repository.decorate(item = "lantern", visible = true)
            assertEquals(true, dao.profile()?.hasPlacedTreasure)
            repository.decorate(item = "lantern", visible = false)
            assertEquals(true, dao.profile()?.hasPlacedTreasure)
            assertTrue("lantern" in dao.profile()!!.hiddenItems.packages())
            assertEquals(0, dao.totalXp())
        } finally { db.close() }
    }

    @Test fun visitsCheckSavedXpAndPreserveTheCollectionWithoutSpending() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, ScrollDatabase::class.java).build()
        try {
            val dao = db.dao(); val worlds = WorldRepository(db)
            dao.saveProfile(Profile(onboarded = true, islandName = "Our haven", roof = 2, hiddenItems = "trees", appearance = "Dusk"))
            dao.insertChest(Chest("welcome", "Welcome", 1, "lantern", "COMMON", openedAt = 2))
            dao.saveReward(Reward("usage:test", "2026-10-04", "USAGE", 4999))
            assertFalse(worlds.visit("cove")); assertFalse(worlds.visit("not-a-world"))
            assertEquals("meadow", dao.profile()?.region)
            dao.saveReward(Reward("usage:test", "2026-10-04", "USAGE", 5000))
            assertTrue(worlds.visit("cove")); assertFalse(worlds.visit("clouds"))
            assertEquals("cove", dao.profile()?.region)
            assertEquals(5000, dao.totalXp())
            assertEquals("Our haven", dao.profile()?.islandName)
            assertEquals(2, dao.profile()?.roof)
            assertEquals("trees", dao.profile()?.hiddenItems)
            assertEquals("lantern", dao.chests().single().itemId)
            assertTrue(worlds.visit("meadow")); assertTrue(worlds.visit("cove"))
            assertEquals(5000, dao.totalXp())
        } finally { db.close() }
    }

    @Test fun selectedRegionAndGuidePreferenceSurviveReopening() = runBlocking<Unit> {
        val name = "world-restart-test.db"; context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, ScrollDatabase::class.java, name).build()
        try {
            val first = open()
            try {
                first.dao().saveProfile(Profile(onboarded = true, hasPlacedTreasure = true))
                first.dao().saveReward(Reward("goal:test", "2026-10-04", "GOAL", 10000))
                val worlds = WorldRepository(first)
                assertTrue(worlds.visit("clouds")); worlds.guideDismissed(true)
            } finally { first.close() }
            val reopened = open()
            try {
                assertEquals("clouds", reopened.dao().profile()?.region)
                assertEquals(true, reopened.dao().profile()?.guideDismissed)
                assertEquals(true, reopened.dao().profile()?.hasPlacedTreasure)
                WorldRepository(reopened).guideDismissed(false)
                assertEquals(false, reopened.dao().profile()?.guideDismissed)
                assertEquals(10000, reopened.dao().totalXp())
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    private fun oldDatabase(name: String, hidden: String, item: String, opened: Boolean) {
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name); path.parentFile?.mkdirs()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.scrollxp.app.data.ScrollDatabase/3.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO profile VALUES (1, 'Old haven', 'app', 45, 'next.app', 60, 123, 1, 'Gentle', ?, 1, 0, 100, 200, 'UTC', 'Dusk', 1, 0)", arrayOf(hidden))
            old.execSQL("INSERT INTO rewards VALUES ('usage:day', '2026-10-04', 'USAGE', 2)")
            old.execSQL("INSERT INTO chests VALUES ('milestone:0', 'Welcome', 1, ?, 'COMMON', ?, 1)", arrayOf<Any?>(item, if (opened) 2 else null))
            old.version = 3
        }
    }

    @Test fun versionThreeUpgradeKeepsXpChestsAndExistingFirstPlacement() = runBlocking<Unit> {
        val name = "world-migration-test.db"
        oldDatabase(name, "flowers", "lantern", true)
        val db = Room.databaseBuilder(context, ScrollDatabase::class.java, name).addMigrations(ScrollDatabase.MIGRATION_3_4).build()
        try {
            val profile = requireNotNull(db.dao().profile())
            assertEquals("Old haven", profile.islandName); assertEquals("next.app", profile.pendingApps)
            assertEquals("Dusk", profile.appearance); assertTrue(profile.reducedMotion); assertFalse(profile.haptics)
            assertEquals("meadow", profile.region); assertFalse(profile.guideDismissed); assertTrue(profile.hasPlacedTreasure)
            assertEquals(2, db.dao().totalXp()); assertEquals(2L, db.dao().chests().single().openedAt)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun hiddenUnopenedAndKeepsakeRewardsDoNotInventAPlacement() = runBlocking<Unit> {
        for ((hidden, item, opened) in listOf(Triple("lantern", "lantern", true), Triple("", "lantern", false), Triple("", "keepsake", true))) {
            val name = "world-no-placement-test.db"
            oldDatabase(name, hidden, item, opened)
            val db = Room.databaseBuilder(context, ScrollDatabase::class.java, name).addMigrations(ScrollDatabase.MIGRATION_3_4).build()
            try { assertEquals(false, db.dao().profile()?.hasPlacedTreasure) }
            finally { db.close(); context.deleteDatabase(name) }
        }
    }
}
