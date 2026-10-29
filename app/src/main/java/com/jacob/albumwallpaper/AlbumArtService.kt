package com.jacob.albumwallpaper

import android.app.Notification
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
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
import androidx.palette.graphics.Palette
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AlbumArtService : NotificationListenerService() {

    companion object {
        private const val TAG = "AlbumArtService"
        const val PKG_YTM     = "com.google.android.apps.youtube.music"
        const val PKG_SPOTIFY = "com.spotify.music"

        const val ACTION_TRACK_CHANGED = "com.jacob.albumwallpaper.TRACK_CHANGED"
        const val ACTION_REAPPLY       = "com.jacob.albumwallpaper.REAPPLY"
        const val EXTRA_TITLE          = "title"
        const val EXTRA_ARTIST         = "artist"
        const val EXTRA_SEED_COLOR     = "seed_color"

        @Volatile var lastCleanBitmap: Bitmap? = null
    }

    private var lastTitle: String? = null

    private val reapplyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val bmp = lastCleanBitmap ?: return
            Thread { applyWallpapers(bmp) }.start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(reapplyReceiver, IntentFilter(ACTION_REAPPLY))
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(reapplyReceiver)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = Settings.prefs(this)
        val isYtm     = sbn.packageName == PKG_YTM     && prefs.getBoolean(Settings.SOURCE_YTM, true)
        val isSpotify = sbn.packageName == PKG_SPOTIFY && prefs.getBoolean(Settings.SOURCE_SPOTIFY, false)
        if (!isYtm && !isSpotify) return

        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return

        val extras = notification.extras
        var fallback: Bitmap? = null
        var artUri: String?   = null
        var title: String?    = null
        var artist: String?   = null

        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token != null) {
            try {
                val meta = MediaController(this, token).metadata
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

        val capUri    = artUri
        val capFb     = fallback
        val capTitle  = title ?: newTitle
        val capArtist = artist ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        Thread {
            val bitmap = fetchHighRes(capUri, capTitle, capArtist) ?: capFb ?: return@Thread
            maybeSaveOriginal()
            lastCleanBitmap = bitmap
            saveThumbnail(bitmap)
            applyWallpapers(bitmap)
            if (prefs.getBoolean(Settings.KUSTOM_ON, false)) saveKustom(bitmap)

            val palette    = Palette.from(bitmap).generate()
            val seedColor  = palette.getVibrantColor(palette.getDominantColor(0xFF1DB954.toInt()))
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent(ACTION_TRACK_CHANGED)
                    .putExtra(EXTRA_TITLE, capTitle)
                    .putExtra(EXTRA_ARTIST, capArtist)
                    .putExtra(EXTRA_SEED_COLOR, seedColor)
            )
        }.start()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val prefs = Settings.prefs(this)
        if (!prefs.getBoolean(Settings.RESTORE_ON_STOP, false)) return
        val isWatched = (sbn.packageName == PKG_YTM     && prefs.getBoolean(Settings.SOURCE_YTM, true))
                     || (sbn.packageName == PKG_SPOTIFY && prefs.getBoolean(Settings.SOURCE_SPOTIFY, false))
        if (!isWatched) return
        Thread { restoreOriginal() }.start()
    }

    // ── Wallpaper application ─────────────────────────────────────────────────

    private fun applyWallpapers(clean: Bitmap) {
        val prefs = Settings.prefs(this)
        val wm    = WallpaperManager.getInstance(this)
        val screen = fitToScreen(clean)

        val lockBitmap = applyScheduleDim(screen, prefs)
        crossfadeSet(lastLockBitmap, lockBitmap, WallpaperManager.FLAG_LOCK,
            prefs.getFloat(Settings.CROSSFADE_DURATION, 600f).toLong(), prefs)
        lastLockBitmap = lockBitmap

        if (prefs.getBoolean(Settings.APPLY_HOME, false)) {
            var home = screen.copy(Bitmap.Config.ARGB_8888, true)

            if (prefs.getBoolean(Settings.BLUR_ON, false))
                home = applyBlur(home, prefs.getFloat(Settings.BLUR_RADIUS, 10f))

            if (prefs.getBoolean(Settings.DIM_ON, false)) {
                val amount = prefs.getFloat(Settings.DIM_AMOUNT, 40f) / 100f
                val color  = if (prefs.getBoolean(Settings.PALETTE_DIM, false)) {
                    val palette = Palette.from(screen).generate()
                    palette.getDarkVibrantColor(palette.getDarkMutedColor(Color.BLACK))
                } else Color.BLACK
                home = applyDim(home, amount, color)
            }

            home = applyScheduleDim(home, prefs)

            crossfadeSet(lastHomeBitmap, home, WallpaperManager.FLAG_SYSTEM,
                prefs.getFloat(Settings.CROSSFADE_DURATION, 600f).toLong(), prefs)
            lastHomeBitmap = home
        }
    }

    private var lastLockBitmap: Bitmap? = null
    private var lastHomeBitmap: Bitmap? = null

    private fun applyScheduleDim(src: Bitmap, prefs: android.content.SharedPreferences): Bitmap {
        if (!prefs.getBoolean(Settings.SCHEDULE_ON, false) || !isInScheduleWindow(prefs)) return src
        val amount = prefs.getFloat(Settings.SCHEDULE_DIM, 30f) / 100f
        return applyDim(src, amount, Color.BLACK)
    }

    private fun isInScheduleWindow(prefs: android.content.SharedPreferences): Boolean {
        val cal   = java.util.Calendar.getInstance()
        val now   = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = prefs.getInt(Settings.SCHEDULE_START, 22 * 60)
        val end   = prefs.getInt(Settings.SCHEDULE_END,    7 * 60)
        return if (start > end) now >= start || now <= end else now in start..end
    }

    // ── Effects ───────────────────────────────────────────────────────────────

    private fun applyBlur(src: Bitmap, radius: Float): Bitmap {
        if (radius < 1f) return src
        val scale   = 4
        val small   = Bitmap.createScaledBitmap(src, src.width / scale, src.height / scale, true)
        val blurred = StackBlur.blur(small, (radius * 1.6f).toInt().coerceIn(2, 50))
        val full    = Bitmap.createScaledBitmap(blurred, src.width, src.height, true)
        val result  = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas  = Canvas(result)
        canvas.drawBitmap(full, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.4f) })
        })
        canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(),
            Paint().apply { color = 0x17FFFFFF.toInt() })
        return result
    }

    private fun applyDim(src: Bitmap, amount: Float, dimColor: Int): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(result).drawRect(
            0f, 0f, result.width.toFloat(), result.height.toFloat(),
            Paint().apply {
                color = dimColor
                alpha = (amount.coerceIn(0f, 1f) * 255).toInt()
            }
        )
        return result
    }

    // ── Crossfade ─────────────────────────────────────────────────────────────

    private fun crossfadeSet(from: Bitmap?, to: Bitmap, flag: Int, durationMs: Long,
                              prefs: android.content.SharedPreferences) {
        val wm = WallpaperManager.getInstance(this)
        if (from == null || !prefs.getBoolean(Settings.CROSSFADE_ON, false) || durationMs < 100) {
            runCatching { wm.setBitmap(to, null, true, flag) }
            return
        }
        val steps = 8
        val delay = durationMs / steps
        for (i in 1..steps) {
            val alpha = i.toFloat() / steps
            val frame = blendBitmaps(from, to, alpha)
            runCatching { wm.setBitmap(frame, null, true, flag) }
            Thread.sleep(delay)
        }
    }

    private fun blendBitmaps(a: Bitmap, b: Bitmap, alpha: Float): Bitmap {
        val result = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(Bitmap.createScaledBitmap(a, b.width, b.height, true), 0f, 0f, null)
        canvas.drawBitmap(b, 0f, 0f, Paint().apply { this.alpha = (alpha * 255).toInt() })
        return result
    }

    // ── Restore on stop ───────────────────────────────────────────────────────

    private fun maybeSaveOriginal() {
        val prefs = Settings.prefs(this)
        if (prefs.getBoolean("original_saved", false)) return
        try {
            val wm  = WallpaperManager.getInstance(this)
            val bmp = wm.getBitmap() ?: return
            FileOutputStream(File(filesDir, "original.jpg")).use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            prefs.edit().putBoolean("original_saved", true).apply()
        } catch (e: Exception) { Log.w(TAG, "saveOriginal failed", e) }
    }

    private fun restoreOriginal() {
        val f = File(filesDir, "original.jpg")
        if (!f.exists()) return
        try {
            val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath) ?: return
            val wm  = WallpaperManager.getInstance(this)
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
            if (Settings.prefs(this).getBoolean(Settings.APPLY_HOME, false))
                wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
            lastCleanBitmap = null
            lastTitle       = null
        } catch (e: Exception) { Log.w(TAG, "restoreOriginal failed", e) }
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    private fun saveThumbnail(bitmap: Bitmap) {
        try {
            val small = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            FileOutputStream(File(filesDir, "thumb.jpg")).use {
                small.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }
        } catch (e: Exception) { Log.w(TAG, "saveThumbnail failed", e) }
    }

    private fun saveKustom(bitmap: Bitmap) {
        try {
            val dir  = java.io.File("/storage/emulated/0/Download/Kustom")
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(java.io.File(dir, "nowplaying.jpg")).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
        } catch (e: Exception) { Log.w(TAG, "saveKustom failed", e) }
    }

    // ── Screen fit ────────────────────────────────────────────────────────────

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
        val sw     = (src.width  * scale).toInt()
        val sh     = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        return Bitmap.createBitmap(scaled, (sw - screenW) / 2, (sh - screenH) / 2, screenW, screenH)
    }

    // ── High-res fetch ────────────────────────────────────────────────────────

    private fun fetchHighRes(uri: String?, title: String?, artist: String?): Bitmap? {
        if (!uri.isNullOrEmpty() && uri.contains("lh3.googleusercontent.com")) {
            val url = uri
                .replace(Regex("=w\\d+-h\\d+[^\"\\s]*$"), "=w1080-h1080-l90-rj")
                .replace(Regex("=s\\d+[^\"\\s]*$"), "=s1080")
            downloadBitmap(url)?.let { return it }
        }
        if (!title.isNullOrEmpty()) {
            try {
                val q    = java.net.URLEncoder.encode(listOfNotNull(artist, title).joinToString(" "), "UTF-8")
                val json = URL("https://itunes.apple.com/search?term=$q&entity=song&limit=1").readText()
                val url  = Regex("\"artworkUrl100\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                    ?.replace("100x100bb", "1080x1080bb")
                if (!url.isNullOrEmpty()) downloadBitmap(url)?.let { return it }
            } catch (e: Exception) { Log.w(TAG, "iTunes fetch failed", e) }
        }
        return null
    }

    private fun downloadBitmap(url: String): Bitmap? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 10000; conn.connect()
        if (conn.responseCode == 200) android.graphics.BitmapFactory.decodeStream(conn.inputStream) else null
    } catch (e: Exception) { Log.w(TAG, "downloadBitmap failed: $url", e); null }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = if (intrinsicWidth > 0) intrinsicWidth else 512
        val h = if (intrinsicHeight > 0) intrinsicHeight else 512
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c   = Canvas(bmp); setBounds(0, 0, w, h); draw(c); return bmp
    }
}
