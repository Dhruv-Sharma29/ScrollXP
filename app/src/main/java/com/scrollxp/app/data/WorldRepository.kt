package com.scrollxp.app.data

import androidx.room.withTransaction
import com.scrollxp.app.domain.Worlds

class WorldRepository(private val database: ScrollDatabase) {
    suspend fun visit(id: String): Boolean = database.withTransaction {
        val dao = database.dao()
        val profile = dao.profile() ?: return@withTransaction false
        if (!profile.onboarded || !Worlds.canVisit(id, dao.totalXp())) return@withTransaction false
        dao.saveProfile(profile.copy(region = id))
        true
    }
    suspend fun guideDismissed(value: Boolean) = database.withTransaction {
        val dao = database.dao()
        val profile = dao.profile() ?: return@withTransaction
        dao.saveProfile(profile.copy(guideDismissed = value))
    }
}
