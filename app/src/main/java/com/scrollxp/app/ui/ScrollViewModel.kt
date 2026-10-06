package com.scrollxp.app.ui

import kotlinx.coroutines.CancellationException
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scrollxp.app.data.*
import com.scrollxp.app.domain.Progression
import com.scrollxp.app.domain.Treasures
import com.scrollxp.app.domain.ReminderSettings
import com.scrollxp.app.domain.Worlds
import com.scrollxp.app.worker.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

data class ScrollState(
    val profile: Profile = Profile(),
    val days: List<DayRecord> = emptyList(),
    val xp: Int = 0,
    val installedApps: List<InstalledApp> = emptyList(),
    val status: TrackingStatus = TrackingStatus.LOADING,
    val hasAccess: Boolean = false,
    val loading: Boolean = true,
    val message: String? = null,
    val chests: List<Chest> = emptyList(),
    val goalDates: List<String> = emptyList(),
    val openingChest: String? = null,
    val reminders: ReminderSettings = ReminderSettings(),
) {
    val date: LocalDate get() = LocalDate.now(ZoneId.of(profile.zone.ifBlank { ZoneId.systemDefault().id }))
    val world get() = Worlds.active(profile.region, xp)
    val ownedTreasures: Set<String> get() = chests.filter { it.openedAt != null && Treasures.find(it.itemId) != null }.map { it.itemId }.toSet()
    val ownedIds: Set<String> get() = ownedTreasures + Progression.milestones.filter { xp >= it.xp }.map { it.id }
    val unopened: List<Chest> get() = chests.filter { it.openedAt == null }
    val today: DayRecord? get() {
        val zone = ZoneId.of(profile.zone.ifBlank { ZoneId.systemDefault().id })
        val date = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate().toString()
        return days.firstOrNull { it.date == date }
    }
}
private data class RuntimeState(val apps: List<InstalledApp> = emptyList(), val status: TrackingStatus = TrackingStatus.LOADING,
    val access: Boolean = false, val loading: Boolean = true, val message: String? = null, val openingChest: String? = null)

class ScrollViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScrollRepository(application)
    private val runtime = MutableStateFlow(RuntimeState())
    private val refreshGate = Mutex()
    private val reminderSettings = MutableStateFlow(ReminderStore(application).settings())
    private val collection = combine(repository.dao.observeChests(), repository.dao.observeGoalDates(), reminderSettings) { chests, dates, reminders -> Triple(chests, dates, reminders) }
    val state: StateFlow<ScrollState> = combine(repository.dao.observeProfile(), repository.dao.observeDays(),
        repository.dao.observeXp(), runtime, collection) { profile, days, xp, run, collected ->
        ScrollState(profile ?: Profile(), days, xp, run.apps, run.status, run.access, run.loading, run.message, collected.first, collected.second, run.openingChest, collected.third)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScrollState())

    init { refresh() }
    fun refresh() { viewModelScope.launch {
        refreshGate.withLock {
            runtime.update { it.copy(loading = true, message = null) }
            try {
                repository.initialize()
                reminderSettings.value = ReminderStore(getApplication()).settings()
                ReminderScheduler.sync(getApplication())
                val apps = repository.installedApps()
                val status = repository.reconcile()
                com.scrollxp.app.widget.IslandWidget.refresh(getApplication())
                runtime.update { it.copy(apps = apps, status = status, access = repository.hasAccess(), loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                runtime.update { it.copy(status = TrackingStatus.ERROR, loading = false,
                    message = "Couldn't refresh right now. Your saved progress is safe. Try again.") }
            }
        }
    } }
    private fun change(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); refresh() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
            runtime.update { it.copy(message = "Couldn't save that change. Please try again.") }
        }
    } }
    fun configure(name: String, apps: Set<String>, budget: Int) = change { repository.configure(name, apps, budget) }
    fun roof(value: Int) = change { repository.decorate(roof = value) }
    fun visitRegion(value: String) = change { repository.visitRegion(value) }
    fun guideDismissed(value: Boolean) = change { repository.guideDismissed(value) }
    fun placeItem(id: String) = change { repository.decorate(item = id, visible = true) }
    fun openChest(key: String) { viewModelScope.launch {
        if (runtime.value.openingChest != null) return@launch
        runtime.update { it.copy(openingChest = key, message = null) }
        try { repository.openChest(key) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { runtime.update { it.copy(message = "Couldn't open that chest. Your treasure is safe; try again.") } }
        finally { runtime.update { it.copy(openingChest = null) }; com.scrollxp.app.widget.IslandWidget.refresh(getApplication()) }
    } }
    fun haptics(value: Boolean) = change { repository.comfort(haptics = value) }
    fun toggleItem(id: String) = change { repository.decorate(item = id) }
    fun personality(value: String) = change { repository.personality(value) }
    fun appearance(value: String) = change { repository.comfort(appearance = value) }
    fun reducedMotion(value: Boolean) = change { repository.comfort(reducedMotion = value) }
    fun pause(value: Boolean) = change { repository.pause(value) }
    fun reminders(value: ReminderSettings) = change { ReminderScheduler.update(getApplication(), value) }
    fun reset() = change {
        ReminderScheduler.reset(getApplication())
        repository.reset()
        getApplication<Application>().getSharedPreferences("pro_preferences", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        com.scrollxp.app.sharing.IslandExport.clear(getApplication())
    }
}
