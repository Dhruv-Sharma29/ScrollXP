package com.scrollxp.app.domain

import java.time.LocalDate

enum class ChapterAction { CONNECT, OPEN_CHEST, PLACE, BALANCE }
data class ChapterStep(val action: ChapterAction, val title: String, val complete: Boolean)

/** An optional guide, without expiry or a consecutive-day requirement. */
object FirstChapter {
    fun steps(access: Boolean, welcomeOpened: Boolean, treasurePlaced: Boolean,
              goalDates: Collection<String>, today: LocalDate) = listOf(
        ChapterStep(ChapterAction.CONNECT, "Connect screen time", access),
        ChapterStep(ChapterAction.OPEN_CHEST, "Open your welcome chest", welcomeOpened),
        ChapterStep(ChapterAction.PLACE, "Place your first treasure", treasurePlaced),
        ChapterStep(ChapterAction.BALANCE, "Complete a full-day budget", goalDates.any {
            runCatching { LocalDate.parse(it) < today }.getOrDefault(false)
        }),
    )
}
