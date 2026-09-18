package com.mossa.pro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mossa.pro.databinding.ActivityHexBinding

class HexActivity : AppCompatActivity() {

    private val TAG = "HexActivity"
    private lateinit var binding: ActivityHexBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHexBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.extractBtn.setOnClickListener { extract() }
        binding.skipBtn.setOnClickListener { goToMain() }
        binding.backBtn.setOnClickListener { finish() }
    }

    private fun extract() {
        val hex = binding.hexInput.text.toString().trim()
        if (hex.isEmpty()) {
            Toast.makeText(this, "الصق الـ hex أو اضغط SKIP", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progress.visibility = View.VISIBLE
        binding.extractBtn.isEnabled = false

        val result = MajorLoginParser.parse(hex)

        binding.progress.visibility = View.GONE
        binding.extractBtn.isEnabled = true

        if (result == null) {
            Toast.makeText(this, "✗ مش قادر أفك الـ hex", Toast.LENGTH_LONG).show()
            return
        }

        // احفظ المفاتيح مؤقتاً في الـ prefs — MainActivity هيقراها
        SecurePrefs.putString("pending_key", result.keyCsv())
        SecurePrefs.putString("pending_iv", result.ivCsv())

        Toast.makeText(this, "✓ تم استخراج المفاتيح", Toast.LENGTH_SHORT).show()
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
