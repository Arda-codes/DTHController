package com.rhythmcontroller

import android.content.Context
import android.content.SharedPreferences

data class KeyInfo(val name: String, val code: Int)

object KeyBindingsManager {

    val AVAILABLE_KEYS = listOf(
        // Alphanumeric
        KeyInfo("Q", 16), KeyInfo("W", 17), KeyInfo("E", 18), KeyInfo("R", 19),
        KeyInfo("T", 20), KeyInfo("Y", 21), KeyInfo("U", 22), KeyInfo("I", 23),
        KeyInfo("O", 24), KeyInfo("P", 25),
        KeyInfo("A", 30), KeyInfo("S", 31), KeyInfo("D", 32), KeyInfo("F", 33),
        KeyInfo("G", 34), KeyInfo("H", 35), KeyInfo("J", 36), KeyInfo("K", 37),
        KeyInfo("L", 38), KeyInfo(";", 39),
        KeyInfo("Z", 44), KeyInfo("X", 45), KeyInfo("C", 46), KeyInfo("V", 47),
        KeyInfo("B", 48), KeyInfo("N", 49), KeyInfo("M", 50),
        // Numbers
        KeyInfo("1", 2), KeyInfo("2", 3), KeyInfo("3", 4), KeyInfo("4", 5),
        KeyInfo("5", 6), KeyInfo("6", 7), KeyInfo("7", 8), KeyInfo("8", 9),
        KeyInfo("9", 10), KeyInfo("0", 11),
        // Controls / Navigation
        KeyInfo("SPACE", 57), KeyInfo("ENTER", 28), KeyInfo("TAB", 15),
        KeyInfo("L-SHIFT", 42), KeyInfo("R-SHIFT", 54),
        KeyInfo("UP", 103), KeyInfo("DOWN", 108), KeyInfo("LEFT", 105), KeyInfo("RIGHT", 106),
        KeyInfo("ESC", 1), KeyInfo("BACKSPACE", 14),
        // Gamepad Buttons
        KeyInfo("BTN_A", 0x130), KeyInfo("BTN_B", 0x131),
        KeyInfo("BTN_X", 0x132), KeyInfo("BTN_Y", 0x133),
        KeyInfo("BTN_L1", 0x134), KeyInfo("BTN_R1", 0x135)
    )

    private val DEFAULT_CODES = intArrayOf(
        16, 17, 18, 19, // Q, W, E, R
        30, 31, 32, 33, // A, S, D, F
        44, 45, 46, 47  // Z, X, C, V
    )

    private const val PREFS_NAME = "rhythm_bindings_prefs"
    private const val KEY_BIND_PREFIX = "bind_btn_"

    fun loadKeycodes(context: Context): IntArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val codes = IntArray(12)
        for (i in 0 until 12) {
            codes[i] = prefs.getInt("$KEY_BIND_PREFIX$i", DEFAULT_CODES[i])
        }
        return codes
    }

    fun saveKeycode(context: Context, buttonId: Int, code: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("$KEY_BIND_PREFIX$buttonId", code).apply()
    }

    fun getKeyName(code: Int): String {
        return AVAILABLE_KEYS.firstOrNull { it.code == code }?.name ?: "KEY_$code"
    }

    fun getCodeByName(name: String): Int? {
        return AVAILABLE_KEYS.firstOrNull { it.name.equals(name, ignoreCase = true) }?.code
    }
}
