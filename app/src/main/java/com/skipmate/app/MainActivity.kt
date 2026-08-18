package com.skipmate.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.skipmate.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEnable.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        handler.post(checkRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(checkRunnable)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${SkipAdAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(service, ignoreCase = true)
    }

    private fun updateUI() {
        if (isAccessibilityEnabled()) {
            binding.layoutInactive.visibility = View.GONE
            binding.layoutActive.visibility = View.VISIBLE
        } else {
            binding.layoutInactive.visibility = View.VISIBLE
            binding.layoutActive.visibility = View.GONE
        }
    }
}