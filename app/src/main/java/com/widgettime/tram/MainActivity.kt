package com.widgettime.tram

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.widgettime.tram.databinding.ActivityMainBinding

/**
 * Main activity for the app. Provides access to settings and station management.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before calling super.onCreate()
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.toolbar.setTitle(R.string.app_name)
        setSupportActionBar(binding.toolbar)
        
        binding.addWidgetHint.text = getString(R.string.add_widget_hint)
        
        binding.refreshButton.setOnClickListener {
            TramWidgetProvider.updateAllWidgets(this)
        }
    }
}
