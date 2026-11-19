package app.still

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.data.ProtectionSettings
import app.still.data.UsageReport
import app.still.protection.FocusService
import app.still.protection.LiveStatus
import app.still.protection.ProtectionStatus
import app.still.ui.AppRow
import app.still.ui.CardDivider
import app.still.ui.CardList
import app.still.ui.Dock
import app.still.ui.DockItem
import app.still.ui.GrayscalePreview
import app.still.ui.Notice
import app.still.ui.Panel
import app.still.ui.PrimaryButton
import app.still.ui.ProtectionState
import app.still.ui.QuietList
import app.still.ui.ScreenTitle
import app.still.ui.StatCard
import app.still.ui.StatusBanner
import app.still.ui.Steps
import app.still.ui.TextAction
import app.still.ui.TopBar
import app.still.ui.Unloop
import app.still.ui.UnloopIcons
import app.still.ui.UnloopTheme
import app.still.ui.UnloopType
import app.still.ui.WeekChart
import app.still.ui.duration
import app.still.ui.plural
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class Device(
    val serviceEnabled: () -> Boolean,
    val openSettings: (String) -> Unit,
    val openAccessibility: () -> Unit,
    val openAppInfo: () -> Unit,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val device = Device(::serviceEnabled, { open(Intent(it)) }, ::openAccessibility, ::openAppInfo)
        setContent { UnloopTheme { UnloopScreen(device) } }
    }

    private val service get() = ComponentName(this, FocusService::class.java)

    private fun serviceEnabled(): Boolean = getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.let { info -> ComponentName(info.packageName, info.name) == service } }

    private fun openAccessibility() {
        val name = service.flattenToString()
        // The direct details screen needs a system-only permission, so open the list; these extras highlight the Unloop row where supported.
        open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", name)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", name) }))
    }

    private fun openAppInfo() = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    private fun open(vararg intents: Intent) {
        for (intent in listOf(*intents, Intent(Settings.ACTION_SETTINGS))) {
            try { startActivity(intent); return } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
        }
        Toast.makeText(this, "Откройте настройки Android вручную", Toast.LENGTH_LONG).show()
    }
}

private val tabs = listOf(
    DockItem("Сегодня", UnloopIcons.Day),
    DockItem("Защита", UnloopIcons.LoopBreak),
    DockItem("Экран", UnloopIcons.Tone),
)
private const val PROTECTION_TAB = 1

private enum class SetupStep { Consent, Enable, Unblock, EnableAgain, Reconnect }
private enum class Opened { None, Accessibility, AppInfo }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnloopScreen(device: Device, model: MainViewModel = viewModel()) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val report by model.report.collectAsStateWithLifecycle()
    val loading by model.loading.collectAsStateWithLifecycle()
    val interruptions by model.interruptions.collectAsStateWithLifecycle()
    val live by ProtectionStatus.live.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf(Opened.None) }
    var disclosure by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var settingsSheet by rememberSaveable { mutableStateOf(false) }
    val scrolls = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbar = remember { SnackbarHostState() }
    val c = Unloop.colors
    // Restarts on every return from Settings, so the setup card moves to the next step right away.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { enabled = device.serviceEnabled(); model.refresh(); delay(30_000) }
        }
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); model.message.value = null }
    }
    val state = when {
        !settings.enabled -> ProtectionState.Off
        settings.consent && enabled && live.connected -> ProtectionState.On
        else -> ProtectionState.Setup
    }
    val step = when {
        !settings.consent -> SetupStep.Consent
        enabled -> SetupStep.Reconnect
        // Android 13+ blocks accessibility for sideloaded APKs ("restricted settings") and apps can't read that state.
        // Coming back from Accessibility with the service still off is the sign; the ⋮ unlock item only appears after that attempt.
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> SetupStep.Enable
        opened == Opened.Accessibility -> SetupStep.Unblock
        opened == Opened.AppInfo -> SetupStep.EnableAgain
        else -> SetupStep.Enable
    }
    val openAccessibility = { opened = Opened.Accessibility; device.openAccessibility() }
    val openAppInfo = { opened = Opened.AppInfo; device.openAppInfo() }
    val continueSetup = { if (step == SetupStep.Unblock) openAppInfo() else openAccessibility() }
    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Dock(tabs, selected = tab, protection = state, protectionIndex = PROTECTION_TAB) { index ->
                if (index == tab) scope.launch { scrolls[index].animateScrollTo(0) } else tab = index
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scrolls[tab]).padding(top = padding.calculateTopPadding()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TopBar(onSettings = { settingsSheet = true })
            when (tab) {
                0 -> Today(report, loading, interruptions, state, settings, openProtection = { tab = PROTECTION_TAB },
                    usageSettings = { device.openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) })
                1 -> Protection(settings, state, step, model,
                    next = { if (step == SetupStep.Consent) disclosure = true else continueSetup() }, other = openAccessibility)
                2 -> Grayscale(device.openSettings)
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
    if (settingsSheet) ModalBottomSheet(onDismissRequest = { settingsSheet = false }, containerColor = c.surface, contentColor = c.ink) {
        SettingsSheet(enabled, live, settings.consent,
            withdraw = { model.withdrawConsent(); settingsSheet = false },
            delete = { settingsSheet = false; deleteDialog = true })
    }
    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false }, containerColor = c.surface, shape = RoundedCornerShape(28.dp),
        titleContentColor = c.ink, textContentColor = c.ink2,
        title = { Text("Доступ к экрану", style = UnloopType.headline) },
        text = { Text("Unloop читает структуру экрана Instagram и YouTube, чтобы закрывать кнопки Reels и Shorts и выводить из ленты роликов.\n\nНичего не сохраняется и не передаётся. Согласие можно отозвать в настройках Unloop.", style = UnloopType.body) },
        confirmButton = { TextAction("Согласен", onClick = { model.consent { disclosure = false; continueSetup() } }) },
        dismissButton = { TextAction("Отмена", onClick = { disclosure = false }) },
    )
    if (deleteDialog) AlertDialog(
        onDismissRequest = { deleteDialog = false }, containerColor = c.surface, shape = RoundedCornerShape(28.dp),
        titleContentColor = c.ink, textContentColor = c.ink2,
        title = { Text("Удалить историю?", style = UnloopType.headline) },
        text = { Text("Записи о прерываниях будут удалены. Настройки останутся.", style = UnloopType.body) },
        confirmButton = { TextAction("Удалить", danger = true, onClick = { model.clearHistory(); deleteDialog = false }) },
        dismissButton = { TextAction("Отмена", onClick = { deleteDialog = false }) },
    )
}

@Composable
private fun Today(
    report: UsageReport?, loading: Boolean, interruptions: Int, state: ProtectionState, settings: ProtectionSettings,
    openProtection: () -> Unit, usageSettings: () -> Unit,
) {
    val c = Unloop.colors
    ScreenTitle("Сегодня")
    val protectedApps = listOfNotNull("Instagram".takeIf { settings.instagram }, "YouTube".takeIf { settings.youtube }).joinToString(", ")
    StatusBanner(
        state,
        detail = when (state) { ProtectionState.On -> protectedApps.ifEmpty { null }; ProtectionState.Setup -> "Подключить"; ProtectionState.Off -> null },
        onClick = openProtection,
    )
    val granted = report?.granted == true && report.error == null
    val today = report?.days?.find { it.date == LocalDate.now() }
    StatCard(
        minutes = if (granted && today != null) today.totalMs / 60_000 else null,
        meta = "$interruptions ${plural(interruptions, "прерывание", "прерывания", "прерываний")}",
    )
    if (report == null || loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.ink2, trackColor = c.surfaceRaised)
    if (report != null && !report.granted) Panel {
        Text("Нет доступа к статистике", style = UnloopType.bodyStrong, color = c.ink)
        PrimaryButton("Разрешить", onClick = usageSettings, small = true)
    }
    report?.error?.let { Panel { Text(it, style = UnloopType.body, color = c.ink2) } }
    if (report != null && granted) {
        if (report.days.isNotEmpty()) Panel { WeekChart(report.days) }
        val apps = today?.apps.orEmpty().entries.sortedByDescending { it.value }.take(8)
        if (apps.isNotEmpty()) QuietList(apps.map { (pkg, time) -> (report.labels[pkg] ?: pkg) to duration(time) })
    }
}

@Composable
private fun Protection(
    settings: ProtectionSettings, state: ProtectionState, step: SetupStep,
    model: MainViewModel, next: () -> Unit, other: () -> Unit,
) {
    val c = Unloop.colors
    ScreenTitle("Защита")
    if (state == ProtectionState.Setup) Panel {
        Text(when (step) {
            SetupStep.Consent -> "Нужен доступ к спецвозможностям"
            SetupStep.Enable -> "Включите Unloop в спецвозможностях"
            SetupStep.Unblock -> "Android запретил доступ?"
            SetupStep.EnableAgain -> "Теперь включите Unloop"
            SetupStep.Reconnect -> "Сервис не отвечает"
        }, style = UnloopType.bodyStrong, color = c.ink)
        when (step) {
            SetupStep.Unblock -> "В сведениях о приложении нажмите ⋮ вверху справа → «Разрешить ограниченные настройки»."
            SetupStep.Reconnect -> "Выключите и снова включите Unloop."
            else -> null
        }?.let { Text(it, style = UnloopType.body, color = c.ink2) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(when (step) { SetupStep.Consent -> "Подключить"; SetupStep.Unblock -> "О приложении"; else -> "Открыть" }, onClick = next, small = true)
            if (step == SetupStep.Unblock) TextAction("Спецвозможности", onClick = other)
        }
    } else StatusBanner(state)
    CardList { AppRow("Защита ленты", null, settings.enabled, model::enable) }
    CardList {
        AppRow("Instagram", "Reels", settings.instagram, model::instagram, tile = "Ig", enabled = settings.enabled)
        CardDivider()
        AppRow("YouTube", "Shorts", settings.youtube, model::youtube, tile = "Yt", enabled = settings.enabled)
    }
    Notice("Один ролик по ссылке можно. Следующий — пауза.")
}

@Composable
private fun Grayscale(openSettings: (String) -> Unit) {
    ScreenTitle("Оттенки серого")
    Panel { GrayscalePreview() }
    Steps(listOf("Цифровое благополучие", "Режим сна", "Оттенки серого"))
    PrimaryButton("Открыть настройки", onClick = { openSettings(Settings.ACTION_SETTINGS) }, modifier = Modifier.fillMaxWidth())
    TextAction("Специальные возможности", onClick = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SettingsSheet(enabled: Boolean, live: LiveStatus, consent: Boolean, withdraw: () -> Unit, delete: () -> Unit) {
    val c = Unloop.colors
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = UnloopType.headline, color = c.ink)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (!enabled) "Сервис выключен" else live.note, style = UnloopType.body, color = c.ink2)
            Text(
                "Экран: ${live.packageName ?: "—"} · Масок: ${live.masks} · Плеер: ${if (live.viewer) "да" else "нет"}",
                style = UnloopType.caption, color = c.ink3,
            )
        }
        Text("Данные хранятся только на телефоне.", style = UnloopType.caption, color = c.ink3)
        Column {
            if (consent) TextAction("Отозвать согласие", onClick = withdraw)
            TextAction("Удалить историю", onClick = delete, danger = true)
        }
    }
}
