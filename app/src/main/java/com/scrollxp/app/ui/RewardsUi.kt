package com.scrollxp.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollxp.app.data.Chest
import com.scrollxp.app.domain.Treasures
import com.scrollxp.app.ui.theme.*
import java.time.format.DateTimeFormatter

@Composable
internal fun TreasureCard(state: ScrollState, open: (String) -> Unit) {
    val chest = state.unopened.firstOrNull()
    if (chest == null) return
    Surface(color = Lime, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            WorldGlyph("chest", Modifier.size(44.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (state.unopened.size == 1) "A little treasure awaits" else "${state.unopened.size} treasures await", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(chest.source, color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = { open(chest.key) }) { Text("View") }
        }
    }
}

@Composable
internal fun ChestDialog(chest: Chest, state: ScrollState, model: ScrollViewModel, dismiss: () -> Unit) {
    var showContents by rememberSaveable(chest.key) { mutableStateOf(false) }
    val opened = chest.openedAt != null
    val item = Treasures.find(chest.itemId)
    val haptic = LocalHapticFeedback.current
    var wasOpened by remember(chest.key) { mutableStateOf(opened) }
    LaunchedEffect(opened) {
        if (opened && !wasOpened && state.profile.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        wasOpened = opened
    }
    AlertDialog(onDismissRequest = dismiss,
        icon = {
            Crossfade(opened, animationSpec = if (LocalReducedMotion.current) snap() else tween(250), label = "Treasure revealed") {
                WorldGlyph(if (it) chest.itemId else "chest", Modifier.size(76.dp))
            }
        },
        title = { Text(if (opened) item?.name ?: "Keepsake star" else "A gift for your island", fontFamily = FontFamily.Serif) },
        text = {
            Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                if (opened) {
                    Text(if (item != null) "${chest.rarity} · Yours to keep" else "Collection complete · A star for your keepsake shelf", color = Green, fontWeight = FontWeight.Bold)
                    Text(item?.description ?: "You've collected every chest treasure. Future chests add a keepsake star to your shelf.")
                    Text(if (item != null) "Place it on your island now, or find it in Little treasures later." else "Your full collection and every keepsake stay with you.", color = Muted)
                } else if (chest.itemId == Treasures.KEEPSAKE) {
                    Text("All 13 chest treasures are yours or reserved in unopened chests.", fontWeight = FontWeight.Bold)
                    Text("This chest contains a keepsake star for your shelf. This is a known reward; no random draw remains.", color = Muted)
                } else {
                    Text(chest.source, fontWeight = FontWeight.Bold)
                    Text("One cosmetic, chosen when this chest was earned. It stays here until you're ready.", color = Muted)
                    Text("Base odds: Common 70% · Uncommon 25% · Rare 5%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("No duplicates: unopened chests reserve their items too. If a rarity is collected, the lowest available rarity is used. Once all 13 are collected, each chest gives a known keepsake star.", color = Muted, fontSize = 12.sp)
                    TextButton(onClick = { showContents = !showContents }) { Text(if (showContents) "Hide possible treasures" else "See possible treasures") }
                    if (showContents) Treasures.all.forEach { possible ->
                        Text("${possible.name} · ${possible.rarity.label}", color = Muted, fontSize = 12.sp)
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (!opened) Button(onClick = { model.openChest(chest.key) }, enabled = state.openingChest == null) {
                Text(if (state.openingChest == chest.key) "Opening…" else "Open chest")
            } else if (item != null) Button(onClick = { model.placeItem(item.id); dismiss() }) { Text("Place on island") }
            else TextButton(onClick = dismiss) { Text("Lovely") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(if (opened) "Keep for later" else "Later") } })
}

@Composable
internal fun WeeklyChallenge(state: ScrollState) {
    val start = Treasures.weekStart(state.date)
    val successes = Treasures.successesThisWeek(state.goalDates, state.date)
    val awarded = state.chests.firstOrNull { it.key == "week:$start" }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Label("THIS WEEK'S LITTLE QUEST")
                Text("Five days of balance", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
            WorldGlyph("chest", Modifier.size(38.dp))
        }
        Text("${start.format(DateTimeFormatter.ofPattern("MMM d"))} – ${start.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d"))}", color = Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(7) { offset ->
                val date = start.plusDays(offset.toLong())
                val success = date in successes
                Column(Modifier.weight(1f).semantics {
                    contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}: ${if (success) "budget completed" else if (date >= state.date) "not finalized" else "no completed goal"}"
                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = if (success) Lime else Mist) {
                        Box(Modifier.fillMaxWidth().height(35.dp), contentAlignment = Alignment.Center) {
                            if (success) WorldGlyph("check", Modifier.size(21.dp))
                            else Text(if (date == state.date) "•" else "–", color = Muted)
                        }
                    }
                    Text(date.format(DateTimeFormatter.ofPattern("EEEEE")), color = Muted, fontSize = 11.sp)
                }
            }
        }
        Text(if (awarded?.openedAt != null) "Weekly chest collected. A lovely week." else if (awarded != null) "Weekly chest earned. It's waiting in your treasures." else "${successes.size.coerceAtMost(5)} of 5 completed budget days", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        GentleProgress(successes.size / 5f)
        Text("Any five full tracked days within budget earn one chest. They don't need to be consecutive. Today's result arrives after the day ends.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
    }
}

@Composable
internal fun TreasureHistory(state: ScrollState, open: (String) -> Unit) {
    val recent = state.chests.filter { it.openedAt != null }.sortedByDescending { it.openedAt }.take(5)
    if (recent.isEmpty()) return
    AppCard {
        Label("RECENT DISCOVERIES")
        recent.forEach { chest ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorldGlyph(chest.itemId, Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Treasures.find(chest.itemId)?.name ?: "Keepsake star", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(chest.source, color = Muted, fontSize = 11.sp)
                }
                TextButton(onClick = { open(chest.key) }) { Text("View", fontSize = 12.sp) }
            }
        }
        val stars = state.chests.count { it.itemId == Treasures.KEEPSAKE && it.openedAt != null }
        if (stars > 0) Text("Keepsake shelf · $stars stars", color = Green, fontWeight = FontWeight.Bold)
    }
}
