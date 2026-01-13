package com.widgettime.tram.data

import retrofit2.http.GET
import okhttp3.ResponseBody

/**
 * Retrofit API interface for Montpellier GTFS-RT transit data.
 * 
 * Uses the Montpellier TAM GTFS-RT API (transport.data.gouv.fr)
 * GTFS-RT provides real-time vehicle positions, trip updates, and service alerts via Protocol Buffers.
 */
interface TramApi {

    /**
     * Get GTFS-RT trip updates (real-time arrival/departure times).
     * Returns protobuf-encoded FeedMessage
     */
    @GET("trip_updates")
    suspend fun getTripUpdates(): ResponseBody

    /**
     * Get GTFS-RT vehicle positions.
     * Returns protobuf-encoded FeedMessage
     */
    @GET("vehicle_positions")
    suspend fun getVehiclePositions(): ResponseBody

    /**
     * Get GTFS-RT service alerts.
     * Returns protobuf-encoded FeedMessage
     */
    @GET("service_alerts")
    suspend fun getServiceAlerts(): ResponseBody
}
