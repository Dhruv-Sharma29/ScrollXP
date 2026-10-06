package com.scrollxp.app.domain

object Progression {
    const val DAILY_CAP = 120
    const val GOAL_BONUS = 100
    fun usageXp(milliseconds: Long): Int = ((milliseconds.coerceAtLeast(0) / 60_000) * 2)
        .coerceAtMost(DAILY_CAP.toLong()).toInt()
    fun level(xp: Int): LevelProgress {
        var remaining = xp.coerceAtLeast(0)
        var level = 1
        while (remaining >= requirement(level)) { remaining -= requirement(level); level++ }
        return LevelProgress(level, remaining, requirement(level))
    }
    private fun requirement(level: Int) = 100 + 25 * (level - 1)
    val milestones = listOf(
        Milestone("house", "Little home", "Your story starts here", 0),
        Milestone("trees", "Forest friends", "A little green goes a long way", 0),
        Milestone("campfire", "Campfire", "A warm spot for your village", 100),
        Milestone("garden", "Secret garden", "Room for something to grow", 300),
        Milestone("workshop", "Workshop", "Big ideas, tiny building", 600),
        Milestone("castle", "Castle wing", "Your kingdom is taking shape", 1_200),
        Milestone("dragon", "Pocket dragon", "A companion with a spark", 2_500),
    )
    fun next(xp: Int) = milestones.firstOrNull { it.xp > xp }
}

data class LevelProgress(val level: Int, val earned: Int, val required: Int) {
    val fraction: Float get() = earned.toFloat() / required
}
data class Milestone(val id: String, val name: String, val description: String, val xp: Int)
fun durationLabel(milliseconds: Long): String {
    val minutes = milliseconds.coerceAtLeast(0) / 60_000
    return when {
        milliseconds <= 0 -> "0 min"
        minutes == 0L -> "<1 min"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
