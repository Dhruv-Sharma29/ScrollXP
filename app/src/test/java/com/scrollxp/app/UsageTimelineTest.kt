package com.scrollxp.app

import com.scrollxp.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class UsageTimelineTest {
    private fun event(t: Long, kind: EventKind, app: String = "a", activity: String = "main") = TimelineEvent(t, kind, app, activity)
    @Test fun clipsAContinuousSessionAtDayBoundaries() {
        val intervals = UsageTimeline.reconstruct(listOf(event(0, EventKind.RESUME), event(300, EventKind.PAUSE)), 100, 200)
        assertEquals(listOf(UsageInterval("a", 100, 200)), intervals)
    }
    @Test fun replayedEventsDoNotDuplicateTime() {
        val events = listOf(event(10, EventKind.RESUME), event(100, EventKind.PAUSE))
        assertEquals(UsageTimeline.reconstruct(events, 0, 200), UsageTimeline.reconstruct(events + events, 0, 200))
    }
    @Test fun switchingAppsCountsEachMomentOnlyOnce() {
        val events = listOf(event(0, EventKind.RESUME), event(100, EventKind.RESUME, "b"), event(200, EventKind.PAUSE, "b"))
        val totals = UsageTimeline.totals(UsageTimeline.reconstruct(events, 0, 300), setOf("a", "b"), 0, 300)
        assertEquals(mapOf("a" to 100L, "b" to 100L), totals)
    }
    @Test fun delayedPauseFromAnotherActivityDoesNotCloseNewActivity() {
        val events = listOf(event(0, EventKind.RESUME, activity = "one"), event(50, EventKind.RESUME, activity = "two"),
            event(60, EventKind.PAUSE, activity = "one"), event(100, EventKind.PAUSE, activity = "two"))
        assertEquals(100L, UsageTimeline.totals(UsageTimeline.reconstruct(events, 0, 200), setOf("a"), 0, 200)["a"])
    }
    @Test fun screenOffStopsEarningAndCannotReopenUntilInteractive() {
        val events = listOf(event(0, EventKind.RESUME), event(100, EventKind.SCREEN_OFF), event(120, EventKind.RESUME),
            event(200, EventKind.SCREEN_ON), event(210, EventKind.RESUME), event(250, EventKind.PAUSE))
        assertEquals(140L, UsageTimeline.totals(UsageTimeline.reconstruct(events, 0, 300), setOf("a"), 0, 300)["a"])
    }
    @Test fun rebootDoesNotCountDowntime() {
        val events = listOf(event(0, EventKind.RESUME), event(100, EventKind.SHUTDOWN), event(200, EventKind.LOCK), event(205, EventKind.STARTUP),
            event(210, EventKind.RESUME), event(300, EventKind.SCREEN_ON), event(310, EventKind.UNLOCK),
            event(320, EventKind.RESUME), event(400, EventKind.PAUSE))
        assertEquals(180L, UsageTimeline.totals(UsageTimeline.reconstruct(events, 0, 500), setOf("a"), 0, 500)["a"])
    }
    @Test fun startupKeepsScreenEvidenceThatArrivesBeforeIt() {
        val events = listOf(event(0, EventKind.SHUTDOWN), event(10, EventKind.SCREEN_ON),
            event(20, EventKind.STARTUP), event(30, EventKind.RESUME), event(130, EventKind.PAUSE))
        assertEquals(100L, UsageTimeline.totals(UsageTimeline.reconstruct(events, 0, 200), setOf("a"), 0, 200)["a"])
    }
    @Test fun unselectedAppsContributeNoTime() {
        val intervals = UsageTimeline.reconstruct(listOf(event(0, EventKind.RESUME), event(100, EventKind.PAUSE)), 0, 200)
        assertEquals(mapOf("b" to 0L), UsageTimeline.totals(intervals, setOf("b"), 0, 200))
    }
    @Test fun emptyEventsRemainEmpty() {
        assertTrue(UsageTimeline.reconstruct(emptyList(), 0, 100).isEmpty())
    }
    @Test fun pausesExcludeOnlyTheAffectedPortions() {
        val intervals = listOf(UsageInterval("a", 0, 100))
        assertEquals(listOf(UsageInterval("a", 0, 30), UsageInterval("a", 70, 100)),
            UsageTimeline.excluding(intervals, listOf(30L to 70L)))
        assertTrue(UsageTimeline.excluding(intervals, listOf(0L to Long.MAX_VALUE)).isEmpty())
    }
    @Test fun multipleOverlappingBreaksAreExcludedOnce() {
        val intervals = UsageTimeline.excluding(listOf(UsageInterval("a", 0, 100)), listOf(20L to 60L, 40L to 80L))
        assertEquals(40L, UsageTimeline.totals(intervals, setOf("a"), 0, 100)["a"])
    }
}
