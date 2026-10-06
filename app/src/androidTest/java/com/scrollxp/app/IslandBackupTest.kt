package com.scrollxp.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrollxp.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IslandBackupTest {
    private fun fixture() = IslandBackup(Profile(islandName = "Cloud cottage",selectedApps = "private.app",zone = "Asia/Kolkata",
        roof = 2,appearance = "Dusk",hiddenItems = "bench",hasPlacedTreasure = true),
        listOf(Reward("usage:2026-10-01","2026-10-01","USAGE",20),Reward("goal:2026-10-01","2026-10-01","GOAL",100)),
        listOf(Chest("milestone:0","Welcome gift",10,"bench","Common",20),Chest("milestone:100","100 XP milestone",30,"lantern","Common")))
    @Test fun backupExcludesDeviceUsageAndKeepsChestReservations() {
        val encoded = fixture().encode()
        assertFalse(encoded.contains("private.app")); assertFalse(encoded.contains("selectedApps")); assertFalse(encoded.contains("perAppJson"))
        val decoded = IslandBackup.decode(encoded)
        assertEquals(120,decoded.xp); assertEquals(fixture().chests,decoded.chests)
        assertEquals("Dusk",decoded.profile.appearance); assertEquals("",decoded.profile.selectedApps)
    }
    @Test fun invalidBackupCannotReplaceLocalData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,ScrollDatabase::class.java).build()
        try {
            db.dao().saveProfile(Profile(islandName = "Keep me",zone = "UTC"))
            val bad = fixture().copy(rewards = fixture().rewards + fixture().rewards.first())
            try { IslandBackupStore(db).restore(bad) { true }; fail("Duplicate reward must be rejected") } catch (_: IllegalArgumentException) { }
            assertEquals("Keep me",db.dao().profile()?.islandName)
            try { IslandBackupStore(db).restore(fixture()) { false }; fail("Wrong account must be rejected") } catch (_: IllegalStateException) { }
            assertEquals("Keep me",db.dao().profile()?.islandName)
        } finally { db.close() }
    }
    @Test fun repeatedRestorePreservesXpAndChestsWithoutKeepingOldUsageCoverage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,ScrollDatabase::class.java).build()
        try {
            val dao = db.dao(); dao.saveProfile(Profile(onboarded = true,zone = "UTC",selectedApps = "local.app",trackingSince = 10,lastObserved = 30))
            dao.saveDay(DayRecord("2026-10-01",10,30,"local.app",45,10,perAppJson = "{private:1}"))
            repeat(2) { IslandBackupStore(db).restore(IslandBackup.decode(fixture().encode())) { true } }
            assertEquals(120,dao.totalXp()); assertEquals(2,dao.chests().size)
            assertEquals(20L,dao.chest("milestone:0")?.openedAt); assertNull(dao.chest("milestone:100")?.openedAt)
            assertEquals("local.app",dao.profile()?.selectedApps); assertEquals(0L,dao.profile()?.trackingSince)
            assertEquals(0L,dao.profile()?.lastObserved); assertNull(dao.day("2026-10-01"))
            assertEquals(listOf("2026-10-01"),dao.goalDates())
        } finally { db.close() }
    }
    @Test fun unknownVersionsAndOverAwardedUsageAreRejected() {
        try { IslandBackup.decode(fixture().encode().replace("\"version\":1","\"version\":2")); fail() } catch (_: IllegalArgumentException) { }
        try { fixture().copy(rewards = listOf(Reward("usage:2026-10-01","2026-10-01","USAGE",122))).encode(); fail() } catch (_: IllegalArgumentException) { }
        try { fixture().copy(chests = fixture().chests + fixture().chests.first().copy(key = "milestone:300")).encode(); fail() } catch (_: IllegalArgumentException) { }
    }
}
