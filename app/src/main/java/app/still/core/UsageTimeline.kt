package app.still.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TransitionKind { RESUME, PAUSE, SCREEN_OFF, SCREEN_ON, LOCK, UNLOCK, SHUTDOWN, STARTUP }
data class UsageTransition(val at: Long, val packageName: String = "", val activity: String = "", val kind: TransitionKind)
data class UsageDay(val date: LocalDate, val totalMs: Long, val apps: Map<String, Long>)

/** Integrates the union of foreground activities; overlapping windows count once in total. */
object UsageTimeline {
    fun aggregate(events: List<UsageTransition>, start: Long, end: Long, zone: ZoneId): List<UsageDay> {
        if (end <= start) return emptyList()
        val totals = linkedMapOf<LocalDate, Long>()
        val apps = linkedMapOf<LocalDate, MutableMap<String, Long>>()
        var day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        while (day.atStartOfDay(zone).toInstant().toEpochMilli() < end) {
            totals[day] = 0
            apps[day] = mutableMapOf()
            day = day.plusDays(1)
        }
        // Public UsageEvents exposes className, not instanceId. Repeated instances of the
        // same class cannot be distinguished; this is an estimate, not a billing clock.
        val active = mutableSetOf<Pair<String, String>>()
        var interactive = true
        var locked = false
        var cursor = start
        fun accumulate(until: Long) {
            val bound = until.coerceIn(start, end)
            var from = cursor
            val packages = active.map { it.first }.toSet()
            while (from < bound && interactive && !locked && packages.isNotEmpty()) {
                val date = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
                val next = minOf(bound, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                val duration = next - from
                totals[date] = totals.getValue(date) + duration
                packages.forEach { pkg -> apps.getValue(date).merge(pkg, duration, Long::plus) }
                from = next
            }
            cursor = bound
        }
        events.sortedBy { it.at }.asSequence().filter { it.at <= end }.forEach { event ->
            if (event.at >= start) accumulate(event.at)
            when (event.kind) {
                TransitionKind.RESUME -> if (interactive && !locked) active.add(event.packageName to event.activity)
                TransitionKind.PAUSE -> active.remove(event.packageName to event.activity)
                TransitionKind.SCREEN_OFF, TransitionKind.SHUTDOWN -> { active.clear(); interactive = false }
                TransitionKind.SCREEN_ON -> interactive = true
                TransitionKind.LOCK -> { active.clear(); locked = true }
                TransitionKind.UNLOCK -> locked = false
                TransitionKind.STARTUP -> { active.clear(); interactive = true; locked = false }
            }
        }
        accumulate(end)
        return totals.map { (date, total) -> UsageDay(date, total, apps.getValue(date).toMap()) }
    }
}
