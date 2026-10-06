package com.scrollxp.app

import com.scrollxp.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class ProgressionTest {
    @Test fun partialMinutesAreNotDiscardedByRefresh() {
        assertEquals(0, Progression.usageXp(59_999))
        assertEquals(2, Progression.usageXp(60_000))
        assertEquals(4, Progression.usageXp(120_000))
    }
    @Test fun usageRewardIsCappedAcrossTheDay() {
        assertEquals(120, Progression.usageXp(60 * 60_000L))
        assertEquals(120, Progression.usageXp(12 * 60 * 60_000L))
        assertEquals(0, Progression.usageXp(-1))
    }
    @Test fun levelBoundariesCarryRemainingXp() {
        assertEquals(LevelProgress(1, 99, 100), Progression.level(99))
        assertEquals(LevelProgress(2, 0, 125), Progression.level(100))
        assertEquals(LevelProgress(3, 15, 150), Progression.level(240))
    }
    @Test fun noScrollingCanStillCompleteAValidGoal() {
        assertEquals("SUCCESS", BudgetPolicy.result(100, 100, 0, 45, true, true))
    }
    @Test fun missingHistoryNeverWinsAGoal() {
        assertEquals("UNKNOWN", BudgetPolicy.result(101, 100, 0, 45, false, true))
        assertEquals("UNKNOWN", BudgetPolicy.result(101, 100, 0, 45, true, false))
    }
    @Test fun todaysGoalIsProvisional() {
        assertEquals("PENDING", BudgetPolicy.result(99, 100, 0, 45, true, true))
    }
    @Test fun goalUsesSecondsRatherThanRoundedDisplayMinutes() {
        assertEquals("SUCCESS", BudgetPolicy.result(100, 100, 45 * 60_000L, 45, true, true))
        assertEquals("OVER_BUDGET", BudgetPolicy.result(100, 100, 45 * 60_000L + 1, 45, true, true))
    }
    @Test fun firstUnlockAndLaterUnlocksArePredictable() {
        assertEquals("campfire", Progression.next(0)?.id)
        assertEquals("garden", Progression.next(100)?.id)
        assertNull(Progression.next(2500))
    }
}
