package app.still

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.still.data.ProtectionSettings
import app.still.data.UsageReport
import app.still.update.Release
import app.still.update.Updater
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StillApp
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ProtectionSettings())
    val report = MutableStateFlow<UsageReport?>(null)
    val loading = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private val today = MutableStateFlow(LocalDate.now())
    val interruptions = today.flatMapLatest { date ->
        val zone = ZoneId.systemDefault()
        app.database.events().count(date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }.catch { message.value = "Не удалось прочитать локальную историю прерываний"; emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    private var refreshJob: Job? = null
    val version = Updater.currentVersion(application)
    val update = MutableStateFlow<UpdateState>(UpdateState.Idle)

    fun refresh() {
        if (refreshJob?.isActive == true) return
        today.value = LocalDate.now()
        refreshJob = viewModelScope.launch {
            loading.value = true
            try { report.value = app.usage.report() } finally { loading.value = false }
        }
    }
    private fun save(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }.onFailure { message.value = "Не удалось сохранить изменения" }
        }
    }
    fun enable(value: Boolean) = save { app.settings.setEnabled(value) }
    fun instagram(value: Boolean) = save { app.settings.setInstagram(value) }
    fun youtube(value: Boolean) = save { app.settings.setYoutube(value) }
    fun consent(onSaved: () -> Unit) = save { app.settings.setConsent(true); onSaved() }
    fun withdrawConsent() = save { app.settings.setConsent(false) }
    fun clearHistory() = save { app.database.events().clear(); app.usage.clear(); refresh(); message.value = "История удалена" }

    /** [quiet] checks stay silent unless a newer version exists: used on app start. */
    fun checkUpdate(quiet: Boolean = false) {
        if (update.value is UpdateState.Checking || update.value is UpdateState.Downloading) return
        if (!quiet) update.value = UpdateState.Checking
        viewModelScope.launch {
            val result = runCatching { Updater.latest() }
            update.value = result.fold(
                { if (Updater.isNewer(it.version, version)) UpdateState.Available(it) else if (quiet) UpdateState.Idle else UpdateState.Latest },
                { if (quiet) UpdateState.Idle else UpdateState.Failed("Не удалось проверить: нет связи с GitHub") },
            )
        }
    }

    fun installUpdate(release: Release) {
        if (update.value is UpdateState.Downloading) return
        val context = getApplication<StillApp>()
        if (!context.packageManager.canRequestPackageInstalls()) { update.value = UpdateState.NeedsPermission(release); return }
        update.value = UpdateState.Downloading(release, 0f)
        viewModelScope.launch {
            runCatching {
                val apk = Updater.download(context, release) { update.value = UpdateState.Downloading(release, it) }
                Updater.install(context, apk)
            }.onSuccess { update.value = UpdateState.Available(release) }
                .onFailure { update.value = UpdateState.Failed(it.message ?: "Не удалось скачать обновление") }
        }
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState
    data class Available(val release: Release) : UpdateState
    data class NeedsPermission(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Failed(val reason: String) : UpdateState
}
