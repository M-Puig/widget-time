package com.widgettime.tram.data.models

/**
 * Represents a tram station.
 */
data class Station(
    val id: String,
    val name: String,
    val lines: List<String>,
    val latitude: Double? = null,
    val longitude: Double? = null
)
