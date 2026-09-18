package com.mossa.pro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mossa.pro.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AuthManager.init(this)

        // لو مسجّل دخول قبل كده → روح على طول
        if (AuthManager.isLoggedIn()) {
            goToMain()
            return
        }

        // املأ الـ username لو محفوظ
        AuthManager.currentUsername?.let {
            binding.usernameInput.setText(it)
        }

        binding.loginBtn.setOnClickListener { doLogin() }
        binding.clearBtn.setOnClickListener {
            binding.usernameInput.setText("")
            binding.passwordInput.setText("")
            binding.errorText.visibility = View.GONE
        }
    }

    private fun doLogin() {
        val username = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            showError("اكتب Username و Password")
            return
        }

        setLoading(true)
        binding.errorText.visibility = View.GONE

        val deviceId = DeviceId.get(this)
        val deviceModel = DeviceId.model()

        lifecycleScope.launch {
            val result = ApiClient.login(username, password, deviceId, deviceModel)
            setLoading(false)

            if (result.ok && result.token != null) {
                AuthManager.saveSession(username, result.token, result.expiresIn)
                Toast.makeText(this@LoginActivity, "✓ مرحباً $username", Toast.LENGTH_SHORT).show()
                goToMain()
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

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
