package com.scrollxp.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollxp.app.data.packages
import com.scrollxp.app.domain.Progression
import com.scrollxp.app.sharing.IslandExport
import com.scrollxp.app.sharing.IslandSnapshot
import com.scrollxp.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun IslandShareDialog(state: ScrollState, dismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dusk = LocalDusk.current
    var progress by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFile by rememberSaveable { mutableStateOf<String?>(null) }
    val snapshot = IslandSnapshot(state.profile.islandName, state.xp, state.profile.roof,
        state.profile.hiddenItems.packages(), state.ownedTreasures, dusk, progress, state.world.id)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val path = pendingFile
        pendingFile = null
        if (uri != null && path != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output -> File(path).inputStream().use { it.copyTo(output) } }
                        ?: error("The destination is unavailable")
                }
                message = "Island image saved."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Couldn't save the image. Please choose a destination and try again." }
            finally { busy = false }
        }
    }
    fun export(share: Boolean) { scope.launch {
        busy = true; message = null
        try {
            val file = withContext(Dispatchers.IO) { IslandExport.create(context, snapshot) }
            if (share) context.startActivity(Intent.createChooser(IslandExport.shareIntent(context, file), "Share your island"))
            else { pendingFile = file.absolutePath; save.launch("ScrollXP-island.png") }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Couldn't prepare your island image. Please try again." }
        finally { busy = false }
    } }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("An island postcard") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.profile.islandName, color = Ink, fontSize = 20.sp)
            Island(snapshot.xp, snapshot.roof, modifier = Modifier.clip(RoundedCornerShape(16.dp)), hidden = snapshot.hidden, treasures = snapshot.treasures, region = snapshot.region)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Include level and XP", Modifier.weight(1f))
                Switch(checked = progress, onCheckedChange = { progress = it }, enabled = !busy)
            }
            if (progress) Text("Level ${Progression.level(state.xp).level} · ${state.xp} XP", color = Green)
            Text("A 1080 × 1350 PNG of your island and its name. Your selected apps and usage history stay private.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
            message?.let { Text(it, color = Green, fontSize = 13.sp) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { export(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save image") }
        } },
        confirmButton = { Button(onClick = { export(true) }, enabled = !busy) { Text("Share image") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Close") } })
}
