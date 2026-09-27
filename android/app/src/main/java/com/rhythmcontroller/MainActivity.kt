package com.rhythmcontroller

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Main rhythm controller activity.
 *
 * Configured for maximum performance:
 * - Immersive sticky fullscreen mode.
 * - FLAG_KEEP_SCREEN_ON to prevent display timeout.
 * - Dynamic selection of highest available display refresh rate (e.g. 120Hz/144Hz).
 * - Interactive Settings Modal for scaling buttons and customizing keybindings.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var touchView: GridTouchView
    private lateinit var networkClient: NetworkClient
    private lateinit var feedbackManager: FeedbackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0. Allow direct socket write on UI thread for microsecond event dispatch
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder().permitAll().build()
        )

        // 1. Keep display always active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Maximize display refresh rate (reduces touch sampling latency)
        optimizeRefreshRate()

        // 3. Immersive sticky fullscreen
        enableImmersiveMode()

        // 4. Initialize low-latency hitsound & haptic feedback manager
        feedbackManager = FeedbackManager(this)

        // 5. Initialize NetworkClient connected to localhost:54321 (tunneled via adb reverse)
        networkClient = NetworkClient(
            host = "127.0.0.1",
            port = 54321,
            onConnectionStateChanged = { connected ->
                touchView.setConnectionStatus(connected)
            },
            onLatencyMeasured = { rttMs ->
                touchView.setLatencyMs(rttMs)
            }
        )

        // 6. Initialize custom touch view directly
        touchView = GridTouchView(this).apply {
            networkClient = this@MainActivity.networkClient
            feedbackManager = this@MainActivity.feedbackManager
            onSettingsClicked = {
                showSettingsDialog()
            }
        }
        setContentView(touchView)

        // Set initial bindings to be synced on connect
        networkClient.pendingKeycodes = touchView.keycodes.clone()
    }

    override fun onStart() {
        super.onStart()
        networkClient.start()
        networkClient.syncAllBindings(touchView.keycodes)
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
    }

    override fun onPause() {
        super.onPause()
        touchView.releaseAllButtons()
    }

    override fun onStop() {
        super.onStop()
        touchView.releaseAllButtons()
        networkClient.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkClient.stop()
        feedbackManager.release()
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun optimizeRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }

            val modes = display?.supportedModes ?: return
            var bestMode = modes.firstOrNull() ?: return
            for (mode in modes) {
                if (mode.refreshRate > bestMode.refreshRate) {
                    bestMode = mode
                }
            }

            val params = window.attributes
            params.preferredDisplayModeId = bestMode.modeId
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                params.preferredRefreshRate = bestMode.refreshRate
            }
            window.attributes = params
        }
    }

    private fun showSettingsDialog() {
        val soundState = if (feedbackManager.soundEnabled) "ON" else "OFF"
        val hapticState = if (feedbackManager.hapticsEnabled) "ON" else "OFF"
        val volPercent = (feedbackManager.volume * 100).toInt()

        val soundStyleName = FeedbackManager.SOUND_STYLE_NAMES[feedbackManager.soundStyle]
        val options = arrayOf(
            "Change Key Bindings",
            "Button Width Scale (Current: ${(touchView.widthScale * 100).toInt()}%)",
            "Button Height Scale (Current: ${(touchView.heightScale * 100).toInt()}%)",
            "Button Spacing / Gap",
            "Vertical Position (Top / Center / Bottom)",
            "🔊 Click Sound: $soundState",
            "🎵 Sound Tone: $soundStyleName",
            "🎚️ Sound Volume: $volPercent%",
            "📳 Haptic Vibration: $hapticState",
            "Preset: Default (QWER / ASDF / ZXCV)",
            "Preset: 4-Key Rhythm (DFJK / Space)"
        )

        AlertDialog.Builder(this)
            .setTitle("Controller Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showKeyBindingPicker()
                    1 -> showWidthScalePicker()
                    2 -> showHeightScalePicker()
                    3 -> showGapScalePicker()
                    4 -> showVAlignPicker()
                    5 -> {
                        feedbackManager.setSoundEnabled(!feedbackManager.soundEnabled)
                        showSettingsDialog()
                    }
                    6 -> showSoundStylePicker()
                    7 -> showVolumePicker()
                    8 -> {
                        feedbackManager.setHapticsEnabled(!feedbackManager.hapticsEnabled)
                        showSettingsDialog()
                    }
                    9 -> applyDefaultPreset()
                    10 -> applyDfjkPreset()
                }
            }
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
                enableImmersiveMode()
            }
            .setOnDismissListener {
                enableImmersiveMode()
            }
            .show()
    }

    private fun showSoundStylePicker() {
        AlertDialog.Builder(this)
            .setTitle("Choose Hitsound Tone")
            .setItems(FeedbackManager.SOUND_STYLE_NAMES) { _, idx ->
                feedbackManager.setSoundStyle(idx)
                showSettingsDialog()
            }
            .setNegativeButton("Cancel") { _, _ -> showSettingsDialog() }
            .show()
    }

    private fun showVolumePicker() {
        val volumeLabels = arrayOf("100% (Maximum)", "85%", "70%", "50%", "30%", "15%", "0% (Mute)")
        val volumeValues = floatArrayOf(1.0f, 0.85f, 0.70f, 0.50f, 0.30f, 0.15f, 0.0f)

        AlertDialog.Builder(this)
            .setTitle("Click Sound Volume")
            .setItems(volumeLabels) { _, idx ->
                feedbackManager.setVolume(volumeValues[idx])
                showSettingsDialog()
            }
            .setNegativeButton("Cancel") { _, _ -> showSettingsDialog() }
            .show()
    }

    private fun showKeyBindingPicker() {
        val buttonItems = Array(12) { i ->
            val rowName = when (i / 4) {
                0 -> "UP"
                1 -> "MID"
                else -> "DOWN"
            }
            val col = (i % 4) + 1
            "Button $i ($rowName $col): ${KeyBindingsManager.getKeyName(touchView.keycodes[i])}"
        }

        AlertDialog.Builder(this)
            .setTitle("Select Button to Rebind")
            .setItems(buttonItems) { _, btnIdx ->
                showKeySelectorForButton(btnIdx)
            }
            .setNegativeButton("Back") { _, _ -> showSettingsDialog() }
            .show()
    }

    private fun showKeySelectorForButton(buttonId: Int) {
        val keyList = KeyBindingsManager.AVAILABLE_KEYS
        val keyNames = keyList.map { "${it.name} (Code ${it.code})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose Key for Button $buttonId")
            .setItems(keyNames) { _, keyIdx ->
                val selectedKey = keyList[keyIdx]
                touchView.updateKeycode(buttonId, selectedKey.code)
                networkClient.sendRemap(buttonId, selectedKey.code)
                showKeyBindingPicker()
            }
            .setNegativeButton("Cancel") { _, _ -> showKeyBindingPicker() }
            .show()
    }

    private fun showWidthScalePicker() {
        val scales = arrayOf("100% (Full Width)", "95%", "90%", "85%", "80%", "75%", "70%", "60%", "50%")
        val values = floatArrayOf(1.0f, 0.95f, 0.90f, 0.85f, 0.80f, 0.75f, 0.70f, 0.60f, 0.50f)

        AlertDialog.Builder(this)
            .setTitle("Grid Width Scale")
            .setItems(scales) { _, idx ->
                ScaleSettingsManager.setWidthScale(this, values[idx])
                touchView.reloadConfiguration()
                showSettingsDialog()
            }
            .setNegativeButton("Cancel") { _, _ -> showSettingsDialog() }
            .show()
    }

    private fun showHeightScalePicker() {
        val scales = arrayOf("100% (Full Height)", "94%", "88%", "82%", "75%", "70%", "60%", "50%")
        val values = floatArrayOf(1.0f, 0.94f, 0.88f, 0.82f, 0.75f, 0.70f, 0.60f, 0.50f)

        AlertDialog.Builder(this)
            .setTitle("Grid Height Scale")
            .setItems(scales) { _, idx ->
                ScaleSettingsManager.setHeightScale(this, values[idx])
                touchView.reloadConfiguration()
                showSettingsDialog()
            }
            .setNegativeButton("Cancel") { _, _ -> showSettingsDialog() }
            .show()
    }

    private fun showGapScalePicker() {
        val gaps = arrayOf("Tiny Gap (4px)", "Normal Gap (10px)", "Wide Gap (16px)", "Extra Wide (24px)")
        val values = floatArrayOf(0.4f, 1.0f, 1.6f, 2.4f)

        AlertDialog.Builder(this)
            .setTitle("Button Spacing")
            .setItems(gaps) { _, idx ->
                ScaleSettingsManager.setGapScale(this, values[idx])
                touchView.reloadConfiguration()
                showSettingsDialog()
            }
            .setNegativeButton("Cancel") { _, _ -> showSettingsDialog() }
            .show()
    }

    private fun showVAlignPicker() {
        val positions = arrayOf("Top", "Center", "Bottom")
        val values = intArrayOf(-1, 0, 1)

        AlertDialog.Builder(this)
            .setTitle("Vertical Position")
            .setItems(positions) { _, idx ->
                ScaleSettingsManager.setVAlign(this, values[idx])
                touchView.reloadConfiguration()
                showSettingsDialog()
            }
            .setNegativeButton("Cancel") { _, _ -> showSettingsDialog() }
            .show()
    }

    private fun applyDefaultPreset() {
        val defaultCodes = intArrayOf(
            16, 17, 18, 19, // Q, W, E, R
            30, 31, 32, 33, // A, S, D, F
            44, 45, 46, 47  // Z, X, C, V
        )
        for (i in 0 until 12) {
            touchView.updateKeycode(i, defaultCodes[i])
        }
        networkClient.syncAllBindings(touchView.keycodes)
        touchView.reloadConfiguration()
        enableImmersiveMode()
    }

    private fun applyDfjkPreset() {
        val dfjkCodes = intArrayOf(
            2, 3, 4, 5,     // 1, 2, 3, 4
            32, 33, 36, 37, // D, F, J, K
            57, 57, 57, 57  // SPACE, SPACE, SPACE, SPACE
        )
        for (i in 0 until 12) {
            touchView.updateKeycode(i, dfjkCodes[i])
        }
        networkClient.syncAllBindings(touchView.keycodes)
        touchView.reloadConfiguration()
        enableImmersiveMode()
    }
}
