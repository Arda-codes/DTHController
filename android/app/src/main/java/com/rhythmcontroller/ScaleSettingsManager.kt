package com.rhythmcontroller

import android.content.Context

object ScaleSettingsManager {
    private const val PREFS_NAME = "rhythm_scale_prefs"

    private const val KEY_WIDTH_SCALE = "width_scale"
    private const val KEY_HEIGHT_SCALE = "height_scale"
    private const val KEY_GAP_SCALE = "gap_scale"
    private const val KEY_V_ALIGN = "v_align" // -1: Top, 0: Center, 1: Bottom

    fun getWidthScale(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_WIDTH_SCALE, 0.96f)
    }

    fun setWidthScale(context: Context, scale: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_WIDTH_SCALE, scale.coerceIn(0.4f, 1.0f)).apply()
    }

    fun getHeightScale(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_HEIGHT_SCALE, 0.94f)
    }

    fun setHeightScale(context: Context, scale: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_HEIGHT_SCALE, scale.coerceIn(0.4f, 1.0f)).apply()
    }

    fun getGapScale(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_GAP_SCALE, 1.0f) // multiplier (0.5x to 2.0x)
    }

    fun setGapScale(context: Context, scale: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_GAP_SCALE, scale.coerceIn(0.2f, 2.5f)).apply()
    }

    fun getVAlign(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_V_ALIGN, 0)
    }

    fun setVAlign(context: Context, align: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_V_ALIGN, align).apply()
    }
}
