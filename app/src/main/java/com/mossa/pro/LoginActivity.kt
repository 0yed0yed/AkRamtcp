package com.mossa.pro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mossa.pro.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val TAG = "LoginActivity"
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AuthManager.init(this)
        Log.i(TAG, "onCreate")

        // ===== AUTO-LOGIN =====
        val savedUser = SecurePrefs.getString("saved_user")
        val savedPass = SecurePrefs.getString("saved_pass")

        if (!savedUser.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
            Log.i(TAG, "auto-login attempt: $savedUser")
            performAutoLogin(savedUser, savedPass)
            return
        }

        // ===== شاشة login عادية =====
        setupUi()
    }

    private fun setupUi() {
        AuthManager.currentUsername?.let {
            binding.usernameInput.setText(it)
        }

        binding.loginBtn.setOnClickListener {
            val u = binding.usernameInput.text.toString().trim()
            val p = binding.passwordInput.text.toString().trim()
            if (u.isEmpty() || p.isEmpty()) {
                showError("اكتب Username و Password")
                return@setOnClickListener
            }
            doLogin(u, p, rememberMe = true)
        }

        binding.clearBtn.setOnClickListener {
            binding.usernameInput.setText("")
            binding.passwordInput.setText("")
            binding.errorText.visibility = View.GONE
        }
    }

    private fun performAutoLogin(username: String, password: String) {
        setLoading(true)
        binding.errorText.visibility = View.GONE
        binding.usernameInput.setText(username)

        val deviceId = DeviceId.get(this)
        val deviceModel = DeviceId.model()

        lifecycleScope.launch {
            val result = ApiClient.login(username, password, deviceId, deviceModel)
            setLoading(false)

            if (result.ok && result.token != null) {
                // نجح → ادخل
                AuthManager.saveSession(username, result.token, result.expiresIn, result.accountExpiresAt)
                Toast.makeText(this@LoginActivity, "✓ مرحباً $username", Toast.LENGTH_SHORT).show()
                goToHex()
            } else {
                // فشل → اعرض السبب وامسح الـ creds
                Log.w(TAG, "auto-login failed: ${result.error}")
                SecurePrefs.remove("saved_user")
                SecurePrefs.remove("saved_pass")

                val msg = when {
                    result.error?.contains("منتهي") == true -> "الحساب انتهى"
                    result.error?.contains("موقوف") == true -> "الحساب موقوف"
                    result.error?.contains("جهاز") == true -> "الحساب مربوط بجهاز تاني"
                    result.error?.contains("صحيحة") == true -> "بيانات الدخول غلط"
                    else -> result.error ?: "فشل تسجيل الدخول التلقائي"
                }
                showError(msg)
                setupUi()
            }
        }
    }

    private fun doLogin(username: String, password: String, rememberMe: Boolean) {
        setLoading(true)
        binding.errorText.visibility = View.GONE

        val deviceId = DeviceId.get(this)
        val deviceModel = DeviceId.model()

        lifecycleScope.launch {
            val result = ApiClient.login(username, password, deviceId, deviceModel)
            setLoading(false)

            if (result.ok && result.token != null) {
                AuthManager.saveSession(username, result.token, result.expiresIn, result.accountExpiresAt)

                // ===== احفظ الـ creds للـ auto-login =====
                if (rememberMe) {
                    SecurePrefs.putString("saved_user", username)
                    SecurePrefs.putString("saved_pass", password)
                    Log.i(TAG, "creds saved for auto-login")
                }

                Toast.makeText(this@LoginActivity, "✓ مرحباً $username", Toast.LENGTH_SHORT).show()
                goToHex()
            } else {
                showError(result.error ?: "فشل تسجيل الدخول")
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loginBtn.isEnabled = !loading
        binding.usernameInput.isEnabled = !loading
        binding.passwordInput.isEnabled = !loading
    }

    private fun showError(msg: String) {
        binding.errorText.text = msg
        binding.errorText.visibility = View.VISIBLE
    }

    private fun goToHex() {
        startActivity(Intent(this, HexActivity::class.java))
        finish()
    }
}
