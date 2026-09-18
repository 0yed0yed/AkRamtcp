package com.mossa.pro

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mossa.pro.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PacketAdapter
    private val packets = mutableListOf<PacketInfo>()

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            toggleFloating()
        } else {
            Toast.makeText(this, "Overlay permission مرفوضة", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PacketAdapter(packets) { packet ->
            val intent = Intent(this, PacketDetailActivity::class.java).apply {
                putExtra(PacketDetailActivity.EXTRA_NUMBER, packet.number)
            }
            startActivity(intent)
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        ProxyService.packetListener = { info ->
            runOnUiThread {
                packets.add(0, info)
                if (packets.size > 200) packets.removeAt(packets.size - 1)
                adapter.notifyDataSetChanged()
                updateTotal()
            }
        }

        binding.startBtn.setOnClickListener { startProxy() }
        binding.stopBtn.setOnClickListener { stopProxy() }
        binding.applyKeys.setOnClickListener { applyKeys() }
        binding.floatBtn.setOnClickListener { checkAndToggleFloating() }

        updateStatus()
        updateFloatButton()
    }

    private fun startProxy() {
        val intent = Intent(this, ProxyService::class.java).apply {
            action = ProxyService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        binding.statusLabel.text = getString(R.string.status_running)
        binding.statusLabel.setTextColor(0xFF4ADE80.toInt())
        binding.statusDot.background = getDrawable(R.drawable.dot_green)
        binding.startBtn.isEnabled = false
        binding.stopBtn.isEnabled = true
    }

    private fun stopProxy() {
        val intent = Intent(this, ProxyService::class.java).apply {
            action = ProxyService.ACTION_STOP
        }
        startService(intent)
        binding.statusLabel.text = getString(R.string.status_idle)
        binding.statusLabel.setTextColor(0xFF94A3B8.toInt())
        binding.statusDot.background = getDrawable(R.drawable.dot_red)
        binding.startBtn.isEnabled = true
        binding.stopBtn.isEnabled = false
    }

    private fun checkAndToggleFloating() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermission.launch(intent)
        } else {
            toggleFloating()
        }
    }

    private fun toggleFloating() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (FloatingWindowService.isVisible) {
            intent.action = FloatingWindowService.ACTION_HIDE
            startService(intent)
        } else {
            intent.action = FloatingWindowService.ACTION_SHOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        binding.floatBtn.postDelayed({ updateFloatButton() }, 300)
    }

    private fun updateFloatButton() {
        binding.floatBtn.text = if (FloatingWindowService.isVisible)
            getString(R.string.float_on)
        else
            getString(R.string.float_off)
    }

    private fun applyKeys() {
        try {
            val keyText = binding.keyInput.text.toString().trim()
            val ivText = binding.ivInput.text.toString().trim()
            val key = parseKey(keyText)
            val iv = parseKey(ivText)
            if (key.size != 16 || iv.size != 16) {
                Toast.makeText(this, "KEY & IV لازم 16 بايت", Toast.LENGTH_LONG).show()
                return
            }
            Toast.makeText(this, "✓ Keys applied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseKey(s: String): IntArray {
        return if (s.contains(",")) {
            s.split(",").map { it.trim().toInt() }.toIntArray()
        } else {
            val clean = s.replace(" ", "")
            val out = IntArray(clean.length / 2)
            for (i in out.indices) {
                out[i] = ((Character.digit(clean[i*2], 16) shl 4) +
                          Character.digit(clean[i*2+1], 16))
            }
            out
        }
    }

    private fun updateStatus() {
        binding.statusLabel.text = if (ProxyService.isRunning)
            getString(R.string.status_running)
        else
            getString(R.string.status_idle)
        binding.startBtn.isEnabled = !ProxyService.isRunning
        binding.stopBtn.isEnabled = ProxyService.isRunning
    }

    private fun updateTotal() {
        binding.totalLabel.text = "TOTAL · ${packets.size}"
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateFloatButton()
    }
}
