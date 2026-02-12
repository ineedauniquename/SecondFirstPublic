package com.secondfirst.paintapp

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var paintView: PaintView
    private lateinit var controlsPanel: LinearLayout
    private var isFullScreen = false

    // Current brush settings
    private var selectedChannel = Channel.RED
    private var intensity = 128
    private var brushWidth = 20f

    enum class Channel { RED, GREEN, BLUE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        // Canvas area
        paintView = PaintView(this)
        root.addView(paintView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Controls panel
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#252525"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        // --- Row 1: Color buttons + Clear ---
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnRed = makeColorButton("R", Color.parseColor("#FF4444"))
        val btnGreen = makeColorButton("G", Color.parseColor("#44CC44"))
        val btnBlue = makeColorButton("B", Color.parseColor("#4488FF"))
        val btnClear = Button(this).apply {
            text = "CLR"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
            setPadding(dp(12), dp(4), dp(12), dp(4))
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { paintView.clear() }
        }
        val btnFullScreen = Button(this).apply {
            text = "VIEW"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#446688"))
            setPadding(dp(12), dp(4), dp(12), dp(4))
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { enterFullScreen() }
        }

        // Highlight default selection
        btnRed.alpha = 1.0f
        btnGreen.alpha = 0.4f
        btnBlue.alpha = 0.4f

        btnRed.setOnClickListener {
            selectedChannel = Channel.RED
            btnRed.alpha = 1.0f; btnGreen.alpha = 0.4f; btnBlue.alpha = 0.4f
            updatePreview()
        }
        btnGreen.setOnClickListener {
            selectedChannel = Channel.GREEN
            btnRed.alpha = 0.4f; btnGreen.alpha = 1.0f; btnBlue.alpha = 0.4f
            updatePreview()
        }
        btnBlue.setOnClickListener {
            selectedChannel = Channel.BLUE
            btnRed.alpha = 0.4f; btnGreen.alpha = 0.4f; btnBlue.alpha = 1.0f
            updatePreview()
        }

        val spacer = View(this)
        colorRow.addView(btnRed, makeRowLP(0, dp(8)))
        colorRow.addView(btnGreen, makeRowLP(0, dp(8)))
        colorRow.addView(btnBlue, makeRowLP(0, dp(8)))
        colorRow.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        colorRow.addView(btnFullScreen, makeRowLP(0, dp(8)))
        colorRow.addView(btnClear, makeRowLP(0, 0))
        controls.addView(colorRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // --- Row 2: Intensity slider ---
        val intensityLabel = makeLabel("Intensity: $intensity")
        val intensitySlider = SeekBar(this).apply {
            max = 255
            progress = intensity
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    intensity = value.coerceAtLeast(1)
                    intensityLabel.text = "Intensity: $intensity"
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        controls.addView(makeSliderRow(intensityLabel, intensitySlider))

        // --- Row 3: Brush width slider ---
        val widthLabel = makeLabel("Brush: ${brushWidth.toInt()}px")
        val widthSlider = SeekBar(this).apply {
            max = 95  // range 5..100
            progress = brushWidth.toInt() - 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    brushWidth = (value + 5).toFloat()
                    widthLabel.text = "Brush: ${brushWidth.toInt()}px"
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        controls.addView(makeSliderRow(widthLabel, widthSlider))

        // --- Preview swatch ---
        previewSwatch = View(this).apply {
            setBackgroundColor(currentColor())
        }
        val previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val previewLabel = makeLabel("Preview:")
        previewRow.addView(previewLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(12) })
        previewRow.addView(previewSwatch, LinearLayout.LayoutParams(dp(48), dp(32)))
        controls.addView(previewRow)

        controlsPanel = controls
        root.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
    }

    private fun enterFullScreen() {
        isFullScreen = true
        controlsPanel.visibility = View.GONE
    }

    fun exitFullScreen() {
        isFullScreen = false
        controlsPanel.visibility = View.VISIBLE
    }

    private lateinit var previewSwatch: View

    private fun currentColor(): Int {
        return when (selectedChannel) {
            Channel.RED -> Color.rgb(intensity, 0, 0)
            Channel.GREEN -> Color.rgb(0, intensity, 0)
            Channel.BLUE -> Color.rgb(0, 0, intensity)
        }
    }

    private fun updatePreview() {
        if (::previewSwatch.isInitialized) {
            previewSwatch.setBackgroundColor(currentColor())
        }
    }

    private fun makeColorButton(label: String, color: Int): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setPadding(dp(20), dp(4), dp(20), dp(4))
            textSize = 16f
        }
    }

    private fun makeLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 14f
        }
    }

    private fun makeSliderRow(label: TextView, slider: SeekBar): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
            addView(label, LinearLayout.LayoutParams(dp(130),
                LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(slider, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun makeRowLP(marginStart: Int, marginEnd: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.marginEnd = marginEnd
            this.marginStart = marginStart
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // ─────────────────────────────────────────────
    // Custom painting view with additive RGB blending
    // ─────────────────────────────────────────────
    inner class PaintView(context: android.content.Context) : View(context) {

        // The composited bitmap that accumulates all strokes
        private var canvasBitmap: Bitmap? = null
        private var drawCanvas: Canvas? = null

        // Temporary layer for the current in-progress stroke
        private var strokeBitmap: Bitmap? = null
        private var strokeCanvas: Canvas? = null

        private val currentPath = Path()
        private val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        private val bitmapPaint = Paint(Paint.DITHER_FLAG)

        private var lastX = 0f
        private var lastY = 0f

        // Snapshot of settings when stroke began
        private var strokeColor = Color.RED
        private var strokeWidth = 20f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val oldBitmap = canvasBitmap
            if (oldBitmap == null || oldBitmap.width != w || oldBitmap.height != h) {
                canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    drawCanvas = Canvas(it)
                    drawCanvas!!.drawColor(Color.BLACK)
                    if (oldBitmap != null) {
                        drawCanvas!!.drawBitmap(oldBitmap, 0f, 0f, null)
                        oldBitmap.recycle()
                    }
                }
            }
            strokeBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                strokeCanvas = Canvas(it)
            }
        }

        override fun onDraw(canvas: Canvas) {
            // Draw the accumulated canvas
            canvasBitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
            // Draw the in-progress stroke on top with additive blending
            strokeBitmap?.let {
                val addPaint = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                }
                canvas.drawBitmap(it, 0f, 0f, addPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isFullScreen) {
                if (event.action == MotionEvent.ACTION_UP) {
                    exitFullScreen()
                }
                return true
            }

            val x = event.x
            val y = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Lock in brush settings for this stroke
                    strokeColor = currentColor()
                    strokeWidth = brushWidth

                    strokePaint.color = strokeColor
                    strokePaint.strokeWidth = strokeWidth

                    currentPath.reset()
                    currentPath.moveTo(x, y)
                    lastX = x
                    lastY = y

                    // Clear the stroke layer
                    strokeCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x
                    lastY = y

                    // Redraw the full current stroke onto the stroke layer
                    strokeCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    strokeCanvas?.drawPath(currentPath, strokePaint)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    currentPath.lineTo(x, y)

                    // Render final stroke onto stroke layer
                    strokeCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    strokeCanvas?.drawPath(currentPath, strokePaint)

                    // Composite stroke onto the main canvas with additive blending
                    compositeStrokeAdditive()

                    // Clear stroke layer
                    strokeCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    currentPath.reset()
                    invalidate()
                    return true
                }
            }
            return false
        }

        /**
         * Additively blends the stroke bitmap onto the canvas bitmap pixel-by-pixel.
         * Each channel is clamped to 255, so overlapping R+G = yellow, R+G+B = white, etc.
         */
        private fun compositeStrokeAdditive() {
            val cb = canvasBitmap ?: return
            val sb = strokeBitmap ?: return
            val w = cb.width
            val h = cb.height

            val canvasPixels = IntArray(w * h)
            val strokePixels = IntArray(w * h)
            cb.getPixels(canvasPixels, 0, w, 0, 0, w, h)
            sb.getPixels(strokePixels, 0, w, 0, 0, w, h)

            for (i in canvasPixels.indices) {
                val sp = strokePixels[i]
                val sa = (sp ushr 24) and 0xFF
                if (sa == 0) continue  // transparent stroke pixel, skip

                val cp = canvasPixels[i]

                // Extract channels
                val cr = (cp shr 16) and 0xFF
                val cg = (cp shr 8) and 0xFF
                val cb2 = cp and 0xFF

                val sr = (sp shr 16) and 0xFF
                val sg = (sp shr 8) and 0xFF
                val sb2 = sp and 0xFF

                // Scale stroke by its alpha (anti-aliased edges)
                val factor = sa / 255f
                val nr = (cr + sr * factor).toInt().coerceAtMost(255)
                val ng = (cg + sg * factor).toInt().coerceAtMost(255)
                val nb = (cb2 + sb2 * factor).toInt().coerceAtMost(255)

                canvasPixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }

            cb.setPixels(canvasPixels, 0, w, 0, 0, w, h)
        }

        fun clear() {
            drawCanvas?.drawColor(Color.BLACK)
            strokeCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            currentPath.reset()
            invalidate()
        }
    }
}
