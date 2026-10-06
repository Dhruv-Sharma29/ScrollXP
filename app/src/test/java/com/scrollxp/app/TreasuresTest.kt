package com.scrollxp.app

import com.scrollxp.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class TreasuresTest {
    private fun roll(value: Int) = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextInt(until: Int) = if (until == 100) value else 0
    }
    @Test fun rarityBoundariesMatchPublishedOdds() {
        listOf(0 to Rarity.COMMON, 69 to Rarity.COMMON, 70 to Rarity.UNCOMMON,
            94 to Rarity.UNCOMMON, 95 to Rarity.RARE, 99 to Rarity.RARE).forEach { (value, rarity) ->
            assertEquals(rarity, Treasures.find(Treasures.draw(emptySet(), roll(value)))?.rarity)
        }
    }
    @Test fun exhaustedRarityFallsBackToLowestUnownedTier() {
        val common = Treasures.all.filter { it.rarity == Rarity.COMMON }.map { it.id }.toSet()
        assertEquals(Rarity.UNCOMMON, Treasures.find(Treasures.draw(common, roll(0)))?.rarity)
        val rare = Treasures.all.filter { it.rarity == Rarity.RARE }.map { it.id }.toSet()
        assertEquals(Rarity.COMMON, Treasures.find(Treasures.draw(rare, roll(99)))?.rarity)
    }
    @Test fun reservationsPreventDuplicatesUntilCollectionIsComplete() {
        val reserved = mutableSetOf<String>()
        repeat(Treasures.all.size) { assertTrue(reserved.add(Treasures.draw(reserved, roll(0)))) }
        assertEquals(Treasures.all.map { it.id }.toSet(), reserved)
        repeat(3) { assertEquals(Treasures.KEEPSAKE, Treasures.draw(reserved, roll(99))) }
    }
    @Test fun weekStartsOnMondayAcrossTheYearBoundary() {
        assertEquals(LocalDate.parse("2025-12-29"), Treasures.weekStart(LocalDate.parse("2026-01-04")))
        assertEquals(LocalDate.parse("2026-01-05"), Treasures.weekStart(LocalDate.parse("2026-01-05")))
    }
    @Test fun fiveNonconsecutiveUniqueDatesEarnExactlyOneWeeklyResult() {
        val days = listOf("2026-09-28", "2026-09-29", "2026-10-01", "2026-10-03", "2026-10-04")
        assertTrue(Treasures.completedWeeks(days.take(4) + days.take(4)).isEmpty())
        assertEquals(listOf(LocalDate.parse("2026-09-28")), Treasures.completedWeeks(days + days + "invalid"))
    }
    @Test fun successesDoNotCarryBetweenWeeks() {
        assertTrue(Treasures.completedWeeks(listOf("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05")).isEmpty())
    }
    @Test fun currentWeekDisplayExcludesTodayFutureAndOtherWeeks() {
        assertEquals(setOf(LocalDate.parse("2026-10-05")), Treasures.successesThisWeek(
            listOf("2026-10-04", "2026-10-05", "2026-10-05", "2026-10-06", "2026-10-07"), LocalDate.parse("2026-10-06")))
    }
}
