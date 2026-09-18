package com.mossa.pro

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class LoginResult(
        val ok: Boolean,
        val token: String?,
        val expiresIn: Long,
        val error: String?
    )

    data class VerifyResult(
        val ok: Boolean,
        val error: String?,
        val errorCode: Int = 0
    )

    suspend fun login(username: String, password: String, deviceId: String, deviceModel: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("username", username)
                addProperty("password", password)
                addProperty("device_id", deviceId)
                addProperty("device_model", deviceModel)
            }
            val req = Request.Builder()
                .url("${AuthConfig.BASE_URL}/api/login")
                .post(gson.toJson(body).toRequestBody(JSON))
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val obj = gson.fromJson(text, JsonObject::class.java)
                    LoginResult(
                        ok = true,
                        token = obj.get("token")?.asString,
                        expiresIn = obj.get("expires_in")?.asLong ?: 3600L,
                        error = null
                    )
                } else {
                    val err = try { gson.fromJson(text, JsonObject::class.java).get("detail")?.asString } catch (e: Exception) { null }
                    LoginResult(false, null, 0L, err ?: "HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "login: ${e.message}")
            LoginResult(false, null, 0L, e.message ?: "network error")
        }
    }

    suspend fun verify(token: String, deviceId: String): VerifyResult = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("token", token)
                addProperty("device_id", deviceId)
            }
            val req = Request.Builder()
                .url("${AuthConfig.BASE_URL}/api/verify")
                .post(gson.toJson(body).toRequestBody(JSON))
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    VerifyResult(true, null)
                } else {
                    val err = try { gson.fromJson(text, JsonObject::class.java).get("detail")?.asString } catch (e: Exception) { null }
                    VerifyResult(false, err ?: "HTTP ${resp.code}", resp.code)
                }
            }
        } catch (e: Exception) {
            VerifyResult(false, e.message ?: "network error", 0)
        }
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${AuthConfig.BASE_URL}/health").get().build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }
}
