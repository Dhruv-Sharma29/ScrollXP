@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.scrollxp.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollxp.app.domain.FriendScorePolicy
import com.scrollxp.app.domain.ProAccessPolicy
import com.scrollxp.app.online.*
import com.scrollxp.app.ui.theme.Muted
import java.text.DateFormat
import java.util.Date

val LocalBillingModel = staticCompositionLocalOf<PlayBillingModel?> { null }
private fun Context.activity(): Activity? = when(this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@Composable
internal fun ProSubscriptionPanel(showHistory: () -> Unit) {
    val model = LocalBillingModel.current
    val pro = model?.state?.collectAsState()?.value ?: BillingState(configured = false)
    val context = LocalContext.current
    var externalError by remember { mutableStateOf<String?>(null) }
    FeaturePanel("ScrollXP Pro") {
        Text("Optional monthly membership: Ocean, Rose and Lavender themes, plus a 90-day local balance journal. The widget, 30-day history, backups and friend circles stay free.",color = Muted,fontSize = 13.sp,lineHeight = 21.sp)
        pro.price?.let { Text("$it / month",fontWeight = FontWeight.Bold,fontSize = 20.sp) }
        Text("Renews automatically each month. Cancel in Google Play. Pro follows your Play account, independently of your Firebase login.",color = Muted,fontSize = 12.sp,lineHeight = 19.sp)
        if (!pro.configured) Text("Subscriptions are not available in this installation yet.",fontSize = 13.sp,color = Muted)
        pro.message?.let { Text(it,fontSize = 13.sp,color = Muted) }
        if (pro.active) {
            Text("Pro active",fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProAccessPolicy.THEMES.forEach { theme -> FilterChip(selected = pro.theme == theme,onClick = { model?.theme(theme) },label = { Text(theme) }) }
            }
            TextButton(onClick = { model?.theme(null) }) { Text("Use regular appearance") }
            OutlinedButton(onClick = showHistory) { Text("View 90-day journal") }
        } else Button(onClick = { context.activity()?.let { model?.buy(it) } },enabled = pro.configured && !pro.busy && !pro.checkoutOpen && !pro.purchaseBlocked && pro.price != null && context.activity() != null) { Text("Subscribe with Google Play") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { model?.refresh() },enabled = pro.configured && !pro.busy) { Text("Restore or refresh purchases") }
            TextButton(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/account/subscriptions?sku=${ProAccessPolicy.PRODUCT}&package=${context.packageName}"))) }
                catch (_: Exception) { externalError = "Open Google Play → Payments & subscriptions → Subscriptions." }
            }) { Text("Manage subscription") }
        }
        externalError?.let { Text(it,color = Muted,fontSize = 12.sp) }
        PrivacyPolicyLink()
    }
}

@Composable
internal fun FriendCirclesPanel(online: OnlineState, model: OnlineViewModel?) {
    val context = LocalContext.current
    val ready = online.configured && online.uid != null && online.verified && !online.busy && !online.deleting
    var action by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var shareError by remember { mutableStateOf<String?>(null) }
    FeaturePanel("Friend balance circles") {
        Text("Up to six members can join a seven-day balance challenge. Join up to three circles. A complete budget day after joining earns one point; more scrolling does not earn points.",color = Muted,fontSize = 13.sp,lineHeight = 21.sp)
        Text("Your chosen name, account identifier and best score are visible to members. Scores are self-reported from this device and are not independently verified. There are no cash prizes or extra XP.",color = Muted,fontSize = 12.sp,lineHeight = 19.sp)
        if (!online.configured) Text("Online circles are unavailable in this build.",fontSize = 13.sp,color = Muted)
        else if (online.uid == null || !online.verified) Text("Sign in, verify your email and refresh your account to join friends.",fontSize = 13.sp,color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { displayName = ""; title = ""; action = "create" },enabled = ready && online.circles.size < 3) { Text("Create circle") }
            OutlinedButton(onClick = { displayName = ""; code = ""; action = "join" },enabled = ready && online.circles.size < 3) { Text("Join with code") }
            TextButton(onClick = { model?.refreshCircles() },enabled = ready) { Text("Refresh circles") }
        }
        online.circles.forEach { circle ->
            HorizontalDivider()
            Text(circle.title,fontWeight = FontWeight.Bold,fontSize = 17.sp)
            val ended = System.currentTimeMillis() >= circle.endsAt
            Text("${if (ended) "Ended" else "Ends"} ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(circle.endsAt))}",fontSize = 12.sp,color = Muted)
            circle.members.forEach { member -> Text("${member.name}${if (member.uid == online.uid) " · you" else ""} — ${member.goalDays} balance days",fontSize = 14.sp) }
            Text("Scores count full days within the circle window and after you joined. A previously shared best score is kept. Final scores can be shared for 48 hours after the end.",fontSize = 12.sp,color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT,"Join my ScrollXP balance circle with code ${circle.code}. Anyone with this code and a verified account can join while space is available. Keep it private."),"Invite a friend")) }
                    catch (_: Exception) { shareError = "Invite code: ${circle.code}" }
                },enabled = !ended && ready) { Text("Share invite") }
                OutlinedButton(onClick = { code = circle.code; action = "score" },enabled = ready && System.currentTimeMillis() <= circle.endsAt + 2*24*60*60*1000L) { Text("Share my balance score") }
                TextButton(onClick = { code = circle.code; action = "leave" },enabled = ready) { Text("Leave circle") }
            }
            androidx.compose.foundation.text.selection.SelectionContainer { Text("Invite code: ${circle.code}",fontSize = 12.sp,color = Muted) }
        }
        shareError?.let { Text(it,fontSize = 12.sp,color = Muted) }
    }
    if (action != null) AlertDialog(onDismissRequest = { action = null },title = { Text(when(action) {
        "create" -> "Create a balance circle"; "join" -> "Join a friend's circle"; "score" -> "Share your balance score?"; else -> "Leave this circle?"
    }) },text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (action == "create" || action == "join") {
            OutlinedTextField(displayName,{ displayName = it.take(32) },label = { Text("Name shown to friends") },singleLine = true,modifier = Modifier.fillMaxWidth())
            if (action == "create") OutlinedTextField(title,{ title = it.take(32) },label = { Text("Circle name") },singleLine = true,modifier = Modifier.fillMaxWidth())
            else OutlinedTextField(code,{ code = it.take(40) },label = { Text("Invite code") },singleLine = true,modifier = Modifier.fillMaxWidth())
            Text("Members can see your display name, account identifier and best score. Anyone with the code and a verified account can join. Avoid using private information in names.",color = Muted,fontSize = 13.sp)
        } else Text(if (action == "score") "Upload your count of completed budget days in this circle. App names, usage minutes and daily dates remain on your phone. The best shared score cannot decrease." else "Your shared name and score will be removed. If you join again, scoring starts from your new join time.",color = Muted,fontSize = 13.sp)
    } },confirmButton = { TextButton(enabled = ready && when(action) {
        "create" -> displayName.isNotBlank() && title.isNotBlank(); "join" -> displayName.isNotBlank() && FriendScorePolicy.validCode(FriendScorePolicy.normalizeCode(code)); else -> true
    },onClick = {
        when(action) { "create" -> model?.createCircle(title,displayName); "join" -> model?.joinCircle(code,displayName); "score" -> model?.publishCircleScore(code); else -> model?.leaveCircle(code) }
        action = null
    }) { Text("Confirm") } },dismissButton = { TextButton(onClick = { action = null }) { Text("Cancel") } })
}
