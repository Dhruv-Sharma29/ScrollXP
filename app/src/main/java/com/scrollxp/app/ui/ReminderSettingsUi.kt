@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scrollxp.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.scrollxp.app.domain.ReminderSettings
import com.scrollxp.app.data.ReminderStore
import com.scrollxp.app.ui.theme.*
import com.scrollxp.app.worker.ReminderScheduler
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private fun clock(minute: Int) = String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

@Composable
internal fun ReminderSettingsCard(value: ReminderSettings, save: (ReminderSettings) -> Unit) {
    val context = LocalContext.current
    val dusk = LocalDusk.current
    val lifecycle = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(value)
    val latestSave by rememberUpdatedState(save)
    var allowed by remember { mutableStateOf(ReminderScheduler.allowed(context)) }
    var warning by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted && ReminderScheduler.allowed(context)
        if (allowed) { latestSave(latest.copy(enabled = true)); warning = null }
        else warning = "Notifications are off. You can enable them in Android settings."
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            allowed = ReminderScheduler.allowed(context)
            now = Instant.now()
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(value, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = Instant.now(); delay(60_000) }
        }
    }
    fun androidSettings() {
        try { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
        catch (_: Exception) { warning = "Open Android Settings → Apps → ScrollXP → Notifications." }
    }
    fun chooseTime(minute: Int, update: (Int) -> ReminderSettings) {
        val pickerTheme = if (dusk) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
        TimePickerDialog(context, pickerTheme, { _, hour, minutes ->
            val changed = update(hour * 60 + minutes)
            if (changed.quietEnabled && changed.quietStart == changed.quietEnd) warning = "Choose different quiet-hour start and end times."
            else { save(changed); warning = null }
        }, minute / 60, minute % 60, true).show()
    }
    AppCard {
        Label("A QUIET CHECK-IN")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily island reminder", fontWeight = FontWeight.Bold)
                Text("An invitation, never a streak warning", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = value.enabled, modifier = Modifier.semantics { contentDescription = "Daily island reminder" }, onCheckedChange = { enabled ->
                if (!enabled) save(value.copy(enabled = false))
                else if (allowed) save(value.copy(enabled = true))
                else if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else { save(value.copy(enabled = true)); androidSettings() }
            })
        }
        Text("At most one silent reminder a day. No usage details in notifications. Tracking pauses also pause delivery.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        if (value.enabled) {
            OutlinedButton(onClick = { chooseTime(value.minute) { latest.copy(minute = it) } }, modifier = Modifier.fillMaxWidth()) {
                Text("Reminder time · ${clock(value.minute)}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quiet hours", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = value.quietEnabled, onCheckedChange = { save(value.copy(quietEnabled = it)) },
                    modifier = Modifier.semantics { contentDescription = "Quiet hours" })
            }
            if (value.quietEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { chooseTime(value.quietStart) { latest.copy(quietStart = it) } }) { Text("From ${clock(value.quietStart)}") }
                    OutlinedButton(onClick = { chooseTime(value.quietEnd) { latest.copy(quietEnd = it) } }) { Text("Until ${clock(value.quietEnd)}") }
                }
            }
            val next = ReminderStore(context).next(now, ZoneId.systemDefault())
            Text("${next?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("'Next check-in: 'EEE, HH:mm")) ?: "No check-in scheduled"}\nTimes follow your phone. Quiet-hour reminders move to the end of quiet hours. Android may delay delivery.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        }
        if (warning != null || value.enabled && !allowed) {
            Text(warning ?: "Android notifications are blocked. Your reminder cannot be delivered.", color = Accent, fontSize = 12.sp)
            TextButton(onClick = ::androidSettings) { Text("Manage Android notifications") }
        }
    }
}
