package app.still

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
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
import app.still.ui.SecondaryButton
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnloopTheme {
                UnloopScreen(openSettings = ::openSettings, serviceEnabled = ::serviceEnabled)
            }
        }
    }
    private fun serviceEnabled(): Boolean = getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.let { service -> service.packageName == packageName && service.name == "app.still.protection.FocusService" } }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action)) }
        catch (_: ActivityNotFoundException) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (_: ActivityNotFoundException) { Toast.makeText(this, "Откройте настройки Android вручную", Toast.LENGTH_LONG).show() }
        }
    }
}

private val tabs = listOf(
    DockItem("Сегодня", UnloopIcons.Day),
    DockItem("Защита", UnloopIcons.LoopBreak),
    DockItem("Экран", UnloopIcons.Tone),
)
private const val PROTECTION_TAB = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnloopScreen(openSettings: (String) -> Unit, serviceEnabled: () -> Boolean, model: MainViewModel = viewModel()) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val report by model.report.collectAsStateWithLifecycle()
    val loading by model.loading.collectAsStateWithLifecycle()
    val interruptions by model.interruptions.collectAsStateWithLifecycle()
    val live by ProtectionStatus.live.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(false) }
    var disclosure by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var settingsSheet by rememberSaveable { mutableStateOf(false) }
    val scrolls = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbar = remember { SnackbarHostState() }
    val c = Unloop.colors
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { enabled = serviceEnabled(); model.refresh(); delay(30_000) }
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
                    refresh = model::refresh, usageSettings = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) })
                1 -> Protection(settings, enabled, live, state, model, connect = { disclosure = true })
                2 -> Grayscale(openSettings)
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
    if (settingsSheet) ModalBottomSheet(onDismissRequest = { settingsSheet = false }, containerColor = c.surface, contentColor = c.ink) {
        SettingsSheet(enabled, live, delete = { settingsSheet = false; deleteDialog = true })
    }
    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false }, containerColor = c.surface, shape = RoundedCornerShape(28.dp),
        titleContentColor = c.ink, textContentColor = c.ink2,
        title = { Text("Доступ к интерфейсу приложений", style = UnloopType.headline) },
        text = { Text("Unloop использует специальные возможности Android, чтобы читать структуру экрана Instagram и YouTube, находить кнопки Reels и Shorts, закрывать их накладками и возвращать назад при перелистывании.\n\nСодержимое экранов, роликов и переписка не сохраняются и не передаются. Сохраняются только приложение и время прерывания.\n\nДоступ можно отозвать в Unloop или в настройках Android. После согласия включите сервис «Unloop» в системных настройках.", style = UnloopType.body) },
        confirmButton = { TextAction("Согласен, открыть настройки", onClick = { model.consent { disclosure = false; openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) } }) },
        dismissButton = { TextAction("Отмена", onClick = { disclosure = false }) },
    )
    if (deleteDialog) AlertDialog(
        onDismissRequest = { deleteDialog = false }, containerColor = c.surface, shape = RoundedCornerShape(28.dp),
        titleContentColor = c.ink, textContentColor = c.ink2,
        title = { Text("Удалить историю Unloop?", style = UnloopType.headline) },
        text = { Text("Будут удалены локальные записи о прерываниях. Настройки защиты сохранятся. Системная история экранного времени Android не изменится.", style = UnloopType.body) },
        confirmButton = { TextAction("Удалить", danger = true, onClick = { model.clearHistory(); deleteDialog = false }) },
        dismissButton = { TextAction("Отмена", onClick = { deleteDialog = false }) },
    )
}

@Composable
private fun Today(
    report: UsageReport?, loading: Boolean, interruptions: Int, state: ProtectionState, settings: ProtectionSettings,
    openProtection: () -> Unit, refresh: () -> Unit, usageSettings: () -> Unit,
) {
    val c = Unloop.colors
    ScreenTitle("Меньше скролла.\nБольше жизни.", "Небольшая пауза вместо ещё одного ролика.")
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
        meta = "$interruptions ${plural(interruptions, "прерывание", "прерывания", "прерываний")} ленты сегодня",
    )
    if (report == null || loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.ink2, trackColor = c.surfaceRaised)
    if (report != null && !report.granted) Panel {
        Text("Познакомьтесь со своими привычками", style = UnloopType.bodyStrong, color = c.ink)
        Text("Разрешите доступ к статистике использования, чтобы видеть время по приложениям. Защита работает и без него.", style = UnloopType.body, color = c.ink2)
        PrimaryButton("Разрешить статистику", onClick = usageSettings, small = true)
    }
    report?.error?.let { Panel { Text(it, style = UnloopType.body, color = c.ink2) } }
    if (report != null && granted) {
        Panel {
            Text("Эта неделя", style = UnloopType.bodyStrong, color = c.ink)
            if (report.days.isEmpty()) Text("История пока недоступна", style = UnloopType.body, color = c.ink2) else WeekChart(report.days)
            Text("Время активности приложений, пока экран включён. Android может хранить неполную историю; цифры могут отличаться от «Цифрового благополучия».", style = UnloopType.caption, color = c.ink3)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Сегодня по приложениям", style = UnloopType.headline, color = c.ink)
            val apps = today?.apps.orEmpty().entries.sortedByDescending { it.value }.take(8)
            if (apps.isEmpty()) Text("За сегодня пока нет данных", style = UnloopType.body, color = c.ink2)
            else QuietList(apps.map { (pkg, time) -> (report.labels[pkg] ?: pkg) to duration(time) })
        }
    }
    if (state != ProtectionState.On) SecondaryButton("Настроить защиту ленты", onClick = openProtection, modifier = Modifier.fillMaxWidth())
    TextAction("Обновить данные", onClick = refresh, enabled = !loading, modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.End))
    Text("На вашем устройстве. В вашем темпе.", style = UnloopType.caption, color = c.ink3)
}

@Composable
private fun Protection(settings: ProtectionSettings, enabled: Boolean, live: LiveStatus, state: ProtectionState, model: MainViewModel, connect: () -> Unit) {
    val c = Unloop.colors
    ScreenTitle("Вы решаете,\nчто смотреть.", "Полезные функции остаются доступны.")
    StatusBanner(state)
    if (state == ProtectionState.Setup) Panel {
        Text(when {
            !settings.consent -> "Нужно ваше согласие на работу с интерфейсом Instagram и YouTube."
            !enabled -> "Включите Unloop в специальных возможностях Android. Для APK Android может сначала потребовать разрешить ограниченные настройки в сведениях о приложении."
            else -> "Android ещё не подключил сервис. Проверьте его состояние в специальных возможностях."
        }, style = UnloopType.body, color = c.ink2)
        PrimaryButton("Подключить защиту", onClick = connect, small = true)
    }
    if (state == ProtectionState.On) Text(
        "Совместимость экспериментальная. Включённый сервис ещё не означает, что кнопки распознаны в вашей версии приложения.",
        style = UnloopType.caption, color = c.ink3,
    )
    CardList { AppRow("Защита ленты", "Общий переключатель", settings.enabled, model::enable) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Приложения", style = UnloopType.headline, color = c.ink)
        CardList {
            val instagram = settings.enabled && settings.instagram
            AppRow(
                "Instagram", if (instagram) "Reels под контролем" else "Reels без защиты", settings.instagram, model::instagram,
                tile = "Ig", enabled = settings.enabled,
                checks = if (instagram) listOf("Кнопка Reels закрывается накладкой", "Выход при перелистывании") else emptyList(),
                note = if (instagram) "Переписка остаётся доступной" else null,
            )
            CardDivider()
            val youtube = settings.enabled && settings.youtube
            AppRow(
                "YouTube", if (youtube) "Shorts под контролем" else "Shorts без защиты", settings.youtube, model::youtube,
                tile = "Yt", enabled = settings.enabled,
                checks = if (youtube) listOf("Кнопка Shorts закрывается накладкой", "Выход при перелистывании") else emptyList(),
                note = if (youtube) "Обычные видео доступны" else null,
            )
        }
    }
    Notice("Один ролик по ссылке можно посмотреть. Свайп к следующему — пауза.")
    Panel {
        Text("Как это работает", style = UnloopType.bodyStrong, color = c.ink)
        Text("Распознанные кнопки Reels и Shorts закрываются нейтральной маской. Ролик по прямой ссылке можно посмотреть, после первого перелистывания Unloop возвращает назад. Переписка, обычные видео и комментарии остаются доступны.", style = UnloopType.body, color = c.ink2)
        Text("Следующий ролик может на мгновение появиться. Защита активируется через 0,7 с после распознавания плеера; быстрый свайп при входе может быть пропущен. Автовоспроизведение, неизвестные версии интерфейса и браузер не гарантированно поддерживаются.", style = UnloopType.caption, color = c.ink3)
    }
    if (settings.consent) TextAction("Отозвать согласие и остановить защиту", onClick = model::withdrawConsent)
}

@Composable
private fun Grayscale(openSettings: (String) -> Unit) {
    val c = Unloop.colors
    ScreenTitle("Приглушите\nлишнее.", "Меньше цвета, спокойнее экран.")
    Panel { GrayscalePreview() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Чёрно-белый режим", style = UnloopType.headline, color = c.ink)
        Text("Включается в системных настройках Android. Unloop не меняет цвет экрана сам.", style = UnloopType.body, color = c.ink2)
    }
    Steps(listOf("Откройте «Цифровое благополучие»", "Выберите «Режим сна»", "Включите «Оттенки серого»"))
    PrimaryButton("Открыть настройки", onClick = { openSettings(Settings.ACTION_SETTINGS) }, modifier = Modifier.fillMaxWidth())
    Text("Названия пунктов зависят от телефона. На некоторых моделях оттенки серого находятся в «Специальные возможности» → «Коррекция цвета».", style = UnloopType.caption, color = c.ink3)
    SecondaryButton("Специальные возможности", onClick = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, modifier = Modifier.fillMaxWidth())
    Panel {
        Text("Попробуйте вечером", style = UnloopType.bodyStrong, color = c.ink)
        Text("Если телефон поддерживает расписание режима сна, задайте там время включения оттенков серого. Лента станет менее яркой; эффект индивидуален.", style = UnloopType.body, color = c.ink2)
    }
}

@Composable
private fun SettingsSheet(enabled: Boolean, live: LiveStatus, delete: () -> Unit) {
    val c = Unloop.colors
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = UnloopType.headline, color = c.ink)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Проверка распознавания", style = UnloopType.bodyStrong, color = c.ink)
            Text(if (!enabled) "Сервис выключен" else live.note, style = UnloopType.body, color = c.ink2)
            Text(
                "Последний активный экран: ${live.packageName ?: "—"}\nМасок: ${live.masks} · Плеер: ${if (live.viewer) "распознан" else "не распознан"}",
                style = UnloopType.caption, color = c.ink3,
            )
        }
        Text("Структура экранов и переписка не сохраняются. Записи о прерываниях старше 30 дней очищаются при запуске сервиса и новых прерываниях.", style = UnloopType.caption, color = c.ink3)
        TextAction("Удалить локальную историю", onClick = delete, danger = true)
    }
}
