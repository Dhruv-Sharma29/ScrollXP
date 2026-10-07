@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.scrollxp.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.scrollxp.app.domain.SocialPolicy
import com.scrollxp.app.online.*
import com.scrollxp.app.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun FriendsPage(openRanking: () -> Unit) {
    val model = LocalOnlineModel.current
    val online = model?.state?.collectAsState()?.value ?: OnlineState()
    val ready = online.configured && online.uid != null && online.verified && !online.deleting && !online.busy
    var handle by remember(online.uid) { mutableStateOf("") }
    var search by remember(online.uid) { mutableStateOf("") }
    var confirmation by remember(online.uid) { mutableStateOf<Pair<String,String>?>(null) }
    val context = LocalContext.current
    var loadedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(online.uid, online.verified, online.busy) {
        if (online.verified && !online.busy && loadedFor != online.uid) { loadedFor = online.uid; model?.refreshSocial() }
    }
    Text("Your people", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("Share small wins, one balanced day at a time.", color = Muted)
    OutlinedButton(onClick = openRanking) { Text("Open Ranking") }
    online.message?.let { Text(it, color = Muted, fontSize = 13.sp) }
    FeaturePanel("Your username") {
        if (online.social.profile == null) {
            Text("Sign in, verify your email and choose a unique username. Your local island stays as it is.", color = Muted, fontSize = 13.sp)
            OutlinedTextField(handle, { handle = it.take(21) }, label = { Text("Choose username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("3–20 lowercase letters, numbers or underscores. Start with a letter. Fixed after claiming.", fontSize = 12.sp, color = Muted)
            Button(onClick = { model?.claimUsername(handle) }, enabled = ready && SocialPolicy.valid(SocialPolicy.handle(handle))) { Text("Claim username") }
        } else {
            val name = "@${online.social.profile.username}"
            Text(name, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Add me on ScrollXP: $name")
            }, "Share your username")) }) { Text("Share my username") }
        }
        TextButton(onClick = { model?.refreshSocial() }, enabled = ready) { Text("Refresh Friends") }
        if (online.uid != null && !online.verified) TextButton(onClick = { model?.refresh() }, enabled = !online.busy) { Text("Refresh account after verification") }
    }
    FeaturePanel("Add a friend") {
        OutlinedTextField(search, { search = it.take(21) }, label = { Text("Friend's username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { model?.searchUsername(search) }, enabled = ready && online.social.profile != null && SocialPolicy.valid(SocialPolicy.handle(search))) { Text("Find friend") }
        online.found?.takeIf { it.username == SocialPolicy.handle(search) }?.let { found ->
            Text("@${found.username}")
            OutlinedButton(onClick = { model?.addFriend(found) }, enabled = ready && found.uid != online.uid) { Text("Send friend request") }
        }
        Text("Requests need acceptance. Emails, app names and usage minutes are never shown here.", fontSize = 12.sp, color = Muted)
    }
    val pending = online.social.friends.filter { it.status == "pending" }
    FeaturePanel("Requests · ${pending.size}") {
        if (pending.isEmpty()) Text("No pending requests.", color = Muted)
        pending.forEach { friend ->
            Text("@${friend.other.username} · ${if (friend.sender == online.uid) "Sent" else "Incoming"}")
            FlowRow {
                if (friend.sender != online.uid) TextButton(onClick = { model?.friendAction(friend.id, "accept") }, enabled = ready) { Text("Accept") }
                TextButton(onClick = { model?.friendAction(friend.id, "remove") }, enabled = ready) { Text(if (friend.sender == online.uid) "Cancel request" else "Decline") }
                TextButton(onClick = { confirmation = friend.id to "block" }, enabled = ready) { Text("Block") }
            }
        }
    }
    val accepted = online.social.friends.filter { it.status == "accepted" }
    FeaturePanel("My friends · ${accepted.size}") {
        if (accepted.isEmpty()) Text("Add your first friend by username. Their score appears after they choose to share.", color = Muted)
        accepted.forEach { friend ->
            Text("@${friend.other.username}", fontWeight = FontWeight.Bold)
            FlowRow {
                TextButton(onClick = { confirmation = friend.id to "remove" }, enabled = ready) { Text("Remove") }
                TextButton(onClick = { confirmation = friend.id to "block" }, enabled = ready) { Text("Block") }
                TextButton(onClick = { confirmation = friend.other.uid to "report" }, enabled = ready) { Text("Report profile") }
            }
        }
        Text("20 total friends, requests and blocks per account. Circles have their own limits.", fontSize = 12.sp, color = Muted)
    }
    online.social.friends.filter { it.status == "blocked" }.takeIf { it.isNotEmpty() }?.let { blocked ->
        FeaturePanel("Blocked relationships") {
            blocked.forEach { friend ->
                Text("@${friend.other.username} · Blocked")
                if (friend.blocker == online.uid) TextButton(onClick = { confirmation = friend.id to "remove" }, enabled = ready) { Text("Unblock (does not restore friendship)") }
            }
        }
    }
    FriendCirclesPanel(online, model)
    confirmation?.let { (id, action) -> AlertDialog(onDismissRequest = { confirmation = null },
        title = { Text(if (action == "report") "Report this profile?" else if (action == "block") "Block this person?" else "Remove this relationship?") },
        text = { Text(if (action == "report") "Send an inappropriate-profile report for private developer review." else "Score access ends. Unblocking or adding them later does not restore the previous friendship automatically.") },
        confirmButton = { TextButton(onClick = { if (action == "report") model?.reportFriend(id) else model?.friendAction(id, action); confirmation = null }, enabled = ready) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } }) }
}

@Composable
internal fun RankingPage() {
    val model = LocalOnlineModel.current
    val online = model?.state?.collectAsState()?.value ?: OnlineState()
    val ready = online.uid != null && online.verified && !online.busy && !online.deleting && online.social.profile != null
    var local by remember { mutableStateOf(false) }
    var area by remember(online.uid) { mutableStateOf(online.social.area ?: SocialPolicy.areas.first()) }
    var confirm by remember(online.uid) { mutableStateOf(false) }
    val week = SocialPolicy.week(System.currentTimeMillis())
    val format = DateTimeFormatter.ofPattern("d MMM, HH:mm")
    val start = Instant.ofEpochMilli(SocialPolicy.start(week)).atZone(ZoneId.systemDefault()).format(format)
    val end = Instant.ofEpochMilli(SocialPolicy.start(week) + com.scrollxp.app.domain.FriendScorePolicy.WEEK).atZone(ZoneId.systemDefault()).format(format)
    var loadedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(online.uid, online.verified, online.busy) {
        if (online.verified && !online.busy && loadedFor != online.uid) { loadedFor = online.uid; model?.refreshSocial() }
    }
    Text("Small wins, together", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("$start — $end\nOne common UTC week, shown in your timezone.", color = Muted, fontSize = 13.sp)
    Row {
        FilterChip(!local, { local = false }, label = { Text("Friends") })
        Spacer(Modifier.width(10.dp)); FilterChip(local, { local = true }, label = { Text("Local") })
    }
    Text("Completed budget days · 0–7\nSelf-reported. A full tracked day must begin after sharing starts and fit within this week. No rewards for scrolling more.", color = Muted, fontSize = 13.sp)
    online.message?.let { Text(it, color = Muted, fontSize = 13.sp) }
    FeaturePanel(if (local) "Selected area" else "Share with friends") {
        if (local) {
            Text("Choose an area manually. No GPS. Public entries can be read by verified users; your selected area is not verified residence.", color = Muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SocialPolicy.areas.forEach { name ->
                FilterChip(area == name, { area = name; model?.clearLocalRanking() }, label = { Text(name) })
            } }
            Button(onClick = { confirm = true }, enabled = ready) { Text("Join / update local board") }
            TextButton(onClick = { model?.localRanking(area) }, enabled = ready) { Text("Refresh local ranking") }
            TextButton(onClick = { model?.withdrawRanking(true) }, enabled = ready) { Text("Leave local board") }
        } else {
            Text("Only accepted friends can read your weekly shared score. Starting sharing creates a score of 0; publish again after an eligible day finishes.", color = Muted, fontSize = 13.sp)
            Button(onClick = { confirm = true }, enabled = ready) { Text(if (online.social.sharing) "Share latest score" else "Start sharing score") }
            TextButton(onClick = { model?.refreshSocial() }, enabled = ready) { Text("Refresh ranking") }
            TextButton(onClick = { model?.withdrawRanking(false) }, enabled = ready) { Text("Stop all score sharing") }
        }
        if (online.social.profile == null) Text("Choose a username in Friends first.", color = Muted)
    }
    val rows = (if (local) online.localRows.takeIf { online.loadedArea == area }.orEmpty() else online.social.rows).filter { it.week == week }
    FeaturePanel(if (local) "Top 50 in $area" else "Friends ranking") {
        if (rows.isEmpty()) Text("No shared results loaded. Refresh the board; scores stay at 0 until a full eligible day finishes.", color = Muted)
        val ranks = SocialPolicy.ranks(rows.map { it.days })
        rows.forEachIndexed { i, row ->
            Text("#${ranks[i]}  @${row.username}${if (row.uid == online.uid) " · You" else ""}  ·  ${row.days} days", fontWeight = if (row.uid == online.uid) FontWeight.Bold else FontWeight.Normal)
            Text("Shared ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(row.updated))}", fontSize = 12.sp, color = Muted)
        }
        if (local && rows.isNotEmpty() && online.social.area == area && rows.none { it.uid == online.uid }) Text("You are outside the displayed top 50, or have not shared this week.", fontSize = 12.sp, color = Muted)
        if (!local) online.social.friends.filter { it.status == "accepted" && rows.none { row -> row.uid == it.other.uid } }.forEach {
            Text("@${it.other.username} · No score shared this week", color = Muted, fontSize = 13.sp)
        }
        Text("Equal scores share a rank. Refresh to see changes; this is not a live board.", fontSize = 12.sp, color = Muted)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(if (local) "Share in $area?" else "Share your weekly score?") },
        text = { Text(if (local) "Verified users can see your username, selected area and completed-day count. App names and usage minutes stay private. Sharing also enables your friends score. You can leave at any time." else "Accepted friends can read your username and completed-day count. If you already joined a local board, its entry is updated too. Sharing is manual; app names and usage minutes stay private.") },
        confirmButton = { TextButton(onClick = { model?.shareRanking(if (local) area else null); confirm = false }, enabled = ready) { Text("Share score") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
