@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scrollxp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollxp.app.domain.ChapterAction
import com.scrollxp.app.domain.FirstChapter
import com.scrollxp.app.ui.theme.*

@Composable
internal fun FirstChapterCard(state: ScrollState, connect: () -> Unit, world: () -> Unit,
                              goals: () -> Unit, openChest: (String) -> Unit, hide: () -> Unit) {
    if (state.profile.guideDismissed) return
    var showSteps by rememberSaveable { mutableStateOf(false) }
    val welcome = state.chests.firstOrNull { it.key == "milestone:0" }
    val steps = FirstChapter.steps(state.hasAccess, welcome?.openedAt != null,
        state.profile.hasPlacedTreasure, state.goalDates, state.date)
    val completed = steps.count { it.complete }
    val next = steps.firstOrNull { !it.complete }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("YOUR FIRST CHAPTER")
            Spacer(Modifier.weight(1f))
            Text("$completed / ${steps.size}", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        GentleProgress(completed.toFloat() / steps.size)
        Text(next?.title ?: "You've settled in beautifully.", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(when (next?.action) {
            ChapterAction.CONNECT -> "Connect whole-app activity to earn capped XP. ScrollXP can't see your messages, videos, or scroll gestures."
            ChapterAction.OPEN_CHEST -> "A cosmetic treasure is waiting. Your welcome chest needs no scrolling or Usage Access."
            ChapterAction.PLACE -> "Give your first treasure a place on the island. It stays yours even when you tuck it away."
            ChapterAction.BALANCE -> if (state.profile.paused) "Tracking is paused. Resume in Settings when you're ready for a full-day budget." else "Stay within your budget for one full tracked day. The result arrives after that day ends; setup and partial days don't earn the bonus."
            null -> "Keep building at your pace. Visit new destinations, collect little treasures, and find a balance that works for you."
        }, color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        next?.let { step ->
            Button(onClick = { when (step.action) {
                ChapterAction.CONNECT -> connect()
                ChapterAction.OPEN_CHEST -> welcome?.let { openChest(it.key) }
                ChapterAction.PLACE -> world()
                ChapterAction.BALANCE -> goals()
            } }, enabled = step.action != ChapterAction.OPEN_CHEST || welcome != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(when (step.action) {
                    ChapterAction.CONNECT -> "Connect Usage Access"
                    ChapterAction.OPEN_CHEST -> "Open welcome chest"
                    ChapterAction.PLACE -> "Make it yours"
                    ChapterAction.BALANCE -> "See my budget goal"
                })
            }
        }
        Text("At your pace. No deadline, no streak to lose.", color = Muted, fontSize = 12.sp)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { showSteps = true }) { Text("View steps", fontSize = 12.sp) }
            TextButton(onClick = hide) { Text("Tuck away guide", fontSize = 12.sp) }
        }
    }
    if (showSteps) AlertDialog(onDismissRequest = { showSteps = false }, title = { Text("Your first chapter") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step.complete) WorldGlyph("check", Modifier.size(22.dp), Green)
                    else Text("${index + 1}", color = Muted, fontWeight = FontWeight.Bold)
                    Text(step.title, Modifier.weight(1f), color = if (step.complete) Green else Ink)
                }
            }
            Text("Whole-app time earns 2 XP/minute, capped at 120 usage XP/day. A completed full-day budget earns +100 XP, including an eligible zero-usage day. Your progress isn't spent or lost when you take a break.", color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
        } }, confirmButton = { TextButton(onClick = { showSteps = false }) { Text("Back to my island") } })
}
