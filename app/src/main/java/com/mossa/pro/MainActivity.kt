package com.mossa.pro

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mossa.pro.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PacketAdapter
    private val allPackets = mutableListOf<PacketInfo>()
    private val shownPackets = mutableListOf<PacketInfo>()
    private var showOnlyImportant = true    // ← الافتراضي: المهمين بس
    private var countdownTimer: CountDownTimer? = null

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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

        // اطلب إذن الإشعارات
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Auth check
        AuthManager.init(this)
        if (!AuthManager.isLoggedIn()) {
            goToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // RecyclerView
        adapter = PacketAdapter(shownPackets) { packet ->
            val intent = Intent(this, PacketDetailActivity::class.java).apply {
                putExtra(PacketDetailActivity.EXTRA_NUMBER, packet.number)
            }
            startActivity(intent)
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        // Packet listener
        ProxyService.packetListener = { info ->
            runOnUiThread {
                allPackets.add(0, info)
                if (allPackets.size > 500) allPackets.removeAt(allPackets.size - 1)

                // فلتر
                if (!showOnlyImportant || PacketTypes.isImportant(info.type)) {
                    shownPackets.add(0, info)
                    if (shownPackets.size > 200) shownPackets.removeAt(shownPackets.size - 1)
                }
                adapter.notifyDataSetChanged()
                updatePacketCount()
            }
        }

        // فلاتر
        binding.filterImportant.setOnClickListener { setFilter(true) }
        binding.filterAll.setOnClickListener { setFilter(false) }

        // User label
        binding.userLabel.text = AuthManager.currentUsername ?: "user"

        // Nav
        binding.navHome.setOnClickListener { selectTab("home") }
        binding.navPackets.setOnClickListener { selectTab("packets") }
        binding.navAccount.setOnClickListener { selectTab("account") }

        // Home tab buttons
        binding.homeToggleBtn.setOnClickListener { toggleProxy() }
        binding.floatToggleBtn.setOnClickListener { checkAndToggleFloating() }
        binding.homeImportBtn.setOnClickListener {
            startActivity(Intent(this, HexActivity::class.java))
        }

        // Account logout
        binding.accountLogoutBtn.setOnClickListener { confirmLogout() }

        // Apply pending keys from HexActivity
        applyPendingKeys()

        // Start verifier
        AuthVerifier.onRevoked = {
            runOnUiThread {
                Toast.makeText(this, "الحساب اتوقف", Toast.LENGTH_LONG).show()
                forceLogout()
            }
        }
        AuthVerifier.start(this)

        // Load account info
        loadAccountInfo()

        // Initial state
        selectTab("home")
        updateProxyUI()
        updateFloatUI()
    }

    // ===== TAB SWITCH =====
    private fun selectTab(tab: String) {
        // Hide all
        binding.tabHome.visibility = View.GONE
        binding.tabPackets.visibility = View.GONE
        binding.tabAccount.visibility = View.GONE

        // Reset nav colors
        val dim = 0xFF64748B.toInt()
        val active = 0xFF4FC3F7.toInt()
        binding.navHomeLabel.setTextColor(dim)
        binding.navPacketsLabel.setTextColor(dim)
        binding.navAccountLabel.setTextColor(dim)
        binding.navHomeLabel.setTypeface(null, android.graphics.Typeface.NORMAL)
        binding.navPacketsLabel.setTypeface(null, android.graphics.Typeface.NORMAL)
        binding.navAccountLabel.setTypeface(null, android.graphics.Typeface.NORMAL)

        when (tab) {
            "home" -> {
                binding.tabHome.visibility = View.VISIBLE
                binding.navHomeLabel.setTextColor(active)
                binding.navHomeLabel.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            "packets" -> {
                binding.tabPackets.visibility = View.VISIBLE
                binding.navPacketsLabel.setTextColor(active)
                binding.navPacketsLabel.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            "account" -> {
                binding.tabAccount.visibility = View.VISIBLE
                binding.navAccountLabel.setTextColor(active)
                binding.navAccountLabel.setTypeface(null, android.graphics.Typeface.BOLD)
                loadAccountInfo()
            }
        }
    }

    // ===== PROXY =====
    @Volatile private var lastToggleTime = 0L

    private fun toggleProxy() {
        // ✅ منع الضغط المزدوج
        val now = System.currentTimeMillis()
        if (now - lastToggleTime < 1500) {
            Log.w(TAG, "toggle ignored (too fast)")
            return
        }
        lastToggleTime = now

        if (ProxyService.isRunning) {
            Log.i(TAG, "toggle → STOP")
            stopProxy()
        } else {
            Log.i(TAG, "toggle → START")
            startProxy()
        }
    }

    private fun startProxy() {
        // ✅ أولاً — مرر المفاتيح النشطة للـ service
        val activeKey = SecurePrefs.getString("active_key")
        val activeIv = SecurePrefs.getString("active_iv")
        if (!activeKey.isNullOrEmpty() && !activeIv.isNullOrEmpty()) {
            try {
                val k = parseKey(activeKey)
                val v = parseKey(activeIv)
                if (k.size == 16 && v.size == 16) {
                    ProxyService.updateKeys(k, v)
                    Log.i(TAG, "Passed active keys to service")
                }
            } catch (e: Exception) {
                Log.e(TAG, "applyKeys on start: ${e.message}")
            }
        }

        val intent = Intent(this, ProxyService::class.java).apply {
            action = ProxyService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateProxyUI()
    }

    private fun stopProxy() {
        val intent = Intent(this, ProxyService::class.java).apply {
            action = ProxyService.ACTION_STOP
        }
        startService(intent)
        updateProxyUI()
    }

    private fun updateProxyUI() {
        val running = ProxyService.isRunning
        if (running) {
            binding.statusDot.background = getDrawable(R.drawable.dot_green)
            binding.statusLabel.text = "RUNNING"
            binding.statusLabel.setTextColor(0xFF4ADE80.toInt())
            binding.homeToggleBtn.text = "■  STOP"
            binding.homeToggleBtn.background = getDrawable(R.drawable.bg_btn_danger)
            binding.proxyState.text = "ON"
            binding.proxyState.setTextColor(0xFF4ADE80.toInt())
        } else {
            binding.statusDot.background = getDrawable(R.drawable.dot_red)
            binding.statusLabel.text = "READY"
            binding.statusLabel.setTextColor(0xFF64748B.toInt())
            binding.homeToggleBtn.text = "▶  START"
            binding.homeToggleBtn.background = getDrawable(R.drawable.bg_btn_success)
            binding.proxyState.text = "OFF"
            binding.proxyState.setTextColor(0xFF64748B.toInt())
        }
    }

    // ===== FLOATING =====
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
        binding.floatToggleBtn.postDelayed({ updateFloatUI() }, 300)
    }

    private fun updateFloatUI() {
        val visible = FloatingWindowService.isVisible
        if (visible) {
            binding.floatToggleBtn.text = "CLOSE"
            binding.floatToggleBtn.background = getDrawable(R.drawable.bg_btn_danger)
            binding.floatState.text = "شغالة"
            binding.floatState.setTextColor(0xFF4ADE80.toInt())
        } else {
            binding.floatToggleBtn.text = "OPEN"
            binding.floatToggleBtn.background = getDrawable(R.drawable.bg_btn_primary)
            binding.floatState.text = "مقفولة"
            binding.floatState.setTextColor(0xFF64748B.toInt())
        }
    }

    // ===== FILTER =====
    private fun setFilter(important: Boolean) {
        showOnlyImportant = important
        shownPackets.clear()
        if (important) {
            shownPackets.addAll(allPackets.filter { PacketTypes.isImportant(it.type) })
        } else {
            shownPackets.addAll(allPackets)
        }
        adapter.notifyDataSetChanged()
        updateFilterUi()
    }

    private fun updateFilterUi() {
        if (showOnlyImportant) {
            binding.filterImportant.background = getDrawable(R.drawable.bg_btn_accent)
            binding.filterImportant.setTextColor(0xFF0A0D12.toInt())
            binding.filterAll.background = getDrawable(R.drawable.bg_btn_outline)
            binding.filterAll.setTextColor(0xFF94A3B8.toInt())
        } else {
            binding.filterImportant.background = getDrawable(R.drawable.bg_btn_outline)
            binding.filterImportant.setTextColor(0xFF94A3B8.toInt())
            binding.filterAll.background = getDrawable(R.drawable.bg_btn_accent)
            binding.filterAll.setTextColor(0xFF0A0D12.toInt())
        }
    }

    // ===== PACKET COUNT =====
    private fun updatePacketCount() {
        binding.homePacketCount.text = allPackets.size.toString()
        binding.totalLabel.text = "PACKETS · ${shownPackets.size} / ${allPackets.size}"
    }

    // ===== KEYS =====
    private fun applyPendingKeys() {
        val key = SecurePrefs.getString("pending_key")
        val iv = SecurePrefs.getString("pending_iv")
        if (!key.isNullOrEmpty() && !iv.isNullOrEmpty()) {
            try {
                val k = parseKey(key)
                val v = parseKey(iv)
                if (k.size == 16 && v.size == 16) {
                    ProxyService.updateKeys(k, v)
                    binding.keysState.text = "custom"
                    binding.keysState.setTextColor(0xFF4ADE80.toInt())
                    Toast.makeText(this, "✓ تم تطبيق المفاتيح", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "applyPendingKeys: ${e.message}")
            }
            SecurePrefs.remove("pending_key")
            SecurePrefs.remove("pending_iv")
        }
    }

    private fun parseKey(s: String): IntArray {
        return if (s.contains(",")) {
            s.split(",").map { it.trim().toInt() }.toIntArray()
        } else {
            val clean = s.replace(" ", "")
            val out = IntArray(clean.length / 2)
            for (i in out.indices) {
                out[i] = ((Character.digit(clean[i * 2], 16) shl 4) +
                          Character.digit(clean[i * 2 + 1], 16))
            }
            out
        }
    }

    // ===== ACCOUNT =====
    private fun loadAccountInfo() {
        binding.accountUsername.text = AuthManager.currentUsername ?: "--"

        val devModel = DeviceId.model()
        val devId = DeviceId.get(this)
        binding.accountDevice.text = devModel
        binding.accountDeviceId.text = devId.take(32) + "..."

        val accExp = AuthManager.getAccountExpiry()
        val tokenExp = AuthManager.getTokenExpiry()
        startCountdowns(accExp, tokenExp)
    }

    private fun startCountdowns(accountExp: Long, tokenExp: Long) {
        countdownTimer?.cancel()

        if (accountExp > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            binding.accountExpiryDate.text = "ينتهي في: " + sdf.format(Date(accountExp * 1000))
        } else {
            binding.accountExpiryDate.text = "ينتهي في: --"
        }

        countdownTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val nowSec = System.currentTimeMillis() / 1000

                if (accountExp > 0) {
                    val left = accountExp - nowSec
                    if (left <= 0) {
                        binding.accountTimeLeft.text = "انتهى"
                        binding.accountTimeLeft.setTextColor(0xFFF87171.toInt())
                        binding.accountStatus.text = "EXPIRED"
                        binding.accountStatus.setTextColor(0xFFFBBF24.toInt())
                    } else {
                        binding.accountTimeLeft.text = formatDuration(left)
                        binding.accountTimeLeft.setTextColor(0xFF4ADE80.toInt())
                        binding.accountStatus.text = "ACTIVE"
                        binding.accountStatus.setTextColor(0xFF4ADE80.toInt())
                    }
                } else {
                    binding.accountTimeLeft.text = "دائم"
                    binding.accountTimeLeft.setTextColor(0xFF4ADE80.toInt())
                    binding.accountStatus.text = "ACTIVE"
                    binding.accountStatus.setTextColor(0xFF4ADE80.toInt())
                }

                if (tokenExp > 0) {
                    val tLeft = tokenExp - nowSec
                    if (tLeft <= 0) {
                        binding.tokenTimeLeft.text = "جاري التجديد..."
                        binding.tokenTimeLeft.setTextColor(0xFFFBBF24.toInt())
                    } else {
                        binding.tokenTimeLeft.text = formatDurationShort(tLeft)
                        binding.tokenTimeLeft.setTextColor(0xFFFBBF24.toInt())
                    }
                } else {
                    binding.tokenTimeLeft.text = "--"
                }
            }

            override fun onFinish() {}
        }.start()
    }

    private fun formatDuration(sec: Long): String {
        if (sec <= 0) return "انتهى"
        val days = sec / 86400
        val hours = (sec % 86400) / 3600
        val minutes = (sec % 3600) / 60
        val seconds = sec % 60
        return when {
            days > 0 -> String.format(Locale.US, "%d يوم · %02d:%02d:%02d", days, hours, minutes, seconds)
            hours > 0 -> String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            else -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatDurationShort(sec: Long): String {
        if (sec <= 0) return "00:00"
        val hours = sec / 3600
        val minutes = (sec % 3600) / 60
        val seconds = sec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    // ===== LOGOUT =====
    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("خروج")
            .setMessage("هتحتاج تسجل دخول تاني بعد الخروج. متأكد؟")
            .setPositiveButton("نعم") { _, _ -> forceLogout() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun forceLogout() {
        AuthVerifier.stop()
        AuthManager.logoutComplete()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // ===== LIFECYCLE =====
    override fun onResume() {
        super.onResume()
        if (!AuthManager.isLoggedIn()) {
            goToLogin()
            return
        }
        updateProxyUI()
        updateFloatUI()
        updatePacketCount()
        loadAccountInfo()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        AuthVerifier.stop()
    }
}
