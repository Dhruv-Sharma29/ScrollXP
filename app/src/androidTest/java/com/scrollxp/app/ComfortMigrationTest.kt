package com.scrollxp.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrollxp.app.data.ScrollDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComfortMigrationTest {
    @Test fun upgradePreservesWorldHistoryAndRewardsAndSavesComfortPreferences() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "comfort-upgrade-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        // Recreate the exported v1 schema, rather than assuming the new schema matches it.
        val schema = JSONObject(instrumentation.context.assets.open("com.scrollxp.app.data.ScrollDatabase/1.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO profile VALUES (1, 'My haven', 'app', 45, 'next.app', 60, 123, 1, 'Gentle', 'trees', 1, 0, 100, 200, 'Asia/Kolkata')")
            old.execSQL("INSERT INTO days VALUES ('day', 0, 100, 'app', 45, 0, 120000, '{}', 99, 1, 'PENDING')")
            old.execSQL("INSERT INTO rewards VALUES ('usage:day', 'day', 'USAGE', 4)")
            old.version = 1
        }
        fun open() = Room.databaseBuilder(context, ScrollDatabase::class.java, name)
            .addMigrations(ScrollDatabase.MIGRATION_1_2, ScrollDatabase.MIGRATION_2_3, ScrollDatabase.MIGRATION_3_4).build()
        try {
            val upgraded = open()
            try {
                val dao = upgraded.dao()
                val profile = requireNotNull(dao.profile())
                assertEquals("My haven", profile.islandName)
                assertEquals(1, profile.roof)
                assertEquals("trees", profile.hiddenItems)
                assertEquals("next.app", profile.pendingApps)
                assertEquals(60, profile.pendingBudget)
                assertEquals(120000L, dao.day("day")?.usageMillis)
                assertEquals(4, dao.observeXp().first())
                assertEquals("Daylight", profile.appearance)
                assertFalse(profile.reducedMotion)
                dao.saveProfile(profile.copy(appearance = "Dusk", reducedMotion = true))
            } finally { upgraded.close() }
            val reopened = open()
            try {
                assertEquals("Dusk", reopened.dao().profile()?.appearance)
                assertEquals(true, reopened.dao().profile()?.reducedMotion)
                assertEquals(4, reopened.dao().observeXp().first())
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
