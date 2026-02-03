package com.abdownloadmanager.shared.util

/**
 * Default values and constraints for speed limiting.
 */
object SpeedLimitDefaults {
    /**
     * Minimum allowed speed limit in bytes per second.
     * Value: 256 KB/s (262,144 bytes/s)
     *
     * This prevents setting unreasonably low limits that would
     * effectively stall downloads.
     */
    const val MIN_LIMIT_BYTES: Long = 256 * 1024L  // 256 KB/s
}
