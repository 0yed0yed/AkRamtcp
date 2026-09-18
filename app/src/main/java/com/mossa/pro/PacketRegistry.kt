package com.mossa.pro

object PacketRegistry {
    private val map = LinkedHashMap<Int, PacketInfo>()

    @Synchronized
    fun put(p: PacketInfo) {
        map[p.number] = p
        if (map.size > 2000) {
            val first = map.keys.firstOrNull()
            if (first != null) map.remove(first)
        }
    }

    @Synchronized
    fun get(number: Int): PacketInfo? = map[number]

    @Synchronized
    fun clear() = map.clear()
}
