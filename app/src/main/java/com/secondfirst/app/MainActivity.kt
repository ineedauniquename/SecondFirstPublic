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
import android.widget.Button
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
    private lateinit var btnPick2: Button
    private lateinit var btnMultiply: Button
    private lateinit var btnSave: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    private var bitmap1: Bitmap? = null
    private var bitmap2: Bitmap? = null
    private var resultBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        val subtitle = TextView(this).apply {
            text = "Select two images, then multiply their pixels together"
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        layout.addView(subtitle)

        // Image 1 section
        layout.addView(sectionLabel("Image 1"))
        preview1 = imagePreview()
        layout.addView(preview1)
        btnPick1 = styledButton("Select Image 1")
        btnPick1.setOnClickListener { pickImage(PICK_IMAGE_1) }
        layout.addView(btnPick1)

        // Image 2 section
        layout.addView(sectionLabel("Image 2"))
        preview2 = imagePreview()
        layout.addView(preview2)
        btnPick2 = styledButton("Select Image 2")
        btnPick2.setOnClickListener { pickImage(PICK_IMAGE_2) }
        layout.addView(btnPick2)

        // Multiply button
        btnMultiply = styledButton("Multiply Images").apply {
            setBackgroundColor(Color.parseColor("#6750A4"))
            isEnabled = false
        }
        btnMultiply.setOnClickListener { multiplyImages() }
        val multiplyParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 48, 0, 16) }
        layout.addView(btnMultiply, multiplyParams)

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
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }
        layout.addView(statusText)

        // Result section
        layout.addView(sectionLabel("Result"))
        previewResult = imagePreview()
        layout.addView(previewResult)

        // Save button
        btnSave = styledButton("Save Result to Gallery").apply {
            isEnabled = false
        }
        btnSave.setOnClickListener { saveResult() }
        layout.addView(btnSave)

        root.addView(layout)
        setContentView(root)
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
                btnPick1.text = "Image 1 loaded (${bitmap.width}x${bitmap.height})"
            }
            PICK_IMAGE_2 -> {
                bitmap2 = bitmap
                preview2.setImageBitmap(bitmap)
                btnPick2.text = "Image 2 loaded (${bitmap.width}x${bitmap.height})"
            }
        }

        btnMultiply.isEnabled = (bitmap1 != null && bitmap2 != null)
        if (bitmap1 != null && bitmap2 != null) {
            statusText.text = "Ready! Output: ${
                minOf(bitmap1!!.width, bitmap2!!.width)
            }x${minOf(bitmap1!!.height, bitmap2!!.height)}"
        }
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

    private fun multiplyImages() {
        val bmp1 = bitmap1 ?: return
        val bmp2 = bitmap2 ?: return

        btnMultiply.isEnabled = false
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

                    // Extract ARGB channels (each 8-bit: 0-255)
                    val a1 = (p1 shr 24) and 0xFF
                    val r1 = (p1 shr 16) and 0xFF
                    val g1 = (p1 shr 8) and 0xFF
                    val b1 = p1 and 0xFF

                    val a2 = (p2 shr 24) and 0xFF
                    val r2 = (p2 shr 16) and 0xFF
                    val g2 = (p2 shr 8) and 0xFF
                    val b2 = p2 and 0xFF

                    // Multiply each channel, modulo 256 (8-bit overflow wrap)
                    val a = (a1 * a2) % 256
                    val r = (r1 * r2) % 256
                    val g = (g1 * g2) % 256
                    val b = (b1 * b2) % 256

                    resultPixels[x] = (a shl 24) or (r shl 16) or (g shl 8) or b
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
                btnMultiply.isEnabled = true
                btnSave.isEnabled = true
            }
        }.start()
    }

    private fun saveResult() {
        val bmp = resultBitmap ?: return
        try {
            val filename = "multiplied_${System.currentTimeMillis()}.png"
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
