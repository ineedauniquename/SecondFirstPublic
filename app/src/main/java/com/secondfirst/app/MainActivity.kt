package com.secondfirst.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStream

class MainActivity : Activity() {

    companion object {
        private const val PICK_IMAGE_1 = 1001
        private const val PICK_IMAGE_2 = 1002
    }

    private lateinit var preview1: ImageView
    private lateinit var preview2: ImageView
    private lateinit var previewResult: ImageView
    private lateinit var btnPick1: Button
    private lateinit var btnClear1: Button
    private lateinit var btnPick2: Button
    private lateinit var btnClear2: Button
    private lateinit var btnMultiply: Button
    private lateinit var btnAverage: Button
    private lateinit var biasInput: EditText
    private lateinit var btnClearResult: Button
    private lateinit var btnSave: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fullscreenOverlay: FrameLayout
    private lateinit var fullscreenImage: ImageView
    private lateinit var exprInput: EditText
    private lateinit var btnApplyExpr: Button

    private var bitmap1: Bitmap? = null
    private var bitmap2: Bitmap? = null
    private var resultBitmap: Bitmap? = null
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root is a FrameLayout so we can overlay fullscreen on top
        val frameRoot = FrameLayout(this)

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1C1B1F"))
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setGravity(Gravity.CENTER_HORIZONTAL)
        }

        val title = TextView(this).apply {
            text = "Image Multiplier"
            textSize = 26f
            setTextColor(Color.WHITE)
            setGravity(Gravity.CENTER)
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        val subtitle = TextView(this).apply {
            text = "Select images, then multiply or average pixels"
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            setGravity(Gravity.CENTER)
            setPadding(0, 0, 0, 48)
        }
        layout.addView(subtitle)

        // Image 1 section
        layout.addView(sectionLabel("Image 1"))
        preview1 = imagePreview()
        layout.addView(preview1)
        val row1 = buttonRow()
        btnPick1 = styledButton("Select Image 1")
        btnPick1.setOnClickListener { pickImage(PICK_IMAGE_1) }
        btnClear1 = styledButton("Clear").apply {
            setBackgroundColor(Color.parseColor("#93000A"))
            isEnabled = false
        }
        btnClear1.setOnClickListener { clearImage(1) }
        row1.addView(btnPick1, rowChildParams(3f))
        row1.addView(btnClear1, rowChildParams(1f))
        layout.addView(row1)

        // Image 2 section
        layout.addView(sectionLabel("Image 2"))
        preview2 = imagePreview()
        layout.addView(preview2)
        val row2 = buttonRow()
        btnPick2 = styledButton("Select Image 2")
        btnPick2.setOnClickListener { pickImage(PICK_IMAGE_2) }
        btnClear2 = styledButton("Clear").apply {
            setBackgroundColor(Color.parseColor("#93000A"))
            isEnabled = false
        }
        btnClear2.setOnClickListener { clearImage(2) }
        row2.addView(btnPick2, rowChildParams(3f))
        row2.addView(btnClear2, rowChildParams(1f))
        layout.addView(row2)

        // Operation buttons
        val opsLabel = sectionLabel("Operations")
        val opsParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 32, 0, 0) }
        layout.addView(opsLabel, opsParams)

        btnMultiply = styledButton("Multiply (2 images)").apply {
            setBackgroundColor(Color.parseColor("#6750A4"))
            isEnabled = false
        }
        btnMultiply.setOnClickListener { multiplyImages() }
        layout.addView(btnMultiply)

        btnAverage = styledButton("Average (1 or 2 images)").apply {
            setBackgroundColor(Color.parseColor("#006C4C"))
            isEnabled = false
        }
        btnAverage.setOnClickListener { averageImages() }
        layout.addView(btnAverage)

        // Expression input row
        val exprRow = buttonRow()
        exprInput = EditText(this).apply {
            setText("P1*P2/255")
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2D2D30"))
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(24, 16, 24, 16)
            textSize = 14f
            setSingleLine(true)
        }
        btnApplyExpr = styledButton("Apply Expr").apply {
            setBackgroundColor(Color.parseColor("#7B5EA7"))
            isEnabled = false
        }
        btnApplyExpr.setOnClickListener { applyExpression() }
        exprRow.addView(exprInput, rowChildParams(3f))
        exprRow.addView(btnApplyExpr, rowChildParams(1f))
        layout.addView(exprRow)

        // Bias input row
        val biasRow = buttonRow()
        val biasLabel = TextView(this).apply {
            text = "Bias:"
            textSize = 16f
            setTextColor(Color.WHITE)
            setGravity(Gravity.CENTER_VERTICAL)
            setPadding(8, 0, 16, 0)
        }
        biasInput = EditText(this).apply {
            setText("1.0")
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2D2D30"))
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            setPadding(24, 16, 24, 16)
            textSize = 16f
            setGravity(Gravity.CENTER)
        }
        val biasHint = TextView(this).apply {
            text = "(result-img1)*bias+img1"
            textSize = 11f
            setTextColor(Color.parseColor("#808080"))
            setGravity(Gravity.CENTER_VERTICAL)
            setPadding(16, 0, 0, 0)
        }
        biasRow.addView(biasLabel, rowChildParams(1f))
        biasRow.addView(biasInput, rowChildParams(1.5f))
        biasRow.addView(biasHint, rowChildParams(3f))
        val biasRowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 16, 0, 16) }
        layout.addView(biasRow, biasRowParams)

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 8, 0, 8)
            layoutParams = lp
        }
        layout.addView(progressBar)

        // Status text
        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#B0B0B0"))
            setGravity(Gravity.CENTER)
            setPadding(0, 8, 0, 16)
        }
        layout.addView(statusText)

        // Result section
        layout.addView(sectionLabel("Result (tap to fullscreen)"))
        previewResult = imagePreview()
        previewResult.setOnClickListener { showFullscreen() }
        layout.addView(previewResult)

        // Result buttons row
        val rowResult = buttonRow()
        btnSave = styledButton("Save to Gallery").apply {
            isEnabled = false
        }
        btnSave.setOnClickListener { saveResult() }
        btnClearResult = styledButton("Clear").apply {
            setBackgroundColor(Color.parseColor("#93000A"))
            isEnabled = false
        }
        btnClearResult.setOnClickListener { clearResult() }
        rowResult.addView(btnSave, rowChildParams(3f))
        rowResult.addView(btnClearResult, rowChildParams(1f))
        layout.addView(rowResult)

        // Brighten / Darken row
        val rowBrightness = buttonRow()
        val btnBrighten = styledButton("Brighten +10%").apply {
            setBackgroundColor(Color.parseColor("#5C5C00"))
        }
        btnBrighten.setOnClickListener { adjustBrightness(1.10f) }
        val btnDarken = styledButton("Darken -10%").apply {
            setBackgroundColor(Color.parseColor("#3A3A3A"))
        }
        btnDarken.setOnClickListener { adjustBrightness(0.90f) }
        rowBrightness.addView(btnBrighten, rowChildParams(1f))
        rowBrightness.addView(btnDarken, rowChildParams(1f))
        layout.addView(rowBrightness)

        root.addView(layout)
        frameRoot.addView(root)

        // Fullscreen overlay (hidden by default)
        fullscreenOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        fullscreenImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { hideFullscreen() }
        }
        fullscreenOverlay.addView(fullscreenImage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        frameRoot.addView(fullscreenOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(frameRoot)
    }

    override fun onBackPressed() {
        if (fullscreenOverlay.visibility == View.VISIBLE) {
            hideFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    private fun showFullscreen() {
        val bmp = resultBitmap ?: return
        fullscreenImage.setImageBitmap(bmp)
        fullscreenOverlay.visibility = View.VISIBLE
    }

    private fun hideFullscreen() {
        fullscreenOverlay.visibility = View.GONE
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.parseColor("#D0BCFF"))
            setPadding(0, 24, 0, 8)
        }
    }

    private fun imagePreview(): ImageView {
        return ImageView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 400
            )
            lp.setMargins(0, 8, 0, 16)
            layoutParams = lp
            setBackgroundColor(Color.parseColor("#2D2D30"))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
    }

    private fun styledButton(label: String): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4A4458"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 8, 0, 8)
            layoutParams = lp
        }
    }

    private fun buttonRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
    }

    private fun rowChildParams(weight: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
            setMargins(4, 8, 4, 8)
        }
    }

    private fun clearImage(which: Int) {
        when (which) {
            1 -> {
                bitmap1 = null
                preview1.setImageBitmap(null)
                btnPick1.text = "Select Image 1"
                btnClear1.isEnabled = false
            }
            2 -> {
                bitmap2 = null
                preview2.setImageBitmap(null)
                btnPick2.text = "Select Image 2"
                btnClear2.isEnabled = false
            }
        }
        updateButtons()
    }

    private fun adjustBrightness(factor: Float) {
        val bmp = resultBitmap ?: return
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in 0 until w * h) {
            val p = pixels[i]
            val r = (((p shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
            val g = (((p shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
            val b = ((p and 0xFF) * factor).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        resultBitmap = result
        previewResult.setImageBitmap(result)
        val pct = if (factor > 1f) "+${((factor - 1f) * 100).toInt()}%" else "-${((1f - factor) * 100).toInt()}%"
        statusText.text = "Brightness $pct applied (${w}x${h})"
    }

    private fun clearResult() {
        resultBitmap = null
        previewResult.setImageBitmap(null)
        btnSave.isEnabled = false
        btnClearResult.isEnabled = false
        updateButtons()
    }

    private fun updateButtons() {
        val has1 = bitmap1 != null
        val has2 = bitmap2 != null
        val hasResult = resultBitmap != null
        btnMultiply.isEnabled = has1 && has2 && !isProcessing
        btnAverage.isEnabled = (has1 || has2 || hasResult) && !isProcessing
        btnApplyExpr.isEnabled = (has1 || has2) && !isProcessing
        btnSave.isEnabled = hasResult && !isProcessing
        btnClearResult.isEnabled = hasResult && !isProcessing
        if (has1 && has2) {
            statusText.text = "Ready! Output: ${
                minOf(bitmap1!!.width, bitmap2!!.width)
            }x${minOf(bitmap1!!.height, bitmap2!!.height)}"
        } else if (has1) {
            statusText.text = "Image 1 loaded. Select Image 2 or use Average."
        } else if (has2) {
            statusText.text = "Image 2 loaded. Select Image 1 or use Average."
        } else if (hasResult) {
            statusText.text = "Result available. Average to blur further."
        } else {
            statusText.text = ""
        }
    }

    private fun pickImage(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        val uri = data.data ?: return
        val bitmap = decodeBitmap(uri) ?: run {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            return
        }

        when (requestCode) {
            PICK_IMAGE_1 -> {
                bitmap1 = bitmap
                preview1.setImageBitmap(bitmap)
                btnPick1.text = "Image 1 (${bitmap.width}x${bitmap.height})"
                btnClear1.isEnabled = true
            }
            PICK_IMAGE_2 -> {
                bitmap2 = bitmap
                preview2.setImageBitmap(bitmap)
                btnPick2.text = "Image 2 (${bitmap.width}x${bitmap.height})"
                btnClear2.isEnabled = true
            }
        }
        updateButtons()
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(inputStream, null, opts).also {
                inputStream?.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getBias(): Float {
        return try {
            biasInput.text.toString().toFloat()
        } catch (e: Exception) {
            1.0f
        }
    }

    // Apply bias: output = (result - img1) * bias + img1, per channel, clamped 0-255
    private fun applyBias(raw: Bitmap, ref: Bitmap, bias: Float): Bitmap {
        if (bias == 1.0f) return raw
        val w = raw.width
        val h = raw.height
        val refScaled = Bitmap.createScaledBitmap(ref, w, h, true)
        val rawPx = IntArray(w * h)
        val refPx = IntArray(w * h)
        raw.getPixels(rawPx, 0, w, 0, 0, w, h)
        refScaled.getPixels(refPx, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in 0 until w * h) {
            val rr = (rawPx[i] shr 16) and 0xFF
            val rg = (rawPx[i] shr 8) and 0xFF
            val rb = rawPx[i] and 0xFF
            val ir = (refPx[i] shr 16) and 0xFF
            val ig = (refPx[i] shr 8) and 0xFF
            val ib = refPx[i] and 0xFF
            val r = ((rr - ir) * bias + ir).toInt().coerceIn(0, 255)
            val g = ((rg - ig) * bias + ig).toInt().coerceIn(0, 255)
            val b = ((rb - ib) * bias + ib).toInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        if (refScaled !== ref) refScaled.recycle()
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    // 3x3 box average with toroidal wrapping (image treated as torus)
    private fun toroidalAverage(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val allPixels = IntArray(w * h)
        src.getPixels(allPixels, 0, w, 0, 0, w, h)
        val result = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                var rSum = 0; var gSum = 0; var bSum = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx + w) % w  // toroidal wrap
                        val ny = (y + dy + h) % h
                        val p = allPixels[ny * w + nx]
                        rSum += (p shr 16) and 0xFF
                        gSum += (p shr 8) and 0xFF
                        bSum += p and 0xFF
                    }
                }
                val r = rSum / 9
                val g = gSum / 9
                val b = bSum / 9
                result[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    private fun averageImages() {
        if (isProcessing) return
        val bmp1 = bitmap1
        val bmp2 = bitmap2
        val resBmp = resultBitmap

        // If result exists, average just the result (snapshot copy to avoid self-reference)
        // Otherwise fall back to source images
        if (resBmp == null && bmp1 == null && bmp2 == null) return

        isProcessing = true
        btnMultiply.isEnabled = false
        btnAverage.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.max = 100
        progressBar.progress = 0

        if (resBmp != null) {
            // Average the result image - copy it first so we read from snapshot, not live result
            statusText.text = "Re-averaging result..."
            val snapshot = resBmp.copy(Bitmap.Config.ARGB_8888, false)

            Thread {
                runOnUiThread { progressBar.progress = 30 }
                val result = toroidalAverage(snapshot)
                snapshot.recycle()

                val bias = getBias()
                val biased = if (bias != 1.0f && bmp1 != null) applyBias(result, bmp1, bias) else result
                if (biased !== result) result.recycle()

                runOnUiThread {
                    resultBitmap = biased
                    previewResult.setImageBitmap(biased)
                    progressBar.visibility = View.GONE
                    val biasStr = if (bias != 1.0f && bmp1 != null) " bias=$bias" else ""
                    statusText.text = "Re-averaged! ${biased.width}x${biased.height}$biasStr"
                    isProcessing = false
                    updateButtons()
                }
            }.start()
        } else {
            statusText.text = "Averaging..."

            Thread {
                if (bmp1 != null && bmp2 != null) {
                    val w = minOf(bmp1.width, bmp2.width)
                    val h = minOf(bmp1.height, bmp2.height)
                    val s1 = Bitmap.createScaledBitmap(bmp1, w, h, true)
                    val s2 = Bitmap.createScaledBitmap(bmp2, w, h, true)

                    runOnUiThread { progressBar.progress = 20 }
                    val avg1 = toroidalAverage(s1)
                    runOnUiThread { progressBar.progress = 50 }
                    val avg2 = toroidalAverage(s2)
                    runOnUiThread { progressBar.progress = 80 }

                    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val p1 = IntArray(w * h)
                    val p2 = IntArray(w * h)
                    val pr = IntArray(w * h)
                    avg1.getPixels(p1, 0, w, 0, 0, w, h)
                    avg2.getPixels(p2, 0, w, 0, 0, w, h)
                    for (i in 0 until w * h) {
                        val r = (((p1[i] shr 16) and 0xFF) + ((p2[i] shr 16) and 0xFF)) / 2
                        val g = (((p1[i] shr 8) and 0xFF) + ((p2[i] shr 8) and 0xFF)) / 2
                        val b = ((p1[i] and 0xFF) + (p2[i] and 0xFF)) / 2
                        pr[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    result.setPixels(pr, 0, w, 0, 0, w, h)

                    if (s1 !== bmp1) s1.recycle()
                    if (s2 !== bmp2) s2.recycle()
                    avg1.recycle()
                    avg2.recycle()

                    val bias = getBias()
                    val biased = if (bias != 1.0f && bmp1 != null) applyBias(result, bmp1, bias) else result
                    if (biased !== result) result.recycle()

                    runOnUiThread {
                        resultBitmap = biased
                        previewResult.setImageBitmap(biased)
                        progressBar.visibility = View.GONE
                        val biasStr = if (bias != 1.0f && bmp1 != null) " bias=$bias" else ""
                        statusText.text = "Averaged! ${w}x${h}$biasStr"
                        isProcessing = false
                        updateButtons()
                    }
                } else {
                    val src = (bmp1 ?: bmp2)!!
                    runOnUiThread { progressBar.progress = 30 }
                    val result = toroidalAverage(src)

                    val bias = getBias()
                    val biased = if (bias != 1.0f && bmp1 != null) applyBias(result, bmp1, bias) else result
                    if (biased !== result) result.recycle()

                    runOnUiThread {
                        resultBitmap = biased
                        previewResult.setImageBitmap(biased)
                        progressBar.visibility = View.GONE
                        val biasStr = if (bias != 1.0f && bmp1 != null) " bias=$bias" else ""
                        statusText.text = "Averaged! ${src.width}x${src.height}$biasStr"
                        isProcessing = false
                        updateButtons()
                    }
                }
            }.start()
        }
    }

    private fun multiplyImages() {
        val bmp1 = bitmap1 ?: return
        val bmp2 = bitmap2 ?: return
        if (isProcessing) return

        isProcessing = true
        btnMultiply.isEnabled = false
        btnAverage.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "Multiplying pixels..."

        val width = minOf(bmp1.width, bmp2.width)
        val height = minOf(bmp1.height, bmp2.height)
        progressBar.max = height

        val scaled1 = Bitmap.createScaledBitmap(bmp1, width, height, true)
        val scaled2 = Bitmap.createScaledBitmap(bmp2, width, height, true)

        Thread {
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val pixels1 = IntArray(width)
            val pixels2 = IntArray(width)
            val resultPixels = IntArray(width)

            for (y in 0 until height) {
                scaled1.getPixels(pixels1, 0, width, 0, y, width, 1)
                scaled2.getPixels(pixels2, 0, width, 0, y, width, 1)

                for (x in 0 until width) {
                    val p1 = pixels1[x]
                    val p2 = pixels2[x]

                    val r1 = (p1 shr 16) and 0xFF
                    val g1 = (p1 shr 8) and 0xFF
                    val b1 = p1 and 0xFF

                    val r2 = (p2 shr 16) and 0xFF
                    val g2 = (p2 shr 8) and 0xFF
                    val b2 = p2 and 0xFF

                    val r = (r1 * r2) % 256
                    val g = (g1 * g2) % 256
                    val b = (b1 * b2) % 256

                    resultPixels[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }

                result.setPixels(resultPixels, 0, width, 0, y, width, 1)

                if (y % 50 == 0) {
                    val progress = y
                    runOnUiThread { progressBar.progress = progress }
                }
            }

            if (scaled1 !== bmp1) scaled1.recycle()
            if (scaled2 !== bmp2) scaled2.recycle()

            val bias = getBias()
            val biased = if (bias != 1.0f) applyBias(result, bmp1, bias) else result
            if (biased !== result) result.recycle()

            runOnUiThread {
                resultBitmap = biased
                previewResult.setImageBitmap(biased)
                progressBar.visibility = View.GONE
                val biasStr = if (bias != 1.0f) " bias=$bias" else ""
                statusText.text = "Done! ${width}x${height}$biasStr"
                isProcessing = false
                btnSave.isEnabled = true
                updateButtons()
            }
        }.start()
    }

    // --- Expression parser (recursive descent) ---
    private abstract class Expr
    private class ExprNum(val value: Float) : Expr()
    private class ExprVar(val name: String) : Expr()
    private class ExprBinOp(val op: Char, val left: Expr, val right: Expr) : Expr()
    private class ExprNeg(val inner: Expr) : Expr()

    private class ExprParser(private val input: String) {
        private var pos = 0
        fun parse(): Expr {
            val r = parseExpr(); skipWs()
            if (pos < input.length) throw RuntimeException("Unexpected '${input[pos]}' at pos $pos")
            return r
        }
        private fun skipWs() { while (pos < input.length && input[pos] == ' ') pos++ }
        private fun parseExpr(): Expr {
            var left = parseTerm(); skipWs()
            while (pos < input.length && (input[pos] == '+' || input[pos] == '-')) {
                val op = input[pos]; pos++; val right = parseTerm(); left = ExprBinOp(op, left, right); skipWs()
            }
            return left
        }
        private fun parseTerm(): Expr {
            var left = parseFactor(); skipWs()
            while (pos < input.length && (input[pos] == '*' || input[pos] == '/')) {
                val op = input[pos]; pos++; val right = parseFactor(); left = ExprBinOp(op, left, right); skipWs()
            }
            return left
        }
        private fun parseFactor(): Expr {
            skipWs()
            if (pos >= input.length) throw RuntimeException("Unexpected end")
            val c = input[pos]
            if (c == '-') { pos++; return ExprNeg(parseFactor()) }
            if (c == '(') { pos++; val inner = parseExpr(); skipWs()
                if (pos >= input.length || input[pos] != ')') throw RuntimeException("Missing ')'")
                pos++; return inner
            }
            if (c == 'P' || c == 'p') {
                if (pos + 1 < input.length && input[pos + 1] == '1') { pos += 2; return ExprVar("P1") }
                if (pos + 1 < input.length && input[pos + 1] == '2') { pos += 2; return ExprVar("P2") }
                throw RuntimeException("Unknown var at pos $pos")
            }
            if (c.isDigit() || c == '.') {
                val start = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                return ExprNum(input.substring(start, pos).toFloat())
            }
            throw RuntimeException("Unexpected '$c' at pos $pos")
        }
    }

    private fun evalExpr(e: Expr, p1: Float, p2: Float): Float {
        if (e is ExprNum) return e.value
        if (e is ExprVar) return if (e.name == "P1") p1 else p2
        if (e is ExprNeg) return -evalExpr(e.inner, p1, p2)
        if (e is ExprBinOp) {
            val l = evalExpr(e.left, p1, p2)
            val r = evalExpr(e.right, p1, p2)
            if (e.op == '+') return l + r
            if (e.op == '-') return l - r
            if (e.op == '*') return l * r
            if (e.op == '/') return if (r != 0f) l / r else 0f
        }
        return 0f
    }

    private fun exprUsesVar(e: Expr, name: String): Boolean {
        if (e is ExprVar) return e.name == name
        if (e is ExprNum) return false
        if (e is ExprNeg) return exprUsesVar(e.inner, name)
        if (e is ExprBinOp) return exprUsesVar(e.left, name) || exprUsesVar(e.right, name)
        return false
    }

    private fun applyExpression() {
        if (isProcessing) return
        val bmp1 = bitmap1
        val bmp2 = bitmap2
        val exprStr = exprInput.text.toString().trim()
        if (exprStr.isEmpty()) {
            Toast.makeText(this, "Enter an expression", Toast.LENGTH_SHORT).show()
            return
        }
        val parsed: Expr
        try { parsed = ExprParser(exprStr).parse() }
        catch (ex: Exception) {
            Toast.makeText(this, "Parse error: ${ex.message}", Toast.LENGTH_LONG).show()
            return
        }
        if (exprUsesVar(parsed, "P1") && bmp1 == null) {
            Toast.makeText(this, "Expression uses P1 but Image 1 not loaded", Toast.LENGTH_SHORT).show()
            return
        }
        if (exprUsesVar(parsed, "P2") && bmp2 == null) {
            Toast.makeText(this, "Expression uses P2 but Image 2 not loaded", Toast.LENGTH_SHORT).show()
            return
        }
        if (bmp1 == null && bmp2 == null) return

        isProcessing = true
        updateButtons()
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "Applying: $exprStr"

        Thread {
            val w: Int; val h: Int
            if (bmp1 != null && bmp2 != null) { w = minOf(bmp1.width, bmp2.width); h = minOf(bmp1.height, bmp2.height) }
            else if (bmp1 != null) { w = bmp1.width; h = bmp1.height }
            else { w = bmp2!!.width; h = bmp2.height }

            val s1 = if (bmp1 != null) Bitmap.createScaledBitmap(bmp1, w, h, true) else null
            val s2 = if (bmp2 != null) Bitmap.createScaledBitmap(bmp2, w, h, true) else null
            val px1 = IntArray(w * h); val px2 = IntArray(w * h)
            s1?.getPixels(px1, 0, w, 0, 0, w, h)
            s2?.getPixels(px2, 0, w, 0, 0, w, h)
            val outPx = IntArray(w * h)
            runOnUiThread { progressBar.max = h }

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    val rr = evalExpr(parsed, ((px1[i] shr 16) and 0xFF).toFloat(), ((px2[i] shr 16) and 0xFF).toFloat()).toInt() and 0xFF
                    val rg = evalExpr(parsed, ((px1[i] shr 8) and 0xFF).toFloat(), ((px2[i] shr 8) and 0xFF).toFloat()).toInt() and 0xFF
                    val rb = evalExpr(parsed, (px1[i] and 0xFF).toFloat(), (px2[i] and 0xFF).toFloat()).toInt() and 0xFF
                    outPx[i] = (0xFF shl 24) or (rr shl 16) or (rg shl 8) or rb
                }
                if (y % 50 == 0) { val p = y; runOnUiThread { progressBar.progress = p } }
            }

            if (s1 != null && s1 !== bmp1) s1.recycle()
            if (s2 != null && s2 !== bmp2) s2.recycle()
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(outPx, 0, w, 0, 0, w, h)

            val bias = getBias()
            val biased = if (bias != 1.0f && bmp1 != null) applyBias(result, bmp1, bias) else result
            if (biased !== result) result.recycle()

            runOnUiThread {
                resultBitmap = biased
                previewResult.setImageBitmap(biased)
                progressBar.visibility = View.GONE
                val biasStr = if (bias != 1.0f && bmp1 != null) " bias=$bias" else ""
                statusText.text = "Expr done! ${w}x${h}$biasStr"
                isProcessing = false
                updateButtons()
            }
        }.start()
    }

    private fun saveResult() {
        val bmp = resultBitmap ?: return
        try {
            val filename = "imgmult_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put("relative_path", "Pictures/ImageMultiplier")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val out: OutputStream? = contentResolver.openOutputStream(uri)
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out!!)
                out.close()
                statusText.text = "Saved: Pictures/ImageMultiplier/$filename"
                Toast.makeText(this, "Image saved!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
