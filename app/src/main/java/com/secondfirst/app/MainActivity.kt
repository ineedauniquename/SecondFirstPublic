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
            showSoftInputOnFocus = false
        }
        btnApplyExpr = styledButton("Apply Expr").apply {
            setBackgroundColor(Color.parseColor("#7B5EA7"))
            isEnabled = false
        }
        btnApplyExpr.setOnClickListener { applyExpression() }
        exprRow.addView(exprInput, rowChildParams(3f))
        exprRow.addView(btnApplyExpr, rowChildParams(1f))
        layout.addView(exprRow)

        // Custom keyboard for formula input
        val kbKeys = arrayOf(
            arrayOf("P", "R", "G", "B", "(", ")", "[", "]"),
            arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            arrayOf("+", "-", "*", "/", " ", "\u2190", "\u2192", "\u232B")
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
                    text = key
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#3A3A3A"))
                    textSize = 14f
                    setPadding(0, 0, 0, 0)
                    minimumWidth = 0
                    minWidth = 0
                    minimumHeight = 0
                    minHeight = 0
                }
                btn.setOnClickListener { onKbKey(key) }
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
        val start = exprInput.selectionStart.coerceAtLeast(0)
        val end = exprInput.selectionEnd.coerceAtLeast(0)
        val editable = exprInput.text
        if (key == "\u232B") { // backspace
            if (start > 0 && start == end) editable.delete(start - 1, start)
            else if (start != end) editable.delete(minOf(start, end), maxOf(start, end))
        } else if (key == "\u2190") { // left
            if (start > 0) exprInput.setSelection(start - 1)
        } else if (key == "\u2192") { // right
            if (end < editable.length) exprInput.setSelection(end + 1)
        } else {
            editable.replace(minOf(start, end), maxOf(start, end), key)
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
            if (c == 'r' || c == 'g' || c == 'b' || c == 'R' || c == 'G' || c == 'B') {
                if (pos + 1 < input.length && (input[pos + 1] == '1' || input[pos + 1] == '2')) {
                    val name = "${c.lowercaseChar()}${input[pos + 1]}"
                    pos += 2; return ExprVar(name)
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

    private fun chVal(px: Int, ch: Int): Float {
        if (ch == 0) return ((px shr 16) and 0xFF).toFloat()
        if (ch == 1) return ((px shr 8) and 0xFF).toFloat()
        return (px and 0xFF).toFloat()
    }

    private fun evalExpr(e: Expr, px1: Int, px2: Int, curCh: Int, divFlag: BooleanArray): Float {
        if (e is ExprNum) return e.value
        if (e is ExprVar) {
            if (e.name == "P1") return chVal(px1, curCh)
            if (e.name == "P2") return chVal(px2, curCh)
            if (e.name == "r1") return chVal(px1, 0)
            if (e.name == "g1") return chVal(px1, 1)
            if (e.name == "b1") return chVal(px1, 2)
            if (e.name == "r2") return chVal(px2, 0)
            if (e.name == "g2") return chVal(px2, 1)
            if (e.name == "b2") return chVal(px2, 2)
            return 0f
        }
        if (e is ExprNeg) return -evalExpr(e.inner, px1, px2, curCh, divFlag)
        if (e is ExprBinOp) {
            val l = evalExpr(e.left, px1, px2, curCh, divFlag)
            val r = evalExpr(e.right, px1, px2, curCh, divFlag)
            if (e.op == '+') return l + r
            if (e.op == '-') return l - r
            if (e.op == '*') return l * r
            if (e.op == '/') {
                if (r == 0f) { divFlag[0] = true; return chVal(px1, curCh) }
                return l / r
            }
        }
        return 0f
    }

    private fun exprUsesImg2(e: Expr): Boolean {
        if (e is ExprVar) return e.name == "P2" || e.name == "r2" || e.name == "g2" || e.name == "b2"
        if (e is ExprNum) return false
        if (e is ExprNeg) return exprUsesImg2(e.inner)
        if (e is ExprBinOp) return exprUsesImg2(e.left) || exprUsesImg2(e.right)
        return false
    }

    // Parse "[r expr] [g expr] [b expr]" syntax
    private fun parseChannelExprs(input: String): Array<Expr?> {
        val result = arrayOfNulls<Expr>(3) // 0=R, 1=G, 2=B
        var pos = 0
        val s = input.trim()
        while (pos < s.length) {
            while (pos < s.length && s[pos] == ' ') pos++
            if (pos >= s.length) break
            if (s[pos] != '[') throw RuntimeException("Expected '[' at pos $pos")
            pos++
            while (pos < s.length && s[pos] == ' ') pos++
            if (pos >= s.length) throw RuntimeException("Expected channel after '['")
            val ch = s[pos].lowercaseChar()
            if (ch != 'r' && ch != 'g' && ch != 'b') throw RuntimeException("Expected r/g/b after '[', got '${s[pos]}'")
            pos++
            val end = s.indexOf(']', pos)
            if (end < 0) throw RuntimeException("Missing ']'")
            val exprStr = s.substring(pos, end).trim()
            val expr = ExprParser(exprStr).parse()
            if (ch == 'r') result[0] = expr
            else if (ch == 'g') result[1] = expr
            else result[2] = expr
            pos = end + 1
        }
        return result
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

        val isPerChannel = exprStr.contains('[')

        // Per-channel mode: [r expr] [g expr] [b expr]
        if (isPerChannel) {
            val chExprs: Array<Expr?>
            try { chExprs = parseChannelExprs(exprStr) }
            catch (ex: Exception) {
                Toast.makeText(this, "Parse error: ${ex.message}", Toast.LENGTH_LONG).show()
                return
            }
            if (bmp1 == null) {
                Toast.makeText(this, "Image 1 required for per-channel mode", Toast.LENGTH_SHORT).show()
                return
            }
            // Check if any channel expr needs image 2
            val needsImg2 = (0..2).any { chExprs[it] != null && exprUsesImg2(chExprs[it]!!) }
            if (needsImg2 && bmp2 == null) {
                Toast.makeText(this, "Expression uses Image 2 but it's not loaded", Toast.LENGTH_SHORT).show()
                return
            }

            isProcessing = true
            updateButtons()
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
            statusText.text = "Applying: $exprStr"

            Thread {
                val w: Int; val h: Int
                if (bmp2 != null) { w = minOf(bmp1.width, bmp2.width); h = minOf(bmp1.height, bmp2.height) }
                else { w = bmp1.width; h = bmp1.height }

                val s1 = Bitmap.createScaledBitmap(bmp1, w, h, true)
                val s2 = if (bmp2 != null) Bitmap.createScaledBitmap(bmp2, w, h, true) else null
                val px1 = IntArray(w * h); val px2 = IntArray(w * h)
                s1.getPixels(px1, 0, w, 0, 0, w, h)
                s2?.getPixels(px2, 0, w, 0, 0, w, h)
                val outPx = IntArray(w * h)
                val divFlag = booleanArrayOf(false)
                runOnUiThread { progressBar.max = h }

                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val i = y * w + x
                        val p1 = px1[i]; val p2 = px2[i]
                        val rr = if (chExprs[0] != null) evalExpr(chExprs[0]!!, p1, p2, 0, divFlag).toInt() and 0xFF else (p1 shr 16) and 0xFF
                        val rg = if (chExprs[1] != null) evalExpr(chExprs[1]!!, p1, p2, 1, divFlag).toInt() and 0xFF else (p1 shr 8) and 0xFF
                        val rb = if (chExprs[2] != null) evalExpr(chExprs[2]!!, p1, p2, 2, divFlag).toInt() and 0xFF else p1 and 0xFF
                        outPx[i] = (0xFF shl 24) or (rr shl 16) or (rg shl 8) or rb
                    }
                    if (y % 50 == 0) { val p = y; runOnUiThread { progressBar.progress = p } }
                }

                if (s1 !== bmp1) s1.recycle()
                if (s2 != null && s2 !== bmp2) s2.recycle()
                val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                result.setPixels(outPx, 0, w, 0, 0, w, h)

                val bias = getBias()
                val biased = if (bias != 1.0f) applyBias(result, bmp1, bias) else result
                if (biased !== result) result.recycle()
                val hadDivZero = divFlag[0]

                runOnUiThread {
                    resultBitmap = biased
                    previewResult.setImageBitmap(biased)
                    progressBar.visibility = View.GONE
                    val biasStr = if (bias != 1.0f) " bias=$bias" else ""
                    val divStr = if (hadDivZero) " [div/0: used img1]" else ""
                    statusText.text = "Expr done! ${w}x${h}$biasStr$divStr"
                    isProcessing = false
                    updateButtons()
                }
            }.start()
        } else {
            // All-channels mode: P1*P2/255 etc.
            val parsed: Expr
            try { parsed = ExprParser(exprStr).parse() }
            catch (ex: Exception) {
                Toast.makeText(this, "Parse error: ${ex.message}", Toast.LENGTH_LONG).show()
                return
            }
            val needsImg2 = exprUsesImg2(parsed)
            if (bmp1 == null && bmp2 == null) {
                Toast.makeText(this, "Load at least one image", Toast.LENGTH_SHORT).show()
                return
            }
            if (needsImg2 && bmp2 == null) {
                Toast.makeText(this, "Expression uses Image 2 but it's not loaded", Toast.LENGTH_SHORT).show()
                return
            }

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
                val divFlag = booleanArrayOf(false)
                runOnUiThread { progressBar.max = h }

                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val i = y * w + x
                        val p1 = px1[i]; val p2 = px2[i]
                        val rr = evalExpr(parsed, p1, p2, 0, divFlag).toInt() and 0xFF
                        val rg = evalExpr(parsed, p1, p2, 1, divFlag).toInt() and 0xFF
                        val rb = evalExpr(parsed, p1, p2, 2, divFlag).toInt() and 0xFF
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
                val hadDivZero = divFlag[0]

                runOnUiThread {
                    resultBitmap = biased
                    previewResult.setImageBitmap(biased)
                    progressBar.visibility = View.GONE
                    val biasStr = if (bias != 1.0f && bmp1 != null) " bias=$bias" else ""
                    val divStr = if (hadDivZero) " [div/0: used img1]" else ""
                    statusText.text = "Expr done! ${w}x${h}$biasStr$divStr"
                    isProcessing = false
                    updateButtons()
                }
            }.start()
        }
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
