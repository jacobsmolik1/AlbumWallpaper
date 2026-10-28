package com.jacob.albumwallpaper

import android.app.Notification
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
        const val ACTION_TRACK_CHANGED  = "com.jacob.albumwallpaper.TRACK_CHANGED"
        const val ACTION_REAPPLY        = "com.jacob.albumwallpaper.REAPPLY"
        const val EXTRA_TITLE           = "title"
        const val EXTRA_ARTIST          = "artist"

        // Cached so settings changes can reapply without waiting for next track
        @Volatile var lastCleanBitmap: Bitmap? = null
    }

    private var lastTitle: String? = null

    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(reapplyReceiver,
                android.content.IntentFilter(ACTION_REAPPLY))
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(reapplyReceiver)
        super.onDestroy()
    }

    private val reapplyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context, intent: Intent) {
            val bmp = lastCleanBitmap ?: return
            Thread { applyWallpapers(bmp) }.start()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != YTM_PACKAGE) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return

        val extras = notification.extras
        var fallback: Bitmap? = null
        var artUri: String? = null
        var title: String? = null
        var artist: String? = null

        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token != null) {
            try {
                val controller = MediaController(this, token)
                val meta = controller.metadata
                fallback = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                artUri  = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                title   = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                artist  = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            } catch (e: Exception) { Log.w(TAG, "MediaSession read failed", e) }
        }

        if (fallback == null)
            fallback = notification.getLargeIcon()?.loadDrawable(this)?.toBitmap()

        val newTitle = title ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (newTitle == lastTitle) return
        lastTitle = newTitle

        val capUri     = artUri
        val capFallback = fallback
        val capTitle   = title ?: newTitle
        val capArtist  = artist ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        Thread {
            val bitmap = fetchHighRes(capUri, capTitle, capArtist) ?: capFallback ?: return@Thread
            lastCleanBitmap = bitmap
            applyWallpapers(bitmap)
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent(ACTION_TRACK_CHANGED)
                    .putExtra(EXTRA_TITLE, capTitle)
                    .putExtra(EXTRA_ARTIST, capArtist)
            )
        }.start()
    }

    // ── Wallpaper application ──────────────────────────────────────────────────

    private fun applyWallpapers(clean: Bitmap) {
        val p   = Settings.prefs(this)
        val wm  = WallpaperManager.getInstance(this)
        val screen = fitToScreen(clean)

        // Lockscreen always gets the clean art
        try { wm.setBitmap(screen, null, true, WallpaperManager.FLAG_LOCK) }
        catch (e: Exception) { Log.e(TAG, "Lock wallpaper failed", e) }

        // Home screen: optional, with blur + dim
        if (p.getBoolean(Settings.APPLY_HOME, false)) {
            var home = screen.copy(Bitmap.Config.ARGB_8888, true)
            if (p.getBoolean(Settings.BLUR_ON, false))
                home = applyBlur(home, p.getFloat(Settings.BLUR_RADIUS, 10f))
            if (p.getBoolean(Settings.DIM_ON, false))
                home = applyDim(home, p.getFloat(Settings.DIM_AMOUNT, 40f) / 100f)
            try { wm.setBitmap(home, null, true, WallpaperManager.FLAG_SYSTEM) }
            catch (e: Exception) { Log.e(TAG, "Home wallpaper failed", e) }
        }
    }

    // ── Effects ────────────────────────────────────────────────────────────────

    private fun applyBlur(src: Bitmap, radius: Float): Bitmap {
        if (radius < 1f) return src

        // Downsample 4x → StackBlur (true Gaussian-like) → upsample
        val scale = 4
        val small   = Bitmap.createScaledBitmap(src, src.width / scale, src.height / scale, true)
        val blurred = StackBlur.blur(small, (radius * 1.6f).toInt().coerceIn(2, 50))
        val full    = Bitmap.createScaledBitmap(blurred, src.width, src.height, true)

        // Liquid-glass finish: vibrancy (saturation boost) + frosted white veil
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(full, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.4f) })
        })
        // Subtle white frost layer (~9% opacity)
        canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(),
            Paint().apply { color = 0x17FFFFFF.toInt() })
        return result
    }

    private fun applyDim(src: Bitmap, amount: Float): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(result).drawRect(
            0f, 0f, result.width.toFloat(), result.height.toFloat(),
            Paint().apply {
                color = Color.BLACK
                alpha = (amount.coerceIn(0f, 1f) * 255).toInt()
            }
        )
        return result
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun fitToScreen(src: Bitmap): Bitmap {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val screenW: Int; val screenH: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds; screenW = b.width(); screenH = b.height()
        } else {
            @Suppress("DEPRECATION")
            val sz = Point().also { wm.defaultDisplay.getRealSize(it) }
            screenW = sz.x; screenH = sz.y
        }
        val scale  = maxOf(screenW.toFloat() / src.width, screenH.toFloat() / src.height)
        val sw     = (src.width * scale).toInt()
        val sh     = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        return Bitmap.createBitmap(scaled, (sw - screenW) / 2, (sh - screenH) / 2, screenW, screenH)
    }

    private fun fetchHighRes(uri: String?, title: String?, artist: String?): Bitmap? {
        if (!uri.isNullOrEmpty() && uri.contains("lh3.googleusercontent.com")) {
            val url = uri
                .replace(Regex("=w\\d+-h\\d+[^\"\\s]*$"), "=w1080-h1080-l90-rj")
                .replace(Regex("=s\\d+[^\"\\s]*$"), "=s1080")
            downloadBitmap(url)?.let { return it }
        }
        if (!title.isNullOrEmpty()) {
            try {
                val q = java.net.URLEncoder.encode(listOfNotNull(artist, title).joinToString(" "), "UTF-8")
                val json = URL("https://itunes.apple.com/search?term=$q&entity=song&limit=1").readText()
                val url100 = Regex("\"artworkUrl100\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                if (!url100.isNullOrEmpty())
                    downloadBitmap(url100.replace("100x100bb", "1080x1080bb"))?.let { return it }
            } catch (e: Exception) { Log.w(TAG, "iTunes fetch failed", e) }
        }
        return null
    }

    private fun downloadBitmap(url: String): Bitmap? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 10000; conn.connect()
        if (conn.responseCode == 200) BitmapFactory.decodeStream(conn.inputStream) else null
    } catch (e: Exception) { Log.w(TAG, "downloadBitmap failed: $url", e); null }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = if (intrinsicWidth > 0) intrinsicWidth else 512
        val h = if (intrinsicHeight > 0) intrinsicHeight else 512
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); setBounds(0, 0, w, h); draw(c)
        return bmp
    }
}
