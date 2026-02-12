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
    private lateinit var biasInput: EditText
    private lateinit var btnClearResult: Button
    private lateinit var btnSave: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fullscreenOverlay: FrameLayout
    private lateinit var fullscreenImage: ImageView
    private lateinit var exprRGB: EditText
    private lateinit var exprR: EditText
    private lateinit var exprG: EditText
    private lateinit var exprB: EditText
    private lateinit var btnApplyExpr: Button
    private lateinit var btnPaste: Button

    private var bitmap1: Bitmap? = null
    private var bitmap2: Bitmap? = null
    private var resultBitmap: Bitmap? = null
    private var isProcessing = false

    // Colour index: per-channel sorted pixel rankings for Image 1
    // colourIndex[ch][rank] = pixel index (flat), sorted ascending by channel value
    // colourBuckets[ch][v] = start position in sorted array for pixels with value >= v
    // colourBuckets[ch][v+1] - colourBuckets[ch][v] = count of pixels with exactly value v
    // Debug rank tracking
    private var dbgRankV = -1f
    private var dbgRankPos = -1
    private var dbgRankPi = -1
    private var dbgRankResult = -1f
    private var dbgRankN = -1
    private var dbgRankCh = -1

    @Volatile private var colourIndex: Array<IntArray>? = null
    @Volatile private var colourBuckets: Array<IntArray>? = null
    @Volatile private var colourIndexW = 0
    @Volatile private var colourIndexH = 0

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
            text = "Image Multiplier v15"
            textSize = 26f
            setTextColor(Color.WHITE)
            setGravity(Gravity.CENTER)
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        val subtitle = TextView(this).apply {
            text = "Select images, apply expression to pixels"
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

        // Result to image buttons
        val rowCopy = buttonRow()
        val btnToImg1 = styledButton("Result \u2192 P1").apply {
            setBackgroundColor(Color.parseColor("#4A4458"))
        }
        btnToImg1.setOnClickListener { copyResultToImage(1) }
        val btnToImg2 = styledButton("Result \u2192 P2").apply {
            setBackgroundColor(Color.parseColor("#4A4458"))
        }
        btnToImg2.setOnClickListener { copyResultToImage(2) }
        rowCopy.addView(btnToImg1, rowChildParams(1f))
        rowCopy.addView(btnToImg2, rowChildParams(1f))
        layout.addView(rowCopy)

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

        // Expression input boxes: RGB (main) + R, G, B (overrides)
        fun exprEditText(default: String): EditText {
            return EditText(this).apply {
                setText(default)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2D2D30"))
                inputType = InputType.TYPE_CLASS_TEXT
                setPadding(24, 12, 24, 12)
                textSize = 14f
                setSingleLine(true)
                showSoftInputOnFocus = false
            }
        }
        fun exprLabelRow(label: String, edit: EditText): LinearLayout {
            val row = buttonRow()
            val lbl = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#D0BCFF"))
                setPadding(8, 0, 8, 0)
                setGravity(Gravity.CENTER_VERTICAL)
            }
            row.addView(lbl, rowChildParams(0.6f))
            row.addView(edit, rowChildParams(3.4f))
            return row
        }
        exprRGB = exprEditText("P1")
        exprR = exprEditText("")
        exprG = exprEditText("")
        exprB = exprEditText("")
        layout.addView(exprLabelRow("RGB:", exprRGB))
        layout.addView(exprLabelRow("R:", exprR))
        layout.addView(exprLabelRow("G:", exprG))
        layout.addView(exprLabelRow("B:", exprB))

        // Apply and Paste buttons
        val exprBtnRow = buttonRow()
        btnApplyExpr = styledButton("Apply Expr").apply {
            setBackgroundColor(Color.parseColor("#7B5EA7"))
            isEnabled = false
        }
        btnApplyExpr.setOnClickListener { applyExpression() }
        btnPaste = styledButton("Paste").apply {
            setBackgroundColor(Color.parseColor("#4A4458"))
        }
        btnPaste.setOnClickListener { pasteFormula() }
        exprBtnRow.addView(btnApplyExpr, rowChildParams(2f))
        exprBtnRow.addView(btnPaste, rowChildParams(1f))
        layout.addView(exprBtnRow)

        // Custom keyboard for formula input
        val kbKeys = arrayOf(
            arrayOf("P", "R", "G", "B"),
            arrayOf("1", "2", "3", "4"),
            arrayOf("5", "6", "7", "8"),
            arrayOf("9", "0", ".", "\u232B"),
            arrayOf("(", ")", "+", "-"),
            arrayOf("*", "/", "avg(", "rank("),
            arrayOf(" ", ",", "repeat(", "")
        )
        for (row in kbKeys) {
            val kbRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (key in row) {
                val btn = Button(this).apply {
                    text = if (key == " ") "SP" else if (key == "repeat(") "rpt(" else key
                    setTextColor(if (key.isEmpty()) Color.TRANSPARENT else Color.WHITE)
                    setBackgroundColor(if (key.isEmpty()) Color.parseColor("#1C1B1F") else Color.parseColor("#3A3A3A"))
                    textSize = 14f
                    setPadding(0, 5, 0, 5)
                    minimumWidth = 0
                    minWidth = 0
                    minimumHeight = 0
                    minHeight = 0
                    isEnabled = key.isNotEmpty()
                }
                if (key.isNotEmpty()) btn.setOnClickListener { onKbKey(key) }
                val w = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                w.setMargins(2, 2, 2, 2)
                kbRow.addView(btn, w)
            }
            layout.addView(kbRow)
        }

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

    private fun onKbKey(key: String) {
        val target = if (exprR.hasFocus()) exprR
                     else if (exprG.hasFocus()) exprG
                     else if (exprB.hasFocus()) exprB
                     else exprRGB
        val start = target.selectionStart.coerceAtLeast(0)
        val end = target.selectionEnd.coerceAtLeast(0)
        val editable = target.text
        if (key == "\u232B") { // backspace
            if (start > 0 && start == end) editable.delete(start - 1, start)
            else if (start != end) editable.delete(minOf(start, end), maxOf(start, end))
        } else if (key == "\u2190") { // left
            if (start > 0) target.setSelection(start - 1)
        } else if (key == "\u2192") { // right
            if (end < editable.length) target.setSelection(end + 1)
        } else {
            editable.replace(minOf(start, end), maxOf(start, end), key)
        }
    }

    private fun pasteFormula() {
        val clipMgr = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipMgr.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        val parts = text.split("|")
        if (parts.size == 4) {
            exprRGB.setText(parts[0])
            exprR.setText(parts[1])
            exprG.setText(parts[2])
            exprB.setText(parts[3])
        } else {
            exprRGB.setText(text)
        }
    }

    private fun clearImage(which: Int) {
        when (which) {
            1 -> {
                bitmap1 = null
                preview1.setImageBitmap(null)
                btnPick1.text = "Select Image 1"
                btnClear1.isEnabled = false
                clearColourIndex()
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

    private fun copyResultToImage(which: Int) {
        val bmp = resultBitmap ?: return
        val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
        when (which) {
            1 -> {
                bitmap1 = copy
                preview1.setImageBitmap(copy)
                btnPick1.text = "Image 1 (${copy.width}x${copy.height})"
                btnClear1.isEnabled = true
                statusText.text = "Result copied to Image 1"
                rebuildColourIndex()
            }
            2 -> {
                bitmap2 = copy
                preview2.setImageBitmap(copy)
                btnPick2.text = "Image 2 (${copy.width}x${copy.height})"
                btnClear2.isEnabled = true
                statusText.text = "Result copied to Image 2"
            }
        }
        updateButtons()
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
        btnApplyExpr.isEnabled = (has1 || has2) && !isProcessing
        btnSave.isEnabled = hasResult && !isProcessing
        btnClearResult.isEnabled = hasResult && !isProcessing
        if (has1 && has2) {
            statusText.text = "Ready! Output: ${
                minOf(bitmap1!!.width, bitmap2!!.width)
            }x${minOf(bitmap1!!.height, bitmap2!!.height)}"
        } else if (has1) {
            statusText.text = "Image 1 loaded. Select Image 2 or apply expression."
        } else if (has2) {
            statusText.text = "Image 2 loaded. Select Image 1 or apply expression."
        } else if (hasResult) {
            statusText.text = "Result available."
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
                rebuildColourIndex()
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

    // Precomputed avg cache: key = ch*100000 + img*10000 + radius -> FloatArray
    private var avgCache = HashMap<Int, FloatArray>()
    private var avgError: String? = null

    private fun avgKey(ch: Int, img: Int, radius: Int): Int = ch * 100000 + img * 10000 + radius

    // Separable box filter with toroidal wrapping - O(w*h) regardless of radius
    // Reuses provided hpass buffer to reduce peak memory
    private fun precomputeBoxBlur(pixels: IntArray, w: Int, h: Int, ch: Int, radius: Int, hpass: FloatArray): FloatArray {
        val size = 2 * radius + 1
        val total = size.toFloat() * size.toFloat()
        // Horizontal pass
        for (y in 0 until h) {
            var sum = 0f
            for (dx in -radius..radius) sum += chVal(pixels[y * w + ((dx % w) + w) % w], ch)
            hpass[y * w] = sum
            for (x in 1 until w) {
                sum += chVal(pixels[y * w + (x + radius) % w], ch) -
                       chVal(pixels[y * w + ((x - radius - 1) % w + w) % w], ch)
                hpass[y * w + x] = sum
            }
        }
        // Vertical pass
        val result = FloatArray(w * h)
        for (x in 0 until w) {
            var sum = 0f
            for (dy in -radius..radius) sum += hpass[((dy % h) + h) % h * w + x]
            result[x] = sum / total
            for (y in 1 until h) {
                sum += hpass[(y + radius) % h * w + x] -
                       hpass[((y - radius - 1) % h + h) % h * w + x]
                result[y * w + x] = sum / total
            }
        }
        return result
    }

    // Collect all (img, radius) pairs from expression tree
    private fun collectAvgParams(e: Expr, result: MutableSet<Pair<Int, Int>>) {
        if (e is ExprAvg) result.add(Pair(e.img, e.radius))
        if (e is ExprNeg) collectAvgParams(e.inner, result)
        if (e is ExprBinOp) { collectAvgParams(e.left, result); collectAvgParams(e.right, result) }
        if (e is ExprRank) collectAvgParams(e.inner, result)
    }

    private fun buildAvgCacheForChannels(exprs: Array<Expr?>, px1: IntArray, px2: IntArray, w: Int, h: Int) {
        val params = mutableSetOf<Pair<Int, Int>>()
        for (e in exprs) if (e != null) collectAvgParams(e, params)
        avgCache.clear()
        avgError = null
        if (params.isEmpty()) return
        try {
            val hpass = FloatArray(w * h)
            for ((img, rad) in params) {
                val pxArr = if (img == 1) px1 else px2
                for (ch in 0..2) {
                    avgCache[avgKey(ch, img, rad)] = precomputeBoxBlur(pxArr, w, h, ch, rad, hpass)
                }
            }
        } catch (e: OutOfMemoryError) {
            avgCache.clear()
            avgError = "Out of memory computing avg - try smaller image or radius"
        }
    }
    private abstract class Expr
    private class ExprNum(val value: Float) : Expr()
    private class ExprVar(val name: String, val neighbor: Int, val offX: Int, val offY: Int) : Expr()
    private class ExprBinOp(val op: Char, val left: Expr, val right: Expr) : Expr()
    private class ExprNeg(val inner: Expr) : Expr()
    private class ExprAvg(val ch: Int, val img: Int, val radius: Int) : Expr()
    // ch: -1=current(P), 0=R, 1=G, 2=B; img: 1 or 2; radius: 1+ (grid = 2r+1 squared)
    private class ExprRank(val inner: Expr, val ch: Int) : Expr()
    // ch: 0=R, 1=G, 2=B; looks up pixel at rank position in colour index

    private class ExprParser(private val input: String) {
        private var pos = 0
        fun parse(): Expr {
            val r = parseExpr(); skipWs()
            if (pos < input.length) throw RuntimeException("Unexpected '${input[pos]}' at pos $pos")
            return r
        }
        private fun skipWs() { while (pos < input.length && input[pos] == ' ') pos++ }
        private fun parseSignedInt(): Int {
            skipWs()
            var neg = false
            if (pos < input.length && input[pos] == '-') { neg = true; pos++ }
            val start = pos
            while (pos < input.length && input[pos].isDigit()) pos++
            if (pos == start) throw RuntimeException("Expected integer at pos $pos")
            val v = input.substring(start, pos).toInt()
            return if (neg) -v else v
        }
        private fun parseOffset(): Pair<Int, Int> {
            pos++ // skip first comma
            val ox = parseSignedInt()
            skipWs()
            if (pos >= input.length || input[pos] != ',') throw RuntimeException("Expected ',' for Y offset at pos $pos")
            pos++ // skip second comma
            val oy = parseSignedInt()
            return Pair(ox, oy)
        }
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
            if ((c == 'a' || c == 'A') && pos + 3 < input.length &&
                (input[pos+1] == 'v' || input[pos+1] == 'V') &&
                (input[pos+2] == 'g' || input[pos+2] == 'G') && input[pos+3] == '(') {
                pos += 4; skipWs()
                val cc = input[pos].lowercaseChar()
                var ach = -1
                if (cc == 'r') ach = 0 else if (cc == 'g') ach = 1 else if (cc == 'b') ach = 2
                else if (cc != 'p') throw RuntimeException("Expected P/R/G/B in avg()")
                pos++
                var aimg = 1
                if (pos < input.length && (input[pos] == '1' || input[pos] == '2')) { aimg = input[pos] - '0'; pos++ }
                skipWs()
                if (pos >= input.length || input[pos] != ',') throw RuntimeException("Expected ',' in avg()")
                pos++; skipWs()
                val rs = pos
                while (pos < input.length && input[pos].isDigit()) pos++
                if (pos == rs) throw RuntimeException("Expected radius in avg()")
                val rad = input.substring(rs, pos).toInt()
                skipWs()
                if (pos >= input.length || input[pos] != ')') throw RuntimeException("Expected ')' in avg()")
                pos++
                return ExprAvg(ach, aimg, rad)
            }
            if (c == 'P' || c == 'p') {
                if (pos + 1 < input.length && (input[pos + 1] == '1' || input[pos + 1] == '2')) {
                    val name = "P${input[pos + 1]}"; pos += 2
                    var nb = 0
                    if (pos < input.length && input[pos] >= '1' && input[pos] <= '8') { nb = input[pos] - '0'; pos++ }
                    if (pos < input.length && input[pos] == ',') {
                        val (ox, oy) = parseOffset()
                        return ExprVar(name, nb, ox, oy)
                    }
                    return ExprVar(name, nb, 0, 0)
                }
                throw RuntimeException("Unknown var at pos $pos")
            }
            if ((c == 'r' || c == 'R') && pos + 4 < input.length &&
                (input[pos+1] == 'a' || input[pos+1] == 'A') &&
                (input[pos+2] == 'n' || input[pos+2] == 'N') &&
                (input[pos+3] == 'k' || input[pos+3] == 'K') && input[pos+4] == '(') {
                pos += 5; skipWs()
                val inner = parseExpr()
                skipWs()
                if (pos >= input.length || input[pos] != ',') throw RuntimeException("Expected ',' in rank()")
                pos++; skipWs()
                val cc2 = input[pos].lowercaseChar()
                val rch: Int
                if (cc2 == 'r') rch = 0
                else if (cc2 == 'g') rch = 1
                else if (cc2 == 'b') rch = 2
                else throw RuntimeException("Expected R/G/B in rank()")
                pos++; skipWs()
                if (pos >= input.length || input[pos] != ')') throw RuntimeException("Expected ')' in rank()")
                pos++
                return ExprRank(inner, rch)
            }
            if (c == 'r' || c == 'g' || c == 'b' || c == 'R' || c == 'G' || c == 'B') {
                if (pos + 1 < input.length && (input[pos + 1] == '1' || input[pos + 1] == '2')) {
                    val name = "${c.lowercaseChar()}${input[pos + 1]}"; pos += 2
                    var nb = 0
                    if (pos < input.length && input[pos] >= '1' && input[pos] <= '8') { nb = input[pos] - '0'; pos++ }
                    if (pos < input.length && input[pos] == ',') {
                        val (ox, oy) = parseOffset()
                        return ExprVar(name, nb, ox, oy)
                    }
                    return ExprVar(name, nb, 0, 0)
                }
                throw RuntimeException("Expected r1/r2/g1/g2/b1/b2 at pos $pos")
            }
            if (c.isDigit() || c == '.') {
                val start = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                return ExprNum(input.substring(start, pos).toFloat())
            }
            throw RuntimeException("Unexpected '$c' at pos $pos")
        }
    }

    // Neighbor offsets: 0=center, 1=top-left, 2=top, 3=top-right,
    // 4=right, 5=bottom-right, 6=bottom, 7=bottom-left, 8=left
    private val NDX = intArrayOf(0, -1, 0, 1, 1, 1, 0, -1, -1)
    private val NDY = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1, 0)

    private fun chVal(px: Int, ch: Int): Float {
        if (ch == 0) return ((px shr 16) and 0xFF).toFloat()
        if (ch == 1) return ((px shr 8) and 0xFF).toFloat()
        return (px and 0xFF).toFloat()
    }

    private fun chValInt(px: Int, ch: Int): Int {
        if (ch == 0) return (px shr 16) and 0xFF
        if (ch == 1) return (px shr 8) and 0xFF
        return px and 0xFF
    }

    // Build colour index for Image 1: counting sort per channel
    private fun rebuildColourIndex() {
        val bmp = bitmap1 ?: run {
            clearColourIndex()
            return
        }
        Thread {
            val t0 = System.currentTimeMillis()
            val w = bmp.width
            val h = bmp.height
            val n = w * h
            val pixels = IntArray(n)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)

            try {
                val idx = Array(3) { IntArray(n) }
                val bkt = Array(3) { IntArray(257) }

                for (ch in 0..2) {
                    // Count occurrences of each value
                    val count = IntArray(256)
                    for (i in 0 until n) count[chValInt(pixels[i], ch)]++
                    // Build cumulative offsets
                    bkt[ch][0] = 0
                    for (v in 0 until 256) bkt[ch][v + 1] = bkt[ch][v] + count[v]
                    // Place indices into sorted positions
                    val pos = IntArray(256)
                    System.arraycopy(bkt[ch], 0, pos, 0, 256)
                    for (i in 0 until n) {
                        val v = chValInt(pixels[i], ch)
                        idx[ch][pos[v]] = i
                        pos[v]++
                    }
                }

                colourIndex = idx
                colourBuckets = bkt
                colourIndexW = w
                colourIndexH = h
                val elapsed = System.currentTimeMillis() - t0
                runOnUiThread { statusText.text = "Colour index: ${w}x${h} (${elapsed}ms)" }
            } catch (e: OutOfMemoryError) {
                clearColourIndex()
                runOnUiThread { statusText.text = "Colour index: out of memory" }
            }
        }.start()
    }

    private fun clearColourIndex() {
        colourIndex = null
        colourBuckets = null
        colourIndexW = 0
        colourIndexH = 0
    }

    private fun evalExpr(e: Expr, px1: IntArray, px2: IntArray, x: Int, y: Int, w: Int, h: Int, curCh: Int, divFlag: BooleanArray): Float {
        if (e is ExprNum) return e.value
        if (e is ExprVar) {
            val nx: Int
            val ny: Int
            if (e.offX != 0 || e.offY != 0) {
                nx = ((x + e.offX) % w + w) % w
                ny = ((y - e.offY) % h + h) % h
            } else {
                val nb = e.neighbor
                nx = (x + NDX[nb] + w) % w
                ny = (y + NDY[nb] + h) % h
            }
            val ni = ny * w + nx
            if (e.name == "P1") return chVal(px1[ni], curCh)
            if (e.name == "P2") return chVal(px2[ni], curCh)
            if (e.name == "r1") return chVal(px1[ni], 0)
            if (e.name == "g1") return chVal(px1[ni], 1)
            if (e.name == "b1") return chVal(px1[ni], 2)
            if (e.name == "r2") return chVal(px2[ni], 0)
            if (e.name == "g2") return chVal(px2[ni], 1)
            if (e.name == "b2") return chVal(px2[ni], 2)
            return 0f
        }
        if (e is ExprAvg) {
            val ch = if (e.ch == -1) curCh else e.ch
            val cached = avgCache[avgKey(ch, e.img, e.radius)]
            if (cached != null) return cached[y * w + x]
            return 0f
        }
        if (e is ExprRank) {
            val idx = colourIndex ?: return 0f
            val n = colourIndexW * colourIndexH
            if (n == 0) return 0f
            val v = evalExpr(e.inner, px1, px2, x, y, w, h, curCh, divFlag)
            val pos = (v / 255f * (n - 1).toFloat()).toInt().coerceIn(0, n - 1)
            val pi = idx[e.ch][pos]
            val result = if (colourIndexW == w && colourIndexH == h) {
                chVal(px1[pi], curCh)
            } else {
                val ox = pi % colourIndexW
                val oy = pi / colourIndexW
                val sx = ox * w / colourIndexW
                val sy = oy * h / colourIndexH
                chVal(px1[sy * w + sx], curCh)
            }
            // Debug: capture first call where v > 0
            if (dbgRankV < 0f && v > 0f) {
                dbgRankV = v; dbgRankPos = pos; dbgRankPi = pi
                dbgRankResult = result; dbgRankN = n; dbgRankCh = curCh
            }
            return result
        }
        if (e is ExprNeg) return -evalExpr(e.inner, px1, px2, x, y, w, h, curCh, divFlag)
        if (e is ExprBinOp) {
            val l = evalExpr(e.left, px1, px2, x, y, w, h, curCh, divFlag)
            val r = evalExpr(e.right, px1, px2, x, y, w, h, curCh, divFlag)
            if (e.op == '+') return l + r
            if (e.op == '-') return l - r
            if (e.op == '*') return l * r
            if (e.op == '/') {
                if (r == 0f) { divFlag[0] = true; return chVal(px1[y * w + x], curCh) }
                return l / r
            }
        }
        return 0f
    }

    private fun exprUsesImg2(e: Expr): Boolean {
        if (e is ExprVar) return e.name == "P2" || e.name == "r2" || e.name == "g2" || e.name == "b2"
        if (e is ExprAvg) return e.img == 2
        if (e is ExprNum) return false
        if (e is ExprNeg) return exprUsesImg2(e.inner)
        if (e is ExprBinOp) return exprUsesImg2(e.left) || exprUsesImg2(e.right)
        if (e is ExprRank) return exprUsesImg2(e.inner)
        return false
    }

    private fun exprUsesRank(e: Expr): Boolean {
        if (e is ExprRank) return true
        if (e is ExprNeg) return exprUsesRank(e.inner)
        if (e is ExprBinOp) return exprUsesRank(e.left) || exprUsesRank(e.right)
        return false
    }

    private fun applyExpression() {
        if (isProcessing) return
        val bmp1 = bitmap1
        val bmp2 = bitmap2
        var rgbStr = exprRGB.text.toString().trim()
        val rStr = exprR.text.toString().trim()
        val gStr = exprG.text.toString().trim()
        val bStr = exprB.text.toString().trim()

        if (rgbStr.isEmpty() && rStr.isEmpty() && gStr.isEmpty() && bStr.isEmpty()) {
            Toast.makeText(this, "Enter an expression", Toast.LENGTH_SHORT).show()
            return
        }

        // Copy all 4 boxes to clipboard as pipe-separated
        val clipMgr = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipMgr.setPrimaryClip(android.content.ClipData.newPlainText("formula", "$rgbStr|$rStr|$gStr|$bStr"))

        // Detect repeat(expr, N) in RGB box
        var repeatCount = 1
        if (rgbStr.startsWith("repeat(", ignoreCase = true) && rgbStr.endsWith(")")) {
            val inner = rgbStr.substring(7, rgbStr.length - 1)
            val lastComma = inner.lastIndexOf(',')
            if (lastComma >= 0) {
                val countStr = inner.substring(lastComma + 1).trim()
                val count = countStr.toIntOrNull()
                if (count != null && count >= 1) {
                    repeatCount = count
                    rgbStr = inner.substring(0, lastComma).trim()
                }
            }
        }

        // Parse expressions
        val rgbExpr: Expr? = if (rgbStr.isNotEmpty()) {
            try { ExprParser(rgbStr).parse() }
            catch (ex: Exception) { Toast.makeText(this, "RGB: ${ex.message}", Toast.LENGTH_LONG).show(); return }
        } else null
        val rExpr: Expr? = if (rStr.isNotEmpty()) {
            try { ExprParser(rStr).parse() }
            catch (ex: Exception) { Toast.makeText(this, "R: ${ex.message}", Toast.LENGTH_LONG).show(); return }
        } else null
        val gExpr: Expr? = if (gStr.isNotEmpty()) {
            try { ExprParser(gStr).parse() }
            catch (ex: Exception) { Toast.makeText(this, "G: ${ex.message}", Toast.LENGTH_LONG).show(); return }
        } else null
        val bExpr: Expr? = if (bStr.isNotEmpty()) {
            try { ExprParser(bStr).parse() }
            catch (ex: Exception) { Toast.makeText(this, "B: ${ex.message}", Toast.LENGTH_LONG).show(); return }
        } else null

        // Effective expression per channel: individual overrides RGB, fallback to 0
        val effR = rExpr ?: rgbExpr ?: ExprNum(0f)
        val effG = gExpr ?: rgbExpr ?: ExprNum(0f)
        val effB = bExpr ?: rgbExpr ?: ExprNum(0f)

        val needsImg2 = exprUsesImg2(effR) || exprUsesImg2(effG) || exprUsesImg2(effB)
        if (bmp1 == null && bmp2 == null) {
            Toast.makeText(this, "Load at least one image", Toast.LENGTH_SHORT).show()
            return
        }
        if (needsImg2 && bmp2 == null) {
            Toast.makeText(this, "Expression uses Image 2 but it's not loaded", Toast.LENGTH_SHORT).show()
            return
        }
        val needsRank = exprUsesRank(effR) || exprUsesRank(effG) || exprUsesRank(effB)
        if (needsRank && bmp1 == null) {
            Toast.makeText(this, "rank() requires Image 1", Toast.LENGTH_LONG).show()
            return
        }

        isProcessing = true
        updateButtons()
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "Applying expression..."

        Thread {
            val t0 = System.currentTimeMillis()
            val w: Int; val h: Int
            if (bmp1 != null && bmp2 != null) { w = minOf(bmp1.width, bmp2.width); h = minOf(bmp1.height, bmp2.height) }
            else if (bmp1 != null) { w = bmp1.width; h = bmp1.height }
            else { w = bmp2!!.width; h = bmp2.height }

            val s1 = if (bmp1 != null) Bitmap.createScaledBitmap(bmp1, w, h, true) else null
            val s2 = if (bmp2 != null) Bitmap.createScaledBitmap(bmp2, w, h, true) else null
            val px1 = IntArray(w * h); val px2 = IntArray(w * h)
            s1?.getPixels(px1, 0, w, 0, 0, w, h)
            s2?.getPixels(px2, 0, w, 0, 0, w, h)

            // Build colour index from px1 inline if rank() is used
            var dbg = "rank=$needsRank"
            if (needsRank) {
                runOnUiThread { statusText.text = "Building colour index..." }
                val n = w * h
                // Debug: check px1 and px2 content
                var maxR1 = 0; var maxR2 = 0; var nonBlack1 = 0; var nonBlack2 = 0
                for (i in 0 until n) {
                    val r1 = chValInt(px1[i], 0); if (r1 > maxR1) maxR1 = r1; if (px1[i] and 0x00FFFFFF != 0) nonBlack1++
                    val r2 = chValInt(px2[i], 0); if (r2 > maxR2) maxR2 = r2; if (px2[i] and 0x00FFFFFF != 0) nonBlack2++
                }
                dbg = "p1:maxR=$maxR1,nb=$nonBlack1 p2:maxR=$maxR2,nb=$nonBlack2"
                try {
                    val idx = Array(3) { IntArray(n) }
                    val bkt = Array(3) { IntArray(257) }
                    for (ch in 0..2) {
                        val count = IntArray(256)
                        for (i in 0 until n) count[chValInt(px1[i], ch)]++
                        bkt[ch][0] = 0
                        for (v in 0 until 256) bkt[ch][v + 1] = bkt[ch][v] + count[v]
                        val pos = IntArray(256)
                        System.arraycopy(bkt[ch], 0, pos, 0, 256)
                        for (i in 0 until n) {
                            val v2 = chValInt(px1[i], ch)
                            idx[ch][pos[v2]] = i
                            pos[v2]++
                        }
                    }
                    colourIndex = idx
                    colourBuckets = bkt
                    colourIndexW = w
                    colourIndexH = h
                    // Debug: verify index
                    val lastPi = idx[0][n - 1]
                    val lastR = chValInt(px1[lastPi], 0)
                    dbg += " idx:lastR=$lastR"
                } catch (e: OutOfMemoryError) {
                    colourIndex = null
                    runOnUiThread { statusText.text = "Colour index: out of memory"; progressBar.visibility = View.GONE; isProcessing = false; updateButtons() }
                    return@Thread
                }
            }

            // Reset debug rank tracking
            dbgRankV = -1f; dbgRankPos = -1; dbgRankPi = -1
            dbgRankResult = -1f; dbgRankN = -1; dbgRankCh = -1

            val outPx = IntArray(w * h)
            val divFlag = booleanArrayOf(false)
            val chExprs = arrayOf<Expr?>(effR, effG, effB)

            for (iter in 0 until repeatCount) {
                val iterLabel = if (repeatCount > 1) " (${iter+1}/$repeatCount)" else ""
                runOnUiThread { statusText.text = "Precomputing avg$iterLabel..." }
                buildAvgCacheForChannels(chExprs, px1, px2, w, h)
                if (avgError != null) {
                    val err = avgError!!
                    runOnUiThread { statusText.text = err; progressBar.visibility = View.GONE; isProcessing = false; updateButtons() }
                    return@Thread
                }

                runOnUiThread { progressBar.max = h; statusText.text = "Processing pixels$iterLabel..." }

                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val rr = evalExpr(effR, px1, px2, x, y, w, h, 0, divFlag).toInt() and 0xFF
                        val rg = evalExpr(effG, px1, px2, x, y, w, h, 1, divFlag).toInt() and 0xFF
                        val rb = evalExpr(effB, px1, px2, x, y, w, h, 2, divFlag).toInt() and 0xFF
                        val i = y * w + x
                        outPx[i] = (0xFF shl 24) or (rr shl 16) or (rg shl 8) or rb
                    }
                    if (y % 50 == 0) { val p = y; runOnUiThread { progressBar.progress = p } }
                }

                if (iter < repeatCount - 1) {
                    System.arraycopy(outPx, 0, px1, 0, w * h)
                }
            }

            // Debug: check output
            var maxOutR = 0; var maxOutG = 0; var maxOutB = 0; var nonBlackOut = 0
            for (i in 0 until w * h) {
                val r = (outPx[i] shr 16) and 0xFF; val g = (outPx[i] shr 8) and 0xFF; val b = outPx[i] and 0xFF
                if (r > maxOutR) maxOutR = r; if (g > maxOutG) maxOutG = g; if (b > maxOutB) maxOutB = b
                if (r > 0 || g > 0 || b > 0) nonBlackOut++
            }
            val dbg2 = if (dbgRankV >= 0f) {
                "v=${dbgRankV.toInt()} p=$dbgRankPos r=${dbgRankResult.toInt()} ch=$dbgRankCh"
            } else if (needsRank) { "never_v>0" } else { "" }
            val dbgAll = "O:$maxOutR,$maxOutG,$maxOutB nb=$nonBlackOut $dbg2"

            if (s1 != null && s1 !== bmp1) s1.recycle()
            if (s2 != null && s2 !== bmp2) s2.recycle()
            avgCache.clear()
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(outPx, 0, w, 0, 0, w, h)

            val bias = getBias()
            val biased = if (bias != 1.0f && bmp1 != null) applyBias(result, bmp1, bias) else result
            if (biased !== result) result.recycle()
            val hadDivZero = divFlag[0]
            val elapsed = (System.currentTimeMillis() - t0) / 1000f

            runOnUiThread {
                resultBitmap = biased
                previewResult.setImageBitmap(biased)
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, dbgAll, Toast.LENGTH_LONG).show()
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
