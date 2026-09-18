package com.mossa.pro

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mossa.pro.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PacketAdapter
    private val packets = mutableListOf<PacketInfo>()

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

        updateStatus()
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
        binding.statusLabel.text = "RUNNING"
        binding.statusLabel.setTextColor(0xFF6BCB77.toInt())
        binding.startBtn.isEnabled = false
        binding.stopBtn.isEnabled = true
    }

    private fun stopProxy() {
        val intent = Intent(this, ProxyService::class.java).apply {
            action = ProxyService.ACTION_STOP
        }
        startService(intent)
        binding.statusLabel.text = "READY"
        binding.statusLabel.setTextColor(0xFFB0C4DE.toInt())
        binding.startBtn.isEnabled = true
        binding.stopBtn.isEnabled = false
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
        binding.statusLabel.text = if (ProxyService.isRunning) "RUNNING" else "READY"
        binding.startBtn.isEnabled = !ProxyService.isRunning
        binding.stopBtn.isEnabled = ProxyService.isRunning
    }

    private fun updateTotal() {
        binding.totalLabel.text = "TOTAL: ${packets.size}"
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
