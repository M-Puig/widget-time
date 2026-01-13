package com.widgettime.tram.data

import android.util.Log
import com.google.protobuf.CodedInputStream
import com.google.protobuf.ExtensionRegistryLite
import java.io.InputStream

/**
 * Simple GTFS-RT parser using protobuf-lite.
 * Parses the essential fields needed for displaying real-time tram arrivals.
 * 
 * Based on GTFS-realtime.proto specification.
 */
object GtfsRtParser {
    private const val TAG = "GtfsRtParser"
    
    // FeedMessage fields
    private const val FEED_HEADER = 1
    private const val FEED_ENTITY = 2
    
    // FeedEntity fields
    private const val ENTITY_ID = 1
    private const val ENTITY_TRIP_UPDATE = 3
    
    // TripUpdate fields
    private const val TRIP_UPDATE_TRIP = 1
    private const val TRIP_UPDATE_STOP_TIME_UPDATE = 2
    
    // TripDescriptor fields
    private const val TRIP_TRIP_ID = 1
    private const val TRIP_ROUTE_ID = 5
    
    // StopTimeUpdate fields
    private const val STU_STOP_SEQUENCE = 1
    private const val STU_STOP_ID = 4
    private const val STU_ARRIVAL = 2
    private const val STU_DEPARTURE = 3
    
    // StopTimeEvent fields
    private const val STE_TIME = 2
    
    data class FeedMessage(
        val header: FeedHeader,
        val entities: List<FeedEntity>
    )
    
    data class FeedHeader(
        val timestamp: Long
    )
    
    data class FeedEntity(
        val id: String,
        val tripUpdate: TripUpdate?
    )
    
    data class TripUpdate(
        val trip: TripDescriptor,
        val stopTimeUpdates: List<StopTimeUpdate>
    )
    
    data class TripDescriptor(
        val tripId: String,
        val routeId: String
    )
    
    data class StopTimeUpdate(
        val stopId: String,
        val stopSequence: Int,
        val arrivalTime: Long?,
        val departureTime: Long?
    )
    
    /**
     * Parse a GTFS-RT feed from an input stream.
     */
    fun parseFeed(inputStream: InputStream): FeedMessage? {
        return try {
            val input = CodedInputStream.newInstance(inputStream)
            parseFeedMessage(input)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GTFS-RT feed: ${e.message}", e)
            null
        }
    }
    
    private fun parseFeedMessage(input: CodedInputStream): FeedMessage {
        var header = FeedHeader(0L)
        val entities = mutableListOf<FeedEntity>()
        
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            
            when (fieldNumber) {
                FEED_HEADER -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    header = parseFeedHeader(input)
                    input.popLimit(limit)
                }
                FEED_ENTITY -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    entities.add(parseFeedEntity(input))
                    input.popLimit(limit)
                }
                else -> input.skipField(tag)
            }
        }
        
        return FeedMessage(header, entities)
    }
    
    private fun parseFeedHeader(input: CodedInputStream): FeedHeader {
        var timestamp = 0L
        
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            
            when (fieldNumber) {
                2 -> timestamp = input.readUInt64() // timestamp field
                else -> input.skipField(tag)
            }
        }
        
        return FeedHeader(timestamp)
    }
    
    private fun parseFeedEntity(input: CodedInputStream): FeedEntity {
        var id = ""
        var tripUpdate: TripUpdate? = null
        
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            
            when (fieldNumber) {
                ENTITY_ID -> id = input.readString()
                ENTITY_TRIP_UPDATE -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    tripUpdate = parseTripUpdate(input)
                    input.popLimit(limit)
                }
                else -> input.skipField(tag)
            }
        }
        
        return FeedEntity(id, tripUpdate)
    }
    
    private fun parseTripUpdate(input: CodedInputStream): TripUpdate {
        var trip = TripDescriptor("", "")
        val stopTimeUpdates = mutableListOf<StopTimeUpdate>()
        
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            
            when (fieldNumber) {
                TRIP_UPDATE_TRIP -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    trip = parseTripDescriptor(input)
                    input.popLimit(limit)
                }
                TRIP_UPDATE_STOP_TIME_UPDATE -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    stopTimeUpdates.add(parseStopTimeUpdate(input))
                    input.popLimit(limit)
                }
                else -> input.skipField(tag)
            }
        }
        
        return TripUpdate(trip, stopTimeUpdates)
    }
    
    private fun parseTripDescriptor(input: CodedInputStream): TripDescriptor {
        var tripId = ""
        var routeId = ""
        
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            
            when (fieldNumber) {
                TRIP_TRIP_ID -> tripId = input.readString()
                TRIP_ROUTE_ID -> routeId = input.readString()
                else -> input.skipField(tag)
            }
        }
        
        return TripDescriptor(tripId, routeId)
    }
    
    private fun parseStopTimeUpdate(input: CodedInputStream): StopTimeUpdate {
        var stopId = ""
        var stopSequence = 0
        var arrivalTime: Long? = null
        var departureTime: Long? = null
        
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            
            when (fieldNumber) {
                STU_STOP_SEQUENCE -> stopSequence = input.readUInt32()
                STU_STOP_ID -> stopId = input.readString()
                STU_ARRIVAL -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    arrivalTime = parseStopTimeEvent(input)
                    input.popLimit(limit)
                }
                STU_DEPARTURE -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    departureTime = parseStopTimeEvent(input)
                    input.popLimit(limit)
                }
                else -> input.skipField(tag)
            }
        }
        
        return StopTimeUpdate(stopId, stopSequence, arrivalTime, departureTime)
    }
    
    private fun parseStopTimeEvent(input: CodedInputStream): Long? {
        var time: Long? = null
        
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            
            when (fieldNumber) {
                STE_TIME -> time = input.readInt64()
                else -> input.skipField(tag)
            }
        }
        
        return time
    }
}
