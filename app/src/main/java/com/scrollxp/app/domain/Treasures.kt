package com.scrollxp.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

enum class Rarity(val label: String) { COMMON("Common"), UNCOMMON("Uncommon"), RARE("Rare") }
data class Treasure(val id: String, val name: String, val description: String, val rarity: Rarity)

/** Version 1 cosmetic economy. Results are persisted at award time, never rerolled on opening. */
object Treasures {
    const val ECONOMY_VERSION = 1
    const val WEEKLY_TARGET = 5
    const val KEEPSAKE = "keepsake"
    val chestMilestones = listOf(0, 100, 300, 600, 1_200, 2_500, 5_000, 10_000)
    val all = listOf(
        Treasure("mushrooms", "Mushroom patch", "Tiny umbrellas for forest visitors", Rarity.COMMON),
        Treasure("bench", "Reading bench", "A quiet seat with a lovely view", Rarity.COMMON),
        Treasure("mailbox", "Little mailbox", "Somewhere for good things to arrive", Rarity.COMMON),
        Treasure("flowers", "Wildflower trail", "A little color along the path", Rarity.COMMON),
        Treasure("lantern", "Lantern post", "A warm light for slower evenings", Rarity.COMMON),
        Treasure("stones", "Pebble spiral", "A small reminder to take your time", Rarity.COMMON),
        Treasure("picnic", "Picnic corner", "A perfect excuse to step outside", Rarity.COMMON),
        Treasure("windmill", "Pocket windmill", "Big daydreams, little sails", Rarity.UNCOMMON),
        Treasure("beehive", "Bee cottage", "Your tiniest neighbors are moving in", Rarity.UNCOMMON),
        Treasure("fountain", "Wishing fountain", "A quiet place for your next idea", Rarity.UNCOMMON),
        Treasure("telescope", "Stargazer's telescope", "Look up. There is a bigger world.", Rarity.UNCOMMON),
        Treasure("crystal", "Moonstone grove", "A little moonlight, even on cloudy nights", Rarity.RARE),
        Treasure("airship", "Cloud voyager", "A small ship for very big adventures", Rarity.RARE),
    )
    fun find(id: String) = all.firstOrNull { it.id == id }
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun completedWeeks(successDates: Collection<String>): List<LocalDate> = successDates.distinct()
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .groupBy(::weekStart).filterValues { it.size >= WEEKLY_TARGET }.keys.sorted()
    fun successesThisWeek(successDates: Collection<String>, today: LocalDate): Set<LocalDate> = successDates
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filter { weekStart(it) == weekStart(today) && it < today }.toSet()

    fun draw(reserved: Set<String>, random: Random): String {
        val available = all.filterNot { it.id in reserved }
        if (available.isEmpty()) return KEEPSAKE
        val rolled = when (random.nextInt(100)) { in 0..69 -> Rarity.COMMON; in 70..94 -> Rarity.UNCOMMON; else -> Rarity.RARE }
        // An exhausted rarity falls back to the lowest remaining rarity, then a uniform item.
        val tier = available.filter { it.rarity == rolled }.ifEmpty {
            available.filter { it.rarity == available.minBy { item -> item.rarity.ordinal }.rarity }
        }
        return tier[random.nextInt(tier.size)].id
    }
}
