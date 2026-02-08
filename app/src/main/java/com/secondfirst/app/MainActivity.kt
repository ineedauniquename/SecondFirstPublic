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
import android.widget.Button
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
    private lateinit var btnSave: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fullscreenOverlay: FrameLayout
    private lateinit var fullscreenImage: ImageView

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

        // Save button
        btnSave = styledButton("Save Result to Gallery").apply {
            isEnabled = false
        }
        btnSave.setOnClickListener { saveResult() }
        layout.addView(btnSave)

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

    private fun updateButtons() {
        val has1 = bitmap1 != null
        val has2 = bitmap2 != null
        btnMultiply.isEnabled = has1 && has2 && !isProcessing
        btnAverage.isEnabled = (has1 || has2) && !isProcessing
        if (has1 && has2) {
            statusText.text = "Ready! Output: ${
                minOf(bitmap1!!.width, bitmap2!!.width)
            }x${minOf(bitmap1!!.height, bitmap2!!.height)}"
        } else if (has1) {
            statusText.text = "Image 1 loaded. Select Image 2 or use Average."
        } else if (has2) {
            statusText.text = "Image 2 loaded. Select Image 1 or use Average."
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
        if (bmp1 == null && bmp2 == null) return

        isProcessing = true
        btnMultiply.isEnabled = false
        btnAverage.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.max = 100
        progressBar.progress = 0
        statusText.text = "Averaging..."

        Thread {
            if (bmp1 != null && bmp2 != null) {
                // Two images: average each, then average the two results
                val w = minOf(bmp1.width, bmp2.width)
                val h = minOf(bmp1.height, bmp2.height)
                val s1 = Bitmap.createScaledBitmap(bmp1, w, h, true)
                val s2 = Bitmap.createScaledBitmap(bmp2, w, h, true)

                runOnUiThread { progressBar.progress = 20 }
                val avg1 = toroidalAverage(s1)
                runOnUiThread { progressBar.progress = 50 }
                val avg2 = toroidalAverage(s2)
                runOnUiThread { progressBar.progress = 80 }

                // Average the two averaged images
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

                runOnUiThread {
                    resultBitmap = result
                    previewResult.setImageBitmap(result)
                    progressBar.visibility = View.GONE
                    statusText.text = "Averaged! ${w}x${h}"
                    isProcessing = false
                    btnSave.isEnabled = true
                    updateButtons()
                }
            } else {
                // Single image: just toroidal average
                val src = (bmp1 ?: bmp2)!!
                runOnUiThread { progressBar.progress = 30 }
                val result = toroidalAverage(src)
                runOnUiThread {
                    resultBitmap = result
                    previewResult.setImageBitmap(result)
                    progressBar.visibility = View.GONE
                    statusText.text = "Averaged! ${src.width}x${src.height}"
                    isProcessing = false
                    btnSave.isEnabled = true
                    updateButtons()
                }
            }
        }.start()
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

            runOnUiThread {
                resultBitmap = result
                previewResult.setImageBitmap(result)
                progressBar.visibility = View.GONE
                statusText.text = "Done! ${width}x${height} — ${width * height} pixels multiplied"
                isProcessing = false
                btnSave.isEnabled = true
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
