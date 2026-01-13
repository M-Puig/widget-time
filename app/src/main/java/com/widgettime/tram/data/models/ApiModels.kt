package com.widgettime.tram.data.models

import com.google.gson.annotations.SerializedName

/**
 * API response models for transit data.
 * Adjust these based on your actual transit API response format.
 */

data class StationsResponse(
    @SerializedName("stations")
    val stations: List<StationApiModel>
)

data class StationApiModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("lines")
    val lines: List<String>,
    @SerializedName("latitude")
    val latitude: Double?,
    @SerializedName("longitude")
    val longitude: Double?
)

data class ArrivalsResponse(
    @SerializedName("arrivals")
    val arrivals: List<ArrivalApiModel>
)

data class ArrivalApiModel(
    @SerializedName("line")
    val line: String,
    @SerializedName("destination")
    val destination: String,
    @SerializedName("arrival_time")
    val arrivalTime: String,
    @SerializedName("minutes")
    val minutes: Int
)

// Extension functions to convert API models to domain models
fun StationApiModel.toDomain(): Station = Station(
    id = id,
    name = name,
    lines = lines,
    latitude = latitude,
    longitude = longitude
)

fun ArrivalApiModel.toDomain(): TramArrival = TramArrival(
    line = line,
    destination = destination,
    arrivalTime = arrivalTime,
    minutesUntilArrival = minutes
)

/**
 * Widget filter configuration for line and direction.
 * When set, only arrivals matching this filter will be shown.
 */
data class WidgetFilter(
    val line: String?,       // e.g., "T1", "T2", or null for all lines
    val direction: String?   // e.g., "Odysseum", "Mosson", or null for all directions
) {
    companion object {
        val NONE = WidgetFilter(null, null)
    }
    
    fun matches(arrival: TramArrival): Boolean {
        if (line != null && arrival.line != line) return false
        if (direction != null && arrival.destination != direction) return false
        return true
    }
    
    fun isActive(): Boolean = line != null || direction != null
}

/**
 * Represents a line-direction pair for selection in the config UI.
 */
data class LineDirection(
    val line: String,
    val direction: String
) {
    override fun toString(): String = "$line → $direction"
}
