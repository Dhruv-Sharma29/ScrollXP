@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scrollxp.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollxp.app.data.*
import com.scrollxp.app.domain.*
import com.scrollxp.app.ui.theme.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ScrollApp(state: ScrollState, model: ScrollViewModel, openAccess: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Home") }
    var chestKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showAccess by rememberSaveable { mutableStateOf(false) }
    val accessAction = { showAccess = true }
    val snackbar = remember { SnackbarHostState() }
    var lastXp by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.xp, state.loading) {
        if (!state.loading) {
            val previous = lastXp
            lastXp = state.xp
            if (previous != null && state.xp > previous) snackbar.showSnackbar("+${state.xp - previous} XP saved. Your world is growing.")
        }
    }
    LaunchedEffect(state.profile.onboarded) { if (!state.profile.onboarded) tab = "Home" }
    if (showAccess) AlertDialog(onDismissRequest = { showAccess = false }, title = { Text("Connect your screen time") },
        text = { Text("""ScrollXP reads when selected apps are in use. It cannot see messages, watched videos, or scroll gestures. Usage history and your world stay on this device.

On the next screen, choose ScrollXP and allow Usage Access. You can revoke it anytime in Android Settings.""") },
        confirmButton = { TextButton(onClick = { showAccess = false; openAccess() }) { Text("Open Android settings") } },
        dismissButton = { TextButton(onClick = { showAccess = false }) { Text("Later") } })
    if (state.loading && state.status == TrackingStatus.LOADING) {
        Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (!state.profile.onboarded) {
        Welcome(state, model::configure)
        return
    }
    state.chests.firstOrNull { it.key == chestKey }?.let { chest ->
        ChestDialog(chest, state, model) { chestKey = null }
    }
    BackHandler(tab != "Home" && chestKey == null) { tab = "Home" }
    Scaffold(containerColor = Paper, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("scroll", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Box(Modifier.padding(start = 3.dp).background(Lime, RoundedCornerShape(9.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text("XP", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { tab = if (tab == "Settings") "Home" else "Settings" },
                modifier = Modifier.semantics { contentDescription = if (tab == "Settings") "Back to Home" else "Settings" }) {
                WorldGlyph(if (tab == "Settings") "Home" else "Settings", color = Muted)
            }
        }
    }, bottomBar = {
        NavigationBar(containerColor = Card, tonalElevation = 0.dp) {
            listOf("Home", "World", "Goals", "Stats").forEach { name ->
                NavigationBarItem(selected = tab == name, onClick = { tab = name },
                    icon = { WorldGlyph(name, color = if (tab == name) Ink else Muted) },
                    label = { Text(name, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Lime, selectedIconColor = Ink,
                        selectedTextColor = Ink, unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Crossfade(targetState = tab, animationSpec = if (LocalReducedMotion.current) snap() else tween(220), label = "Page") { page ->
              key(page) { Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).padding(top = 14.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                state.message?.let { Notice(it) }
                when (page) {
                    "Home" -> HomePage(state, model::refresh, accessAction, { tab = "World" }, { tab = "Goals" }, { chestKey = it }, { model.guideDismissed(true) })
                    "World" -> WorldPage(state, model) { chestKey = it }
                    "Goals" -> GoalsPage(state, { tab = "Settings" })
                    "Stats" -> StatsPage(state)
                    "Settings" -> SettingsPage(state, model, accessAction)
                }
              } }
            }
        }
    }
}

@Composable
internal fun Welcome(state: ScrollState, configure: (String, Set<String>, Int) -> Unit) {
    var setup by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(setup) { scroll.scrollTo(0) }
    BackHandler(setup) { setup = false }
    Column(Modifier.fillMaxSize().background(Paper).safeDrawingPadding().verticalScroll(scroll)
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("scrollXP", fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, letterSpacing = (-1).sp)
            Spacer(Modifier.weight(1f)); Pill("ANDROID BETA")
        }
        if (!setup) {
            Spacer(Modifier.height(8.dp))
            Text("A little scrolling.\nA world of progress.", fontFamily = FontFamily.Serif,
                fontSize = 38.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold)
            Text("Make something of your screen time. Build a cozy island, find your balance, and watch your world grow.", color = Muted, lineHeight = 24.sp)
            WelcomeWorldPreview()
            Label("YOUR FIRST HOME IS ON US")
            Text("Earn capped XP from app usage. Complete a daily screen-time budget for a bigger bonus. Your island keeps everything you earn.", color = Muted, lineHeight = 23.sp)
            Text("Your welcome chest is free. Open it, place a treasure, then follow an optional four-step guide. There is no daily check-in requirement.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            Button(onClick = { setup = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Create my island", fontWeight = FontWeight.Bold)
            }
            Text("Private by default · No account needed", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 12.sp, color = Muted)
        } else {
            PageTitle("Make it yours", "A name, a few apps, and a little intention.")
            SetupForm(state, "Start my world", configure)
            TextButton(onClick = { setup = false }) { Text("Back to preview") }
        }
        PrivacyPolicyLink()
    }
}

@Composable
private fun HomePage(state: ScrollState, refresh: () -> Unit, access: () -> Unit, world: () -> Unit, goals: () -> Unit, openChest: (String) -> Unit, hideGuide: () -> Unit) {
    val level = Progression.level(state.xp)
    val today = state.today
    val selected = state.profile.selectedApps.packages()
    val connected = state.hasAccess && state.status !in listOf(TrackingStatus.PAUSED, TrackingStatus.EMPTY_HISTORY, TrackingStatus.ERROR)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Label(LocalDate.now(java.time.ZoneId.of(state.profile.zone.ifBlank { java.time.ZoneId.systemDefault().id }))
                .format(DateTimeFormatter.ofPattern("EEEE, MMM d")).uppercase())
            Spacer(Modifier.height(8.dp))
            Text("Your little escape.", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp)
            Text("A little balance. A little growth.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
    TreasureCard(state, openChest)
    if (state.status == TrackingStatus.ACCESS_REQUIRED) AccessCard(access)
    Surface(shape = RoundedCornerShape(28.dp), color = Sky, border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Visit your island", onClick = world)) {
        Column {
            Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 19.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Label("YOUR ISLAND"); Text(state.profile.islandName, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(top = 4.dp)) }
                Pill("LVL ${level.level}", Card.copy(alpha = .8f))
                Spacer(Modifier.width(8.dp)); WorldGlyph("chevron", color = Muted)
            }
            Island(state.xp, state.profile.roof, hidden = state.profile.hiddenItems.packages(), treasures = state.ownedTreasures, region = state.world.id)
            Column(Modifier.fillMaxWidth().background(Card.copy(alpha = .78f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.xp} XP", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Text("${level.required - level.earned} to level ${level.level + 1}", color = Muted, fontSize = 12.sp)
                }
                GentleProgress(level.fraction)
            }
        }
    }
    FirstChapterCard(state, access, world, goals, openChest, hideGuide)
    NextUnlock(state, world)
    GoalCard(state, goals)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("Your screen time", Modifier.weight(1f))
        TextButton(onClick = refresh, enabled = !state.loading) { Text(if (state.loading) "Refreshing…" else "Refresh", fontSize = 12.sp) }
    }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (today?.measuredAt != null && connected) durationLabel(today.usageMillis) else "—", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Text("today · selected apps", fontSize = 12.sp, color = Muted)
            }
            Pill("+${Progression.usageXp(today?.usageMillis ?: 0)} XP", Lime)
        }
        HorizontalDivider(color = Line)
        val breakdown = runCatching { JSONObject(today?.perAppJson ?: "{}") }.getOrDefault(JSONObject())
        selected.forEach { pkg ->
            AppUsageRow(state.installedApps.firstOrNull { it.packageName == pkg }?.name ?: pkg.substringAfterLast('.'),
                if (today?.measuredAt != null && connected) durationLabel(breakdown.optLong(pkg)) else "—", pkg)
        }
        Text("2 XP / min · 120 usage XP daily cap", color = Muted, fontSize = 12.sp)
        if (today != null) Text("Updated ${java.time.Instant.ofEpochMilli(today.measuredAt).atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))} · app activity estimate", color = Muted, fontSize = 11.sp)
    }
    when (state.status) {
        TrackingStatus.PAUSED -> Notice("Tracking is paused. Resume in Settings whenever you're ready.")
        TrackingStatus.EMPTY_HISTORY -> Notice("Waiting for Android usage history. Use a selected app and refresh. Missing data cannot complete a goal.")
        TrackingStatus.PARTIAL -> Notice("Today's history is partial. Usage XP still counts; the budget bonus needs a full tracked day.")
        TrackingStatus.ERROR -> Notice("Couldn't refresh tracking. These totals may be out of date.")
        else -> Unit
    }
    if (state.profile.personality != "Silent") {
        Text(if (state.profile.personality == "Sarcastic") "“The village is thriving. Your open tabs remain a mystery.”" else "“A small step is still a step. Your island will be here tomorrow.”",
            color = Muted, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
    }
}

@Composable
private fun NextUnlock(state: ScrollState, click: () -> Unit) {
    val xp = state.xp
    val nextItem = Progression.next(xp)
    val nextRegion = if (nextItem == null) Worlds.next(xp) else null
    val next = nextItem ?: nextRegion?.let { Milestone(it.id, it.name, it.description, it.xp) }
    val complete = state.ownedIds.size == Progression.milestones.size + Treasures.all.size
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = click)) {
            if (nextRegion != null) WorldGlyph("World", Modifier.size(44.dp), Green) else ItemBadge(next?.id ?: "dragon", true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(if (nextRegion != null) "NEXT DESTINATION" else if (next != null) "GROWING NEXT" else if (complete) "COLLECTION COMPLETE" else "KEEP EXPLORING")
                Text(next?.name ?: if (complete) "A world of your own" else "More treasures await", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(if (next != null) "${next.xp - xp} XP until it's yours" else if (complete) "Make it yours in the World tab" else "Discover weekly balance chests", color = Muted, fontSize = 12.sp)
            }
            WorldGlyph("chevron", color = Muted)
        }
        if (next != null) {
            val previous = if (nextRegion != null) maxOf(2_500, Worlds.all.lastOrNull { it.xp < next.xp }?.xp ?: 0)
                else Progression.milestones.lastOrNull { it.xp < next.xp }?.xp ?: 0
            GentleProgress((xp - previous).toFloat() / (next.xp - previous))
        }
    }
}

private data class CollectionItem(val id: String, val name: String, val description: String, val xp: Int? = null, val rarity: String? = null)

@Composable
private fun WorldPage(state: ScrollState, model: ScrollViewModel, openChest: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var preview by remember { mutableStateOf<CollectionItem?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var share by rememberSaveable { mutableStateOf(false) }
    val collection = Progression.milestones.map { CollectionItem(it.id, it.name, it.description, it.xp) } +
        Treasures.all.map { CollectionItem(it.id, it.name, it.description, rarity = it.rarity.label) }
    PageTitle(state.profile.islandName, "${state.world.name} · A tiny place to make your own.")
    if (share) IslandShareDialog(state) { share = false }
    TreasureCard(state, openChest)
    Surface(shape = RoundedCornerShape(28.dp), color = Sky, border = BorderStroke(1.dp, Line)) {
        Column {
            Island(state.xp, state.profile.roof, hidden = state.profile.hiddenItems.packages(), treasures = state.ownedTreasures, region = state.world.id)
            Row(Modifier.fillMaxWidth().background(Card.copy(alpha = .75f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("LEVEL ${Progression.level(state.xp).level}", Lime)
                Spacer(Modifier.weight(1f)); Text("${state.xp} lifetime XP", color = Muted, fontSize = 12.sp)
            }
        }
    }
    OutlinedButton(onClick = { share = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Share my island")
    }
    WorldRegions(state, model::visitRegion)
    AppCard {
        Label("YOUR COTTAGE, YOUR COLORS")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Meadow", "Clay", "Lavender").forEachIndexed { index, name ->
                FilterChip(selected = state.profile.roof == index, onClick = {
                    if (index != state.profile.roof) {
                        if (state.profile.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        model.roof(index); feedback = "$name roof selected"
                    }
                }, label = { Text(name, fontSize = 12.sp) }, leadingIcon = {
                    Box(Modifier.size(12.dp).background(listOf(Color(0xFF648F72), Color(0xFFB57559), Color(0xFF8792B3))[index], CircleShape))
                })
            }
        }
        feedback?.let { Text(it, color = Green, fontSize = 12.sp) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("Little treasures", Modifier.weight(1f))
        Pill("${state.ownedIds.size} / ${collection.size}")
    }
    Text("Keep what you love on your island. Tap a future treasure for a peek.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Owned", "To discover").forEach { value ->
            FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value, fontSize = 12.sp) })
        }
    }
    val visibleItems = collection.filter { when (filter) { "Owned" -> it.id in state.ownedIds; "To discover" -> it.id !in state.ownedIds; else -> true } }
    if (visibleItems.isEmpty()) Notice("You've discovered every treasure. Your collection is complete.")
    val columns = if (LocalDensity.current.fontScale > 1.2f) 1 else 2
    visibleItems.chunked(columns).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            pair.forEach { item ->
                val owned = item.id in state.ownedIds
                val placed = item.id !in state.profile.hiddenItems.packages()
                Surface(shape = RoundedCornerShape(24.dp), color = Card, border = BorderStroke(1.dp, Line), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ItemBadge(item.id, owned)
                            Spacer(Modifier.weight(1f))
                            if (owned) WorldGlyph("check", Modifier.size(18.dp), Green)
                        }
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (owned) if (placed) "On your island" else "Yours to keep" else item.xp?.let { "${it - state.xp} XP to unlock" } ?: "${item.rarity} · In chests", color = Muted, fontSize = 12.sp)
                        OutlinedButton(onClick = {
                            if (owned) {
                                if (state.profile.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                model.toggleItem(item.id)
                                feedback = if (placed) "${item.name} tucked away" else "${item.name} placed on your island"
                            } else preview = item
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                            contentDescription = if (!owned) "Preview ${item.name}" else if (placed) "Hide ${item.name}" else "Place ${item.name}"
                        }, contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(14.dp)) {
                            Text(if (owned) if (placed) "Tuck away" else "Place" else "Preview", fontSize = 12.sp)
                        }
                    }
                }
            }
            if (pair.size == 1 && columns == 2) Spacer(Modifier.weight(1f))
        }
    }
    Text("Your XP is never spent. Everything you unlock stays yours.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
    TreasureHistory(state, openChest)
    preview?.let { item ->
        AlertDialog(onDismissRequest = { preview = null }, icon = { WorldGlyph(item.id, Modifier.size(72.dp), Green) },
            title = { Text(item.name, fontFamily = FontFamily.Serif) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(item.description)
                Text(item.xp?.let { "Unlocks at $it lifetime XP. ${it - state.xp} XP to go." }
                    ?: "${item.rarity} treasure. Find it in a milestone or weekly balance chest.", color = Muted)
                Text("Earn progress through capped usage XP and full-day balance goals. There's no rush.", color = Muted)
            } }, confirmButton = { TextButton(onClick = { preview = null }) { Text("Back to my island") } })
    }
}

@Composable
private fun GoalCard(state: ScrollState, click: (() -> Unit)? = null) {
    val day = state.today
    val budget = day?.budgetMinutes ?: state.profile.budgetMinutes
    val used = day?.usageMillis ?: 0
    Surface(shape = RoundedCornerShape(26.dp), color = Lime, modifier = Modifier.fillMaxWidth().then(if (click != null) Modifier.clickable(role = Role.Button, onClick = click) else Modifier)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorldGlyph("Goals", color = Green)
                Spacer(Modifier.width(10.dp))
                Text("Room for real life", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 17.sp)
                Pill("+100 XP", Card.copy(alpha = .65f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$budget min", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text("your daily app budget", color = Muted, fontSize = 12.sp)
                }
                WorldGlyph("garden", Modifier.size(42.dp), Green)
            }
            GentleProgress(used.toFloat() / (budget * 60_000f))
            Text(when {
                !state.hasAccess || state.profile.paused || day == null -> "A full tracked day within budget earns the bonus."
                day.partial -> "Today's history is partial · bonus needs a full day"
                used > budget * 60_000L -> "A fresh start tomorrow. Your island keeps its progress."
                else -> "Within budget so far · bonus after the day ends"
            }, color = Ink, fontSize = 12.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun GoalsPage(state: ScrollState, settings: () -> Unit) {
    PageTitle("Find your balance", "Small goals. A growing world.")
    GoalCard(state)
    WeeklyChallenge(state)
    AppCard {
        Label("HOW IT WORKS")
        Text("Finish a full tracked day within your budget to earn 100 bonus XP—even if you don't use your selected apps at all.", color = Ink, lineHeight = 24.sp)
        Text("Setup days, pauses, and uncertain history cannot earn a budget bonus. Results are evaluated on the next successful refresh after the day ends.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        Text("Day boundaries use ${state.profile.zone}. Tracking is an estimate; split-screen and picture-in-picture may differ from Android's screen-time report.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        OutlinedButton(onClick = settings, modifier = Modifier.fillMaxWidth()) { Text("Adjust tomorrow's budget") }
    }
    state.profile.pendingBudget?.let { Notice("Tomorrow's budget: $it minutes. Today's challenge stays unchanged.") }
    SectionTitle("Recent goals")
    val completed = state.days.filter { it.goalStatus != "PENDING" }
    if (completed.isEmpty()) AppCard { Text("Your story is just starting", fontWeight = FontWeight.Bold); Text("Completed daily goals will appear here. No pressure to do more scrolling.", color = Muted, fontSize = 13.sp) }
    completed.forEach { day ->
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(formatDay(day.date), fontWeight = FontWeight.Bold); Text("${durationLabel(day.usageMillis)} / ${day.budgetMinutes} min budget", fontSize = 12.sp, color = Muted) }
                Pill(when (day.goalStatus) { "SUCCESS" -> "+100 XP"; "OVER_BUDGET" -> "Fresh start"; else -> "Partial data" },
                    if (day.goalStatus == "SUCCESS") Lime else Mist)
            }
        }
    }
}

@Composable
private fun StatsPage(state: ScrollState) {
    PageTitle("The bigger picture", "Your recent app activity, at a glance.")
    val days = state.days.reversed()
    AppCard {
        Label("LAST 7 RECORDED DAYS")
        Text(durationLabel(days.sumOf { it.usageMillis }), fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
        Text("Measured selected-app activity", fontSize = 12.sp, color = Muted)
        if (days.isEmpty()) Notice("Connect Usage Access to start your history. Empty days are not treated as zero usage.")
        else {
            val maximum = maxOf(days.maxOf { it.usageMillis }, 60_000L)
            Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${day.usageMillis / 60_000}m", fontSize = 10.sp, color = Muted)
                        Box(Modifier.fillMaxWidth().height((day.usageMillis.toFloat() / maximum * 100).coerceAtLeast(4f).dp)
                            .background(if (day.partial) Green.copy(alpha = .5f) else Green, RoundedCornerShape(5.dp)))
                        Text(LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EE")), fontSize = 10.sp, color = Muted)
                    }
                }
            }
            Text("Lighter bars indicate partial history. Missing days are omitted.", color = Muted, fontSize = 11.sp)
        }
    }
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatNumber("${state.xp}", "Lifetime XP")
            StatNumber("${state.days.count { it.goalStatus == "SUCCESS" }}", "Recent goals met")
            StatNumber("${state.ownedIds.size}", "Items unlocked")
        }
    }
    SectionTitle("Daily breakdown")
    days.reversed().forEach { day ->
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDay(day.date), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(durationLabel(day.usageMillis), fontWeight = FontWeight.Bold, color = Green)
            }
            val breakdown = runCatching { JSONObject(day.perAppJson) }.getOrDefault(JSONObject())
            day.selectedApps.packages().forEach { pkg ->
                AppUsageRow(state.installedApps.firstOrNull { it.packageName == pkg }?.name ?: pkg.substringAfterLast('.'), durationLabel(breakdown.optLong(pkg)), pkg)
            }
            if (day.partial) Text("Partial history · budget bonus unavailable", color = Muted, fontSize = 11.sp)
        }
    }
    Text("Your usage history stays on this device. Foreground app activity doesn't identify watched content or individual scrolls.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
}

@Composable
private fun SettingsPage(state: ScrollState, model: ScrollViewModel, access: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    PageTitle("Your preferences", "Make the experience work for you.")
    OnlineFeaturesEntry(state,model::refresh)
    if (state.profile.guideDismissed) AppCard {
        Text("Getting started, at your pace", fontWeight = FontWeight.Bold)
        Text("Bring back the guide to Usage Access, your welcome treasure, and the first full-day goal.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        TextButton(onClick = { model.guideDismissed(false) }) { Text("Show getting-started guide") }
    }
    AppCard {
        Label("VISUAL COMFORT")
        Text("Choose your light", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Warm daylight or a softer dusk palette. System follows your phone's theme.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Daylight", "Dusk", "System").forEach { value ->
                FilterChip(selected = state.profile.appearance == value, onClick = { model.appearance(value) }, label = { Text(value, fontSize = 12.sp) })
            }
        }
        HorizontalDivider(color = Line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reduce motion", fontWeight = FontWeight.Bold)
                Text("Instant page and progress changes", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = state.profile.reducedMotion, onCheckedChange = model::reducedMotion,
                modifier = Modifier.semantics { contentDescription = "Reduce motion" })
        }
    }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gentle haptics", fontWeight = FontWeight.Bold)
                Text("Feedback for placement and discoveries", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = state.profile.haptics, onCheckedChange = model::haptics,
                modifier = Modifier.semantics { contentDescription = "Gentle haptics" })
        }
    }
    if (!state.hasAccess) AccessCard(access)
    ReminderSettingsCard(state.reminders, model::reminders)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Pause tracking", fontWeight = FontWeight.Bold); Text("Affected days won't earn goal bonuses", color = Muted, fontSize = 12.sp) }
            Switch(checked = state.profile.paused, onCheckedChange = model::pause)
        }
    }
    AppCard {
        Text("A little personality", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Gentle", "Sarcastic", "Silent").forEach { value ->
                FilterChip(selected = state.profile.personality == value, onClick = { model.personality(value) }, label = { Text(value, fontSize = 12.sp) })
            }
        }
    }
    SectionTitle("Island and tracking")
    SetupForm(state, "Save preferences", model::configure)
    if (state.profile.pendingAt > 0) Notice("App and budget changes are saved for the next day. Your island name updates immediately.")
    AppCard {
        Label("PRIVATE BY DEFAULT")
          Text("App names and daily usage totals stay on this phone. Optional backups upload your game and dated XP rewards only when you choose Back up now. Friend circles share chosen names and confirmed balance counts. Android's automatic backups remain disabled.", fontSize = 13.sp, color = Muted, lineHeight = 21.sp)
        PrivacyPolicyLink()
        TextButton(onClick = access) { Text("Manage Usage Access") }
        HorizontalDivider(color = Line)
        TextButton(onClick = { confirmReset = true }) { Text("Delete data and reset island", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Start over?") },
        text = { Text("This deletes your local usage history, XP, island customization, and preferences. It can't be undone. Cloud data and friend circles have separate deletion controls. Google Play subscriptions must be cancelled in Play. Android Usage Access is managed separately in system settings.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; model.reset() }) { Text("Delete and reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Keep my world") } })
}

@Composable
private fun SetupForm(state: ScrollState, button: String, save: (String, Set<String>, Int) -> Unit) {
    val profile = state.profile
    var name by rememberSaveable(profile.pendingAt) { mutableStateOf(profile.islandName) }
    var selection by rememberSaveable(profile.pendingAt) { mutableStateOf(profile.pendingApps ?: profile.selectedApps) }
    var budget by rememberSaveable(profile.pendingAt) { mutableIntStateOf(profile.pendingBudget ?: profile.budgetMinutes) }
    var search by rememberSaveable { mutableStateOf("") }
    val selected = selection.packages()
    OutlinedTextField(value = name, onValueChange = { name = it.take(28) }, label = { Text("Island name") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
    Label("CHOOSE UP TO 3 INSTALLED APPS")
    OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Find an app…") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
    val apps = state.installedApps.filter { it.name.contains(search, ignoreCase = true) }
    Surface(shape = RoundedCornerShape(18.dp), color = Card, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 270.dp).verticalScroll(rememberScrollState())) {
            if (apps.isEmpty()) Text("No matching installed apps. Try another search.", modifier = Modifier.padding(18.dp), color = Muted)
            apps.forEach { app ->
                val checked = app.packageName in selected
                Row(Modifier.fillMaxWidth().clickable(enabled = checked || selected.size < 3) {
                    selection = (if (checked) selected - app.packageName else selected + app.packageName).encoded()
                }.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppAvatar(app.name, app.packageName)
                    Spacer(Modifier.width(12.dp))
                    Text(app.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    Checkbox(checked = checked, enabled = checked || selected.size < 3, onCheckedChange = {
                        selection = (if (it) selected + app.packageName else selected - app.packageName).encoded()
                    }, modifier = Modifier.semantics { contentDescription = "Track ${app.name}" })
                }
            }
        }
    }
    Text("${selected.size} of 3 selected · measures whole-app usage", color = Muted, fontSize = 12.sp)
    Label("YOUR DAILY SCREEN-TIME BUDGET")
    Text("$budget minutes", fontWeight = FontWeight.Bold, fontSize = 24.sp)
    Slider(value = budget.toFloat(), onValueChange = { budget = it.roundToInt() }, valueRange = 15f..180f, steps = 10,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Daily screen-time budget" })
    Text(if (profile.onboarded) "App and budget changes begin tomorrow. This keeps today's challenge consistent." else
        "Your first full-day challenge starts tomorrow after tracking is connected. You can still earn usage XP today.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
    Button(onClick = { save(name, selected, budget) }, enabled = selected.isNotEmpty() && name.isNotBlank(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) { Text(button, fontWeight = FontWeight.Bold) }
}

@Composable
private fun AccessCard(access: () -> Unit) {
    AppCard {
        Label("CONNECT SCREEN TIME")
        Text("Your island is ready. Let's track your apps.", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Allow Usage Access to measure selected apps. We don't read your messages or watched content.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        Button(onClick = access, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Connect Usage Access") }
    }
}
@Composable
internal fun AppCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Card, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
private fun Notice(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Mist, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(14.dp), fontSize = 12.sp, lineHeight = 19.sp, color = Muted)
    }
}
@Composable
internal fun Label(text: String) { Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Muted) }
@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) { Text(text, modifier, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 31.sp, lineHeight = 36.sp)
        Text(subtitle, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
    }
}
@Composable
private fun Pill(text: String, color: Color = Mist) {
    Box(Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 7.dp)) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = .4.sp)
    }
}
@Composable
private fun AppAvatar(name: String, pkg: String) {
    val color = if (LocalDusk.current) Mist else when {
        "instagram" in pkg -> Color(0xFFF7E4E9)
        "youtube" in pkg -> Color(0xFFF9E6DE)
        "tiktok" in pkg || "musically" in pkg -> Color(0xFFE9E7F2)
        else -> Color(0xFFE8EFE3)
    }
    Box(Modifier.size(34.dp).background(color, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp)
    }
}
@Composable
private fun AppUsageRow(name: String, usage: String, pkg: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppAvatar(name, pkg); Spacer(Modifier.width(12.dp))
        Text(name, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp)); Text(usage, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun ItemBadge(id: String, owned: Boolean) {
    Box(Modifier.size(48.dp).background(if (owned) Mist else Paper, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        WorldGlyph(id, Modifier.size(32.dp), if (owned) Green else Muted)
    }
}

@Composable
private fun StatNumber(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp, color = Muted)
    }
}
private fun formatDay(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, MMM d"))
