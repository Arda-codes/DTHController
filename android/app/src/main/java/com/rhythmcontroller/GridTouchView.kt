package com.rhythmcontroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Ultra-low-latency 3x4 rhythm game touch grid view (12 buttons).
 *
 * Performance & Customization features:
 * - Dynamic scaling & positioning (width/height scale, pad gap, vertical alignment).
 * - Stylized row icons:
 *     Row 0: UP arrow icon
 *     Row 1: MID diamond/target icon
 *     Row 2: DOWN arrow icon
 * - Dynamic keybindings with instant on-screen labels.
 * - Direct MotionEvent processing with multi-touch tracking.
 * - Zero allocations on touch and draw hot paths.
 * - Instant visual state changes without animation/ripple latency.
 */
class GridTouchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val NUM_ROWS = 3
        const val NUM_COLS = 4
        const val NUM_BUTTONS = NUM_ROWS * NUM_COLS // 12
        private const val MAX_POINTERS = 32
        private const val BASE_PAD_MARGIN_PX = 10f
        private const val CORNER_RADIUS = 18f
    }

    var networkClient: NetworkClient? = null
    var feedbackManager: FeedbackManager? = null
    var onSettingsClicked: (() -> Unit)? = null

    // Scaling factors
    var widthScale = 0.96f
    var heightScale = 0.94f
    var gapScale = 1.0f
    var vAlign = 0 // -1: Top, 0: Center, 1: Bottom

    // Grid bounding area
    private val gridRect = RectF()

    // Preallocated tracking state for zero GC allocations
    private val pointerToButton = IntArray(MAX_POINTERS) { -1 }
    private val buttonTouchCount = IntArray(NUM_BUTTONS)
    private val buttonPressed = BooleanArray(NUM_BUTTONS)
    private val buttonRects = Array(NUM_BUTTONS) { RectF() }

    // Preallocated icon paths
    private val rowIconPaths = Array(NUM_BUTTONS) { Path() }

    // Keybindings & Labels
    val keycodes = IntArray(NUM_BUTTONS)
    private val buttonMainLabels = arrayOf(
        "UP 1", "UP 2", "UP 3", "UP 4",
        "MID 1", "MID 2", "MID 3", "MID 4",
        "DOWN 1", "DOWN 2", "DOWN 3", "DOWN 4"
    )
    private val keyNameLabels = Array(NUM_BUTTONS) { "" }

    // Settings icon touch area (top-left)
    private val settingsHitRect = RectF()
    private val settingsIconPath = Path()

    // Preallocated Paints
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0C0D14")
        style = Paint.Style.FILL
    }

    private val padNormalPaint = Paint().apply {
        color = Color.parseColor("#171924")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val padPressedPaint = Paint().apply {
        color = Color.parseColor("#00E5FF") // High-contrast neon cyan
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val padBorderPaint = Paint().apply {
        color = Color.parseColor("#262A3E")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val padPressedBorderPaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val iconNormalPaint = Paint().apply {
        color = Color.parseColor("#3D4566")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val iconPressedPaint = Paint().apply {
        color = Color.parseColor("#003840")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textMainPaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        textSize = 38f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val textMainPressedPaint = Paint().apply {
        color = Color.parseColor("#000000")
        textSize = 38f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val textSubPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val textSubPressedPaint = Paint().apply {
        color = Color.parseColor("#00252B")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val statusIndicatorPaint = Paint().apply {
        color = Color.parseColor("#F44336") // Red until connected
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val settingsPaint = Paint().apply {
        color = Color.parseColor("#5A6284")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private var isConnected = false

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            requestUnbufferedDispatch(android.view.InputDevice.SOURCE_TOUCHSCREEN)
        }
        reloadConfiguration()
    }

    fun reloadConfiguration() {
        widthScale = ScaleSettingsManager.getWidthScale(context)
        heightScale = ScaleSettingsManager.getHeightScale(context)
        gapScale = ScaleSettingsManager.getGapScale(context)
        vAlign = ScaleSettingsManager.getVAlign(context)

        val loadedCodes = KeyBindingsManager.loadKeycodes(context)
        for (i in 0 until NUM_BUTTONS) {
            keycodes[i] = loadedCodes[i]
            keyNameLabels[i] = "[${KeyBindingsManager.getKeyName(keycodes[i])}]"
        }

        recomputeLayout(width, height)
        invalidate()
    }

    fun updateKeycode(buttonId: Int, code: Int) {
        if (buttonId in 0 until NUM_BUTTONS) {
            keycodes[buttonId] = code
            keyNameLabels[buttonId] = "[${KeyBindingsManager.getKeyName(code)}]"
            KeyBindingsManager.saveKeycode(context, buttonId, code)
            networkClient?.sendRemap(buttonId, code)
            invalidate()
        }
    }

    fun setConnectionStatus(connected: Boolean) {
        if (isConnected != connected) {
            isConnected = connected
            statusIndicatorPaint.color = if (connected) {
                Color.parseColor("#00E676") // Green
            } else {
                Color.parseColor("#F44336") // Red
            }
            postInvalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeLayout(w, h)
    }

    private fun recomputeLayout(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return

        // Settings button at top-left
        settingsHitRect.set(16f, 16f, 86f, 86f)

        // Calculate scaled grid area
        val effectiveW = w * widthScale
        val effectiveH = h * heightScale
        val gridLeft = (w - effectiveW) / 2f
        val gridTop = when (vAlign) {
            -1 -> 20f
            1 -> h - effectiveH - 20f
            else -> (h - effectiveH) / 2f
        }
        gridRect.set(gridLeft, gridTop, gridLeft + effectiveW, gridTop + effectiveH)

        val colWidth = effectiveW / NUM_COLS
        val rowHeight = effectiveH / NUM_ROWS
        val margin = BASE_PAD_MARGIN_PX * gapScale

        for (row in 0 until NUM_ROWS) {
            for (col in 0 until NUM_COLS) {
                val index = row * NUM_COLS + col
                val left = gridLeft + col * colWidth + margin
                val top = gridTop + row * rowHeight + margin
                val right = gridLeft + (col + 1) * colWidth - margin
                val bottom = gridTop + (row + 1) * rowHeight - margin
                buttonRects[index].set(left, top, right, bottom)

                // Build row iconography
                buildRowIcon(index, row, buttonRects[index])
            }
        }
    }

    private fun buildRowIcon(index: Int, row: Int, rect: RectF) {
        val path = rowIconPaths[index]
        path.reset()

        val cx = rect.centerX()
        val cy = rect.top + rect.height() * 0.28f // Positioned in top third of pad

        when (row) {
            0 -> {
                // ROW 0: UP arrow / chevron
                val size = 22f
                path.moveTo(cx, cy - size)
                path.lineTo(cx - size * 1.3f, cy + size * 0.8f)
                path.lineTo(cx - size * 0.5f, cy + size * 0.8f)
                path.lineTo(cx, cy - size * 0.1f)
                path.lineTo(cx + size * 0.5f, cy + size * 0.8f)
                path.lineTo(cx + size * 1.3f, cy + size * 0.8f)
                path.close()
            }
            1 -> {
                // ROW 1: MIDDLE diamond / target
                val rx = 24f
                val ry = 18f
                path.moveTo(cx, cy - ry)
                path.lineTo(cx + rx, cy)
                path.lineTo(cx, cy + ry)
                path.lineTo(cx - rx, cy)
                path.close()
            }
            2 -> {
                // ROW 2: DOWN arrow / chevron
                val size = 22f
                path.moveTo(cx, cy + size)
                path.lineTo(cx - size * 1.3f, cy - size * 0.8f)
                path.lineTo(cx - size * 0.5f, cy - size * 0.8f)
                path.lineTo(cx, cy + size * 0.1f)
                path.lineTo(cx + size * 0.5f, cy - size * 0.8f)
                path.lineTo(cx + size * 1.3f, cy - size * 0.8f)
                path.close()
            }
        }
    }

    private fun getButtonAt(x: Float, y: Float): Int {
        if (!gridRect.contains(x, y)) return -1
        val relX = x - gridRect.left
        val relY = y - gridRect.top
        val col = ((relX / gridRect.width()) * NUM_COLS).toInt().coerceIn(0, NUM_COLS - 1)
        val row = ((relY / gridRect.height()) * NUM_ROWS).toInt().coerceIn(0, NUM_ROWS - 1)

        val btn = row * NUM_COLS + col
        return if (buttonRects[btn].contains(x, y)) btn else -1
    }

    /**
     * Hot path: Multi-touch event handler.
     * ZERO allocations: all structures preallocated, no object instantiation.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            requestUnbufferedDispatch(event)
        }

        val action = event.actionMasked
        val actionIndex = event.actionIndex

        // Check settings click on first touch
        if (action == MotionEvent.ACTION_DOWN) {
            val x = event.getX(actionIndex)
            val y = event.getY(actionIndex)
            if (settingsHitRect.contains(x, y)) {
                onSettingsClicked?.invoke()
                return true
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = event.getPointerId(actionIndex)
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                val btn = getButtonAt(x, y)

                if (pointerId in 0 until MAX_POINTERS) {
                    pointerToButton[pointerId] = btn
                }

                if (btn in 0 until NUM_BUTTONS) {
                    val count = ++buttonTouchCount[btn]
                    if (count == 1) {
                        buttonPressed[btn] = true
                        // 1. TRANSMIT OVER WIRE IMMEDIATELY
                        networkClient?.sendEvent(btn, 1) // 1 = DOWN
                        // 2. Audio/Haptic feedback (async)
                        feedbackManager?.triggerPressFeedback()
                        // 3. UI draw
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                var visualChanged = false
                val pointerCount = event.pointerCount

                for (i in 0 until pointerCount) {
                    val pointerId = event.getPointerId(i)
                    if (pointerId in 0 until MAX_POINTERS) {
                        val prevBtn = pointerToButton[pointerId]
                        val x = event.getX(i)
                        val y = event.getY(i)
                        val newBtn = getButtonAt(x, y)

                        if (prevBtn != newBtn) {
                            // Finger slid to a different button or off grid
                            if (prevBtn in 0 until NUM_BUTTONS) {
                                val count = --buttonTouchCount[prevBtn]
                                if (count <= 0) {
                                    buttonTouchCount[prevBtn] = 0
                                    buttonPressed[prevBtn] = false
                                    networkClient?.sendEvent(prevBtn, 0) // 0 = UP
                                    visualChanged = true
                                }
                            }

                            pointerToButton[pointerId] = newBtn

                            if (newBtn in 0 until NUM_BUTTONS) {
                                val count = ++buttonTouchCount[newBtn]
                                if (count == 1) {
                                    buttonPressed[newBtn] = true
                                    networkClient?.sendEvent(newBtn, 1) // 1 = DOWN
                                    feedbackManager?.triggerPressFeedback()
                                    visualChanged = true
                                }
                            }
                        }
                    }
                }

                if (visualChanged) {
                    invalidate()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(actionIndex)
                if (pointerId in 0 until MAX_POINTERS) {
                    val btn = pointerToButton[pointerId]
                    pointerToButton[pointerId] = -1

                    if (btn in 0 until NUM_BUTTONS) {
                        val count = --buttonTouchCount[btn]
                        if (count <= 0) {
                            buttonTouchCount[btn] = 0
                            buttonPressed[btn] = false
                            networkClient?.sendEvent(btn, 0) // 0 = UP
                            invalidate()
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                // Final finger lifted: guarantee complete release of all buttons
                releaseAllButtons()
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseAllButtons()
            }
        }
        return true
    }

    /**
     * Complete safety flush: releases all touch tracking and sends KEY_UP for any active button.
     */
    fun releaseAllButtons() {
        for (i in 0 until MAX_POINTERS) {
            pointerToButton[i] = -1
        }
        var visualChanged = false
        for (btn in 0 until NUM_BUTTONS) {
            if (buttonPressed[btn] || buttonTouchCount[btn] > 0) {
                buttonTouchCount[btn] = 0
                buttonPressed[btn] = false
                networkClient?.sendEvent(btn, 0) // 0 = UP
                visualChanged = true
            }
        }
        if (visualChanged) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw 12 buttons
        for (i in 0 until NUM_BUTTONS) {
            val rect = buttonRects[i]
            val pressed = buttonPressed[i]

            // 1. Draw pad body
            val fillPaint = if (pressed) padPressedPaint else padNormalPaint
            canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, fillPaint)

            // 2. Draw pad border
            val borderPaint = if (pressed) padPressedBorderPaint else padBorderPaint
            canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, borderPaint)

            // 3. Draw row icon (Up / Mid / Down image)
            val iconPaint = if (pressed) iconPressedPaint else iconNormalPaint
            canvas.drawPath(rowIconPaths[i], iconPaint)

            // 4. Draw text labels
            val centerX = rect.centerX()
            val centerY = rect.centerY() + rect.height() * 0.12f

            val mainPaint = if (pressed) textMainPressedPaint else textMainPaint
            val subPaint = if (pressed) textSubPressedPaint else textSubPaint

            canvas.drawText(buttonMainLabels[i], centerX, centerY, mainPaint)
            canvas.drawText(keyNameLabels[i], centerX, centerY + 42f, subPaint)
        }

        // Draw gear icon for Settings (top-left)
        canvas.drawCircle(51f, 51f, 20f, settingsPaint)
        canvas.drawCircle(51f, 51f, 8f, settingsPaint)

        // Draw connection status indicator (top-right)
        val statusX = width.toFloat() - 24f
        val statusY = 24f
        canvas.drawCircle(statusX, statusY, 8f, statusIndicatorPaint)

        // Draw live RTT latency
        if (latencyText.isNotEmpty() && isConnected) {
            canvas.drawText(latencyText, statusX - 22f, statusY + 8f, latencyPaint)
        }
    }

    private var latencyText = ""
    private val latencyPaint = Paint().apply {
        color = Color.parseColor("#00E676")
        textSize = 24f
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
        isAntiAlias = true
    }

    fun setLatencyMs(rtt: Double) {
        latencyText = String.format(java.util.Locale.US, "⚡ %.1f ms", rtt)
        latencyPaint.color = when {
            rtt < 2.5 -> Color.parseColor("#00E676")
            rtt < 6.0 -> Color.parseColor("#FFD600")
            else -> Color.parseColor("#FF5252")
        }
        postInvalidate()
    }
}
