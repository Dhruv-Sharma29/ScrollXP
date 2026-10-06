package com.scrollxp.app

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrollxp.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    @Test fun rewardKeysPreventDuplicateXpAndTransactionsRollBack() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ScrollDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.saveReward(Reward("usage:day", "day", "USAGE", 20))
            dao.saveReward(Reward("usage:day", "day", "USAGE", 40))
            assertEquals(40, dao.observeXp().first())
            try {
                db.withTransaction {
                    dao.saveReward(Reward("goal:day", "day", "GOAL", 100))
                    error("Simulated interrupted award")
                }
            } catch (_: IllegalStateException) { }
            assertNull(dao.reward("goal:day"))
            assertEquals(40, dao.observeXp().first())
        } finally { db.close() }
    }
    @Test fun resetCanClearHistoryRewardsAndPreferences() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ScrollDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.saveProfile(Profile(onboarded = true))
            dao.insertChest(Chest("milestone:0", "Welcome", 0, "bench", "Common"))
            dao.saveDay(DayRecord("day", 0, 100, "app", 45, 0))
            dao.saveReward(Reward("usage:day", "day", "USAGE", 20))
            db.withTransaction { dao.clearChests(); dao.clearDays(); dao.clearRewards(); dao.clearBreaks(); dao.clearProfile() }
            assertNull(dao.profile()); assertNull(dao.day("day")); assertEquals(0, dao.observeXp().first()); assertTrue(dao.chests().isEmpty())
        } finally { db.close() }
    }
}
