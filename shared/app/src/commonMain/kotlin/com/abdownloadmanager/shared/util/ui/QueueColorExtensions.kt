package com.abdownloadmanager.shared.util.ui

import androidx.compose.ui.graphics.Color

/**
 * Returns a color from the theme's queue color palette for the given queue ID.
 *
 * Uses modulo operation to cycle through available colors when there are
 * more queues than colors in the palette.
 *
 * @param queueId The unique identifier of the queue
 * @return A color from the theme's queueColors palette
 */
fun MyColors.getQueueColor(queueId: Long): Color {
    return queueColors[(queueId % queueColors.size).toInt()]
}
