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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.data.ProtectionSettings
import app.still.data.UsageReport
import app.still.protection.LiveStatus
import app.still.protection.ProtectionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val Paper = Color(0xFFF6F5F0)
private val Pine = Color(0xFF285D4B)
private val Ink = Color(0xFF202D29)
private val Muted = Color(0xFF65726C)
private val Soft = Color(0xFFE6EDE5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Pine, onPrimary = Color.White, background = Paper, surface = Paper,
                onBackground = Ink, onSurface = Ink, surfaceVariant = Soft, onSurfaceVariant = Muted,
            )) {
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { enabled = serviceEnabled(); model.refresh(); delay(30_000) }
        }
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); model.message.value = null }
    }
    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Paper) {
                listOf("Сегодня", "Защита", "Экран").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Text(listOf("◷", "Ⅱ", "◐")[index], fontSize = 23.sp) }, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("unloop", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                Surface(color = Soft, shape = RoundedCornerShape(50)) {
                    Text("Меньше шума", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Pine, fontSize = 12.sp)
                }
            }
            when (tab) {
                0 -> Today(report, loading, interruptions, settings.enabled && settings.consent && enabled && live.connected,
                    refresh = model::refresh, setup = { tab = 1 }, usageSettings = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) })
                1 -> Protection(settings, enabled, live, model, connect = { disclosure = true }, delete = { deleteDialog = true })
                2 -> Grayscale(openSettings)
            }
            Text("На вашем устройстве. В вашем темпе.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false }, title = { Text("Доступ к интерфейсу приложений") },
        text = { Text("Unloop использует специальные возможности Android, чтобы читать структуру экрана Instagram и YouTube, находить кнопки Reels/Shorts, закрывать их накладками и выполнять возврат при перелистывании.\n\nСодержимое экранов, роликов и переписка не сохраняются и не передаются. Сохраняются только приложение и время прерывания.\n\nМожно отключить защиту в Unloop или отозвать доступ в настройках Android. После согласия включите сервис «Unloop» в системных настройках.") },
        confirmButton = { TextButton(onClick = { model.consent { disclosure = false; openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) } }) { Text("Согласен, открыть настройки") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Отмена") } },
    )
    if (deleteDialog) AlertDialog(
        onDismissRequest = { deleteDialog = false }, title = { Text("Удалить историю Unloop?") },
        text = { Text("Будут удалены локальные записи о прерываниях. Настройки защиты сохранятся. Системная история экранного времени Android не изменится.") },
        confirmButton = { TextButton(onClick = { model.clearHistory(); deleteDialog = false }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Отмена") } },
    )
}

@Composable
private fun Today(report: UsageReport?, loading: Boolean, interruptions: Int, active: Boolean, refresh: () -> Unit, setup: () -> Unit, usageSettings: () -> Unit) {
    Title("Верните себе\nсвоё внимание.", "Небольшая пауза вместо ещё одного ролика.")
    Surface(color = Pine, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ЭКРАННОЕ ВРЕМЯ · СЕГОДНЯ", color = Color.White.copy(alpha = .75f), fontSize = 11.sp, letterSpacing = 1.sp)
            val today = report?.days?.find { it.date == LocalDate.now() }
            Text(if (report?.granted == true && report.error == null && today != null) duration(today.totalMs) else "—", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Medium)
            Text("$interruptions прерываний ленты сегодня", color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
        }
    }
    if (report == null || loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Pine)
    if (report != null && !report.granted) Panel {
        Text("Познакомьтесь со своими привычками", fontWeight = FontWeight.SemiBold)
        Text("Разрешите доступ к статистике использования, чтобы видеть время по приложениям.", color = Muted)
        Button(onClick = usageSettings) { Text("Разрешить статистику") }
    }
    report?.error?.let { Panel { Text(it, color = Muted) } }
    if (report?.granted == true && report.error == null) {
        Panel {
            Text("Последние 7 дней", fontWeight = FontWeight.SemiBold)
            WeekChart(report)
            Text("Время активности приложений, пока экран включён. Android может хранить неполную историю; цифры могут отличаться от «Цифрового благополучия». При разделённом экране время приложений пересекается.", fontSize = 12.sp, color = Muted)
        }
        Panel {
            Text("Сегодня по приложениям", fontWeight = FontWeight.SemiBold)
            val apps = report.days.find { it.date == LocalDate.now() }?.apps.orEmpty().entries.sortedByDescending { it.value }.take(8)
            if (apps.isEmpty()) Text("За сегодня пока нет данных", color = Muted)
            apps.forEach { (pkg, time) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(report.labels[pkg] ?: pkg, Modifier.weight(1f), maxLines = 2)
                    Text(duration(time), color = Pine, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    OutlinedButton(onClick = setup, modifier = Modifier.fillMaxWidth()) { Text(if (active) "Сервис включён · проверить" else "Настроить защиту ленты") }
    TextButton(onClick = refresh, enabled = !loading, modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.End)) { Text("Обновить данные") }
}

@Composable
private fun WeekChart(report: UsageReport) {
    val days = report.days
    if (days.isEmpty()) { Text("История пока недоступна", color = Muted); return }
    val max = days.maxOf { it.totalMs }.coerceAtLeast(1).toFloat()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${day.totalMs / 60_000}м", color = Muted, fontSize = 10.sp)
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    val h = if (day.totalMs > 0) (size.height * day.totalMs / max).coerceAtLeast(3.dp.toPx()) else 0f
                    drawRoundRect(Soft, cornerRadius = CornerRadius(6.dp.toPx()))
                    if (h > 0) drawRoundRect(Pine, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = CornerRadius(6.dp.toPx()))
                }
                Text(day.date.format(DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("ru"))), color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Protection(settings: ProtectionSettings, enabled: Boolean, live: LiveStatus, model: MainViewModel, connect: () -> Unit, delete: () -> Unit) {
    Title("Один ролик.\nБез бесконечности.", "Сохраните полезное. Уберите привычку листать.")
    Panel {
        Toggle("Защита ленты", "Общий переключатель", settings.enabled, model::enable)
        HorizontalDivider(color = Soft)
        Toggle("Instagram", "Скрывать Reels и выходить при перелистывании", settings.instagram, model::instagram)
        Toggle("YouTube", "Скрывать Shorts и выходить при перелистывании", settings.youtube, model::youtube)
    }
    Panel {
        val ready = enabled && live.connected && settings.consent && settings.enabled
        Text(if (ready) "Сервис включён" else "Защита требует настройки", fontWeight = FontWeight.SemiBold)
        Text(when {
            !settings.consent -> "Нужно ваше согласие на работу с интерфейсом Instagram и YouTube."
            !enabled -> "Включите Unloop в специальных возможностях Android. Для APK Android может сначала потребовать разрешить ограниченные настройки в сведениях о приложении."
            !settings.enabled -> "Включите общий переключатель защиты."
            !live.connected -> "Android ещё не подключил сервис. Проверьте его состояние в специальных возможностях."
            else -> "Совместимость экспериментальная. Включённый сервис ещё не означает, что кнопки распознаны в вашей версии приложения."
        }, color = Muted)
        if (!enabled || !settings.consent || !live.connected) Button(onClick = connect) { Text("Подключить защиту") }
        if (settings.consent) TextButton(onClick = model::withdrawConsent) { Text("Отозвать согласие и остановить защиту") }
    }
    Panel {
        Text("Как это работает", fontWeight = FontWeight.SemiBold)
        Text("1. Распознанные кнопки Reels и Shorts закрываются нейтральной маской.\n\n2. Ролик по прямой ссылке можно посмотреть. После первого распознанного перелистывания Unloop возвращает назад.\n\n3. Переписка, обычные видео и комментарии остаются доступны.", color = Muted)
        Text("Следующий ролик может на мгновение появиться. Защита активируется через 0,7 с после распознавания плеера; быстрый свайп при входе может быть пропущен. Автовоспроизведение, неизвестные версии интерфейса и браузер не гарантированно поддерживаются.", fontSize = 12.sp, color = Muted)
    }
    Panel {
        Text("Проверка распознавания", fontWeight = FontWeight.SemiBold)
        Text(if (!enabled) "Сервис выключен" else live.note, color = Muted)
        Text("Последний активный экран: ${live.packageName ?: "—"}\nМасок: ${live.masks} · Плеер: ${if (live.viewer) "распознан" else "не распознан"}", fontSize = 12.sp, color = Muted)
        Text("Модель телефона и версии Instagram/YouTube пока не проверены. Структура экранов и переписка не сохраняются. Записи старше 30 дней очищаются при запуске сервиса и новых прерываниях.", fontSize = 12.sp, color = Muted)
    }
    OutlinedButton(onClick = delete, modifier = Modifier.fillMaxWidth()) { Text("Удалить локальную историю") }
}

@Composable
private fun Grayscale(openSettings: (String) -> Unit) {
    Title("Меньше цвета.\nМеньше соблазна.", "Сделайте экран визуально спокойнее.")
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0xFF242424, 0xFF606060, 0xFF999999, 0xFFD4D4D4).forEach { color ->
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(color), RoundedCornerShape(20.dp)))
        }
    }
    Panel {
        Text("Чёрно-белый режим Android", fontWeight = FontWeight.SemiBold)
        Text("Unloop не может самостоятельно изменить цвет всего экрана без дополнительных системных прав. Используйте встроенные настройки телефона.", color = Muted)
        Text("Вариант 1\nНастройки → Цифровое благополучие → Режим сна → Оттенки серого. Названия зависят от производителя.", color = Muted)
        Text("Вариант 2\nНастройки → Специальные возможности → Коррекция цвета → Оттенки серого, если такой пункт доступен.", color = Muted)
        Button(onClick = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Специальные возможности") }
        OutlinedButton(onClick = { openSettings(Settings.ACTION_SETTINGS) }) { Text("Все настройки Android") }
    }
    Panel {
        Text("Попробуйте вечером", fontWeight = FontWeight.SemiBold)
        Text("Если телефон поддерживает расписание режима сна, задайте время включения оттенков серого там. Это может уменьшить визуальную привлекательность ленты; эффект индивидуален.", color = Muted)
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        Text(subtitle, color = Muted, fontSize = 15.sp)
    }
}
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = change)
    }
}
private fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} ч ${minutes % 60} мин" else "$minutes мин"
}
