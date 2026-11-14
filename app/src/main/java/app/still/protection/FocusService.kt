package app.still.protection

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import app.still.StillApp
import app.still.core.Bounds
import app.still.core.Detection
import app.still.core.Detectors
import app.still.core.ScrollGuard
import app.still.core.ScrollSignal
import app.still.data.Interruption
import app.still.data.ProtectionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.time.ZoneId

class FocusService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as StillApp
    private lateinit var masks: ButtonMasks
    private var settings = ProtectionSettings()
    private val guard = ScrollGuard()
    private var scanJob: Job? = null
    private var exitJob: Job? = null
    private var lastScan = 0L

    override fun onServiceConnected() {
        scope.coroutineContext.cancelChildren()
        if (::masks.isInitialized) masks.clear()
        guard.reset()
        masks = ButtonMasks(this)
        ProtectionStatus.live.value = LiveStatus(connected = true)
        scope.launch {
            app.settings.settings.collect {
                settings = it
                // Any rule change cancels a pending exit and immediately removes old masks.
                exitJob?.cancel()
                clear()
                inspect()
            }
        }
        scope.launch {
            // Revalidate static masks and foreground identity even if an app emits no events.
            while (isActive) { delay(700); inspect() }
        }
        scope.launch {
            val cutoff = LocalDate.now().minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            runCatching { app.database.events().prune(cutoff) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::masks.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // Our non-focusable overlays must not reset the underlying session.
        if (pkg !in Detectors.packages) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                clear()
            }
            return
        }
        if (!settings.protects(pkg)) { clear(); return }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val source = event.source ?: return
            val signal = try {
                val rect = Rect()
                source.getBoundsInScreen(rect)
                ScrollSignal(NodeReader.sourcePath(source), event.fromIndex, event.toIndex, event.scrollDeltaY, rect.height())
            } catch (_: RuntimeException) { return } finally { NodeReader.recycle(source) }
            val observed = inspect() ?: return
            if (observed.first == pkg && guard.shouldExit(observed.second, signal, SystemClock.elapsedRealtime())) exitViewer(pkg)
        } else {
            // Coalesce content changes; scroll signals above are never discarded by this throttle.
            if (SystemClock.elapsedRealtime() - lastScan >= 120) inspect()
            else if (scanJob?.isActive != true) scanJob = scope.launch { delay(120); inspect() }
        }
    }

    private fun inspect(): Pair<String, Detection>? {
        if (!::masks.isInitialized) return null
        lastScan = SystemClock.elapsedRealtime()
        if (!getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            clear(); return null
        }
        val root = rootInActiveWindow ?: run { clear(); return null }
        try {
            val pkg = root.packageName?.toString().orEmpty()
            if (!settings.protects(pkg)) { clear(); return null }
            val screenRect = Rect()
            root.getBoundsInScreen(screenRect)
            val screen = Bounds(screenRect.left, screenRect.top, screenRect.right, screenRect.bottom)
            val detection = Detectors.detect(pkg, NodeReader.snapshot(root), screen)
            guard.update(pkg, detection, lastScan)
            val applied = masks.sync(detection.masks)
            ProtectionStatus.live.value = LiveStatus(
                connected = true, packageName = pkg, masks = if (applied) detection.masks.size else 0, viewer = detection.viewer,
                note = when {
                    !applied -> "Android не разрешил наложить маску"
                    detection.comments -> "Комментарии: выход по прокрутке приостановлен"
                    detection.viewer -> "Распознан плеер коротких видео; ожидаем перелистывание"
                    detection.masks.isNotEmpty() -> "Кнопки скрыты; совместимость экспериментальная"
                    else -> "На текущем экране кнопки и плеер не распознаны"
                },
            )
            return pkg to detection
        } catch (_: RuntimeException) {
            clear("Не удалось прочитать интерфейс. Защита на этом экране недоступна")
            return null
        } finally { NodeReader.recycle(root) }
    }

    private fun exitViewer(pkg: String) {
        if (exitJob?.isActive == true) return
        val sessionToken = guard.sessionToken
        exitJob = scope.launch {
            masks.clear()
            val backAccepted = performGlobalAction(GLOBAL_ACTION_BACK)
            delay(500)
            if (!settings.protects(pkg)) return@launch
            val remaining = inspect()
            // Do not navigate away from a different app the user has switched to.
            val stillInViewer = remaining?.first == pkg && remaining.second.viewer && guard.sessionToken == sessionToken
            val success = if (stillInViewer) performGlobalAction(GLOBAL_ACTION_HOME) else backAccepted
            if (success) {
                Toast.makeText(this@FocusService, "Пауза. Один ролик — достаточно.", Toast.LENGTH_SHORT).show()
                runCatching {
                    val now = System.currentTimeMillis()
                    app.database.events().insert(Interruption(packageName = pkg, timestamp = now))
                    app.database.events().prune(now - 30L * 24 * 60 * 60 * 1000)
                }
            } else {
                ProtectionStatus.live.value = ProtectionStatus.live.value.copy(note = "Android не выполнил возврат. Выйдите из ленты вручную")
            }
        }
    }

    private fun clear(note: String = "Откройте Instagram или YouTube для проверки распознавания") {
        if (::masks.isInitialized) masks.clear()
        guard.reset()
        ProtectionStatus.live.value = LiveStatus(connected = true, note = note)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        clear()
    }
    override fun onInterrupt() { clear("Сервис прерван системой") }
    override fun onDestroy() {
        scope.cancel()
        if (::masks.isInitialized) masks.clear()
        ProtectionStatus.live.value = LiveStatus()
        super.onDestroy()
    }
}
