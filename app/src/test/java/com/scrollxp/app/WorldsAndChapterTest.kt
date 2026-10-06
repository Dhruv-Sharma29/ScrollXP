package com.scrollxp.app

import com.scrollxp.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WorldsAndChapterTest {
    private val today = LocalDate.of(2026, 10, 5)
    @Test fun destinationsUnlockAtExactLifetimeThresholds() {
        assertTrue(Worlds.canVisit("meadow", 0))
        assertFalse(Worlds.canVisit("cove", 4999))
        assertTrue(Worlds.canVisit("cove", 5000))
        assertFalse(Worlds.canVisit("clouds", 9999))
        assertTrue(Worlds.canVisit("clouds", 10000))
    }
    @Test fun malformedAndLockedChoicesFallBackToTheStarter() {
        assertFalse(Worlds.canVisit("unknown", 100000))
        assertEquals("meadow", Worlds.active("unknown", 100000).id)
        assertEquals("meadow", Worlds.active("clouds", 2).id)
    }
    @Test fun earnedDestinationCanBeRevisitedAtHigherXp() {
        assertEquals("cove", Worlds.active("cove", 5000).id)
        assertEquals("cove", Worlds.active("cove", 10000).id)
        assertEquals("clouds", Worlds.active("clouds", 10000).id)
    }
    @Test fun laterProgressionHasAnHonestNextDestination() {
        assertEquals("cove", Worlds.next(2500)?.id)
        assertEquals("clouds", Worlds.next(5000)?.id)
        assertNull(Worlds.next(10000))
    }
    @Test fun firstChapterStartsWithFourIncompleteSteps() {
        val steps = FirstChapter.steps(false, false, false, emptyList(), today)
        assertEquals(4, steps.size)
        assertTrue(steps.none { it.complete })
        assertEquals(ChapterAction.CONNECT, steps.first().action)
    }
    @Test fun welcomeRewardAndPlacementDoNotRequireUsageAccess() {
        val steps = FirstChapter.steps(false, true, true, emptyList(), today)
        assertEquals(2, steps.count { it.complete })
        assertFalse(steps.first().complete)
    }
    @Test fun unfinalizedFutureAndInvalidDatesCannotCompleteTheGuide() {
        val dates = listOf(today.toString(), today.plusDays(1).toString(), "invalid")
        assertFalse(FirstChapter.steps(true, true, true, dates, today).last().complete)
    }
    @Test fun completingTheChapterHasNoExpiryOrConsecutiveDayRule() {
        val steps = FirstChapter.steps(true, true, true, listOf(today.minusDays(30).toString()), today)
        assertTrue(steps.all { it.complete })
    }
}
