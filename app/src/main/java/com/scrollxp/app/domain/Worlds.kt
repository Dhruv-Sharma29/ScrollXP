package com.scrollxp.app.domain

data class WorldRegion(val id: String, val name: String, val description: String, val xp: Int)

/** Permanent destinations unlocked by lifetime XP; visiting never spends XP. */
object Worlds {
    const val STARTER = "meadow"
    val all = listOf(
        WorldRegion(STARTER, "Meadow haven", "A cottage among the hills, with room for your little treasures.", 0),
        WorldRegion("cove", "Seaside cove", "A sandy shore, a small sailing boat, and a lighthouse to guide you home.", 5_000),
        WorldRegion("clouds", "Cloud sanctuary", "An island above the clouds, with hanging gardens and a quiet moon gate.", 10_000),
    )
    fun find(id: String) = all.firstOrNull { it.id == id }
    fun canVisit(id: String, xp: Int) = find(id)?.let { xp >= it.xp } ?: false
    fun active(id: String, xp: Int) = find(id)?.takeIf { canVisit(id, xp) } ?: all.first()
    fun next(xp: Int) = all.firstOrNull { it.xp > xp }
}
