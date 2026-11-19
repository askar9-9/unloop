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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UsageReport(val granted: Boolean, val days: List<UsageDay> = emptyList(), val labels: Map<String, String> = emptyMap(), val error: String? = null)

class UsageRepository(private val context: Context, private val store: UsageDao) {
    fun hasAccess(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    suspend fun report(): UsageReport = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext UsageReport(false)
        try {
            val now = ZonedDateTime.now()
            val today = now.toLocalDate()
            val start = today.minusDays(LIVE_DAYS - 1L).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val end = now.toInstant().toEpochMilli()
            // A preceding day seeds sessions that cross midnight. Android may retain fewer events.
            val queryStart = today.minusDays(LIVE_DAYS.toLong()).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val source = context.getSystemService(UsageStatsManager::class.java).queryEvents(queryStart, end)
                ?: return@withContext UsageReport(true, error = "Android временно не выдал историю. Разблокируйте телефон и откройте Unloop снова.")
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
            val live = UsageTimeline.aggregate(transitions, start, end, ZoneId.systemDefault())
            val days = merge(live, today)
            val labels = days.flatMap { it.apps.keys }.distinct().associateWith { pkg ->
                runCatching {
                    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
            }
            UsageReport(true, days, labels)
        } catch (_: SecurityException) {
            UsageReport(false)
        } catch (_: RuntimeException) {
            UsageReport(true, error = "Не удалось прочитать статистику Android.")
        }
    }

    /** Saves finished live days and fills older ones from storage. Only per-app daily totals are kept. */
    private suspend fun merge(live: List<UsageDay>, today: LocalDate): List<UsageDay> {
        val from = today.minusDays(HISTORY_DAYS - 1L)
        val stored = store.days(from.toString()).associateBy { it.date }
        val storedApps = store.apps(from.toString()).groupBy { it.date }
        live.filter { it.date < today && it.totalMs > 0 }.forEach { day ->
            val key = day.date.toString()
            store.keepFuller(StoredDay(key, day.totalMs), day.apps.map { (pkg, ms) -> StoredApp(key, pkg, ms) }, stored[key])
        }
        store.pruneDays(today.minusDays(KEEP_DAYS).toString())
        store.pruneApps(today.minusDays(KEEP_DAYS).toString())
        val liveByDate = live.associateBy { it.date }
        return (0 until HISTORY_DAYS).map { from.plusDays(it.toLong()) }.map { date ->
            val fromLive = liveByDate[date]
            val saved = stored[date.toString()]
            if (saved != null && saved.totalMs > (fromLive?.totalMs ?: 0)) {
                UsageDay(date, saved.totalMs, storedApps[date.toString()].orEmpty().associate { it.packageName to it.ms })
            } else fromLive ?: UsageDay(date, 0, emptyMap())
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { store.clear() }

    companion object {
        private const val LIVE_DAYS = 8
        /** Shown in the app: five rolling weeks. */
        const val HISTORY_DAYS = 35
        private const val KEEP_DAYS = 90L
    }
}
