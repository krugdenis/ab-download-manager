package com.abdownloadmanager.shared.storage

import com.abdownloadmanager.shared.util.SpeedLimitDefaults
import io.github.amir1376.schemakt.Schema
import io.github.amir1376.schemakt.ValidationError
import io.github.amir1376.schemakt.ValidationResult
import ir.amirab.util.config.MapToJsonObject
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

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

/**
 * The datastore writes this value as a nested JSON object and reads it back as a plain [Map],
 * so it is converted to a [JsonObject] again before decoding.
 */
val SpeedScheduleSchema = object : Schema<SpeedSchedule> {
    private val json = Json { ignoreUnknownKeys = true }

    override fun parse(value: Any?): ValidationResult<SpeedSchedule> {
        if (value !is Map<*, *>) {
            return ValidationResult.Failure(ValidationError("expected an object"))
        }
        @Suppress("UNCHECKED_CAST")
        val jsonObject = MapToJsonObject().transformMap(value as Map<String, Any?>)
        return try {
            ValidationResult.Success(json.decodeFromJsonElement(jsonObject))
        } catch (e: Exception) {
            ValidationResult.Failure(ValidationError(e.message ?: "invalid speed schedule"))
        }
    }
}
