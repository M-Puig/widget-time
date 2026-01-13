package com.widgettime.tram

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.widgettime.tram.data.TramRepository
import com.widgettime.tram.worker.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Widget provider for displaying tram arrival times on the home screen.
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
        
        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, TramWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, appWidgetIds)
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
            val stationId = repository.getWidgetStation(appWidgetId)
            val filter = repository.getWidgetFilter(appWidgetId)
            
            val views = RemoteViews(context.packageName, R.layout.widget_tram)
            
            if (stationId != null) {
                // Show loading state
                views.setTextViewText(R.id.widget_station_name, "Loading...")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                
                try {
                    val arrivals = repository.getFilteredArrivals(stationId, filter)
                    val stationName = repository.getStationName(stationId)
                    
                    // Add filter indicator to station name if filter is active
                    val displayName = if (filter.isActive()) {
                        "$stationName (${filter.line})"
                    } else {
                        stationName
                    }
                    views.setTextViewText(R.id.widget_station_name, displayName)
                    
                    if (arrivals.isNotEmpty()) {
                        Log.d("TramWidget", "Got ${arrivals.size} arrivals for station $stationId")
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
                        val noDataMsg = if (filter.isActive()) {
                            "No ${filter.line} trams scheduled"
                        } else {
                            "No trams scheduled"
                        }
                        views.setTextViewText(R.id.widget_next_tram, noDataMsg)
                        views.setTextViewText(R.id.widget_time, "--")
                        views.setTextViewText(R.id.widget_next_tram_2, "")
                    }
                } catch (e: Exception) {
                    views.setTextViewText(R.id.widget_station_name, "Error")
                    views.setTextViewText(R.id.widget_next_tram, "Tap to retry")
                    views.setTextViewText(R.id.widget_time, "--")
                    views.setTextViewText(R.id.widget_next_tram_2, "")
                }
            } else {
                views.setTextViewText(R.id.widget_station_name, "Tap to configure")
                views.setTextViewText(R.id.widget_next_tram, "")
                views.setTextViewText(R.id.widget_time, "")
                views.setTextViewText(R.id.widget_next_tram_2, "")
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
