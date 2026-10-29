package com.jacob.albumwallpaper

import android.content.Context

object Settings {
    private const val NAME = "wallpaper_prefs"

    // Sources
    const val SOURCE_YTM     = "source_ytm"
    const val SOURCE_SPOTIFY = "source_spotify"

    // Home screen
    const val APPLY_HOME  = "apply_home"
    const val BLUR_ON     = "blur_on"
    const val BLUR_RADIUS = "blur_radius"
    const val DIM_ON      = "dim_on"
    const val DIM_AMOUNT  = "dim_amount"
    const val PALETTE_DIM = "palette_dim"

    // Transitions
    const val CROSSFADE_ON       = "crossfade_on"
    const val CROSSFADE_DURATION = "crossfade_dur"

    // Behavior
    const val RESTORE_ON_STOP = "restore_on_stop"

    // Schedule
    const val SCHEDULE_ON    = "schedule_on"
    const val SCHEDULE_START = "schedule_start"  // minutes from midnight
    const val SCHEDULE_END   = "schedule_end"
    const val SCHEDULE_DIM   = "schedule_dim"

    // Integrations
    const val KUSTOM_ON = "kustom_on"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)!!
}
