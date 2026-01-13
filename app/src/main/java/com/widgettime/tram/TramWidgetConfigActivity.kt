package com.widgettime.tram

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.widgettime.tram.data.TramRepository
import com.widgettime.tram.data.models.LineDirection
import com.widgettime.tram.data.models.Station
import com.widgettime.tram.data.models.StopConfig
import com.widgettime.tram.data.models.WidgetConfig
import com.widgettime.tram.data.models.WidgetFilter
import com.widgettime.tram.databinding.ActivityConfigBinding
import kotlinx.coroutines.launch

/**
 * Configuration activity that appears when adding a new widget to the home screen.
 * Multi-step flow:
 * 1. Select a station
 * 2. Optionally select a line/direction filter
 * 3. Optionally add more stops (repeat 1-2)
 * 4. Done - finish configuration
 */
class TramWidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private lateinit var repository: TramRepository
    private lateinit var stationAdapter: StationAdapter
    private lateinit var lineDirectionAdapter: LineDirectionAdapter
    
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedStation: Station? = null
    private var allStations: List<Station> = emptyList()
    
    // Multi-stop support
    private var configuredStops: MutableList<StopConfig> = mutableListOf()
    
    private enum class Step { SELECT_STATION, SELECT_LINE_DIRECTION }
    private var currentStep = Step.SELECT_STATION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set the result to CANCELED. This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)
        
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = TramRepository.getInstance(this)
        
        // Get the widget ID from the intent that launched this activity
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        // If we didn't get a valid widget ID, just bail
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        setupToolbar()
        setupRecyclerViews()
        setupSearch()
        
        // Load existing configuration if editing an existing widget
        lifecycleScope.launch {
            val existingConfig = repository.getWidgetConfig(appWidgetId)
            if (existingConfig.stops.isNotEmpty()) {
                configuredStops.addAll(existingConfig.stops)
            }
            showStationSelection()
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        // Initially hide the back button for first step
        binding.toolbar.navigationIcon = null
    }

    private fun setupRecyclerViews() {
        stationAdapter = StationAdapter { station ->
            onStationSelected(station)
        }
        
        binding.recyclerViewStations.apply {
            layoutManager = LinearLayoutManager(this@TramWidgetConfigActivity)
            adapter = stationAdapter
        }
        
        lineDirectionAdapter = LineDirectionAdapter { lineDirection ->
            onLineDirectionSelected(lineDirection)
        }
        
        binding.recyclerViewLineDirections.apply {
            layoutManager = LinearLayoutManager(this@TramWidgetConfigActivity)
            adapter = lineDirectionAdapter
        }
    }
    
    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterStations(s?.toString() ?: "")
            }
        })
    }
    
    private fun filterStations(query: String) {
        val filtered = if (query.isBlank()) {
            allStations
        } else {
            allStations.filter { station ->
                station.name.contains(query, ignoreCase = true)
            }
        }
        stationAdapter.submitList(filtered)
    }
    
    private fun showStationSelection() {
        currentStep = Step.SELECT_STATION
        
        // Show back button if we have existing stops (to allow finishing)
        if (configuredStops.isNotEmpty()) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        } else {
            binding.toolbar.navigationIcon = null
        }
        
        binding.stepTitle.text = getString(R.string.select_station)
        
        // Show count of configured stops
        if (configuredStops.isNotEmpty()) {
            binding.stepSubtitle.text = getString(R.string.stops_configured, configuredStops.size)
            binding.stepSubtitle.visibility = View.VISIBLE
        } else {
            binding.stepSubtitle.visibility = View.GONE
        }
        
        binding.searchInputLayout.visibility = View.VISIBLE
        binding.searchEditText.text?.clear()
        binding.recyclerViewStations.visibility = View.VISIBLE
        binding.recyclerViewLineDirections.visibility = View.GONE
        
        // Show "Done" button if we have at least one stop configured
        if (configuredStops.isNotEmpty()) {
            binding.skipFilterButton.visibility = View.VISIBLE
            binding.skipFilterButton.text = getString(R.string.done)
            binding.skipFilterButton.setOnClickListener {
                finishConfiguration()
            }
        } else {
            binding.skipFilterButton.visibility = View.GONE
        }
        
        loadStations()
    }
    
    private fun showLineDirectionSelection() {
        currentStep = Step.SELECT_LINE_DIRECTION
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.stepTitle.text = getString(R.string.select_line_direction)
        binding.stepSubtitle.text = getString(R.string.filter_optional)
        binding.stepSubtitle.visibility = View.VISIBLE
        binding.searchInputLayout.visibility = View.GONE
        binding.recyclerViewStations.visibility = View.GONE
        binding.recyclerViewLineDirections.visibility = View.VISIBLE
        
        loadLineDirections()
    }

    private fun loadStations() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewStations.visibility = View.GONE
        binding.errorText.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val stations = repository.getStations()
                
                binding.progressBar.visibility = View.GONE
                
                if (stations.isNotEmpty()) {
                    allStations = stations
                    binding.recyclerViewStations.visibility = View.VISIBLE
                    stationAdapter.submitList(stations)
                } else {
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = getString(R.string.no_stations_found)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = getString(R.string.error_loading_stations)
                
                binding.retryButton.visibility = View.VISIBLE
                binding.retryButton.setOnClickListener {
                    binding.retryButton.visibility = View.GONE
                    loadStations()
                }
            }
        }
    }
    
    private fun loadLineDirections() {
        val station = selectedStation ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewLineDirections.visibility = View.GONE
        binding.errorText.visibility = View.GONE
        binding.skipFilterButton.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val lineDirections = repository.getAvailableLinesAtStation(station.id)
                
                binding.progressBar.visibility = View.GONE
                
                if (lineDirections.isNotEmpty()) {
                    binding.recyclerViewLineDirections.visibility = View.VISIBLE
                    lineDirectionAdapter.submitList(lineDirections)
                    
                    // Show "Show all lines" button
                    binding.skipFilterButton.visibility = View.VISIBLE
                    binding.skipFilterButton.text = getString(R.string.show_all_lines)
                    binding.skipFilterButton.setOnClickListener {
                        addStopAndContinue(WidgetFilter.NONE)
                    }
                } else {
                    // No lines found - might be no service right now
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = getString(R.string.no_lines_found)
                    
                    binding.skipFilterButton.visibility = View.VISIBLE
                    binding.skipFilterButton.text = getString(R.string.show_all_lines)
                    binding.skipFilterButton.setOnClickListener {
                        addStopAndContinue(WidgetFilter.NONE)
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = getString(R.string.error_loading_lines)
                
                binding.skipFilterButton.visibility = View.VISIBLE
                binding.skipFilterButton.text = getString(R.string.show_all_lines)
                binding.skipFilterButton.setOnClickListener {
                    addStopAndContinue(WidgetFilter.NONE)
                }
            }
        }
    }

    private fun onStationSelected(station: Station) {
        selectedStation = station
        // Move to line/direction selection (don't save yet)
        showLineDirectionSelection()
    }
    
    private fun onLineDirectionSelected(lineDirection: LineDirection) {
        val filter = WidgetFilter(
            line = lineDirection.line,
            direction = lineDirection.direction
        )
        addStopAndContinue(filter)
    }
    
    /**
     * Add the current station+filter as a stop and go back to station selection
     * to potentially add more stops.
     */
    private fun addStopAndContinue(filter: WidgetFilter) {
        val station = selectedStation ?: return
        
        // Add to configured stops
        val stop = StopConfig(
            stationId = station.id,
            stationName = station.name,
            filter = filter
        )
        configuredStops.add(stop)
        
        val message = if (filter.isActive()) {
            getString(R.string.filter_selected, filter.line, filter.direction)
        } else {
            getString(R.string.station_selected, station.name)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        
        // Clear selection and go back to add more
        selectedStation = null
        showStationSelection()
    }
    
    /**
     * Finish configuration and save all stops.
     */
    private fun finishConfiguration() {
        if (configuredStops.isEmpty()) {
            Toast.makeText(this, "Please add at least one stop", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            // Save the widget config with all stops
            val config = WidgetConfig(stops = configuredStops.toList(), currentIndex = 0)
            repository.saveWidgetConfig(appWidgetId, config)
            
            val message = if (configuredStops.size == 1) {
                configuredStops.first().displayName()
            } else {
                getString(R.string.stops_configured, configuredStops.size)
            }
            Toast.makeText(this@TramWidgetConfigActivity, message, Toast.LENGTH_SHORT).show()
            
            // Update the widget
            TramWidgetProvider.updateAllWidgets(this@TramWidgetConfigActivity)
            
            // Return success
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (currentStep) {
            Step.SELECT_LINE_DIRECTION -> {
                showStationSelection()
            }
            Step.SELECT_STATION -> {
                if (configuredStops.isNotEmpty()) {
                    // Finish with current stops
                    finishConfiguration()
                } else {
                    super.onBackPressed()
                }
            }
        }
    }
}
