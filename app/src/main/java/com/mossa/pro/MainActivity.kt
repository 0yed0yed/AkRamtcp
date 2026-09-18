package com.mossa.pro

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

        // 1. تأكد من الـ auth
        AuthManager.init(this)
        if (!AuthManager.isLoggedIn()) {
            goToLogin()
            return
        }

        // 2. Anti-tamper check
        AntiTamper.check(this)?.let { err ->
            AlertDialog.Builder(this)
                .setTitle("Security Warning")
                .setMessage(err)
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }

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
        binding.importBtn.setOnClickListener { importFromHex() }
        binding.importClear.setOnClickListener { binding.importHex.setText("") }

        // زر logout — لو مش موجود في الـ layout، نضيفه ديناميكياً
        setupLogout()

        // ابدأ الـ verifier
        AuthVerifier.onRevoked = {
            runOnUiThread {
                Toast.makeText(this, "الحساب اتوقف", Toast.LENGTH_LONG).show()
                forceLogout()
            }
        }
        AuthVerifier.start(this)

        // اعرض اسم المستخدم
        binding.statusLabel.text = "مرحباً ${AuthManager.currentUsername ?: ""}"
        updateStatus()
        updateFloatButton()
    }

    private fun setupLogout() {
        // ضيف زر logout بجانب العنوان
        binding.root.post {
            try {
                val parent = binding.statusLabel.parent as? android.view.ViewGroup ?: return@post
                if (parent.findViewById<TextView>(9999) != null) return@post
                val tv = TextView(this).apply {
                    id = 9999
                    text = "LOGOUT"
                    setTextColor(0xFFF87171.toInt())
                    textSize = 11f
                    setPadding(20, 10, 20, 10)
                    setOnClickListener { confirmLogout() }
                }
                parent.addView(tv)
            } catch (_: Exception) {}
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("متأكد من تسجيل الخروج؟")
            .setPositiveButton("نعم") { _, _ -> forceLogout() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun forceLogout() {
        AuthVerifier.stop()
        AuthManager.logout()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun importFromHex() {
        val hex = binding.importHex.text.toString().trim()
        if (hex.isEmpty()) {
            Toast.makeText(this, "الصق الـ hex الأول", Toast.LENGTH_SHORT).show()
            return
        }

        val result = MajorLoginParser.parse(hex)
        if (result == null) {
            Toast.makeText(this, "✗ مش قادر أفك الـ hex", Toast.LENGTH_LONG).show()
            return
        }

        binding.keyInput.setText(result.keyCsv())
        binding.ivInput.setText(result.ivCsv())
        applyKeysSilent(result.key, result.iv)

        val msg = "✓ تم استخراج المفاتيح\nKEY: ${result.keyHex()}\nIV: ${result.ivHex()}"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
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
            applyKeysSilent(key, iv)
            Toast.makeText(this, "✓ Keys applied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyKeysSilent(keyInts: IntArray, ivInts: IntArray) {
        ProxyService.updateKeys(keyInts, ivInts)
    }

    private fun applyKeysSilent(key: ByteArray, iv: ByteArray) {
        val k = IntArray(key.size) { key[it].toInt() and 0xFF }
        val v = IntArray(iv.size) { iv[it].toInt() and 0xFF }
        applyKeysSilent(k, v)
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
        binding.startBtn.isEnabled = !ProxyService.isRunning
        binding.stopBtn.isEnabled = ProxyService.isRunning
    }

    private fun updateTotal() {
        binding.totalLabel.text = "TOTAL · ${packets.size}"
    }

    override fun onResume() {
        super.onResume()
        // تحقق إن لسه مسجّل دخول
        if (!AuthManager.isLoggedIn()) {
            goToLogin()
            return
        }
        updateStatus()
        updateFloatButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        AuthVerifier.stop()
    }
}
