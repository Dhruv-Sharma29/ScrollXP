@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scrollxp.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollxp.app.data.packages
import com.scrollxp.app.domain.Worlds
import com.scrollxp.app.ui.theme.*

@Composable
internal fun WorldRegions(state: ScrollState, visit: (String) -> Unit) {
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    AppCard {
        Label("NEW HORIZONS")
        Text("A world worth coming back to", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("${Worlds.all.count { state.xp >= it.xp }} of ${Worlds.all.size} destinations discovered. Your cottage and treasures travel with you.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        Worlds.all.forEach { region ->
            val unlocked = state.xp >= region.xp
            val here = state.world.id == region.id
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Island(0, state.profile.roof, Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)), region = region.id)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(region.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(if (region.xp == 0) "Your first home" else if (unlocked) "Yours to visit" else "${region.xp} lifetime XP", fontSize = 12.sp, color = Muted)
                    TextButton(onClick = {
                        if (unlocked) {
                            if (state.profile.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            visit(region.id)
                        } else preview = region.id
                    }, enabled = !here, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(if (here) "You're here" else if (unlocked) "Visit ${region.name}" else "Preview ${region.name}", fontSize = 12.sp)
                    }
                }
            }
        }
        Text("Permanent unlocks. Your XP is never spent, and your islands never decay.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
    }
    Worlds.find(preview ?: "")?.let { region ->
        AlertDialog(onDismissRequest = { preview = null }, title = { Text(region.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Label("DESTINATION PREVIEW")
                Island(state.xp, state.profile.roof, Modifier.clip(RoundedCornerShape(18.dp)),
                    hidden = state.profile.hiddenItems.packages(), treasures = state.ownedTreasures, region = region.id)
                Text(region.description, color = Muted, lineHeight = 22.sp)
                Text("Unlocks at ${region.xp} lifetime XP · ${(region.xp - state.xp).coerceAtLeast(0)} XP to go", color = Green, fontWeight = FontWeight.Bold)
                GentleProgress(state.xp.toFloat() / region.xp.coerceAtLeast(1))
                Text("Both capped usage XP and completed budget bonuses count. This preview keeps your progress and placement unchanged.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            } }, confirmButton = { TextButton(onClick = { preview = null }) { Text("Back to my world") } })
    }
}

@Composable
internal fun WelcomeWorldPreview() {
    var selected by rememberSaveable { mutableStateOf(Worlds.STARTER) }
    val region = Worlds.find(selected) ?: Worlds.all.first()
    Surface(shape = RoundedCornerShape(28.dp), color = Sky, border = BorderStroke(1.dp, Line)) {
        Column {
            Island(0, 0, region = region.id)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(region.name, fontWeight = FontWeight.Bold)
                Text(if (region.xp == 0) "Your cottage and trees are free from the start." else "A future destination · unlocks at ${region.xp} XP", color = Muted, fontSize = 12.sp)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Worlds.all.forEachIndexed { index, world ->
                        FilterChip(selected = selected == world.id, onClick = { selected = world.id },
                            label = { Text(listOf("Meadow", "Coast", "Clouds")[index], fontSize = 12.sp) })
                    }
                }
            }
        }
    }
}
