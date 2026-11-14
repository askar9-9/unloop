package app.still.core

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class UsageTimelineTest {
    private val zone = ZoneId.of("UTC")
    private val start = LocalDate.of(2026, 11, 14).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun event(offset: Long, kind: TransitionKind, pkg: String = "a", activity: String = "Main") = UsageTransition(start + offset, pkg, activity, kind)
    private fun report(events: List<UsageTransition>, end: Long = 10_000) = UsageTimeline.aggregate(events, start, start + end, zone)

    @Test fun normalSessionAndDuplicateResumeCountOnce() {
        val day = report(listOf(event(1000, TransitionKind.RESUME), event(2000, TransitionKind.RESUME), event(5000, TransitionKind.PAUSE))).single()
        assertEquals(4000L, day.totalMs)
        assertEquals(4000L, day.apps.getValue("a"))
    }
    @Test fun activityHandoffDoesNotCloseNewActivity() {
        val day = report(listOf(event(0, TransitionKind.RESUME, activity = "Splash"), event(1000, TransitionKind.RESUME), event(2000, TransitionKind.PAUSE, activity = "Splash"), event(6000, TransitionKind.PAUSE))).single()
        assertEquals(6000L, day.totalMs)
    }
    @Test fun multiWindowUsesUnionForTotal() {
        val day = report(listOf(event(0, TransitionKind.RESUME), event(1000, TransitionKind.RESUME, "b"), event(4000, TransitionKind.PAUSE), event(6000, TransitionKind.PAUSE, "b"))).single()
        assertEquals(6000L, day.totalMs)
        assertEquals(4000L, day.apps.getValue("a"))
        assertEquals(5000L, day.apps.getValue("b"))
    }
    @Test fun screenOffEndsSessionsAndDoesNotResumeOldApp() {
        val day = report(listOf(event(0, TransitionKind.RESUME), event(2000, TransitionKind.SCREEN_OFF), event(6000, TransitionKind.SCREEN_ON))).single()
        assertEquals(2000L, day.totalMs)
    }
    @Test fun wakingLockedScreenIsNotUsage() {
        val day = report(listOf(event(0, TransitionKind.LOCK), event(1000, TransitionKind.SCREEN_ON), event(2000, TransitionKind.RESUME), event(6000, TransitionKind.UNLOCK), event(7000, TransitionKind.RESUME))).single()
        assertEquals(3000L, day.totalMs)
    }
    @Test fun openSessionIsClippedToNow() {
        assertEquals(9000L, report(listOf(event(1000, TransitionKind.RESUME))).single().totalMs)
    }
    @Test fun previousDaySeedsSessionAcrossMidnight() {
        val days = report(listOf(event(-2000, TransitionKind.RESUME), event(3000, TransitionKind.PAUSE)))
        assertEquals(3000L, days.single().totalMs)
    }
    @Test fun sessionSplitsAtLocalMidnight() {
        val dayMs = 86_400_000L
        val days = report(listOf(event(dayMs - 1000, TransitionKind.RESUME), event(dayMs + 2000, TransitionKind.PAUSE)), dayMs + 5000)
        assertEquals(listOf(1000L, 2000L), days.map { it.totalMs })
    }
    @Test fun daylightSavingDayHasCorrectLength() {
        val ny = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 3, 8)
        val from = date.atStartOfDay(ny).toInstant().toEpochMilli()
        val until = date.plusDays(1).atStartOfDay(ny).toInstant().toEpochMilli()
        val days = UsageTimeline.aggregate(listOf(UsageTransition(from, "a", "Main", TransitionKind.RESUME)), from, until, ny)
        assertEquals(23 * 3_600_000L, days.single().totalMs)
    }
    @Test fun rebootClearsOldSessions() {
        val day = report(listOf(event(0, TransitionKind.RESUME), event(1000, TransitionKind.SHUTDOWN), event(2000, TransitionKind.STARTUP), event(3000, TransitionKind.RESUME, "b"), event(4000, TransitionKind.PAUSE, "b"))).single()
        assertEquals(2000L, day.totalMs)
    }
    @Test fun emptyHistoryProducesZeroDaysWithoutInventingUsage() {
        assertEquals(0L, report(emptyList()).single().totalMs)
        assertTrue(UsageTimeline.aggregate(emptyList(), start, start, zone).isEmpty())
    }
}
