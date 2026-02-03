package com.abdownloadmanager.shared.ui.configurable.item

import com.abdownloadmanager.shared.ui.configurable.BaseLongConfigurable
import com.abdownloadmanager.shared.ui.configurable.Configurable
import ir.amirab.util.compose.StringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Configurable for speed limit settings with toggle functionality.
 *
 * @param lastCustomLimit Optional StateFlow to persist the last non-zero custom limit value.
 *                        Used by the UI to restore previous limit when toggling from unlimited (0)
 *                        back to limited mode. If null, the toggle functionality won't persist state.
 */
class SpeedLimitConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<Long>,
    describe: (Long) -> StringSource,
    val lastCustomLimit: MutableStateFlow<Long>? = null,
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : BaseLongConfigurable(
    title = title,
    description = description,
    backedBy = backedBy,
    describe = describe,
    range = 0..Long.MAX_VALUE,
    enabled = enabled,
    visible = visible,
) {
    object Key : Configurable.Key

    override fun getKey() = Key
}
