package com.jacob.albumwallpaper

import android.app.Notification
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

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
        var bitmap: Bitmap? = null
        var title: String? = null
        var artist: String? = null

        // MediaSession gives us the highest-quality bitmap
        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token != null) {
            try {
                val controller = MediaController(this, token)
                val meta = controller.metadata
                bitmap = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            } catch (e: Exception) {
                Log.w(TAG, "MediaSession read failed", e)
            }
        }

        // Fall back to the notification's large icon
        if (bitmap == null) {
            bitmap = notification.getLargeIcon()?.loadDrawable(this)?.toBitmap()
        }

        val newTitle = title ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (bitmap == null || newTitle == lastTitle) return
        lastTitle = newTitle

        applyLockscreenWallpaper(bitmap)

        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(ACTION_TRACK_CHANGED)
                .putExtra(EXTRA_TITLE, title ?: newTitle)
                .putExtra(EXTRA_ARTIST, artist ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        )
    }

    private fun applyLockscreenWallpaper(bitmap: Bitmap) {
        try {
            WallpaperManager.getInstance(this)
                .setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            Log.d(TAG, "Lockscreen wallpaper updated")
        } catch (e: Exception) {
            Log.e(TAG, "setBitmap failed", e)
        }
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
