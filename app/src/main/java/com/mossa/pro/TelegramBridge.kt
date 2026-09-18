package com.mossa.pro

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TelegramBridge {
    private const val TAG = "AkRamtcp-TG"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val API = "https://api.telegram.org/bot${Config.TG_TOKEN}/sendMessage"

    fun sendPacket(info: PacketInfo) {
        val text = formatPacket(info)
        sendText(text)
    }

    fun sendText(text: String) {
        try {
            val json = JSONObject().apply {
                put("chat_id", Config.TG_CHAT_ID)
                put("text", if (text.length > 4000) text.substring(0, 4000) else text)
                put("parse_mode", "HTML")
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(API).post(body).build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "send failed: ${e.message}")
                }
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "err: ${e.message}")
        }
    }

    private fun formatPacket(p: PacketInfo): String {
        val sb = StringBuilder()
        sb.append("<b>#").append(p.number).append(" — ").append(p.type).append("</b>\n")
        sb.append("Direction: ").append(p.direction).append("\n")
        sb.append("Time: ").append(p.datetime).append("\n")
        sb.append("Size: ").append(p.hex.length / 2).append(" bytes\n")
        if (p.decrypted != null) {
            sb.append("\n<b>Decrypted:</b>\n<code>")
            sb.append(p.decrypted.take(500))
            sb.append("</code>\n")
        }
        if (p.decoded != null) {
            sb.append("\n<b>Decoded:</b>\n<code>")
            sb.append(ProtobufDecoder.format(p.decoded).take(800))
            sb.append("</code>\n")
        }
        return sb.toString()
    }
}
