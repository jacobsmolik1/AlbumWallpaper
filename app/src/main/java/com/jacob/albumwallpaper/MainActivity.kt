package com.jacob.albumwallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var trackText: TextView
    private lateinit var grantButton: android.widget.Button

    private lateinit var switchHome: SwitchMaterial
    private lateinit var switchBlur: SwitchMaterial
    private lateinit var sliderBlur: Slider
    private lateinit var switchDim: SwitchMaterial
    private lateinit var sliderDim: Slider

    private val prefs get() = Settings.prefs(this)

    private val trackReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val t = intent.getStringExtra(AlbumArtService.EXTRA_TITLE) ?: "Unknown"
            val a = intent.getStringExtra(AlbumArtService.EXTRA_ARTIST) ?: ""
            trackText.text = if (a.isNotEmpty()) "$t\n$a" else t
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText  = findViewById(R.id.statusText)
        trackText   = findViewById(R.id.trackText)
        grantButton = findViewById(R.id.grantButton)
        switchHome  = findViewById(R.id.switchHome)
        switchBlur  = findViewById(R.id.switchBlur)
        sliderBlur  = findViewById(R.id.sliderBlur)
        switchDim   = findViewById(R.id.switchDim)
        sliderDim   = findViewById(R.id.sliderDim)

        grantButton.setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Restore saved state
        switchHome.isChecked = prefs.getBoolean(Settings.APPLY_HOME, false)
        switchBlur.isChecked = prefs.getBoolean(Settings.BLUR_ON, false)
        sliderBlur.value     = prefs.getFloat(Settings.BLUR_RADIUS, 10f)
        switchDim.isChecked  = prefs.getBoolean(Settings.DIM_ON, false)
        sliderDim.value      = prefs.getFloat(Settings.DIM_AMOUNT, 40f)

        refreshEffectControls()

        switchHome.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(Settings.APPLY_HOME, v).apply()
            refreshEffectControls()
            reapply()
        }
        switchBlur.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(Settings.BLUR_ON, v).apply()
            sliderBlur.isEnabled = v
            reapply()
        }
        sliderBlur.addOnChangeListener { _, v, fromUser ->
            if (fromUser) { prefs.edit().putFloat(Settings.BLUR_RADIUS, v).apply(); reapply() }
        }
        switchDim.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(Settings.DIM_ON, v).apply()
            sliderDim.isEnabled = v
            reapply()
        }
        sliderDim.addOnChangeListener { _, v, fromUser ->
            if (fromUser) { prefs.edit().putFloat(Settings.DIM_AMOUNT, v).apply(); reapply() }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(trackReceiver, IntentFilter(AlbumArtService.ACTION_TRACK_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(trackReceiver)
    }

    private fun updateStatus() {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        statusText.text = if (granted) "Active — watching YouTube Music"
                          else "Needs notification access"
        statusText.setTextColor(
            if (granted) getColor(android.R.color.holo_green_light)
            else getColor(android.R.color.holo_red_light)
        )
        grantButton.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun refreshEffectControls() {
        val homeOn = switchHome.isChecked
        val alpha  = if (homeOn) 1f else 0.38f
        listOf(switchBlur, sliderBlur, switchDim, sliderDim).forEach { it.alpha = alpha }
        switchBlur.isEnabled = homeOn
        switchDim.isEnabled  = homeOn
        sliderBlur.isEnabled = homeOn && switchBlur.isChecked
        sliderDim.isEnabled  = homeOn && switchDim.isChecked
    }

    private fun reapply() {
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(AlbumArtService.ACTION_REAPPLY))
    }
}
