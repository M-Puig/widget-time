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
import com.widgettime.tram.data.models.WidgetFilter
import com.widgettime.tram.databinding.ActivityConfigBinding
import kotlinx.coroutines.launch

/**
 * Configuration activity that appears when adding a new widget to the home screen.
 * Two-step flow:
 * 1. Select a station
 * 2. Optionally select a line/direction filter
 */
class TramWidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private lateinit var repository: TramRepository
    private lateinit var stationAdapter: StationAdapter
    private lateinit var lineDirectionAdapter: LineDirectionAdapter
    
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedStation: Station? = null
    private var allStations: List<Station> = emptyList()
    
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
        showStationSelection()
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
        binding.toolbar.navigationIcon = null
        binding.stepTitle.text = getString(R.string.select_station)
        binding.stepSubtitle.visibility = View.GONE
        binding.searchInputLayout.visibility = View.VISIBLE
        binding.searchEditText.text?.clear()
        binding.recyclerViewStations.visibility = View.VISIBLE
        binding.recyclerViewLineDirections.visibility = View.GONE
        binding.skipFilterButton.visibility = View.GONE
        
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
                    binding.skipFilterButton.setOnClickListener {
                        finishConfiguration(WidgetFilter.NONE)
                    }
                } else {
                    // No lines found - might be no service right now
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = getString(R.string.no_lines_found)
                    
                    binding.skipFilterButton.visibility = View.VISIBLE
                    binding.skipFilterButton.setOnClickListener {
                        finishConfiguration(WidgetFilter.NONE)
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = getString(R.string.error_loading_lines)
                
                binding.skipFilterButton.visibility = View.VISIBLE
                binding.skipFilterButton.setOnClickListener {
                    finishConfiguration(WidgetFilter.NONE)
                }
            }
        }
    }

    private fun onStationSelected(station: Station) {
        selectedStation = station
        
        lifecycleScope.launch {
            // Save the selected station for this widget
            repository.saveWidgetStation(appWidgetId, station.id)
            
            // Move to line/direction selection
            showLineDirectionSelection()
        }
    }
    
    private fun onLineDirectionSelected(lineDirection: LineDirection) {
        val filter = WidgetFilter(
            line = lineDirection.line,
            direction = lineDirection.direction
        )
        finishConfiguration(filter)
    }
    
    private fun finishConfiguration(filter: WidgetFilter) {
        val station = selectedStation ?: return
        
        lifecycleScope.launch {
            // Save the filter
            repository.saveWidgetFilter(appWidgetId, filter)
            
            val message = if (filter.isActive()) {
                getString(R.string.filter_selected, filter.line, filter.direction)
            } else {
                getString(R.string.station_selected, station.name)
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
        if (currentStep == Step.SELECT_LINE_DIRECTION) {
            showStationSelection()
        } else {
            super.onBackPressed()
        }
    }
}
