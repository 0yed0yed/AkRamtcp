package com.mossa.pro

data class PacketInfo(
    val number: Int,
    val hex: String,
    val direction: String,
    val type: String,
    val decrypted: String?,
    val decoded: Map<Int, Any>?,
    val timestamp: Long,
    val datetime: String
)

enum class PacketCategory(val label: String, val color: String) {
    AUTH("🔐 AUTH", "#FF6B6B"),
    SQUAD("👥 SQUAD", "#FFD93D"),
    ROOM("🏠 ROOM", "#A78BFA"),
    MESSAGE("💬 MESSAGE", "#4D96FF"),
    GUILD("🛡️ GUILD", "#6BCB77"),
    FRIEND("🤝 FRIEND", "#FF6BB5"),
    CONNECT("🔌 CONNECT", "#00D2D3"),
    SYSTEM("⚙️ SYSTEM", "#94A3B8"),
    UPDATE("🔄 UPDATE", "#F59E0B"),
    OTHER("❓ OTHER", "#64748B")
}

object PacketTypes {

    // ===== كل الأنواع المعروفة =====
    val NAMES = mapOf(
        // Auth
        "0300" to "AUTH",

        // Squad / Team
        "0500" to "GHOST",
        "0515" to "SQUAD",

        // Room
        "0E00" to "BASIC",
        "0E15" to "ROOM",

        // Info / Connections
        "0F00" to "CONNECT",
        "0F15" to "ADVANCED",

        // Message
        "1200" to "MESSAGE",
        "1215" to "GUILD",

        // Friend
        "1600" to "FRIEND",

        // Update
        "1D00" to "UPDATE",

        // Encrypt
        "2F33" to "ENCRYPT"
    )

    val COLORS = mapOf(
        "0300" to "#FF6B6B",
        "0500" to "#A66CFF",
        "0515" to "#FF6B6B",
        "0E00" to "#6BCB77",
        "0E15" to "#5F27CD",
        "0F00" to "#FF9FF3",
        "0F15" to "#54A0FF",
        "1200" to "#4D96FF",
        "1215" to "#FFD93D",
        "1600" to "#FF6BB5",
        "1D00" to "#00D2D3",
        "2F33" to "#F368E0"
    )

    // ===== الفئات =====
    val CATEGORIES = mapOf(
        "0300" to PacketCategory.AUTH,

        "0500" to PacketCategory.SQUAD,
        "0515" to PacketCategory.SQUAD,

        "0E00" to PacketCategory.ROOM,
        "0E15" to PacketCategory.ROOM,

        "0F00" to PacketCategory.CONNECT,
        "0F15" to PacketCategory.CONNECT,

        "1200" to PacketCategory.MESSAGE,

        "1215" to PacketCategory.GUILD,

        "1600" to PacketCategory.FRIEND,

        "1D00" to PacketCategory.UPDATE,

        "2F33" to PacketCategory.SYSTEM
    )

    fun name(code: String) = NAMES[code] ?: "UNKNOWN"
    fun color(code: String) = COLORS[code] ?: "#64748B"
    fun category(code: String) = CATEGORIES[code] ?: PacketCategory.OTHER

    // ===== الأنواع المهمة — اللي عايز إشعار أو تسجيل فوري =====
    val IMPORTANT = setOf(
        "1200",  // MESSAGE
        "0500",  // GHOST (Squad)
        "0515",  // SQUAD
        "1215",  // GUILD
        "0300"   // AUTH
    )

    fun isImportant(code: String) = IMPORTANT.contains(code)
}
