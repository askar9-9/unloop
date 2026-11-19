package app.still.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.sp
import app.still.core.UsageDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ProtectionState { On, Setup, Off }

@Composable
fun TopBar(onSettings: () -> Unit) {
    val c = Unloop.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("unloop", style = UnloopType.wordmark, color = c.ink)
        IconButton(onClick = onSettings) { Icon(UnloopIcons.Settings, contentDescription = "Настройки", tint = c.ink) }
    }
}

@Composable
fun ScreenTitle(title: String) = Text(title, style = UnloopType.title, color = Unloop.colors.ink)

@Composable
fun StatusBanner(state: ProtectionState, detail: String? = null, onClick: (() -> Unit)? = null) {
    val c = Unloop.colors
    val bg = when (state) { ProtectionState.On -> c.accentSoft; ProtectionState.Setup -> c.warnSoft; ProtectionState.Off -> c.surface }
    val fg = when (state) { ProtectionState.On -> c.accent; ProtectionState.Setup -> c.warn; ProtectionState.Off -> c.ink2 }
    val text = when (state) { ProtectionState.On -> "Защита включена"; ProtectionState.Setup -> "Нужна настройка"; ProtectionState.Off -> "Защита выключена" }
    val icon = if (state == ProtectionState.Setup) UnloopIcons.Info else UnloopIcons.LoopBreak
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Открыть защиту", onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg)
        Text(text, style = UnloopType.bodyStrong, color = fg, modifier = Modifier.weight(1f))
        detail?.let { Text(it, style = UnloopType.caption, color = fg) }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Unloop.colors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
fun CardList(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Unloop.colors.surface), content = content)
}

@Composable
fun CardDivider() = HorizontalDivider(color = Unloop.colors.line)

@Composable
fun StatCard(minutes: Long?, meta: String) {
    val c = Unloop.colors
    val unit = SpanStyle(fontSize = 32.sp, letterSpacing = (-1).sp, color = c.ink2)
    val value = buildAnnotatedString {
        when {
            minutes == null -> append("—")
            minutes >= 60 -> { append("${minutes / 60} "); withStyle(unit) { append("ч") }; append(" ${minutes % 60} "); withStyle(unit) { append("мин") } }
            else -> { append("$minutes "); withStyle(unit) { append("мин") } }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surface).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("ЭКРАННОЕ ВРЕМЯ", style = UnloopType.label, color = c.ink2)
        Text(value, style = UnloopType.number, color = c.ink, maxLines = 1, softWrap = false)
        Text(meta, style = UnloopType.body, color = c.ink2)
    }
}

/** Bars are tabs: tapping a day selects it. */
@Composable
fun WeekChart(days: List<UsageDay>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val c = Unloop.colors
    val max = days.maxOfOrNull { it.totalMs }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            val active = day.date == selected
            val labelColor = if (active) c.ink else c.ink3
            val labelStyle = if (active) UnloopType.caption.copy(fontWeight = UnloopType.bodyStrong.fontWeight) else UnloopType.caption
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(day.date) })
                    .clearAndSetSemantics { contentDescription = "${dayTitle(day.date)}, ${duration(day.totalMs)}" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(shortDuration(day.totalMs), style = labelStyle, color = labelColor, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(6.dp)).background(c.surfaceRaised)) {
                    if (day.totalMs > 0) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .fillMaxHeight((day.totalMs.toFloat() / max).coerceIn(0.03f, 1f))
                                .clip(RoundedCornerShape(6.dp)).background(if (active) c.accent else c.ink3),
                        )
                    }
                }
                Text(day.date.format(DateTimeFormatter.ofPattern("EE", ru)), style = labelStyle, color = labelColor)
            }
        }
    }
}

private val ru = Locale.forLanguageTag("ru")

/** "Сегодня", "Вчера" or "Пн, 17 ноября". */
fun dayTitle(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Сегодня"
    LocalDate.now().minusDays(1) -> "Вчера"
    else -> date.format(DateTimeFormatter.ofPattern("EE, d MMMM", ru)).replaceFirstChar { it.uppercase() }
}

fun dayRange(from: LocalDate, to: LocalDate): String =
    if (from.month == to.month) "${from.dayOfMonth}–${to.format(DateTimeFormatter.ofPattern("d MMMM", ru))}"
    else "${from.format(DateTimeFormatter.ofPattern("d MMM", ru))} – ${to.format(DateTimeFormatter.ofPattern("d MMM", ru))}"

private object IconCache {
    val bitmaps = mutableMapOf<String, ImageBitmap?>()
}

/** The installed app's own icon; a letter tile when Android hides the app from Unloop. */
@Composable
fun AppIcon(packageName: String, label: String, size: Dp = 40.dp) {
    val c = Unloop.colors
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState(IconCache.bitmaps[packageName], packageName) {
        if (packageName in IconCache.bitmaps) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap() }.getOrNull()
        }
        IconCache.bitmaps[packageName] = value
    }
    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) } ?: Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(c.surfaceRaised), contentAlignment = Alignment.Center,
    ) { Text(label.take(1).uppercase(), style = UnloopType.bodyStrong, color = c.ink) }
}

class UsageRow(val packageName: String, val label: String, val value: String)

@Composable
fun QuietList(rows: List<UsageRow>) {
    val c = Unloop.colors
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = c.line)
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(row.packageName, row.label, 32.dp)
                Text(row.label, style = UnloopType.body, color = c.ink, modifier = Modifier.weight(1f), maxLines = 2)
                Text(row.value, style = UnloopType.body.copy(fontFeatureSettings = "tnum"), color = c.ink2, maxLines = 1)
            }
            HorizontalDivider(color = c.line)
        }
    }
}

@Composable
fun UnloopSwitch(checked: Boolean, enabled: Boolean = true) {
    val c = Unloop.colors
    Switch(
        checked = checked, onCheckedChange = null, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.accent, checkedThumbColor = c.onAccent, checkedBorderColor = c.accent,
            uncheckedTrackColor = Color.Transparent, uncheckedThumbColor = c.ink2, uncheckedBorderColor = c.lineStrong,
            disabledCheckedTrackColor = c.accent.copy(alpha = .4f), disabledCheckedThumbColor = c.onAccent.copy(alpha = .4f),
            disabledUncheckedTrackColor = Color.Transparent, disabledUncheckedThumbColor = c.ink2.copy(alpha = .4f),
            disabledUncheckedBorderColor = c.lineStrong.copy(alpha = .4f),
        ),
    )
}

@Composable
fun AppRow(
    name: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit,
    packageName: String? = null, enabled: Boolean = true,
) {
    val c = Unloop.colors
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        packageName?.let { AppIcon(it, name) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = UnloopType.bodyStrong, color = c.ink)
            subtitle?.let { Text(it, style = UnloopType.caption, color = c.ink2) }
        }
        UnloopSwitch(checked, enabled)
    }
}

@Composable
fun Notice(text: String) {
    val c = Unloop.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(UnloopIcons.Info, contentDescription = null, tint = c.ink2)
        Text(text, style = UnloopType.body, color = c.ink2)
    }
}

@Composable
fun Steps(items: List<String>) {
    val c = Unloop.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).border(1.5.dp, c.lineStrong, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = UnloopType.caption.copy(fontWeight = UnloopType.bodyStrong.fontWeight), color = c.ink2)
                }
                Text(item, style = UnloopType.body, color = c.ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GrayscalePreview() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Scene(grey = false, label = "Цвет", description = "Пейзаж в цвете", modifier = Modifier.weight(1f))
        Scene(grey = true, label = "Оттенки серого", description = "Тот же пейзаж в оттенках серого", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Scene(grey: Boolean, label: String, description: String, modifier: Modifier) {
    val c = Unloop.colors
    fun tone(color: Color): Color {
        if (!grey) return color
        val l = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        return Color(l, l, l)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).semantics { contentDescription = description }) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(tone(Color(0xFFF2A76C)), tone(Color(0xFFF6CF9C)))))
            drawCircle(tone(Color(0xFFFFE2A8)), radius = h * 0.12f, center = Offset(w * 0.7f, h * 0.38f))
            drawPath(Path().apply { moveTo(0f, h * 0.62f); quadraticTo(w * 0.45f, h * 0.42f, w, h * 0.58f); lineTo(w, h); lineTo(0f, h); close() }, tone(Color(0xFF2F6F5A)))
            drawPath(Path().apply { moveTo(0f, h * 0.8f); quadraticTo(w * 0.6f, h * 0.66f, w, h * 0.86f); lineTo(w, h); lineTo(0f, h); close() }, tone(Color(0xFF1E4D3F)))
        }
        Text(label, style = UnloopType.caption, color = c.ink2, textAlign = TextAlign.Center)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val c = Unloop.colors
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = if (small) 36.dp else 54.dp), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
        contentPadding = PaddingValues(horizontal = if (small) 16.dp else 24.dp),
    ) { Text(text, style = if (small) UnloopType.button.copy(fontSize = 13.sp) else UnloopType.button) }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true) {
    val c = Unloop.colors
    TextButton(
        onClick = onClick, modifier = modifier.heightIn(min = 44.dp), enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = if (danger) c.danger else c.ink, disabledContentColor = c.ink3),
    ) { Text(text, style = UnloopType.button) }
}

class DockItem(val label: String, val icon: ImageVector)

/** Floating icon-only navigation. The protection button carries the protection state as a dot, so it is visible from every tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dock(items: List<DockItem>, selected: Int, protection: ProtectionState, protectionIndex: Int, onSelect: (Int) -> Unit) {
    val c = Unloop.colors
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to c.bg.copy(alpha = 0f), 0.45f to c.bg))
            .navigationBarsPadding().padding(top = 28.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.shadow(12.dp, CircleShape, clip = false).clip(CircleShape).background(c.surface)
                .border(1.dp, c.line, CircleShape).padding(6.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                val stateText = when (protection) { ProtectionState.On -> "включена"; ProtectionState.Setup -> "требует настройки"; ProtectionState.Off -> "выключена" }
                val description = if (index == protectionIndex) "${item.label}: $stateText" else item.label
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(item.label) } },
                    state = rememberTooltipState(),
                ) {
                    Box(Modifier.size(52.dp)) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(if (active) c.primary else Color.Transparent)
                                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                                .semantics { contentDescription = description },
                            contentAlignment = Alignment.Center,
                        ) { Icon(item.icon, contentDescription = null, tint = if (active) c.onPrimary else c.ink2) }
                        if (index == protectionIndex && protection != ProtectionState.Off) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(12.dp)
                                    .clip(CircleShape).background(c.surface).padding(2.dp)
                                    .clip(CircleShape).background(if (protection == ProtectionState.On) c.accent else c.warn),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} ч ${minutes % 60} мин" else "$minutes мин"
}

private fun shortDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60}ч%02d".format(minutes % 60) else "${minutes}м"
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
