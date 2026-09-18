package com.mossa.pro

data class PacketInfo(
    val number: Int,
    val hex: String,
    val direction: String,   // SERVER أو CLIENT
    val type: String,        // أول 4 حروف من الـ hex
    val decrypted: String?,  // AES-decrypted hex
    val decoded: Map<Int, Any>?,  // protobuf decoded
    val timestamp: Long,
    val datetime: String
)

object PacketTypes {
    val NAMES = mapOf(
        "0515" to "SQUAD",
        "1215" to "GUILD",
        "0E00" to "BASIC",
        "1200" to "MESSAGE",
        "1600" to "FRIEND",
        "0500" to "GHOST",
        "0300" to "AUTH",
        "1D00" to "UPDATE",
        "2F33" to "ENCRYPT",
        "0F00" to "CONNECT",
        "0F15" to "ADVANCED",
        "0E15" to "SYSTEM"
    )

    val COLORS = mapOf(
        "0515" to "#ff6b6b",
        "1215" to "#ffd93d",
        "0E00" to "#6bcb77",
        "1200" to "#4d96ff",
        "1600" to "#ff6bb5",
        "0500" to "#a66cff",
        "0300" to "#ff9f43",
        "1D00" to "#00d2d3",
        "2F33" to "#f368e0",
        "0F00" to "#ff9ff3",
        "0F15" to "#54a0ff",
        "0E15" to "#5f27cd"
    )

    fun name(code: String) = NAMES[code] ?: "UNKNOWN"
    fun color(code: String) = COLORS[code] ?: "#888888"
}
