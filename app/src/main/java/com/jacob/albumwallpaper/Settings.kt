package com.jacob.albumwallpaper

import android.content.Context

object Settings {
    private const val NAME = "wallpaper_prefs"
    const val APPLY_HOME  = "apply_home"
    const val BLUR_ON     = "blur_on"
    const val BLUR_RADIUS = "blur_radius"   // 1–25 float
    const val DIM_ON      = "dim_on"
    const val DIM_AMOUNT  = "dim_amount"    // 0–100 float

    fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)!!
}
