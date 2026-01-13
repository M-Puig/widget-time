package com.widgettime.tram.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.widgettime.tram.data.models.LineDirection
import com.widgettime.tram.data.models.Station
import com.widgettime.tram.data.models.StopConfig
import com.widgettime.tram.data.models.TramArrival
import com.widgettime.tram.data.models.WidgetConfig
import com.widgettime.tram.data.models.WidgetFilter
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

// DataStore for widget preferences
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_prefs")

/**
 * Repository for fetching Montpellier GTFS-RT tram data and managing widget preferences.
 * 
 * Montpellier TAM GTFS Data:
 * - Static GTFS: https://data.montpellier3m.fr/sites/default/files/ressources/TAM_MMM_GTFS.zip
 * - Real-time GTFS-RT: https://transport.data.gouv.fr/gtfs_rt/tam_montpellier/
 */
class TramRepository private constructor(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Cache for station data
    private var stationsCache: List<Station>? = null
    
    // Mapping of child stop_id to parent station_id
    private var childToParentMap: Map<String, String> = emptyMap()
    
    // Route information: route_id -> route_short_name (e.g., "1" -> "T1")
    private var routeNames: Map<String, String> = emptyMap()
    
    // Trip information: trip_id -> TripInfo (route_id, headsign)
    private var tripInfoMap: Map<String, TripInfo> = emptyMap()
    
    // Flag to check if GTFS data has been loaded
    private var gtfsDataLoaded = false
    
    data class TripInfo(
        val routeId: String,
        val headsign: String,
        val tripShortName: String  // e.g., "4a", "4b" for circular lines
    )

    /**
     * Get all available stations from GTFS data.
     * Downloads and parses stops.txt from the Montpellier GTFS ZIP file.
     */
    suspend fun getStations(): List<Station> = withContext(Dispatchers.IO) {
        // Return cached data if available
        stationsCache?.let { return@withContext it }
        
        try {
            loadGtfsData()
            stationsCache?.let { return@withContext it }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading GTFS data: ${e.message}")
        }
        
        // Fallback to cached/hardcoded stations if download fails
        getFallbackStations().also { stationsCache = it }
    }

    /**
     * Download and parse all needed GTFS static data.
     */
    private suspend fun loadGtfsData() = withContext(Dispatchers.IO) {
        if (gtfsDataLoaded) return@withContext
        
        val request = Request.Builder()
            .url(GTFS_URL)
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to download GTFS: ${response.code}")
        }
        
        val stations = mutableListOf<Station>()
        val childToParent = mutableMapOf<String, String>()
        val routes = mutableMapOf<String, String>()
        val trips = mutableMapOf<String, TripInfo>()
        
        response.body?.byteStream()?.use { inputStream ->
            ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                var stopsData: String? = null
                var routesData: String? = null
                var tripsData: String? = null
                
                // Read all needed files
                while (entry != null) {
                    when (entry.name.lowercase()) {
                        "stops.txt" -> {
                            stopsData = BufferedReader(InputStreamReader(zipStream, Charsets.UTF_8)).readText()
                        }
                        "routes.txt" -> {
                            routesData = BufferedReader(InputStreamReader(zipStream, Charsets.UTF_8)).readText()
                        }
                        "trips.txt" -> {
                            tripsData = BufferedReader(InputStreamReader(zipStream, Charsets.UTF_8)).readText()
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
                
                // Parse routes.txt - get route_id -> route_short_name mapping
                routesData?.let { data ->
                    parseRoutes(data, routes)
                }
                Log.d(TAG, "Parsed ${routes.size} routes")
                
                // Parse trips.txt - get trip_id -> (route_id, headsign) mapping
                tripsData?.let { data ->
                    parseTrips(data, trips)
                }
                Log.d(TAG, "Parsed ${trips.size} trips")
                
                // Parse stops.txt - get parent stations and child->parent mapping
                stopsData?.let { data ->
                    parseStops(data, stations, childToParent)
                }
                Log.d(TAG, "Parsed ${stations.size} stations, ${childToParent.size} child stops")
            }
        }
        
        // Store parsed data
        stationsCache = stations.sortedBy { it.name }.distinctBy { it.name }
        childToParentMap = childToParent
        routeNames = routes
        tripInfoMap = trips
        gtfsDataLoaded = true
        
        Log.d(TAG, "GTFS data fully loaded")
    }
    
    private fun normalizeData(data: String): String {
        return data
            .removePrefix("\uFEFF") // Remove UTF-8 BOM if present
            .replace("\r\n", "\n")
            .replace("\r", "\n")
    }
    
    private fun parseRoutes(data: String, routes: MutableMap<String, String>) {
        val normalizedData = normalizeData(data)
        val lines = normalizedData.lines()
        if (lines.isEmpty()) return
        
        val headers = lines[0].split(",").map { it.trim().lowercase().replace("\"", "") }
        val routeIdIdx = headers.indexOf("route_id")
        val routeShortNameIdx = headers.indexOf("route_short_name")
        
        if (routeIdIdx < 0 || routeShortNameIdx < 0) return
        
        lines.drop(1).forEach { line ->
            if (line.isNotBlank()) {
                val values = parseCsvLine(line)
                val routeId = values.getOrNull(routeIdIdx)?.replace("\"", "")?.trim() ?: ""
                val routeShortName = values.getOrNull(routeShortNameIdx)?.replace("\"", "")?.trim() ?: ""
                
                if (routeId.isNotEmpty() && routeShortName.isNotEmpty()) {
                    routes[routeId] = routeShortName
                }
            }
        }
    }
    
    private fun parseTrips(data: String, trips: MutableMap<String, TripInfo>) {
        val normalizedData = normalizeData(data)
        val lines = normalizedData.lines()
        if (lines.isEmpty()) return
        
        val headers = lines[0].split(",").map { it.trim().lowercase().replace("\"", "") }
        val tripIdIdx = headers.indexOf("trip_id")
        val routeIdIdx = headers.indexOf("route_id")
        val headsignIdx = headers.indexOf("trip_headsign")
        val tripShortNameIdx = headers.indexOf("trip_short_name")
        
        if (tripIdIdx < 0 || routeIdIdx < 0) return
        
        lines.drop(1).forEach { line ->
            if (line.isNotBlank()) {
                val values = parseCsvLine(line)
                val tripId = values.getOrNull(tripIdIdx)?.replace("\"", "")?.trim() ?: ""
                val routeId = values.getOrNull(routeIdIdx)?.replace("\"", "")?.trim() ?: ""
                val headsign = if (headsignIdx >= 0) {
                    values.getOrNull(headsignIdx)?.replace("\"", "")?.trim() ?: ""
                } else ""
                val tripShortName = if (tripShortNameIdx >= 0) {
                    values.getOrNull(tripShortNameIdx)?.replace("\"", "")?.trim() ?: ""
                } else ""
                
                if (tripId.isNotEmpty() && routeId.isNotEmpty()) {
                    trips[tripId] = TripInfo(routeId, headsign, tripShortName)
                }
            }
        }
    }
    
    private fun parseStops(data: String, stations: MutableList<Station>, childToParent: MutableMap<String, String>) {
        val normalizedData = normalizeData(data)
        val lines = normalizedData.lines()
        if (lines.isEmpty()) return
        
        val headers = lines[0].split(",").map { it.trim().lowercase().replace("\"", "") }
        val stopIdIdx = headers.indexOf("stop_id")
        val stopNameIdx = headers.indexOf("stop_name")
        val locationTypeIdx = headers.indexOf("location_type")
        val parentStationIdx = headers.indexOf("parent_station")
        
        if (stopIdIdx < 0 || stopNameIdx < 0) return
        
        // Track stops by name to group them (for flat structure)
        val stopsByName = mutableMapOf<String, MutableList<String>>()
        var hasParentStations = false
        
        lines.drop(1).forEach { line ->
            if (line.isNotBlank()) {
                val values = parseCsvLine(line)
                
                val minSize = maxOf(stopIdIdx, stopNameIdx) + 1
                if (values.size >= minSize) {
                    val stopId = values.getOrNull(stopIdIdx)?.replace("\"", "")?.trim() ?: ""
                    val stopName = values.getOrNull(stopNameIdx)?.replace("\"", "")?.trim() ?: ""
                    val locationType = if (locationTypeIdx >= 0 && values.size > locationTypeIdx) {
                        values.getOrNull(locationTypeIdx)?.replace("\"", "")?.trim() ?: ""
                    } else ""
                    val parentStation = if (parentStationIdx >= 0 && values.size > parentStationIdx) {
                        values.getOrNull(parentStationIdx)?.replace("\"", "")?.trim() ?: ""
                    } else ""
                    
                    if (stopId.isEmpty() || stopName.isEmpty()) return@forEach
                    
                    // Track stops by name for grouping
                    stopsByName.getOrPut(stopName) { mutableListOf() }.add(stopId)
                    
                    // Handle parent-child structure if present
                    if (locationType == "1") {
                        hasParentStations = true
                        stations.add(Station(
                            id = stopId,
                            name = stopName,
                            lines = listOf()
                        ))
                    } else if (parentStation.isNotEmpty()) {
                        // Child stop - map to parent
                        childToParent[stopId] = parentStation
                    }
                }
            }
        }
        
        // If no parent stations found, use flat structure (group by name)
        if (!hasParentStations && stations.isEmpty()) {
            Log.d(TAG, "Using flat structure - grouping ${stopsByName.size} station names")
            stopsByName.forEach { (name, stopIds) ->
                // Use the first stop_id as the "station" id
                val primaryStopId = stopIds.first()
                stations.add(Station(
                    id = primaryStopId,
                    name = name,
                    lines = listOf()
                ))
                // Map all other stop_ids to the primary one
                stopIds.drop(1).forEach { stopId ->
                    childToParent[stopId] = primaryStopId
                }
                if (stopIds.size > 1) {
                    Log.d(TAG, "Station '$name': primary=$primaryStopId, children=${stopIds.drop(1)}")
                }
            }
        }
    }
    
    /**
     * Parse a CSV line handling quoted values with commas.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    /**
     * Get real-time arrivals for a station.
     */
    suspend fun getTramArrivals(stationId: String): List<TramArrival> = withContext(Dispatchers.IO) {
        try {
            // Ensure GTFS data is loaded for mappings
            if (!gtfsDataLoaded) {
                loadGtfsData()
            }
            
            // Fetch GTFS-RT data
            val arrivals = fetchRealtimeArrivals(stationId)
            if (arrivals.isNotEmpty()) {
                return@withContext arrivals
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching real-time data: ${e.message}", e)
        }
        
        // Fallback to mock data if real-time fails
        getMockArrivals(stationId)
    }
    
    /**
     * Fetch and parse GTFS-RT TripUpdates for a specific station.
     */
    private suspend fun fetchRealtimeArrivals(stationId: String): List<TramArrival> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GTFS_RT_URL)
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch GTFS-RT: ${response.code}")
        }
        
        val arrivals = mutableListOf<TramArrival>()
        val currentTime = System.currentTimeMillis() / 1000
        
        response.body?.byteStream()?.use { inputStream ->
            val feed = GtfsRtParser.parseFeed(inputStream)
                ?: throw Exception("Failed to parse GTFS-RT feed")
            
            // Find the primary station ID (resolve if this stationId is a child)
            val primaryStationId = childToParentMap[stationId] ?: stationId
            
            // Find all child stop_ids that belong to this parent station
            val childStopIds = childToParentMap.entries
                .filter { it.value == primaryStationId }
                .map { it.key }
                .toSet()
            
            // Include the primary station and all its children
            val relevantStopIds = childStopIds + primaryStationId
            Log.d(TAG, "Station $stationId (primary: $primaryStationId): looking for stop_ids: $relevantStopIds (${relevantStopIds.size} total)")
            
            for (entity in feed.entities) {
                val tripUpdate = entity.tripUpdate ?: continue
                
                val tripId = tripUpdate.trip.tripId
                val tripInfo = tripInfoMap[tripId] ?: continue
                
                // Use trip_short_name if available (e.g., "4a", "4b"), otherwise use route name
                val routeName = routeNames[tripInfo.routeId] ?: tripInfo.routeId
                val lineName = if (tripInfo.tripShortName.isNotEmpty()) {
                    tripInfo.tripShortName.uppercase()
                } else {
                    routeName
                }
                val destination = tripInfo.headsign.ifEmpty { "---" }
                
                for (stopTimeUpdate in tripUpdate.stopTimeUpdates) {
                    val stopId = stopTimeUpdate.stopId
                    
                    if (stopId !in relevantStopIds) continue
                    
                    // Get arrival time (prefer arrival, fallback to departure)
                    val arrivalTime = stopTimeUpdate.arrivalTime 
                        ?: stopTimeUpdate.departureTime 
                        ?: continue
                    
                    val minutesUntilArrival = ((arrivalTime - currentTime) / 60).toInt()
                    
                    // Only show future arrivals (within next 90 minutes)
                    if (minutesUntilArrival in 0..90) {
                        val displayTime = when {
                            minutesUntilArrival <= 0 -> "Now"
                            minutesUntilArrival == 1 -> "1 min"
                            else -> "$minutesUntilArrival min"
                        }
                        
                        arrivals.add(TramArrival(
                            line = lineName,
                            destination = destination,
                            arrivalTime = displayTime,
                            minutesUntilArrival = minutesUntilArrival
                        ))
                    }
                }
            }
        }
        
        // Sort by arrival time (don't limit here - filtering happens later)
        val sortedArrivals = arrivals.sortedBy { it.minutesUntilArrival }
        Log.d(TAG, "Found ${arrivals.size} raw arrivals, returning ${sortedArrivals.size} sorted for station $stationId")
        sortedArrivals
    }

    /**
     * Get the station name for a given station ID.
     */
    suspend fun getStationName(stationId: String): String {
        return getStations().find { it.id == stationId }?.name ?: "Unknown Station"
    }

    /**
     * Save the selected station for a widget.
     */
    suspend fun saveWidgetStation(widgetId: Int, stationId: String) {
        context.dataStore.edit { preferences ->
            preferences[widgetStationKey(widgetId)] = stationId
        }
    }

    /**
     * Get the saved station for a widget.
     */
    suspend fun getWidgetStation(widgetId: Int): String? {
        return context.dataStore.data.map { preferences ->
            preferences[widgetStationKey(widgetId)]
        }.first()
    }

    /**
     * Remove the saved station for a widget.
     */
    suspend fun removeWidgetStation(widgetId: Int) {
        context.dataStore.edit { preferences ->
            preferences.remove(widgetStationKey(widgetId))
        }
    }

    private fun widgetStationKey(widgetId: Int) = stringPreferencesKey("widget_$widgetId")
    private fun widgetLineKey(widgetId: Int) = stringPreferencesKey("widget_${widgetId}_line")
    private fun widgetDirectionKey(widgetId: Int) = stringPreferencesKey("widget_${widgetId}_direction")
    private fun widgetConfigKey(widgetId: Int) = stringPreferencesKey("widget_${widgetId}_config")
    private fun widgetCurrentIndexKey(widgetId: Int) = stringPreferencesKey("widget_${widgetId}_current_index")

    /**
     * Save the filter (line + direction) for a widget.
     */
    suspend fun saveWidgetFilter(widgetId: Int, filter: WidgetFilter) {
        context.dataStore.edit { preferences ->
            if (filter.line != null) {
                preferences[widgetLineKey(widgetId)] = filter.line
            } else {
                preferences.remove(widgetLineKey(widgetId))
            }
            if (filter.direction != null) {
                preferences[widgetDirectionKey(widgetId)] = filter.direction
            } else {
                preferences.remove(widgetDirectionKey(widgetId))
            }
        }
    }

    /**
     * Get the saved filter for a widget.
     */
    suspend fun getWidgetFilter(widgetId: Int): WidgetFilter {
        return context.dataStore.data.map { preferences ->
            WidgetFilter(
                line = preferences[widgetLineKey(widgetId)],
                direction = preferences[widgetDirectionKey(widgetId)]
            )
        }.first()
    }

    // ==================== Multi-stop configuration ====================

    /**
     * Save the widget configuration with multiple stops.
     */
    suspend fun saveWidgetConfig(widgetId: Int, config: WidgetConfig) {
        val json = serializeWidgetConfig(config)
        context.dataStore.edit { preferences ->
            preferences[widgetConfigKey(widgetId)] = json
        }
    }

    /**
     * Get the widget configuration with multiple stops.
     * Falls back to legacy single-stop config if no multi-stop config exists.
     */
    suspend fun getWidgetConfig(widgetId: Int): WidgetConfig {
        return context.dataStore.data.map { preferences ->
            val json = preferences[widgetConfigKey(widgetId)]
            if (json != null) {
                deserializeWidgetConfig(json)
            } else {
                // Legacy fallback: convert old single-station config to new format
                val stationId = preferences[widgetStationKey(widgetId)]
                if (stationId != null) {
                    val filter = WidgetFilter(
                        line = preferences[widgetLineKey(widgetId)],
                        direction = preferences[widgetDirectionKey(widgetId)]
                    )
                    val stationName = getStationName(stationId)
                    WidgetConfig(
                        stops = listOf(StopConfig(stationId, stationName, filter)),
                        currentIndex = 0
                    )
                } else {
                    WidgetConfig()
                }
            }
        }.first()
    }

    /**
     * Add a stop to the widget configuration.
     */
    suspend fun addStopToWidget(widgetId: Int, stop: StopConfig) {
        val config = getWidgetConfig(widgetId)
        val newConfig = config.copy(stops = config.stops + stop)
        saveWidgetConfig(widgetId, newConfig)
    }

    /**
     * Update the current index (for navigation).
     */
    suspend fun setWidgetCurrentIndex(widgetId: Int, index: Int) {
        val config = getWidgetConfig(widgetId)
        val newConfig = config.copy(currentIndex = index.coerceIn(0, maxOf(0, config.stops.size - 1)))
        saveWidgetConfig(widgetId, newConfig)
    }

    /**
     * Navigate to next stop.
     */
    suspend fun nextStop(widgetId: Int): WidgetConfig {
        val config = getWidgetConfig(widgetId)
        val newConfig = config.copy(currentIndex = config.nextIndex())
        saveWidgetConfig(widgetId, newConfig)
        return newConfig
    }

    /**
     * Navigate to previous stop.
     */
    suspend fun prevStop(widgetId: Int): WidgetConfig {
        val config = getWidgetConfig(widgetId)
        val newConfig = config.copy(currentIndex = config.prevIndex())
        saveWidgetConfig(widgetId, newConfig)
        return newConfig
    }

    /**
     * Remove a widget configuration entirely.
     */
    suspend fun removeWidgetConfig(widgetId: Int) {
        context.dataStore.edit { preferences ->
            preferences.remove(widgetConfigKey(widgetId))
            // Also clean up legacy keys
            preferences.remove(widgetStationKey(widgetId))
            preferences.remove(widgetLineKey(widgetId))
            preferences.remove(widgetDirectionKey(widgetId))
        }
    }

    private fun serializeWidgetConfig(config: WidgetConfig): String {
        val json = JSONObject()
        json.put("currentIndex", config.currentIndex)
        val stopsArray = JSONArray()
        for (stop in config.stops) {
            val stopJson = JSONObject()
            stopJson.put("stationId", stop.stationId)
            stopJson.put("stationName", stop.stationName)
            stopJson.put("filterLine", stop.filter.line)
            stopJson.put("filterDirection", stop.filter.direction)
            stopsArray.put(stopJson)
        }
        json.put("stops", stopsArray)
        return json.toString()
    }

    private fun deserializeWidgetConfig(jsonStr: String): WidgetConfig {
        return try {
            val json = JSONObject(jsonStr)
            val currentIndex = json.optInt("currentIndex", 0)
            val stopsArray = json.optJSONArray("stops") ?: JSONArray()
            val stops = mutableListOf<StopConfig>()
            for (i in 0 until stopsArray.length()) {
                val stopJson = stopsArray.getJSONObject(i)
                val filter = WidgetFilter(
                    line = stopJson.optString("filterLine", null).takeIf { it.isNotEmpty() },
                    direction = stopJson.optString("filterDirection", null).takeIf { it.isNotEmpty() }
                )
                stops.add(StopConfig(
                    stationId = stopJson.getString("stationId"),
                    stationName = stopJson.getString("stationName"),
                    filter = filter
                ))
            }
            WidgetConfig(stops = stops, currentIndex = currentIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing widget config: ${e.message}")
            WidgetConfig()
        }
    }

    /**
     * Get available lines and directions at a station by fetching current real-time data.
     * Returns a list of unique line-direction combinations currently available.
     */
    suspend fun getAvailableLinesAtStation(stationId: String): List<LineDirection> = withContext(Dispatchers.IO) {
        try {
            // Ensure GTFS data is loaded
            if (!gtfsDataLoaded) {
                loadGtfsData()
            }
            
            val arrivals = fetchRealtimeArrivals(stationId)
            arrivals
                .map { LineDirection(it.line, it.destination) }
                .distinct()
                .sortedWith(compareBy({ it.line }, { it.direction }))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available lines: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get filtered arrivals for a widget (applies line/direction filter if set).
     */
    suspend fun getFilteredArrivals(stationId: String, filter: WidgetFilter): List<TramArrival> {
        val arrivals = getTramArrivals(stationId)
        Log.d(TAG, "getFilteredArrivals: got ${arrivals.size} arrivals, filter active: ${filter.isActive()}")
        val result = if (filter.isActive()) {
            val filtered = arrivals.filter { filter.matches(it) }
            Log.d(TAG, "After filter: ${filtered.size} arrivals")
            filtered
        } else {
            arrivals
        }
        // Limit to top 6 after filtering
        return result.take(6)
    }

    // Fallback stations if GTFS download fails
    private fun getFallbackStations(): List<Station> = listOf(
        Station("S5902", "École de Chimie", listOf("T1")),
        Station("S5997", "Pôle Chimie Balard", listOf("T1")),
        Station("S1190", "Mosson", listOf("T1")),
        Station("S1241", "Odysseum", listOf("T1")),
        Station("S1206", "Gare Saint-Roch", listOf("T1", "T2", "T4")),
        Station("S1199", "Corum", listOf("T1", "T2")),
        Station("S1201", "Place de l'Europe", listOf("T1")),
        Station("S1203", "Antigone", listOf("T1")),
        Station("S1198", "Comédie", listOf("T1", "T2", "T3", "T4")),
        Station("S1196", "Peyrou - Arc de Triomphe", listOf("T1"))
    )

    private fun getMockArrivals(stationId: String): List<TramArrival> {
        return listOf(
            TramArrival("T1", "Odysseum", "2 min", 2),
            TramArrival("T1", "Mosson", "5 min", 5),
            TramArrival("T2", "Saint-Jean-de-Védas", "8 min", 8),
            TramArrival("T1", "Odysseum", "12 min", 12)
        ).take(4)
    }

    companion object {
        private const val TAG = "TramRepository"
        
        // Montpellier TAM GTFS static data (Urban network)
        private const val GTFS_URL = "https://data.montpellier3m.fr/GTFS/Urbain/GTFS.zip"
        
        // GTFS-RT real-time TripUpdate data (Urban network)
        private const val GTFS_RT_URL = "https://data.montpellier3m.fr/GTFS/Urbain/TripUpdate.pb"

        @Volatile
        private var instance: TramRepository? = null

        fun getInstance(context: Context): TramRepository {
            return instance ?: synchronized(this) {
                instance ?: TramRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
