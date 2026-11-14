package app.still.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import app.still.core.TransitionKind
import app.still.core.UsageDay
import app.still.core.UsageTimeline
import app.still.core.UsageTransition
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UsageReport(val granted: Boolean, val days: List<UsageDay> = emptyList(), val labels: Map<String, String> = emptyMap(), val error: String? = null)

class UsageRepository(private val context: Context) {
    fun hasAccess(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    suspend fun report(): UsageReport = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext UsageReport(false)
        try {
            val now = ZonedDateTime.now()
            val start = now.toLocalDate().minusDays(6).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val end = now.toInstant().toEpochMilli()
            // A preceding day seeds sessions that cross midnight. Android may retain fewer events.
            val queryStart = now.toLocalDate().minusDays(7).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val source = context.getSystemService(UsageStatsManager::class.java).queryEvents(queryStart, end)
                ?: return@withContext UsageReport(true, error = "Android временно не предоставил историю. Разблокируйте телефон и обновите данные.")
            val transitions = mutableListOf<UsageTransition>()
            val event = UsageEvents.Event()
            while (source.hasNextEvent()) {
                source.getNextEvent(event)
                val kind = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> TransitionKind.RESUME
                    UsageEvents.Event.ACTIVITY_PAUSED -> TransitionKind.PAUSE
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> TransitionKind.SCREEN_OFF
                    UsageEvents.Event.SCREEN_INTERACTIVE -> TransitionKind.SCREEN_ON
                    UsageEvents.Event.KEYGUARD_SHOWN -> TransitionKind.LOCK
                    UsageEvents.Event.KEYGUARD_HIDDEN -> TransitionKind.UNLOCK
                    UsageEvents.Event.DEVICE_SHUTDOWN -> TransitionKind.SHUTDOWN
                    UsageEvents.Event.DEVICE_STARTUP -> TransitionKind.STARTUP
                    else -> null
                } ?: continue
                // Ignore STOPPED: a delayed stop from an old activity must not close a
                // newly resumed activity of the same class. PAUSED closes sessions.
                transitions += UsageTransition(event.timeStamp, event.packageName.orEmpty(), event.className.orEmpty(), kind)
            }
            val days = UsageTimeline.aggregate(transitions, start, end, ZoneId.systemDefault())
            val labels = days.flatMap { it.apps.keys }.distinct().associateWith { pkg ->
                runCatching {
                    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
            }
            UsageReport(true, days, labels)
        } catch (_: SecurityException) {
            UsageReport(false)
        } catch (_: RuntimeException) {
            UsageReport(true, error = "Не удалось прочитать статистику Android. Попробуйте обновить данные.")
        }
    }
}
