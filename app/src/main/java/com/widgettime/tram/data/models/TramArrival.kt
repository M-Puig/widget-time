package com.widgettime.tram.data.models

/**
 * Represents a tram arrival at a station.
 */
data class TramArrival(
    val line: String,
    val destination: String,
    val arrivalTime: String,
    val minutesUntilArrival: Int
)
