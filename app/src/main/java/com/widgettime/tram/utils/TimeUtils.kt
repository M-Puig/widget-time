package com.widgettime.tram.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Utility functions for time formatting.
 */
object TimeUtils {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /**
     * Format minutes until arrival in a human-readable way.
     */
    fun formatMinutesUntilArrival(minutes: Int): String {
        return when {
            minutes <= 0 -> "Now"
            minutes == 1 -> "1 min"
            minutes < 60 -> "$minutes min"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes == 0) {
                    "${hours}h"
                } else {
                    "${hours}h ${remainingMinutes}m"
                }
            }
        }
    }

    /**
     * Get the current time formatted as HH:mm.
     */
    fun getCurrentTime(): String {
        return timeFormat.format(Calendar.getInstance().time)
    }

    /**
     * Calculate minutes between two times.
     */
    fun minutesBetween(startMillis: Long, endMillis: Long): Int {
        val diffMillis = endMillis - startMillis
        return TimeUnit.MILLISECONDS.toMinutes(diffMillis).toInt()
    }

    /**
     * Format a timestamp to a readable time string.
     */
    fun formatTime(timeMillis: Long): String {
        return timeFormat.format(timeMillis)
    }
}
