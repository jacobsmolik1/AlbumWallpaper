package com.jacob.albumwallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var trackText: TextView

    private val trackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val title = intent.getStringExtra(AlbumArtService.EXTRA_TITLE) ?: "Unknown"
            val artist = intent.getStringExtra(AlbumArtService.EXTRA_ARTIST) ?: ""
            trackText.text = if (artist.isNotEmpty()) "$title\n$artist" else title
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        trackText = findViewById(R.id.trackText)

        findViewById<Button>(R.id.grantButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
        if (granted) {
            statusText.text = "Active — watching YouTube Music"
            statusText.setTextColor(getColor(android.R.color.holo_green_light))
        } else {
            statusText.text = "Needs notification access — tap the button below"
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
        }
    }
}
