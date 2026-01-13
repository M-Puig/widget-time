package com.widgettime.tram

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.widgettime.tram.data.TramRepository
import com.widgettime.tram.data.models.WidgetConfig
import com.widgettime.tram.worker.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Widget provider for displaying tram arrival times on the home screen.
 * Supports multiple stops per widget with prev/next navigation.
 */
class TramWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update each widget instance
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Schedule periodic updates when first widget is added
        scheduleWidgetUpdates(context)
    }

    override fun onDisabled(context: Context) {
        // Cancel updates when last widget is removed
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        
        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, TramWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            ACTION_NEXT_STOP -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    scope.launch {
                        val repository = TramRepository.getInstance(context)
                        repository.nextStop(appWidgetId)
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                }
            }
            ACTION_PREV_STOP -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    scope.launch {
                        val repository = TramRepository.getInstance(context)
                        repository.prevStop(appWidgetId)
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                }
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        scope.launch {
            val repository = TramRepository.getInstance(context)
            val config = repository.getWidgetConfig(appWidgetId)
            
            val views = RemoteViews(context.packageName, R.layout.widget_tram_multi)
            
            val currentStop = config.currentStop()
            
            if (currentStop != null) {
                // Show loading state
                views.setTextViewText(R.id.widget_station_name, "Loading...")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                
                try {
                    val arrivals = repository.getFilteredArrivals(currentStop.stationId, currentStop.filter)
                    
                    // Set station name with filter indicator
                    views.setTextViewText(R.id.widget_station_name, currentStop.displayName())
                    
                    if (arrivals.isNotEmpty()) {
                        Log.d("TramWidget", "Got ${arrivals.size} arrivals for station ${currentStop.stationId}")
                        arrivals.forEachIndexed { index, arr -> 
                            Log.d("TramWidget", "  [$index] ${arr.line} → ${arr.destination}: ${arr.arrivalTime}")
                        }
                        val nextArrival = arrivals.first()
                        views.setTextViewText(
                            R.id.widget_next_tram,
                            "${nextArrival.line} → ${nextArrival.destination}"
                        )
                        
                        // Show up to 2 arrival times separated by "|"
                        val timeText = if (arrivals.size > 1) {
                            "${nextArrival.arrivalTime} | ${arrivals[1].arrivalTime}"
                        } else {
                            nextArrival.arrivalTime
                        }
                        views.setTextViewText(R.id.widget_time, timeText)
                        
                        // Show second arrival line info if different line
                        if (arrivals.size > 1) {
                            val secondArrival = arrivals[1]
                            if (secondArrival.line != nextArrival.line || secondArrival.destination != nextArrival.destination) {
                                views.setTextViewText(
                                    R.id.widget_next_tram_2,
                                    "${secondArrival.line} → ${secondArrival.destination}"
                                )
                            } else {
                                views.setTextViewText(R.id.widget_next_tram_2, "")
                            }
                        } else {
                            views.setTextViewText(R.id.widget_next_tram_2, "")
                        }
                    } else {
                        val noDataMsg = if (currentStop.filter.isActive()) {
                            "No ${currentStop.filter.line} trams scheduled"
                        } else {
                            "No trams scheduled"
                        }
                        views.setTextViewText(R.id.widget_next_tram, noDataMsg)
                        views.setTextViewText(R.id.widget_time, "--")
                        views.setTextViewText(R.id.widget_next_tram_2, "")
                    }
                } catch (e: Exception) {
                    Log.e("TramWidget", "Error updating widget: ${e.message}", e)
                    views.setTextViewText(R.id.widget_station_name, "Error")
                    views.setTextViewText(R.id.widget_next_tram, "Tap to retry")
                    views.setTextViewText(R.id.widget_time, "--")
                    views.setTextViewText(R.id.widget_next_tram_2, "")
                }
                
                // Set up navigation buttons visibility and page indicator
                if (config.hasMultipleStops()) {
                    views.setViewVisibility(R.id.widget_prev_button, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_next_button, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_page_indicator, View.VISIBLE)
                    
                    // Create page indicator dots
                    val indicator = config.stops.mapIndexed { index, _ ->
                        if (index == config.currentIndex) "●" else "○"
                    }.joinToString(" ")
                    views.setTextViewText(R.id.widget_page_indicator, indicator)
                    
                    // Set up prev button
                    val prevIntent = Intent(context, TramWidgetProvider::class.java).apply {
                        action = ACTION_PREV_STOP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val prevPendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 100 + 1,  // Unique request code for prev
                        prevIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_prev_button, prevPendingIntent)
                    
                    // Set up next button
                    val nextIntent = Intent(context, TramWidgetProvider::class.java).apply {
                        action = ACTION_NEXT_STOP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val nextPendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 100 + 2,  // Unique request code for next
                        nextIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_next_button, nextPendingIntent)
                } else {
                    views.setViewVisibility(R.id.widget_prev_button, View.GONE)
                    views.setViewVisibility(R.id.widget_next_button, View.GONE)
                    views.setViewVisibility(R.id.widget_page_indicator, View.GONE)
                }
            } else {
                views.setTextViewText(R.id.widget_station_name, "Tap to configure")
                views.setTextViewText(R.id.widget_next_tram, "")
                views.setTextViewText(R.id.widget_time, "")
                views.setTextViewText(R.id.widget_next_tram_2, "")
                views.setViewVisibility(R.id.widget_prev_button, View.GONE)
                views.setViewVisibility(R.id.widget_next_button, View.GONE)
                views.setViewVisibility(R.id.widget_page_indicator, View.GONE)
            }
            
            // Set up refresh click action
            val refreshIntent = Intent(context, TramWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
            
            // Set up configuration click action
            val configIntent = Intent(context, TramWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, configPendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun scheduleWidgetUpdates(context: Context) {
        val updateRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            15, TimeUnit.MINUTES  // Minimum interval for periodic work
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    companion object {
        private const val WORK_NAME = "tram_widget_update"
        const val ACTION_REFRESH = "com.widgettime.tram.ACTION_REFRESH"
        const val ACTION_NEXT_STOP = "com.widgettime.tram.ACTION_NEXT_STOP"
        const val ACTION_PREV_STOP = "com.widgettime.tram.ACTION_PREV_STOP"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, TramWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TramWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(intent)
        }
    }
}
