package com.jacob.albumwallpaper

import android.app.Notification
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.WindowManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.net.HttpURLConnection
import java.net.URL

class AlbumArtService : NotificationListenerService() {

    companion object {
        private const val TAG = "AlbumArtService"
        private const val YTM_PACKAGE = "com.google.android.apps.youtube.music"
        const val ACTION_TRACK_CHANGED = "com.jacob.albumwallpaper.TRACK_CHANGED"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
    }

    private var lastTitle: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != YTM_PACKAGE) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return

        val extras = notification.extras
        var fallbackBitmap: Bitmap? = null
        var artUri: String? = null
        var title: String? = null
        var artist: String? = null

        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token != null) {
            try {
                val controller = MediaController(this, token)
                val meta = controller.metadata
                fallbackBitmap = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                artUri = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            } catch (e: Exception) {
                Log.w(TAG, "MediaSession read failed", e)
            }
        }

        if (fallbackBitmap == null) {
            fallbackBitmap = notification.getLargeIcon()?.loadDrawable(this)?.toBitmap()
        }

        val newTitle = title ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (newTitle == lastTitle) return
        lastTitle = newTitle

        val capturedUri = artUri
        val capturedFallback = fallbackBitmap
        val capturedTitle = title ?: newTitle
        val capturedArtist = artist ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        Thread {
            val bitmap = fetchHighRes(capturedUri, capturedTitle, capturedArtist)
                ?: capturedFallback
                ?: return@Thread
            applyLockscreenWallpaper(bitmap)
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent(ACTION_TRACK_CHANGED)
                    .putExtra(EXTRA_TITLE, capturedTitle)
                    .putExtra(EXTRA_ARTIST, capturedArtist)
            )
        }.start()
    }

    // 1) Bump Google CDN size param  2) iTunes fallback
    private fun fetchHighRes(uri: String?, title: String?, artist: String?): Bitmap? {
        // Google image CDN — replace size suffix with 1080x1080
        if (!uri.isNullOrEmpty() && uri.contains("lh3.googleusercontent.com")) {
            val highResUrl = uri
                .replace(Regex("=w\\d+-h\\d+[^\"\\s]*$"), "=w1080-h1080-l90-rj")
                .replace(Regex("=s\\d+[^\"\\s]*$"), "=s1080")
            downloadBitmap(highResUrl)?.let { return it }
        }

        // iTunes Search API — free, no key, returns up to ~3000x3000
        if (!title.isNullOrEmpty()) {
            try {
                val query = java.net.URLEncoder.encode(
                    listOfNotNull(artist, title).joinToString(" "), "UTF-8"
                )
                val searchUrl = "https://itunes.apple.com/search?term=$query&entity=song&limit=1"
                val json = URL(searchUrl).readText()
                val artUrl100 = Regex("\"artworkUrl100\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                if (!artUrl100.isNullOrEmpty()) {
                    val artUrl = artUrl100.replace("100x100bb", "1080x1080bb")
                    downloadBitmap(artUrl)?.let { return it }
                }
            } catch (e: Exception) {
                Log.w(TAG, "iTunes fetch failed", e)
            }
        }

        return null
    }

    private fun downloadBitmap(url: String): Bitmap? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.connect()
        if (conn.responseCode == 200) BitmapFactory.decodeStream(conn.inputStream) else null
    } catch (e: Exception) {
        Log.w(TAG, "downloadBitmap failed for $url", e)
        null
    }

    private fun applyLockscreenWallpaper(bitmap: Bitmap) {
        try {
            WallpaperManager.getInstance(this)
                .setBitmap(fitToScreen(bitmap), null, true, WallpaperManager.FLAG_LOCK)
            Log.d(TAG, "Lockscreen wallpaper updated")
        } catch (e: Exception) {
            Log.e(TAG, "setBitmap failed", e)
        }
    }

    private fun fitToScreen(src: Bitmap): Bitmap {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val screenW: Int
        val screenH: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenW = bounds.width()
            screenH = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val size = Point().also { wm.defaultDisplay.getRealSize(it) }
            screenW = size.x
            screenH = size.y
        }
        val scale = maxOf(screenW.toFloat() / src.width, screenH.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt()
        val scaledH = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = (scaledW - screenW) / 2
        val y = (scaledH - screenH) / 2
        return Bitmap.createBitmap(scaled, x, y, screenW, screenH)
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = if (intrinsicWidth > 0) intrinsicWidth else 512
        val h = if (intrinsicHeight > 0) intrinsicHeight else 512
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, w, h)
        draw(canvas)
        return bmp
    }
}
