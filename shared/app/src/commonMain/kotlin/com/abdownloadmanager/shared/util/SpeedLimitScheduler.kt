@file:OptIn(ExperimentalTime::class)

package com.abdownloadmanager.shared.util

import com.abdownloadmanager.shared.storage.SpeedSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Manages scheduled speed limit enforcement based on time-of-day and days-of-week.
 *
 * Automatically switches between normal and alternative speed limits according
 * to a user-defined schedule. Supports overnight schedules that span midnight.
 *
 * ## Overnight Schedule Handling
 * When `startTime > endTime` (e.g., 18:00 to 12:00), the schedule is treated as
 * overnight, spanning two calendar days:
 * - If current time is 00:00-12:00, checks if previous day is in `daysOfWeek`
 * - If current time is 18:00-23:59, checks if current day is in `daysOfWeek`
 *
 * Example: Schedule from Friday 18:00 to Saturday 12:00
 * - Friday 19:00: Active (current day = Friday, which is in schedule)
 * - Saturday 02:00: Active (previous day = Friday, which is in schedule)
 * - Saturday 13:00: Inactive (outside time range)
 *
 * ## Timezone & DST Handling
 * - Uses `TimeZone.currentSystemDefault()` for all time calculations
 * - During DST transitions:
 *   - Spring forward (clock advances): May skip a scheduled period entirely
 *   - Fall back (clock repeats): May trigger schedule twice for the same hour
 * - Timezone changes during runtime are NOT automatically detected
 * - Schedule transitions are recalculated after each state change
 *
 * ## Known Limitations
 * - Schedule precision is minute-level (seconds are ignored)
 * - DST transitions may cause up to 1 hour of scheduling drift
 * - Requires app restart to detect system timezone changes
 *
 * @param scope CoroutineScope for managing scheduler lifecycle
 * @param scheduleFlow StateFlow of schedule configuration (updates are reactive)
 */
class SpeedLimitScheduler(
    private val scope: CoroutineScope,
    private val scheduleFlow: StateFlow<SpeedSchedule>,
) {
    private var schedulerJob: Job? = null
    private val _isScheduleActive = MutableStateFlow(false)
    val isScheduleActive: StateFlow<Boolean> = _isScheduleActive.asStateFlow()

    private companion object {
        /**
         * Minimum delay between schedule checks in milliseconds.
         *
         * This fallback delay protects against:
         * - System clock changes (backward time adjustments)
         * - Negative or zero calculated delays
         * - Busy-waiting in edge cases
         *
         * Value of 1000ms (1 second) provides sufficient granularity
         * for minute-level schedule precision while preventing CPU spinning.
         */
        const val FALLBACK_DELAY_MS = 1000L
    }

    fun start() {
        scheduleFlow
            .onEach { schedule ->
                restartScheduler(schedule)
            }
            .launchIn(scope)
    }

    fun isActiveNow(): Boolean = isCurrentlyInScheduledTime(scheduleFlow.value)

    suspend fun stop() {
        schedulerJob?.cancelAndJoin()
        schedulerJob = null
        _isScheduleActive.value = false
    }

    private fun restartScheduler(schedule: SpeedSchedule) {
        schedulerJob?.cancel()

        if (!schedule.enabled) {
            _isScheduleActive.value = false
            return
        }

        schedulerJob = scope.launch {
            while (isActive) {
                val isCurrentlyScheduled = isCurrentlyInScheduledTime(schedule)
                _isScheduleActive.value = isCurrentlyScheduled

                val nextTransition = calculateNextTransitionTime(schedule)
                val delayMs = nextTransition - Clock.System.now().toEpochMilliseconds()

                // Guarantee minimum delay even if system clock changed
                delay(maxOf(delayMs, FALLBACK_DELAY_MS))
            }
        }
    }

    /**
     * Checks if the current system time falls within the configured schedule.
     *
     * Handles overnight schedules by adjusting day-of-week checks:
     * - For overnight schedules (startTime > endTime):
     *   - If current time ≤ endTime: checks if PREVIOUS day is in daysOfWeek
     *   - If current time > endTime: checks if CURRENT day is in daysOfWeek
     *
     * @param schedule The speed schedule configuration
     * @return true if current time is within active schedule period
     */
    private fun isCurrentlyInScheduledTime(schedule: SpeedSchedule): Boolean {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentTime = now.time
        val isOvernight = schedule.startTime > schedule.endTime
        val dayToCheck = when {
            isOvernight && currentTime <= schedule.endTime -> now.dayOfWeek.minus(1)
            else -> now.dayOfWeek
        }
        if (dayToCheck !in schedule.daysOfWeek) return false

        return when {
            !isOvernight -> currentTime in schedule.startTime..schedule.endTime
            else -> currentTime >= schedule.startTime || currentTime <= schedule.endTime
        }
    }

    /**
     * Calculates the next time when the schedule state will change (start or end).
     *
     * For overnight schedules, the end time is considered to occur on the day
     * following the scheduled day, so we shift the valid days forward by one.
     *
     * @param schedule The speed schedule configuration
     * @return Unix timestamp (milliseconds) of the next schedule transition
     */
    private fun calculateNextTransitionTime(schedule: SpeedSchedule): Long {
        val nowInstant = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val nextStart = nextOccurrence(
            nowInstant,
            schedule.startTime,
            schedule.daysOfWeek,
            timeZone,
        )
        // For overnight schedules (start > end), map end time to next day's weekdays
        // Example: If schedule is Mon 18:00 -> 12:00, end occurs on Tue
        val endDays = when {
            schedule.startTime > schedule.endTime -> schedule.daysOfWeek.map { it.plus(1) }.toSet()
            else -> schedule.daysOfWeek
        }
        val nextEnd = nextOccurrence(
            nowInstant,
            schedule.endTime,
            endDays,
            timeZone,
        )
        return minInstant(nextStart, nextEnd).toEpochMilliseconds()
    }

    /**
     * Finds the next occurrence of a specific time on valid days-of-week.
     *
     * Searches forward up to 7 days to find the next instance where:
     * 1. The day-of-week is in the validDays set
     * 2. The resulting instant is in the future
     *
     * Uses timezone-aware date arithmetic to handle DST transitions correctly.
     */
    private fun nextOccurrence(
        nowInstant: Instant,
        time: LocalTime,
        validDays: Set<DayOfWeek>,
        timeZone: TimeZone,
    ): Instant {
        // Search up to 7 days ahead for next valid occurrence
        // Handles timezone offsets and DST via TimeZone-aware arithmetic
        for (dayOffset in 0..7) {
            val candidateDate = nowInstant
                .plus(dayOffset, DateTimeUnit.DAY, timeZone)
                .toLocalDateTime(timeZone)
                .date
            if (candidateDate.dayOfWeek !in validDays) continue
            val candidate = LocalDateTime(candidateDate, time).toInstant(timeZone)
            if (candidate > nowInstant) {
                return candidate
            }
        }
        return nowInstant.plus(1, DateTimeUnit.DAY, timeZone)
    }

    private fun minInstant(first: Instant, second: Instant): Instant {
        return if (first < second) first else second
    }
}

private fun DayOfWeek.plus(days: Int): DayOfWeek {
    val entries = DayOfWeek.entries
    val size = entries.size
    val index = ((entries.indexOf(this) + days) % size + size) % size
    return entries[index]
}

private fun DayOfWeek.minus(days: Int): DayOfWeek = plus(-days)
