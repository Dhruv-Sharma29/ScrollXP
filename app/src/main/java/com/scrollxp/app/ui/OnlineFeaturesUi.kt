@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.scrollxp.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scrollxp.app.data.DayRecord
import com.scrollxp.app.data.ScrollDatabase
import com.scrollxp.app.domain.durationLabel
import com.scrollxp.app.online.*
import com.scrollxp.app.ui.theme.*
import com.scrollxp.app.widget.IslandWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.text.DateFormat
import java.util.Date

val LocalOnlineModel = staticCompositionLocalOf<OnlineViewModel?> { null }

@Composable
internal fun OnlineFeaturesEntry(state: ScrollState, refreshLocal: () -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    FeaturePanel("More ways to grow") {
        Text("Free widget, cloud backups and private friend circles. Optional Pro adds themes and longer local insights.",color = Muted,fontSize = 13.sp,lineHeight = 21.sp)
        OutlinedButton(onClick = { open = true }) { Text("Explore connected features") }
    }
    if (open) Dialog(onDismissRequest = { open = false },properties = DialogProperties(usePlatformDefaultWidth = false,decorFitsSystemWindows = false)) {
        Scaffold(containerColor = Paper,contentColor = Ink,topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp,vertical = 10.dp)) {
                Text("A little more ScrollXP",Modifier.weight(1f),fontWeight = FontWeight.Bold,fontSize = 21.sp)
                TextButton(onClick = { open = false }) { Text("Done") }
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 22.dp,vertical = 20.dp),verticalArrangement = Arrangement.spacedBy(20.dp)) {
                OnlineFeatures(state,refreshLocal)
            }
        }
    }
}

@Composable
internal fun FeaturePanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Card,shape = RoundedCornerShape(24.dp),border = BorderStroke(1.dp,Line),modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title,fontWeight = FontWeight.Bold,fontSize = 19.sp)
            content()
        }
    }
}

@Composable
internal fun OnlineFeatures(state: ScrollState, refreshLocal: () -> Unit) {
    val model = LocalOnlineModel.current
    val online = model?.state?.collectAsState()?.value ?: OnlineState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogin by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var widgetDetail by remember { mutableStateOf(context.getSharedPreferences("widget_preferences",Context.MODE_PRIVATE).getBoolean("detail",false)) }
    var history by remember { mutableStateOf<List<DayRecord>?>(null) }
    var historyWindow by remember { mutableIntStateOf(30) }
    val pro = LocalBillingModel.current?.state?.collectAsState()?.value ?: BillingState(configured = false)
    LaunchedEffect(pro.active) { if (!pro.active && historyWindow == 90) history = null }
    val ready = online.uid != null && online.verified && !online.busy && !online.deleting
    LaunchedEffect(online.message) { if (online.message?.startsWith("Island restored.") == true) refreshLocal() }
    online.message?.let { Text(it,color = Muted,fontSize = 13.sp,lineHeight = 20.sp) }
    FeaturePanel("Your account") {
        Text(online.email ?: "A local island, with optional cloud backup.",color = Muted)
        Text("Signing in doesn't upload or replace your island. This device keeps one local island, even when you switch accounts.",fontSize = 13.sp,color = Muted,lineHeight = 20.sp)
        if (!online.configured) Text("Online accounts aren't available in this build. Your local island works normally.",fontSize = 13.sp,color = Muted)
        else if (online.uid == null) Button(onClick = { showLogin = true }) { Text("Sign in or create account") }
        else {
            if (online.deleting) Text("Account deletion is pending. Your backup is removed; enter your password again to finish deleting the account.",fontSize = 13.sp,color = Muted)
            else if (!online.verified) {
                Text("Verify your email before using cloud backups.",fontSize = 13.sp,color = Muted)
                TextButton(onClick = { model?.resendVerification() },enabled = !online.busy) { Text("Resend verification email") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model?.refresh() },enabled = !online.busy) { Text("Refresh account") }
                TextButton(onClick = { model?.signOut() },enabled = !online.busy) { Text("Sign out") }
                TextButton(onClick = { password = ""; action = "account" },enabled = !online.busy) { Text("Delete online account",color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    FeaturePanel("Cloud island backup") {
        Text("Save or restore the same island on Android and iPhone, including XP, reward dates, and chest contents. App selections, daily usage totals, and reminder settings stay on the phone.",fontSize = 13.sp,color = Muted,lineHeight = 21.sp)
        Text("Backups are manual. Each account keeps one latest backup; saving replaces it. Restoring replaces this device's game and clears local usage history.",fontSize = 13.sp,color = Muted,lineHeight = 21.sp)
        online.backupTime?.let { Text("${online.backupName.orEmpty()} · ${online.backupXp ?: 0} XP\nSaved ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(it))}",fontSize = 13.sp) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { action = "backup" },enabled = ready && state.profile.onboarded) { Text("Back up now") }
            OutlinedButton(onClick = { action = "restore" },enabled = ready && online.backupTime != null) { Text("Restore island") }
            TextButton(onClick = { action = "deleteBackup" },enabled = ready && online.backupTime != null) { Text("Delete cloud backup") }
        }
    }
    FeaturePanel("Your recent balance") {
        Text("Look back over your last 30 calendar days. This history stays on your device and works without an account.",color = Muted,fontSize = 13.sp,lineHeight = 21.sp)
        TextButton(onClick = {
            scope.launch {
                val records = withContext(Dispatchers.IO) { ScrollDatabase.get(context).dao().recentHistory() }
                history = records.filter { runCatching { LocalDate.parse(it.date) in state.date.minusDays(29)..state.date }.getOrDefault(false) }
                historyWindow = 30
            }
        }) { Text("View 30-day history") }
    }
    ProSubscriptionPanel {
        scope.launch {
            val records = withContext(Dispatchers.IO) { ScrollDatabase.get(context).dao().extendedHistory() }
            historyWindow = 90
            history = records.filter { runCatching { LocalDate.parse(it.date) in state.date.minusDays(89)..state.date }.getOrDefault(false) }
        }
    }
    FriendCirclesPanel(online, model)
    FeaturePanel("Home-screen island widget") {
        Text("Keep a small view of your island on your home screen. Tap it to open ScrollXP. Updates follow app refreshes and Android's background schedule.",fontSize = 13.sp,color = Muted,lineHeight = 21.sp)
        val manager = AppWidgetManager.getInstance(context)
        Button(onClick = { manager.requestPinAppWidget(ComponentName(context,IslandWidget::class.java),null,null) },enabled = manager.isRequestPinAppWidgetSupported) { Text("Add island widget") }
        Text("You can also long-press your home screen, choose Widgets, and find ScrollXP.",fontSize = 12.sp,color = Muted)
        Row {
            Column(Modifier.weight(1f)) { Text("Weekly balance detail",fontWeight = FontWeight.Bold); Text("Shows goal progress without app names or usage minutes.",fontSize = 12.sp,color = Muted) }
            Switch(checked = widgetDetail,onCheckedChange = {
                widgetDetail = it
                context.getSharedPreferences("widget_preferences",Context.MODE_PRIVATE).edit().putBoolean("detail",it).apply()
                scope.launch { IslandWidget.refresh(context) }
            })
        }
    }
    if (showLogin) AlertDialog(onDismissRequest = { showLogin = false },title = { Text("Your account") },
        text = { AccountForm(online,model,finished = { showLogin = false }) },confirmButton = { TextButton(onClick = { showLogin = false }) { Text("Close") } })
    if (action != null) AlertDialog(onDismissRequest = { action = null; password = "" },title = { Text(when(action) { "restore" -> "Replace this island?"; "backup" -> "Replace cloud backup?"; "account" -> "Delete online account?"; else -> "Delete cloud backup?" }) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(when(action) {
                "backup" -> "Save this device's island to ${online.email}. Any existing backup on that account will be replaced. No daily usage totals or selected-app names are uploaded."
                "restore" -> "Restore the latest backup from ${online.email}. This replaces local XP, rewards, and preferences and clears usage history. It cannot be undone. App selections stay on this phone; new full-day goals need tracking coverage again."
                "account" -> "Remove your shared friend names and scores, cloud backup and Firebase login. Your local island stays here. A minimal deletion guard remains to block old sessions. If interrupted, repeat to finish. Google Play subscriptions must be cancelled separately in Play. Enter your current password to confirm."
                else -> "Delete the backup from ${online.email}. This leaves the local island unchanged."
            })
            if (action == "account") OutlinedTextField(password,{ password = it },label = { Text("Current password") },singleLine = true,visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password))
        } },confirmButton = { TextButton(enabled = !online.busy && (action != "account" || password.isNotBlank()),onClick = {
            when(action) { "backup" -> model?.backup(); "restore" -> model?.restore(); "account" -> model?.deleteAccount(password); else -> model?.deleteBackup() }
            action = null; password = ""
        }) { Text("Confirm") } },dismissButton = { TextButton(onClick = { action = null; password = "" }) { Text("Cancel") } })
    history?.let { days -> AlertDialog(onDismissRequest = { history = null },title = { Text("Last $historyWindow calendar days") },text = {
        LazyColumn(Modifier.heightIn(max = 440.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (historyWindow == 90) item {
                val completed = days.count { it.goalStatus == "SUCCESS" }
                Text("$completed completed budgets · ${days.size} recorded days\nOnly existing local records are shown. Restoring an island clears this history.",fontSize = 13.sp)
            }
            if (days.isEmpty()) item { Text("No tracked days in this window yet.") }
            items(days,key = { it.date }) { Text("${it.date} · ${durationLabel(it.usageMillis)}\nBudget: ${it.goalStatus.lowercase()}",fontSize = 14.sp) }
        }
    },confirmButton = { TextButton(onClick = { history = null }) { Text("Close") } }) }
}

@Composable
internal fun AccountForm(online: OnlineState, model: OnlineViewModel?, finished: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    LaunchedEffect(online.uid) { if (online.uid != null) { password = ""; finished() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("An account is optional. Island backups upload only when you choose Back up now; friend scores have a separate sharing action. No subscription is needed for either.",color = Muted,fontSize = 13.sp,lineHeight = 20.sp)
        OutlinedTextField(email,{ email = it.take(254) },label = { Text("Email address") },singleLine = true,enabled = !online.busy,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password,{ password = it },label = { Text("Password") },singleLine = true,enabled = !online.busy,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier = Modifier.fillMaxWidth())
        if (create) Text("Choose a password with at least 8 characters.",color = Muted,fontSize = 12.sp)
        Button(onClick = { model?.signIn(email,password,create); password = "" },enabled = online.configured && !online.busy && email.contains('@') && password.isNotEmpty() && (!create || password.length >= 8)) {
            Text(if (online.busy) "Connecting…" else if (create) "Create account" else "Sign in")
        }
        TextButton(onClick = { create = !create; password = "" },enabled = !online.busy) { Text(if (create) "Already have an account? Sign in" else "New here? Create account") }
        if (!create) TextButton(onClick = { model?.resetPassword(email) },enabled = online.configured && !online.busy && email.contains('@')) { Text("Forgot password?") }
        online.message?.let { Text(it,color = Muted,fontSize = 13.sp) }
        PrivacyPolicyLink()
    }
}
