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
        Log.i(TAG, "onCreate: isLoggedIn=${AuthManager.isLoggedIn()}")

        if (AuthManager.isLoggedIn()) {
            Log.i(TAG, "already logged in → goToMain")
            goToMain()
            return
        }

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

        Log.i(TAG, "login: $username / device=$deviceModel")

        lifecycleScope.launch {
            val result = ApiClient.login(username, password, deviceId, deviceModel)
            setLoading(false)

            Log.i(TAG, "login result: ok=${result.ok} err=${result.error}")

            if (result.ok && result.token != null) {
                AuthManager.saveSession(username, result.token, result.expiresIn)

                // ✅ تحقق إن الحفظ نجح
                val checkToken = SecurePrefs.getString(AuthConfig.PREF_TOKEN)
                val checkUser = SecurePrefs.getString(AuthConfig.PREF_USERNAME)
                Log.i(TAG, "after save: user=$checkUser token=${checkToken?.take(20)}")

                if (checkToken.isNullOrEmpty()) {
                    Log.e(TAG, "✗ SAVE FAILED — token not persisted!")
                    showError("مشكلة في الحفظ — جرب تاني")
                    return@launch
                }

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
        Log.i(TAG, "goToMain()")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
