package com.abdownloadmanager.shared.storage

import com.abdownloadmanager.shared.util.SpeedLimitDefaults
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * Configuration for time-based speed limit scheduling.
 *
 * Allows switching to an alternative speed limit during specific
 * times and days of the week. Supports overnight schedules that span midnight.
 *
 * @property enabled Whether the schedule is active
 * @property daysOfWeek Days when the schedule applies (must not be empty)
 * @property startTime Start of the scheduled period
 * @property endTime End of the scheduled period (can be < startTime for overnight)
 * @property alternativeSpeedLimit Speed limit to apply during scheduled period (bytes/sec)
 */
@Serializable
data class SpeedSchedule(
    val enabled: Boolean = false,
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val startTime: LocalTime = LocalTime(18, 0),
    val endTime: LocalTime = LocalTime(12, 0),
    val alternativeSpeedLimit: Long = SpeedLimitDefaults.MIN_LIMIT_BYTES,
) {
    companion object {
        fun default() = SpeedSchedule()
    }

    init {
        require(daysOfWeek.isNotEmpty()) {
            "Speed schedule must have at least one active day"
        }
        require(alternativeSpeedLimit >= SpeedLimitDefaults.MIN_LIMIT_BYTES) {
            "Alternative speed limit must be at least ${SpeedLimitDefaults.MIN_LIMIT_BYTES} bytes (256 KB)"
        }
    }
}
