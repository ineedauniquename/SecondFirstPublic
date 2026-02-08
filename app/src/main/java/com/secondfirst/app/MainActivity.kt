package com.secondfirst.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.secondfirst.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var tapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnTap.setOnClickListener {
            tapCount++
            binding.tvCounter.text = getString(R.string.tap_count, tapCount)
        }

        binding.btnReset.setOnClickListener {
            tapCount = 0
            binding.tvCounter.text = getString(R.string.tap_count, tapCount)
        }
    }
}
